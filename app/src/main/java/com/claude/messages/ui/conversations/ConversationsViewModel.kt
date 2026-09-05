package com.claude.messages.ui.conversations

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claude.messages.data.db.NotificationRule
import com.claude.messages.data.db.SenderMatch
import com.claude.messages.data.db.ThreadMeta
import com.claude.messages.data.model.Conversation
import com.claude.messages.data.model.Message
import com.claude.messages.data.prefs.UiPrefs
import com.claude.messages.data.repo.ContactsRepository
import com.claude.messages.di.ServiceLocator
import com.claude.messages.notifications.IncomingMessage
import com.claude.messages.notifications.MessageNotifier
import com.claude.messages.notifications.RuleEngine
import com.claude.messages.util.DefaultSmsHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A search hit, already resolved to a display name. */
data class SearchHit(
    val message: Message,
    val displayName: String,
)

data class ConversationsUiState(
    val loading: Boolean = true,
    val conversations: List<Conversation> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<SearchHit> = emptyList(),
    val searching: Boolean = false,
    val showArchived: Boolean = false,
    val isDefaultSmsApp: Boolean = true,
    val hasSmsPermission: Boolean = true,
    val archivedCount: Int = 0,
    val selected: Set<Long> = emptySet(),
    /** Set when the inbox could not be read at all. */
    val loadError: String? = null,
    /** The user closed the setup banner; Settings still reports the real state. */
    val setupDismissed: Boolean = false,
) {
    val inSelectionMode: Boolean get() = selected.isNotEmpty()
    val selectedConversations: List<Conversation>
        get() = conversations.filter { it.threadId in selected }
}

class ConversationsViewModel(app: Application) : AndroidViewModel(app) {

    private val smsRepo = ServiceLocator.smsRepository(app)
    private val contacts = ServiceLocator.contactsRepository(app)
    private val db = ServiceLocator.database(app)
    private val notifier = MessageNotifier(app)
    private val uiPrefs = UiPrefs(app)

