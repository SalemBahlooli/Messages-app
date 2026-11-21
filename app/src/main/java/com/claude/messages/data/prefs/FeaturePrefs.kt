package com.claude.messages.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.claude.messages.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

data class FeatureSettings(
    /** Seconds a sent message waits so it can be taken back; 0 sends at once. */
    val undoSeconds: Int = 5,
    val quietHoursEnabled: Boolean = false,
    /** Minutes after midnight. */
    val quietStart: Int = 23 * 60,
    val quietEnd: Int = 7 * 60,
    val appLockEnabled: Boolean = false,
    val quickReplies: List<String> = emptyList(),
    /** Show a one-tap "copy code" on verification messages. */
    val otpHelper: Boolean = true,
) {
    /** True when [minuteOfDay] falls inside quiet hours, including spans past midnight. */
    fun isQuietAt(minuteOfDay: Int): Boolean {
        if (!quietHoursEnabled || quietStart == quietEnd) return false
        return if (quietStart < quietEnd) {
            minuteOfDay in quietStart until quietEnd
        } else {
            minuteOfDay >= quietStart || minuteOfDay < quietEnd
        }
    }
}

/**
 * Settings for the newer features. Read by receivers as well as screens, so it
 * is a process-wide holder like [Appearance], initialised by the Application.
 */
object FeaturePrefs {

    val UNDO_CHOICES = listOf(0, 3, 5, 10)

    private val _settings = MutableStateFlow(FeatureSettings())
    val settings: StateFlow<FeatureSettings> = _settings.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences("features", Context.MODE_PRIVATE)
        prefs = store
        val replies = store.getString(KEY_REPLIES, null)
            ?.split(SEPARATOR)?.filter { it.isNotBlank() }
            // Seeded in the phone's language on first run.
            ?: context.resources.getStringArray(R.array.default_quick_replies).toList()
        _settings.value = FeatureSettings(
            undoSeconds = store.getInt(KEY_UNDO, 5),
            quietHoursEnabled = store.getBoolean(KEY_QUIET, false),
            quietStart = store.getInt(KEY_QUIET_START, 23 * 60),
            quietEnd = store.getInt(KEY_QUIET_END, 7 * 60),
            appLockEnabled = store.getBoolean(KEY_LOCK, false),
            quickReplies = replies,
            otpHelper = store.getBoolean(KEY_OTP, true),
        )
    }

    /** Receivers can run before the Application finished; make sure we are loaded. */
    fun current(context: Context): FeatureSettings {
        init(context)
        return _settings.value
    }

    fun isQuietNow(context: Context): Boolean {
        val now = Calendar.getInstance()
        return current(context).isQuietAt(now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE))
    }

    fun setUndoSeconds(value: Int) = update({ putInt(KEY_UNDO, value) }) { it.copy(undoSeconds = value) }

    fun setQuietHours(enabled: Boolean) =
        update({ putBoolean(KEY_QUIET, enabled) }) { it.copy(quietHoursEnabled = enabled) }

    fun setQuietStart(minutes: Int) =
        update({ putInt(KEY_QUIET_START, minutes) }) { it.copy(quietStart = minutes) }

    fun setQuietEnd(minutes: Int) =
        update({ putInt(KEY_QUIET_END, minutes) }) { it.copy(quietEnd = minutes) }

    fun setAppLock(enabled: Boolean) =
        update({ putBoolean(KEY_LOCK, enabled) }) { it.copy(appLockEnabled = enabled) }

    fun setOtpHelper(enabled: Boolean) =
        update({ putBoolean(KEY_OTP, enabled) }) { it.copy(otpHelper = enabled) }

    fun addQuickReply(text: String) {
        val clean = text.trim().replace(SEPARATOR, " ")
        if (clean.isEmpty() || clean in _settings.value.quickReplies) return
        setReplies(_settings.value.quickReplies + clean)
    }

    fun removeQuickReply(text: String) = setReplies(_settings.value.quickReplies - text)

    private fun setReplies(list: List<String>) =
        update({ putString(KEY_REPLIES, list.joinToString(SEPARATOR)) }) { it.copy(quickReplies = list) }

    private inline fun update(
        write: SharedPreferences.Editor.() -> Unit,
        transform: (FeatureSettings) -> FeatureSettings,
    ) {
        prefs?.edit()?.apply(write)?.apply()
        _settings.value = transform(_settings.value)
    }

    private const val SEPARATOR = "\u001F"
    private const val KEY_UNDO = "undo_seconds"
    private const val KEY_QUIET = "quiet_enabled"
    private const val KEY_QUIET_START = "quiet_start"
    private const val KEY_QUIET_END = "quiet_end"
    private const val KEY_LOCK = "app_lock"
    private const val KEY_REPLIES = "quick_replies"
    private const val KEY_OTP = "otp_helper"

}
