package com.claude.messages.ui.thread

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claude.messages.data.db.NotificationRule
import com.claude.messages.data.db.ThreadMeta
import com.claude.messages.data.model.Message
import com.claude.messages.data.repo.ContactsRepository
import com.claude.messages.di.ServiceLocator
import com.claude.messages.notifications.MessageNotifier
import com.claude.messages.sms.SmsSender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ThreadUiState(
    val threadId: Long = -1,
    val title: String = "",
    val photoUri: String? = null,
    val recipients: List<String> = emptyList(),
    val messages: List<Message> = emptyList(),
    val draft: String = "",
    val sending: Boolean = false,
    val muted: Boolean = false,
    val loading: Boolean = true,
    /** Rules the user can pin to this conversation. */
    val availableRules: List<NotificationRule> = emptyList(),
    val forcedRuleId: Long? = null,
    val isGroup: Boolean = false,
)

class ThreadViewModel(app: Application) : AndroidViewModel(app) {

    private val smsRepo = ServiceLocator.smsRepository(app)
    private val contacts = ServiceLocator.contactsRepository(app)
    private val db = ServiceLocator.database(app)
    private val sender = SmsSender(app)
    private val notifier = MessageNotifier(app)

    private val _state = MutableStateFlow(ThreadUiState())
    val state: StateFlow<ThreadUiState> = _state.asStateFlow()

    private var initialized = false

    /** Opens an existing thread. */
    fun load(threadId: Long) {
        if (initialized && _state.value.threadId == threadId) return
        initialized = true
        _state.update { it.copy(threadId = threadId) }
        viewModelScope.launch {
            ServiceLocator.dataChanged.collect { if (_state.value.threadId > 0) refresh() }
        }
        refresh()
    }

    /** Opens a compose screen pre-addressed to [address] (from a share or dial intent). */
    fun loadForAddress(address: String, prefill: String = "") {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            val threadId = smsRepo.threadIdFor(setOf(address))
            _state.update { it.copy(threadId = threadId, draft = prefill) }
            viewModelScope.launch {
                ServiceLocator.dataChanged.collect { if (_state.value.threadId > 0) refresh() }
            }
            refresh()
        }
    }

    fun refresh() {
        val threadId = _state.value.threadId
        if (threadId <= 0) return
        viewModelScope.launch {
            val messages = smsRepo.loadMessages(threadId)
            val addresses = messages.map { it.address }.filter { it.isNotBlank() }.distinct()
            val meta = db.threadMetaDao().get(threadId)
            val contact = addresses.firstOrNull()?.let { contacts.lookup(it) }
            val names = addresses.map { addr ->
                contacts.lookup(addr)?.name?.takeIf { it.isNotBlank() }
                    ?: ContactsRepository.formatNumber(addr)
            }
            val rules = db.ruleDao().getAll()

            _state.update {
                it.copy(
                    loading = false,
                    messages = messages,
                    recipients = addresses,
                    isGroup = addresses.size > 1,
                    title = meta?.customName?.takeIf { n -> n.isNotBlank() }
                        ?: names.joinToString(", ").ifBlank { "New message" },
                    photoUri = contact?.photoUri,
                    muted = meta?.muted == true,
                    forcedRuleId = meta?.forcedRuleId,
                    availableRules = rules,
                    draft = if (it.draft.isEmpty()) meta?.draft.orEmpty() else it.draft,
                )
            }

            smsRepo.markThreadRead(threadId)
            notifier.cancel(threadId)
        }
    }

    fun onDraftChange(text: String) = _state.update { it.copy(draft = text) }

    fun send() {
        val current = _state.value
        val body = current.draft.trim()
        if (body.isEmpty() || current.recipients.isEmpty() || current.sending) return

        _state.update { it.copy(sending = true, draft = "") }
        viewModelScope.launch {
            sender.send(current.recipients, body)
            persistDraft("")
            _state.update { it.copy(sending = false) }
            refresh()
        }
    }

    /** Called when leaving the screen so an unsent draft survives. */
    fun saveDraft() {
        viewModelScope.launch { persistDraft(_state.value.draft) }
    }

    private suspend fun persistDraft(text: String) {
        val threadId = _state.value.threadId
        if (threadId <= 0) return
        val meta = db.threadMetaDao().get(threadId) ?: ThreadMeta(threadId = threadId)
        db.threadMetaDao().upsert(meta.copy(draft = text))
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            smsRepo.deleteMessage(messageId)
            refresh()
        }
    }

    /** Re-sends a message that previously failed. */
    fun resend(message: Message) {
        viewModelScope.launch {
            smsRepo.deleteMessage(message.id)
            sender.send(listOf(message.address), message.body)
            refresh()
        }
    }

    fun toggleMute() {
        viewModelScope.launch {
            val threadId = _state.value.threadId
            val meta = db.threadMetaDao().get(threadId) ?: ThreadMeta(threadId = threadId)
            db.threadMetaDao().upsert(meta.copy(muted = !meta.muted))
            refresh()
        }
    }

    /**
     * Pins a notification rule to this conversation. Passing null goes back to
     * normal condition-based matching.
     */
    fun setForcedRule(ruleId: Long?) {
        viewModelScope.launch {
            val threadId = _state.value.threadId
            val meta = db.threadMetaDao().get(threadId) ?: ThreadMeta(threadId = threadId)
            db.threadMetaDao().upsert(meta.copy(forcedRuleId = ruleId))
            refresh()
        }
    }

    fun deleteThread(onDone: () -> Unit) {
        viewModelScope.launch {
            val threadId = _state.value.threadId
            smsRepo.deleteThread(threadId)
            db.threadMetaDao().delete(threadId)
            notifier.cancel(threadId)
            ServiceLocator.notifyDataChanged()
            onDone()
        }
    }
}
