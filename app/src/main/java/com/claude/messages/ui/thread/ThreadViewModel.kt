package com.claude.messages.ui.thread

import android.app.Application
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.core.content.FileProvider
import com.claude.messages.data.db.BlockedNumber
import com.claude.messages.data.db.ScheduledMessage
import com.claude.messages.data.db.StarredMessage
import com.claude.messages.data.prefs.FeaturePrefs
import com.claude.messages.sms.MessageScheduler
import com.claude.messages.util.Formatting
import com.claude.messages.util.PhoneNumbers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claude.messages.R
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
    /** Messages waiting for their scheduled time. */
    val scheduled: List<ScheduledMessage> = emptyList(),
    val starredIds: Set<Long> = emptySet(),
    val blocked: Boolean = false,
    /** A sent message still inside its undo window. */
    val pendingSend: PendingSend? = null,
    /** How many of the newest messages are loaded; grows as the user scrolls up. */
    val messageLimit: Int = PAGE_SIZE,
) {
    val canSend: Boolean get() = draft.isNotBlank() && recipients.isNotEmpty() && !sending
    val hasMore: Boolean get() = messages.size >= messageLimit

    companion object {
        const val PAGE_SIZE = 300
    }
}

data class PendingSend(val body: String, val sendAt: Long, val seconds: Int)

/** One-off things the screen has to do, which don't belong in state. */
sealed interface ThreadEvent {
    data class Share(val intent: Intent) : ThreadEvent
    data class Toast(val text: String) : ThreadEvent
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
    private var pendingJob: Job? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsQueued: String? = null

    private val _events = MutableSharedFlow<ThreadEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ThreadEvent> = _events

    private companion object {
        const val TAG = "ThreadVM"
    }

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
            // Provider reads go through OEM code; degrade rather than crash.
            val messages = runCatching {
                if (threadId > 0) smsRepo.loadMessages(threadId, _state.value.messageLimit) else emptyList()
            }.onFailure { Log.e(TAG, "Could not load thread $threadId", it) }
                .getOrDefault(emptyList())

            // Prefer the thread's own recipient list so an empty conversation
            // still knows its recipients; fall back to whatever we were seeded with.
            val fromThread = runCatching {
                if (threadId > 0) smsRepo.recipientsForThread(threadId) else emptyList()
            }.getOrDefault(emptyList())
            val recipients = fromThread.ifEmpty { _state.value.recipients }

