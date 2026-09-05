package com.claude.messages.notifications

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import com.claude.messages.data.db.NotificationRule
import com.claude.messages.data.db.RuleImportance
import com.claude.messages.data.db.VibrationPattern

/**
 * Owns the notification channels.
 *
 * Android freezes a channel's sound, vibration and importance when the channel
 * is created, so a rule whose alerting settings change gets a brand-new channel
 * id (see [NotificationRule.channelId]) and the stale one is deleted. That is
 * what makes per-rule ringtones actually take effect on Android 8+.
 */
class ChannelManager(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)
    private val systemManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureBaseChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        systemManager.createNotificationChannelGroup(
            NotificationChannelGroup(GROUP_RULES, "Custom rules")
        )

        val default = NotificationChannel(
            CHANNEL_DEFAULT,
            "Messages",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Incoming text messages"
            enableVibration(true)
            enableLights(true)
            setShowBadge(true)
        }

        val silent = NotificationChannel(
            CHANNEL_SILENT,
            "Muted conversations",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Messages from conversations you have muted"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }

        val sending = NotificationChannel(
            CHANNEL_STATUS,
            "Sending status",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Failures and delivery reports"
            setShowBadge(false)
        }

        systemManager.createNotificationChannels(listOf(default, silent, sending))
    }

    /**
     * Creates (once) the channel backing [rule] and removes any channel left
     * over from an earlier version of the same rule.
     * Returns the channel id to post on.
     */
    fun channelFor(rule: NotificationRule): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return CHANNEL_DEFAULT
        val wanted = rule.channelId
        pruneStaleChannels(rule.id, keep = wanted)
        if (systemManager.getNotificationChannel(wanted) == null) {
            systemManager.createNotificationChannel(buildChannel(rule, wanted))
        }
        return wanted
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildChannel(rule: NotificationRule, channelId: String) =
        NotificationChannel(channelId, rule.name.ifBlank { "Rule ${rule.id}" }, rule.importance.toPlatform())
            .apply {
                group = GROUP_RULES
                description = RuleEngine.describe(rule)
                setShowBadge(true)

                if (rule.importance == RuleImportance.SILENT) {
                    setSound(null, null)
                    enableVibration(false)
                } else {
                    val attributes = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                        .build()
                    setSound(rule.soundUri?.let(Uri::parse), attributes)

                    when (rule.vibration) {
                        VibrationPattern.DEFAULT -> enableVibration(true)
                        VibrationPattern.NONE -> enableVibration(false)
                        else -> {
                            enableVibration(true)
                            vibrationPattern = rule.vibration.timings
                        }
                    }
                }

                rule.accentColor?.let {
                    enableLights(true)
                    lightColor = it
                }

                setBypassDnd(rule.bypassDnd)
            }

    /** Deletes channels belonging to [ruleId] other than [keep]. */
    fun pruneStaleChannels(ruleId: Long, keep: String? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val prefix = "rule_${ruleId}_"
        systemManager.notificationChannels
            .filter { it.id.startsWith(prefix) && it.id != keep }
            .forEach { systemManager.deleteNotificationChannel(it.id) }
    }

    fun areNotificationsEnabled(): Boolean = manager.areNotificationsEnabled()

    private fun RuleImportance.toPlatform(): Int = when (this) {
        RuleImportance.SILENT -> NotificationManager.IMPORTANCE_LOW
        RuleImportance.LOW -> NotificationManager.IMPORTANCE_LOW
        RuleImportance.NORMAL -> NotificationManager.IMPORTANCE_DEFAULT
        RuleImportance.HIGH -> NotificationManager.IMPORTANCE_HIGH
    }

    companion object {
        const val CHANNEL_DEFAULT = "messages_default"
        const val CHANNEL_SILENT = "messages_silent"
        const val CHANNEL_STATUS = "messages_status"
        const val GROUP_RULES = "messages_rules"
    }
}