    private val _state = MutableStateFlow(ConversationsUiState())
    val state: StateFlow<ConversationsUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            // collectLatest coalesces the burst of provider notifications a
            // single write produces into one reload.
            ServiceLocator.dataChanged.collectLatest { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val isDefault = DefaultSmsHelper.isDefault(app)
            val hasPermission = DefaultSmsHelper.hasSmsPermissions(app)
            // Completing setup should clear a previous dismissal, so the banner
            // comes back if the role is ever revoked.
            if (isDefault && hasPermission) uiPrefs.setupBannerDismissed = false
            _state.update {
                it.copy(
                    isDefaultSmsApp = isDefault,
                    hasSmsPermission = hasPermission,
                    setupDismissed = uiPrefs.setupBannerDismissed,
                )
            }
            if (!hasPermission) {
                _state.update { it.copy(loading = false, conversations = emptyList()) }
                return@launch
            }

            // Reading the SMS provider goes through OEM code with its own quirks.
            // A failure here should show an empty inbox, never take down the app.
            val raw = runCatching { smsRepo.loadConversations() }
                .onFailure { Log.e(TAG, "Could not load conversations", it) }
                .getOrNull()

            if (raw == null) {
                _state.update {
                    it.copy(loading = false, loadError = "Couldn't read your messages")
                }
                return@launch
            }

            val metas = runCatching { db.threadMetaDao().getAll() }
                .getOrDefault(emptyList()).associateBy { it.threadId }
            val rules = runCatching { db.ruleDao().getEnabled() }.getOrDefault(emptyList())

            // One bad row shouldn't hide every other conversation.
            val decorated = raw.mapNotNull { conv ->
                runCatching { decorate(conv, metas[conv.threadId], rules) }
                    .onFailure { Log.e(TAG, "Skipping thread ${conv.threadId}", it) }
                    .getOrNull()
            }

            val visible = decorated
                .filter { it.archived == _state.value.showArchived }
                .sortedWith(
                    compareByDescending<Conversation> { it.pinned }.thenByDescending { it.date }
                )

            _state.update {
                it.copy(
                    loading = false,
                    loadError = null,
                    conversations = visible,
                    archivedCount = decorated.count { c -> c.archived },
                    // Drop selections for conversations that no longer exist.
                    selected = it.selected intersect visible.map { c -> c.threadId }.toSet(),
                )
            }
        }
    }

    /** Resolves names, photos and per-thread state for one conversation row. */
    private suspend fun decorate(
        conv: Conversation,
        meta: ThreadMeta?,
        rules: List<NotificationRule>,
    ): Conversation {
        val contact = contacts.lookup(conv.primaryAddress)
        val memberNames = conv.addresses.map { addr ->
            contacts.lookup(addr)?.name?.takeIf { it.isNotBlank() }
                ?: ContactsRepository.formatNumber(addr)
        }
        val custom = meta?.customName
        val name = when {
            !custom.isNullOrBlank() -> custom
            memberNames.isEmpty() -> "(no recipient)"
            else -> memberNames.joinToString(", ")
        }

        // Only sender-based rules are meaningful for a whole conversation;
        // a content rule describes one message, not the thread.
        val ruleLabel = meta?.forcedRuleId
            ?.let { id -> rules.firstOrNull { it.id == id }?.name }
            ?: RuleEngine.match(
                rules.filter { it.senderMatch != SenderMatch.ANY },
                IncomingMessage(
                    address = conv.primaryAddress,
                    body = "",
                    senderIsKnownContact = contact != null,
                ),
            )?.rule?.name

        return conv.copy(
            displayName = name,
            photoUri = contact?.photoUri,
            pinned = meta?.pinned == true,
            archived = meta?.archived == true,
            muted = meta?.muted == true,
            ruleLabel = ruleLabel,
        )
    }

    fun dismissSetupBanner() {
        uiPrefs.setupBannerDismissed = true
        _state.update { it.copy(setupDismissed = true) }
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query, searching = query.isNotBlank()) }
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            val hits = runCatching {
                smsRepo.searchMessages(query).map { message ->
                    SearchHit(
                        message = message,
                        displayName = contacts.lookup(message.address)?.name
                            ?.takeIf { it.isNotBlank() }
                            ?: ContactsRepository.formatNumber(message.address),
                    )
                }
            }.getOrDefault(emptyList())
            // Ignore results for a query the user has already moved on from.
            if (_state.value.searchQuery == query) {
                _state.update { it.copy(searchResults = hits) }
            }
        }
    }

    fun closeSearch() {
        _state.update { it.copy(searchQuery = "", searchResults = emptyList(), searching = false) }
    }

    fun toggleArchivedView() {
        _state.update { it.copy(showArchived = !it.showArchived, selected = emptySet()) }
        refresh()
    }

    // ------------------------------------------------------- selection mode

    fun toggleSelection(threadId: Long) {
        _state.update {
            val next = it.selected.toMutableSet()
            if (!next.add(threadId)) next.remove(threadId)
            it.copy(selected = next)
        }
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    fun deleteSelected() {
        val ids = _state.value.selected
        viewModelScope.launch {
            ids.forEach {
                smsRepo.deleteThread(it)
                db.threadMetaDao().delete(it)
                notifier.cancel(it)
            }
            clearSelection()
            refresh()
        }
    }

    fun markSelectedRead() {
        val ids = _state.value.selected
        viewModelScope.launch {
            ids.forEach {
                smsRepo.markThreadRead(it)
                notifier.cancel(it)
            }
            clearSelection()
            refresh()
        }
    }

    /** Archives, or unarchives when viewing the archive. */
    fun archiveSelected() {
        val target = !_state.value.showArchived
        updateSelectedMeta { it.copy(archived = target) }
    }

    /** Pins unless everything selected is already pinned, in which case it unpins. */
    fun togglePinSelected() {
        val allPinned = _state.value.selectedConversations.all { it.pinned }
        updateSelectedMeta { it.copy(pinned = !allPinned) }
    }

    fun toggleMuteSelected() {
        val allMuted = _state.value.selectedConversations.all { it.muted }
        updateSelectedMeta { it.copy(muted = !allMuted) }
    }

    private fun updateSelectedMeta(transform: (ThreadMeta) -> ThreadMeta) {
        val ids = _state.value.selected
        viewModelScope.launch {
            ids.forEach { id ->
                val current = db.threadMetaDao().get(id) ?: ThreadMeta(threadId = id)
                db.threadMetaDao().upsert(transform(current))
            }
            clearSelection()
            refresh()
        }
    }

    private companion object {
        const val TAG = "ConversationsVM"
    }
}
