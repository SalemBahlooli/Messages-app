package com.claude.messages.ui.conversations

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claude.messages.data.db.ThreadMeta
import com.claude.messages.data.model.Conversation
import com.claude.messages.data.repo.ContactsRepository
import com.claude.messages.data.model.Message
import com.claude.messages.di.ServiceLocator
import com.claude.messages.notifications.MessageNotifier
import com.claude.messages.notifications.RuleEngine
import com.claude.messages.notifications.IncomingMessage
import com.claude.messages.util.DefaultSmsHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConversationsUiState(
    val loading: Boolean = true,
    val conversations: List<Conversation> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<Message> = emptyList(),
    val searching: Boolean = false,
    val showArchived: Boolean = false,
    val isDefaultSmsApp: Boolean = true,
    val selected: Set<Long> = emptySet(),
) {
    val inSelectionMode: Boolean get() = selected.isNotEmpty()
}

class ConversationsViewModel(app: Application) : AndroidViewModel(app) {

    private val smsRepo = ServiceLocator.smsRepository(app)
    private val contacts = ServiceLocator.contactsRepository(app)
    private val db = ServiceLocator.database(app)
    private val notifier = MessageNotifier(app)

    private val _state = MutableStateFlow(ConversationsUiState())
    val state: StateFlow<ConversationsUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            ServiceLocator.dataChanged.collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val isDefault = DefaultSmsHelper.isDefault(getApplication())
            _state.update { it.copy(isDefaultSmsApp = isDefault) }
            if (!isDefault) {
                _state.update { it.copy(loading = false, conversations = emptyList()) }
                return@launch
            }

            val raw = smsRepo.loadConversations()
            val metas = raw.associate { conv ->
                conv.threadId to db.threadMetaDao().get(conv.threadId)
            }
            val rules = db.ruleDao().getEnabled()

            val decorated = raw.map { conv ->
                val meta = metas[conv.threadId]
                val contact = contacts.lookup(conv.primaryAddress)
                val custom = meta?.customName
                // Resolve every participant up front: lookup() suspends and
                // joinToString's transform is not an inline lambda.
                val memberNames = conv.addresses.map { addr ->
                    contacts.lookup(addr)?.name?.takeIf { it.isNotBlank() }
                        ?: ContactsRepository.formatNumber(addr)
                }
                val name = when {
                    !custom.isNullOrBlank() -> custom
                    conv.isGroup -> memberNames.joinToString(", ")
                    else -> memberNames.firstOrNull()
                        ?: ContactsRepository.formatNumber(conv.primaryAddress)
                }
                val ruleLabel = meta?.forcedRuleId
                    ?.let { id -> rules.firstOrNull { it.id == id }?.name }
                    ?: RuleEngine.match(
                        rules,
                        IncomingMessage(
                            address = conv.primaryAddress,
                            body = conv.snippet,
                            senderIsKnownContact = contact != null,
                        ),
                    )?.rule?.name

                conv.copy(
                    displayName = name,
                    photoUri = contact?.photoUri,
                    pinned = meta?.pinned == true,
                    archived = meta?.archived == true,
                    muted = meta?.muted == true,
                    ruleLabel = ruleLabel,
                )
            }

            val visible = decorated
                .filter { it.archived == _state.value.showArchived }
                .sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.date })

            _state.update { it.copy(loading = false, conversations = visible) }
        }
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query, searching = query.isNotBlank()) }
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(searchResults = smsRepo.searchMessages(query)) }
        }
    }

    fun closeSearch() {
        _state.update { it.copy(searchQuery = "", searchResults = emptyList(), searching = false) }
    }

    fun toggleArchivedView() {
        _state.update { it.copy(showArchived = !it.showArchived) }
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

    fun archiveSelected(archived: Boolean) = updateSelectedMeta { it.copy(archived = archived) }

    fun pinSelected(pinned: Boolean) = updateSelectedMeta { it.copy(pinned = pinned) }

    fun muteSelected(muted: Boolean) = updateSelectedMeta { it.copy(muted = muted) }

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
}
