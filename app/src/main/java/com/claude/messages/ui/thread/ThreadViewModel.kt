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
import com.claude.messages.sms.SendResult
import com.claude.messages.sms.SmsSender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    val availableRules: List<NotificationRule> = emptyList(),
    val forcedRuleId: Long? = null,
    val isGroup: Boolean = false,
    /** Transient message shown in a snackbar, e.g. why a send failed. */
    val errorMessage: String? = null,
) {
    val canSend: Boolean get() = draft.isNotBlank() && recipients.isNotEmpty() && !sending
}

class ThreadViewModel(app: Application) : AndroidViewModel(app) {

    private val smsRepo = ServiceLocator.smsRepository(app)
    private val contacts = ServiceLocator.contactsRepository(app)
    private val db = ServiceLocator.database(app)
    private val sender = SmsSender(app)
    private val notifier = MessageNotifier(app)

    private val _state = MutableStateFlow(ThreadUiState())
    val state: StateFlow<ThreadUiState> = _state.asStateFlow()

    private var initialized = false

    /**
     * Opens a conversation.
     *
     * [address] is what makes composing to a brand-new contact work: a thread
     * with no messages yet has no addresses to derive recipients from, so the
     * address the user picked has to be carried in explicitly — otherwise Send
     * has nobody to send to and silently does nothing.
     */
    fun open(threadId: Long, address: String? = null, prefill: String = "") {
        if (initialized) return
        initialized = true

        viewModelScope.launch {
            val seedRecipients = listOfNotNull(address?.trim()?.takeIf { it.isNotEmpty() })
            val resolvedThreadId = when {
                threadId > 0 -> threadId
                seedRecipients.isNotEmpty() -> smsRepo.threadIdFor(seedRecipients.toSet())
                else -> -1L
            }
            _state.update {
                it.copy(
                    threadId = resolvedThreadId,
                    recipients = seedRecipients,
                    title = seedRecipients.firstOrNull()
                        ?.let(ContactsRepository::formatNumber).orEmpty(),
                    draft = prefill,
                )
            }
            refresh()

            // Live updates while the screen is open.
            launch {
                ServiceLocator.dataChanged.collectLatest { refresh() }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val threadId = _state.value.threadId
            val messages = if (threadId > 0) smsRepo.loadMessages(threadId) else emptyList()

            // Prefer the thread's own recipient list so an empty conversation
            // still knows its recipients; fall back to whatever we were seeded with.
            val fromThread = if (threadId > 0) smsRepo.recipientsForThread(threadId) else emptyList()
            val recipients = fromThread.ifEmpty { _state.value.recipients }

            val meta = if (threadId > 0) db.threadMetaDao().get(threadId) else null
            val contact = recipients.firstOrNull()?.let { contacts.lookup(it) }
            val names = recipients.map { addr ->
                contacts.lookup(addr)?.name?.takeIf { it.isNotBlank() }
                    ?: ContactsRepository.formatNumber(addr)
            }
            val rules = db.ruleDao().getAll()

            _state.update {
                it.copy(
                    loading = false,
                    messages = messages,
                    recipients = recipients,
                    isGroup = recipients.size > 1,
                    title = meta?.customName?.takeIf { n -> n.isNotBlank() }
                        ?: names.joinToString(", ").ifBlank { "New message" },
                    photoUri = contact?.photoUri,
                    muted = meta?.muted == true,
                    forcedRuleId = meta?.forcedRuleId,
                    availableRules = rules,
                    draft = if (it.draft.isEmpty()) meta?.draft.orEmpty() else it.draft,
                )
            }

            // Only write when there is something to clear: an unconditional
            // update would notify the provider and re-trigger this refresh.
            if (threadId > 0 && messages.any { it.isIncoming && !it.read }) {
                smsRepo.markThreadRead(threadId)
                notifier.cancel(threadId)
            }
        }
    }

    fun onDraftChange(text: String) = _state.update { it.copy(draft = text) }

    fun send() {
        val current = _state.value
        val body = current.draft.trim()
        if (body.isEmpty() || current.sending) return

        if (current.recipients.isEmpty()) {
            _state.update { it.copy(errorMessage = "No recipient for this conversation") }
            return
        }

        _state.update { it.copy(sending = true, draft = "") }
        viewModelScope.launch {
            when (val result = sender.send(current.recipients, body)) {
                is SendResult.Success -> {
                    persistDraft("")
                    _state.update {
                        it.copy(
                            sending = false,
                            // Adopt the thread the provider created for a new chat.
                            threadId = if (it.threadId > 0) it.threadId else result.threadId,
                        )
                    }
                }

                is SendResult.Failure -> _state.update {
                    // Give the text back so the user doesn't lose it.
                    it.copy(sending = false, draft = body, errorMessage = result.reason)
                }
            }
            refresh()
        }
    }

    fun consumeError() = _state.update { it.copy(errorMessage = null) }

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
            val target = message.address.takeIf { it.isNotBlank() }
                ?: _state.value.recipients.firstOrNull()
            if (target == null) {
                _state.update { it.copy(errorMessage = "No recipient for this message") }
                return@launch
            }
            when (val result = sender.send(listOf(target), message.body)) {
                is SendResult.Failure ->
                    _state.update { it.copy(errorMessage = result.reason) }

                is SendResult.Success -> Unit
            }
            refresh()
        }
    }

    fun copyText(message: Message) {
        val clipboard = getApplication<Application>()
            .getSystemService(android.content.ClipboardManager::class.java)
        clipboard?.setPrimaryClip(
            android.content.ClipData.newPlainText("Message", message.body)
        )
    }

    fun toggleMute() {
        viewModelScope.launch {
            val threadId = _state.value.threadId
            if (threadId <= 0) return@launch
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
            if (threadId <= 0) return@launch
            val meta = db.threadMetaDao().get(threadId) ?: ThreadMeta(threadId = threadId)
            db.threadMetaDao().upsert(meta.copy(forcedRuleId = ruleId))
            refresh()
        }
    }

    fun deleteThread(onDone: () -> Unit) {
        viewModelScope.launch {
            val threadId = _state.value.threadId
            if (threadId > 0) {
                smsRepo.deleteThread(threadId)
                db.threadMetaDao().delete(threadId)
                notifier.cancel(threadId)
                ServiceLocator.notifyDataChanged()
            }
            onDone()
        }
    }
}
