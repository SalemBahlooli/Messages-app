package com.claude.messages.ui.rules

import android.app.Application
import android.media.RingtoneManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claude.messages.data.db.NotificationRule
import com.claude.messages.data.db.VibrationPattern
import com.claude.messages.data.model.Contact
import com.claude.messages.di.ServiceLocator
import com.claude.messages.notifications.ChannelManager
import com.claude.messages.notifications.IncomingMessage
import com.claude.messages.notifications.MessageNotifier
import com.claude.messages.notifications.RuleEngine
import com.claude.messages.notifications.RuleMatch
import com.claude.messages.notifications.RuleProblem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Result of running the rule tester. Holds the engine's structured match so the
 * screen can render the explanation in the phone's language.
 */
data class RuleTestOutcome(
    val matched: Boolean,
    val match: RuleMatch?,
)

data class RuleEditorState(
    val rule: NotificationRule = NotificationRule(),
    val isNew: Boolean = true,
    val problem: RuleProblem? = null,
    val testSender: String = "",
    val testBody: String = "",
    val testMatch: RuleTestOutcome? = null,
    val contacts: List<Contact> = emptyList(),
    val saved: Boolean = false,
)

class RulesViewModel(app: Application) : AndroidViewModel(app) {

    private val db = ServiceLocator.database(app)
    private val contactsRepo = ServiceLocator.contactsRepository(app)
    private val channels = ChannelManager(app)
    private val notifier = MessageNotifier(app)

    val rules: StateFlow<List<NotificationRule>> = db.ruleDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _editor = MutableStateFlow(RuleEditorState())
    val editor: StateFlow<RuleEditorState> = _editor.asStateFlow()

    // ------------------------------------------------------------ list ops

    fun setEnabled(rule: NotificationRule, enabled: Boolean) {
        viewModelScope.launch { db.ruleDao().update(rule.copy(enabled = enabled)) }
    }

    fun delete(rule: NotificationRule) {
        viewModelScope.launch {
            db.threadMetaDao().clearForcedRule(rule.id)
            db.ruleDao().delete(rule)
            channels.pruneStaleChannels(rule.id)
        }
    }

    /** Moves a rule up or down in evaluation order. */
    fun move(rule: NotificationRule, up: Boolean) {
        viewModelScope.launch {
            val all = db.ruleDao().getAll().toMutableList()
            val index = all.indexOfFirst { it.id == rule.id }
            val target = if (up) index - 1 else index + 1
            if (index < 0 || target !in all.indices) return@launch
            val a = all[index]
            val b = all[target]
            db.ruleDao().update(a.copy(order = b.order))
            db.ruleDao().update(b.copy(order = a.order))
        }
    }

    // ---------------------------------------------------------- editor ops

    fun startNew() {
        viewModelScope.launch {
            val order = db.ruleDao().nextOrder()
            _editor.value = RuleEditorState(
                rule = NotificationRule(order = order),
                isNew = true,
                contacts = contactsRepo.allContacts(),
            )
        }
    }

    fun startEdit(ruleId: Long) {
        viewModelScope.launch {
            val rule = db.ruleDao().getById(ruleId) ?: return@launch
            _editor.value = RuleEditorState(
                rule = rule,
                isNew = false,
                contacts = contactsRepo.allContacts(),
            )
        }
    }

    fun updateRule(transform: (NotificationRule) -> NotificationRule) {
        _editor.update { it.copy(rule = transform(it.rule), problem = null, testMatch = null) }
    }

    fun setSound(uri: Uri?) {
        val label = uri?.let {
            runCatching {
                RingtoneManager.getRingtone(getApplication(), it)?.getTitle(getApplication())
            }.getOrNull()
        } ?: "Default"
        updateRule { it.copy(soundUri = uri?.toString(), soundLabel = label) }
    }

    fun previewVibration(pattern: VibrationPattern) = notifier.previewVibration(pattern)

    fun onTestSenderChange(value: String) =
        _editor.update { it.copy(testSender = value, testMatch = null) }

    fun onTestBodyChange(value: String) =
        _editor.update { it.copy(testBody = value, testMatch = null) }

    /**
     * Runs the rule under construction against the sample message so the user
     * can see whether it would fire — and why — before saving.
     */
    fun runTest() {
        viewModelScope.launch {
            val state = _editor.value
            val known = state.testSender.isNotBlank() &&
                contactsRepo.lookup(state.testSender) != null
            val match = RuleEngine.evaluate(
                state.rule,
                IncomingMessage(
                    address = state.testSender,
                    body = state.testBody,
                    senderIsKnownContact = known,
                ),
            )
            _editor.update {
                it.copy(testMatch = RuleTestOutcome(matched = match != null, match = match))
            }
        }
    }

    fun save() {
        val rule = _editor.value.rule
        val problem = RuleEngine.validate(rule)
        if (problem != null) {
            _editor.update { it.copy(problem = problem) }
            return
        }
        viewModelScope.launch {
            if (_editor.value.isNew) {
                val id = db.ruleDao().insert(rule)
                // Create the channel eagerly so the tone is registered with the OS.
                db.ruleDao().getById(id)?.let(channels::channelFor)
            } else {
                db.ruleDao().update(rule)
                channels.channelFor(rule)
            }
            _editor.update { it.copy(saved = true) }
        }
    }

    fun consumeSaved() = _editor.update { it.copy(saved = false) }
}
