package com.claude.messages.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** How a rule decides whether the *sender* matches. */
enum class SenderMatch {
    /** Applies to every sender. */
    ANY,

    /** Applies only to the numbers listed in [NotificationRule.senderValues]. */
    NUMBERS,

    /** Applies to any sender that is NOT in the user's contacts (unknown numbers). */
    NOT_IN_CONTACTS,

    /** Applies to any sender that IS in the user's contacts. */
    IN_CONTACTS,
}

/** How a rule decides whether the message *body* matches. */
enum class ContentMatch {
    /** Body is not considered. */
    ANY,

    /** Matches when the body contains at least one of the keywords. */
    CONTAINS_ANY,

    /** Matches only when the body contains every keyword. */
    CONTAINS_ALL,

    /** Matches when the body starts with the first keyword. */
    STARTS_WITH,

    /** Matches the body against a regular expression. */
    REGEX,
}

/** Vibration presets offered in the rule editor. */
enum class VibrationPattern(val label: String, val timings: LongArray?) {
    DEFAULT("Default", null),
    NONE("None", longArrayOf(0)),
    SHORT("Short", longArrayOf(0, 120)),
    DOUBLE("Double tap", longArrayOf(0, 100, 120, 100)),
    LONG("Long", longArrayOf(0, 600)),
    HEARTBEAT("Heartbeat", longArrayOf(0, 120, 90, 120, 400, 120, 90, 120)),
    SOS("S.O.S.", longArrayOf(0, 100, 80, 100, 80, 100, 200, 300, 100, 300, 100, 300, 200, 100, 80, 100, 80, 100)),
    ;

    companion object {
        fun from(name: String?): VibrationPattern =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Notification prominence a rule can force. */
enum class RuleImportance(val label: String) {
    SILENT("Silent — no sound, no banner"),
    LOW("Quiet — status bar only"),
    NORMAL("Normal"),
    HIGH("Urgent — pop on screen"),
}

/**
 * A user-authored rule that customises how an incoming message is announced.
 *
 * This is the app's headline feature: instead of one global notification sound,
 * the user can say things like "messages from Mum ring with "Chimes" and bypass
 * Do Not Disturb" or "anything containing the word OTP vibrates twice and is
 * marked urgent".
 *
 * Rules are evaluated in [order]; the first enabled rule that matches wins.
 */
@Entity(tableName = "notification_rules")
data class NotificationRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val enabled: Boolean = true,
    /** Lower runs first. */
    val order: Int = 0,

    // ---- conditions ----
    val senderMatch: SenderMatch = SenderMatch.ANY,
    /** Comma-separated phone numbers used when [senderMatch] is NUMBERS. */
    val senderValues: String = "",
    val contentMatch: ContentMatch = ContentMatch.ANY,
    /** Newline-separated keywords, or a single regex when [contentMatch] is REGEX. */
    val contentValues: String = "",
    val caseSensitive: Boolean = false,

    // ---- actions ----
    /** Ringtone to play. Null keeps the system default. */
    val soundUri: String? = null,
    val soundLabel: String = "Default",
    val vibration: VibrationPattern = VibrationPattern.DEFAULT,
    val importance: RuleImportance = RuleImportance.NORMAL,
    /** ARGB colour for the notification accent / LED. Null keeps the app default. */
    val accentColor: Int? = null,
    /** Show even while Do Not Disturb is on. */
    val bypassDnd: Boolean = false,
    /** Optional prefix shown in the notification title, e.g. "🔴 Urgent". */
    val titlePrefix: String = "",
    /** Hide the message text on the lock screen for this rule. */
    val hideContentOnLockScreen: Boolean = false,
    /** Stop evaluating further rules once this one matches. */
    val stopOnMatch: Boolean = true,
) {
    val senderNumbers: List<String>
        get() = senderValues.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    val keywords: List<String>
        get() = contentValues.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Identity of the notification channel this rule needs. Android freezes a
     * channel's sound and vibration at creation time, so the id embeds the
     * alerting settings: editing a rule yields a new channel instead of a
     * silently stale one.
     */
    val channelId: String
        get() = "rule_${id}_" + listOf(
            soundUri.orEmpty(),
            vibration.name,
            importance.name,
            bypassDnd.toString(),
        ).joinToString("|").hashCode().toString().replace('-', 'n')
}