            val meta = runCatching {
                if (threadId > 0) db.threadMetaDao().get(threadId) else null
            }.getOrNull()
            val contact = recipients.firstOrNull()?.let { contacts.lookup(it) }
            val names = recipients.map { addr ->
                contacts.lookup(addr)?.name?.takeIf { it.isNotBlank() }
                    ?: ContactsRepository.formatNumber(addr)
            }
            val rules = runCatching { db.ruleDao().getAll() }.getOrDefault(emptyList())
            val scheduled = runCatching {
                if (threadId > 0) db.scheduledDao().forThread(threadId) else emptyList()
            }.getOrDefault(emptyList())
            val starred = runCatching {
                if (threadId > 0) db.starredDao().idsForThread(threadId).toSet() else emptySet()
            }.getOrDefault(emptySet())
            val blocked = recipients.size == 1 && runCatching {
                db.blockedDao().count(PhoneNumbers.blockKey(recipients.first())) > 0
            }.getOrDefault(false)

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
                    draft = if (it.draft.isEmpty() && it.pendingSend == null) {
                        meta?.draft.orEmpty()
                    } else {
                        it.draft
                    },
                    scheduled = scheduled,
                    starredIds = starred,
                    blocked = blocked,
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
            _state.update { it.copy(errorMessage = getApplication<Application>().getString(R.string.err_no_recipient)) }
            return
        }

        // A second message while one is still in its undo window: let the
        // first one go now so they arrive in the order they were written.
        flushPendingSend()

        val seconds = FeaturePrefs.settings.value.undoSeconds
        if (seconds <= 0) {
            _state.update { it.copy(draft = "") }
            deliver(current.recipients, body)
            return
        }

        _state.update {
            it.copy(
                draft = "",
                pendingSend = PendingSend(body, System.currentTimeMillis() + seconds * 1000L, seconds),
            )
        }
        viewModelScope.launch { persistDraft("") }
        pendingJob = viewModelScope.launch {
            delay(seconds * 1000L)
            val pending = _state.value.pendingSend ?: return@launch
            _state.update { it.copy(pendingSend = null) }
            deliver(_state.value.recipients, pending.body)
        }
    }

    /** Takes back a message that is still in its undo window. */
    fun undoSend() {
        val pending = _state.value.pendingSend ?: return
        pendingJob?.cancel()
        pendingJob = null
        _state.update {
            it.copy(
                pendingSend = null,
                draft = if (it.draft.isBlank()) pending.body else pending.body + "\n" + it.draft,
            )
        }
        _events.tryEmit(ThreadEvent.Toast(getApplication<Application>().getString(R.string.send_cancelled)))
    }

    /** Sends a message in its undo window immediately. */
    private fun flushPendingSend() {
        val pending = _state.value.pendingSend ?: return
        pendingJob?.cancel()
        pendingJob = null
        _state.update { it.copy(pendingSend = null) }
        deliver(_state.value.recipients, pending.body)
    }

    private fun deliver(recipients: List<String>, body: String) {
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            when (val result = sender.send(recipients, body)) {
                is SendResult.Success -> {
                    persistDraft(_state.value.draft)
                    _state.update {
                        it.copy(
                            sending = false,
                            // Adopt the thread the provider created for a new chat.
                            threadId = if (it.threadId > 0) it.threadId else result.threadId,
                            errorMessage = result.warning,
                        )
                    }
                }

                is SendResult.Failure -> _state.update {
                    // Give the text back so the user doesn't lose it.
                    it.copy(
                        sending = false,
                        draft = if (it.draft.isBlank()) body else it.draft,
                        errorMessage = result.reason,
                    )
                }
            }
            refresh()
        }
    }

    // ------------------------------------------------------------ scheduling

    /** Queues the current draft to be sent at [sendAt]. */
    fun schedule(sendAt: Long) {
        val current = _state.value
        val body = current.draft.trim()
        val app = getApplication<Application>()
        if (body.isEmpty()) return
        if (current.recipients.isEmpty()) {
            _state.update { it.copy(errorMessage = app.getString(R.string.err_no_recipient)) }
            return
        }
        if (sendAt <= System.currentTimeMillis()) {
            _state.update { it.copy(errorMessage = app.getString(R.string.schedule_past)) }
            return
        }
        viewModelScope.launch {
            val threadId = current.threadId.takeIf { it > 0 }
                ?: smsRepo.threadIdFor(current.recipients.toSet())
            MessageScheduler.schedule(
                app,
                ScheduledMessage(
                    threadId = threadId,
                    recipients = current.recipients.joinToString(","),
                    body = body,
                    sendAt = sendAt,
                ),
            )
            persistDraft("")
            _state.update {
                it.copy(
                    draft = "",
                    threadId = if (it.threadId > 0) it.threadId else threadId,
                )
            }
            _events.tryEmit(
                ThreadEvent.Toast(app.getString(R.string.scheduled_done, Formatting.fullTimestamp(sendAt)))
            )
            refresh()
        }
    }

    fun cancelScheduled(id: Long) {
        viewModelScope.launch {
            val item = _state.value.scheduled.firstOrNull { it.id == id }
            MessageScheduler.cancel(getApplication(), id)
            // Hand the text back rather than throwing it away.
            if (item != null && _state.value.draft.isBlank()) {
                _state.update { it.copy(draft = item.body) }
            }
            refresh()
        }
    }

    fun sendScheduledNow(id: Long) {
        viewModelScope.launch {
            MessageScheduler.sendNow(getApplication(), id)
            refresh()
        }
    }

    /** Older messages, as the user scrolls up past what is loaded. */
    fun loadMore() {
        val current = _state.value
        if (!current.hasMore || current.loading) return
        _state.update { it.copy(messageLimit = it.messageLimit + ThreadUiState.PAGE_SIZE) }
        refresh()
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
                _state.update { it.copy(errorMessage = getApplication<Application>().getString(R.string.err_no_recipient)) }
                return@launch
            }
            when (val result = sender.send(listOf(target), message.body)) {
                is SendResult.Failure ->
                    _state.update { it.copy(errorMessage = result.reason) }

                is SendResult.Success ->
                    result.warning?.let { w -> _state.update { it.copy(errorMessage = w) } }
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

    // ----------------------------------------------------------- star & block

    fun toggleStar(message: Message) {
        viewModelScope.launch {
            val dao = db.starredDao()
            if (message.id in _state.value.starredIds) {
                dao.delete(message.id)
            } else {
                dao.upsert(
                    StarredMessage(
                        messageId = message.id,
                        threadId = message.threadId,
                        address = message.address,
                        body = message.body,
                        date = message.date,
                        outgoing = message.isOutgoing,
                    )
                )
            }
            refresh()
        }
    }

    fun toggleBlock() {
        val address = _state.value.recipients.singleOrNull() ?: return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val key = PhoneNumbers.blockKey(address)
            val dao = db.blockedDao()
            val name = _state.value.title
            if (dao.count(key) > 0) {
                dao.delete(key)
                _events.tryEmit(ThreadEvent.Toast(app.getString(R.string.unblocked_toast, name)))
            } else {
                dao.upsert(BlockedNumber(key = key, display = address))
                _events.tryEmit(ThreadEvent.Toast(app.getString(R.string.blocked_toast, name)))
            }
            refresh()
        }
    }

    // ----------------------------------------------------------- export & tts

    /** Writes the whole conversation to a text file and offers to share it. */
    fun exportConversation() {
        val current = _state.value
        if (current.threadId <= 0) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val intent = runCatching {
                withContext(Dispatchers.IO) {
                    val all = smsRepo.loadMessages(current.threadId, limit = 100_000)
                    val me = app.getString(R.string.me)
                    val names = mutableMapOf<String, String>()
                    val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    val text = buildString {
                        appendLine(current.title)
                        appendLine("—".repeat(24))
                        all.forEach { m ->
                            val who = if (m.isOutgoing) me else names.getOrPut(m.address) {
                                contacts.lookup(m.address)?.name?.takeIf { it.isNotBlank() }
                                    ?: ContactsRepository.formatNumber(m.address)
                            }
                            append('[').append(stamp.format(Date(m.date))).append("] ")
                            append(who).append(": ").appendLine(m.body)
                        }
                    }
                    val dir = File(app.cacheDir, "exports").apply { mkdirs() }
                    val safe = current.title.replace(Regex("[^\\p{L}\\p{N}]+"), "_").take(40).ifBlank { "chat" }
                    val file = File(dir, "$safe.txt").apply { writeText(text) }
                    val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, current.title)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    Intent.createChooser(send, app.getString(R.string.export_chat))
                }
            }.onFailure { Log.e(TAG, "Export failed", it) }.getOrNull()

            _events.tryEmit(
                if (intent != null) ThreadEvent.Share(intent)
                else ThreadEvent.Toast(app.getString(R.string.export_failed))
            )
        }
    }

    /** Reads a message out loud, in Arabic when the text is Arabic. */
    fun speak(message: Message) {
        val engine = tts
        if (engine == null) {
            ttsQueued = message.body
            tts = TextToSpeech(getApplication()) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (!ttsReady) {
                    _events.tryEmit(
                        ThreadEvent.Toast(getApplication<Application>().getString(R.string.tts_unavailable))
                    )
                    return@TextToSpeech
                }
                ttsQueued?.let(::speakNow)
                ttsQueued = null
            }
            return
        }
        if (ttsReady) speakNow(message.body) else ttsQueued = message.body
    }

    private fun speakNow(text: String) {
        val engine = tts ?: return
        val arabic = text.any { it in '\u0600'..'\u06FF' }
        val result = engine.setLanguage(if (arabic) Locale("ar") else Locale.getDefault())
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            _events.tryEmit(ThreadEvent.Toast(getApplication<Application>().getString(R.string.tts_unavailable)))
            return
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "message")
    }

    override fun onCleared() {
        // Leaving the chat inside the undo window still sends: the user did
        // press Send. This scope outlives the view model on purpose.
        _state.value.pendingSend?.let { pending ->
            pendingJob?.cancel()
            val recipients = _state.value.recipients
            val appSender = sender
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                appSender.send(recipients, pending.body)
            }
        }
        tts?.shutdown()
        tts = null
        super.onCleared()
    }
}
