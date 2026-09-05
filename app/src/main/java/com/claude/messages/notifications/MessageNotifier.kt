package com.claude.messages.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.claude.messages.R
import com.claude.messages.data.db.RuleImportance
import com.claude.messages.data.db.VibrationPattern
import com.claude.messages.data.model.Message
import com.claude.messages.di.ServiceLocator
import com.claude.messages.ui.MainActivity

/**
 * Builds the incoming-message notification, applying the winning
 * [com.claude.messages.data.db.NotificationRule] when one matches.
 */
class MessageNotifier(private val context: Context) {

    private val channels = ChannelManager(context)
    private val manager = NotificationManagerCompat.from(context)


    @SuppressLint("MissingPermission")
    suspend fun notifyIncoming(message: Message) {
        if (!hasPostPermission()) return

        val contacts = ServiceLocator.contactsRepository(context)
        val db = ServiceLocator.database(context)
        val meta = db.threadMetaDao().get(message.threadId)
        val contact = contacts.lookup(message.address)
        val senderName = contact?.name?.takeIf { it.isNotBlank() }
            ?: meta?.customName
            ?: com.claude.messages.data.repo.ContactsRepository.formatNumber(message.address)

        // ---- rule resolution -------------------------------------------------
        val rules = db.ruleDao().getEnabled()
        val forced = meta?.forcedRuleId?.let { id -> rules.firstOrNull { it.id == id } }
        val matched = forced?.let { RuleMatch(it, "pinned to this conversation") }
            ?: RuleEngine.match(
                rules,
                IncomingMessage(
                    address = message.address,
                    body = message.body,
                    senderIsKnownContact = contact != null,
                ),
            )
        val rule = matched?.rule

        val muted = meta?.muted == true
        val channelId = when {
            muted -> ChannelManager.CHANNEL_SILENT
            rule != null -> channels.channelFor(rule)
            else -> ChannelManager.CHANNEL_DEFAULT
        }

        // ---- transcript ------------------------------------------------------
        val transcript = history.getOrPut(message.threadId) { mutableListOf() }
        val snapshot = synchronized(transcript) {
            transcript += senderName to message
            while (transcript.size > MAX_HISTORY) transcript.removeAt(0)
            transcript.toList()
        }

        val self = Person.Builder().setName("You").build()
        val title = if (rule == null || rule.titlePrefix.isBlank()) {
            senderName
        } else {
            "${rule.titlePrefix} $senderName"
        }
        val style = NotificationCompat.MessagingStyle(self)
            .setConversationTitle(title)
            .setGroupConversation(false)
        snapshot.forEach { (name, msg) ->
            val person = Person.Builder().setName(name).setKey(msg.address).build()
            style.addMessage(msg.body, msg.date, person)
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            message.threadId.toInt(),
            MainActivity.threadIntent(context, message.threadId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setContentTitle(senderName)
            .setContentText(message.body)
            .setWhen(message.date)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(contentIntent)
            .setDeleteIntent(NotificationActionReceiver.dismissIntent(context, message.threadId))
            .setGroup(GROUP_KEY)
            .addAction(replyAction(message.threadId))
            .addAction(markReadAction(message.threadId))
            .setVisibility(
                if (rule?.hideContentOnLockScreen == true) {
                    NotificationCompat.VISIBILITY_PRIVATE
                } else {
                    NotificationCompat.VISIBILITY_PRIVATE
                }
            )

        rule?.accentColor?.let { builder.setColor(it).setColorized(false) }
            ?: builder.setColor(DEFAULT_ACCENT)

        applyPreOreoAlerting(builder, rule, muted)

        if (rule != null && rule.importance == RuleImportance.HIGH) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
        }
        if (rule?.bypassDnd == true) {
            builder.setCategory(NotificationCompat.CATEGORY_ALARM)
        }

        manager.notify(message.threadId.toInt(), builder.build())
        postSummary()

        // Some OEMs ignore a channel's custom vibration pattern; enforce it here.
        if (!muted && rule != null && rule.vibration != VibrationPattern.DEFAULT &&
            rule.vibration != VibrationPattern.NONE &&
            rule.importance != RuleImportance.SILENT
        ) {
            vibrate(rule.vibration)
        }
    }

    /** Pre-Android-8 devices carry sound and vibration on the notification itself. */
    private fun applyPreOreoAlerting(
        builder: NotificationCompat.Builder,
        rule: com.claude.messages.data.db.NotificationRule?,
        muted: Boolean,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return
        if (muted) {
            builder.setSilent(true)
            return
        }
        when {
            rule == null -> builder.setDefaults(Notification.DEFAULT_ALL)
            rule.importance == RuleImportance.SILENT -> builder.setSilent(true)
            else -> {
                builder.setSound(rule.soundUri?.let(Uri::parse) ?: defaultSound())
                when (rule.vibration) {
                    VibrationPattern.DEFAULT -> builder.setDefaults(Notification.DEFAULT_VIBRATE)
                    VibrationPattern.NONE -> Unit
                    else -> rule.vibration.timings?.let(builder::setVibrate)
                }
                rule.accentColor?.let { builder.setLights(it, 500, 2000) }
            }
        }
    }

    private fun defaultSound(): Uri =
        android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)

    @SuppressLint("MissingPermission")
    private fun postSummary() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val summary = NotificationCompat.Builder(context, ChannelManager.CHANNEL_DEFAULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setColor(DEFAULT_ACCENT)
            .build()
        manager.notify(SUMMARY_ID, summary)
    }

    private fun replyAction(threadId: Long): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY_TEXT)
            .setLabel(context.getString(R.string.reply))
            .build()
        return NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            context.getString(R.string.reply),
            NotificationActionReceiver.replyIntent(context, threadId),
        )
            .addRemoteInput(remoteInput)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setAllowGeneratedReplies(true)
            .build()
    }

    private fun markReadAction(threadId: Long): NotificationCompat.Action =
        NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            context.getString(R.string.mark_as_read),
            NotificationActionReceiver.markReadIntent(context, threadId),
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .build()

    @SuppressLint("MissingPermission")
    fun notifySendFailure(threadId: Long, recipient: String) {
        if (!hasPostPermission()) return
        val notification = NotificationCompat.Builder(context, ChannelManager.CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Message not sent")
            .setContentText("Tap to retry sending to $recipient")
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    threadId.toInt(),
                    MainActivity.threadIntent(context, threadId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()
        manager.notify(FAILURE_ID_BASE + threadId.toInt(), notification)
    }

    fun cancel(threadId: Long) {
        history.remove(threadId)
        manager.cancel(threadId.toInt())
        if (history.isEmpty()) manager.cancel(SUMMARY_ID)
    }

    fun cancelAll() {
        history.clear()
        manager.cancelAll()
    }

    /** Plays a rule's vibration so the user can feel it while editing. */
    fun previewVibration(pattern: VibrationPattern) {
        if (pattern == VibrationPattern.DEFAULT || pattern == VibrationPattern.NONE) return
        vibrate(pattern)
    }

    private fun vibrate(pattern: VibrationPattern) {
        val timings = pattern.timings ?: return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }

    private fun hasPostPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        /**
         * Conversation history per thread, shared across instances: receivers
         * construct a new MessageNotifier per broadcast, so a per-instance map
         * would leave MessagingStyle with a single message every time.
         */
        private val history = java.util.concurrent.ConcurrentHashMap<Long, MutableList<Pair<String, Message>>>()

        private const val GROUP_KEY = "com.claude.messages.MESSAGES"
        private const val SUMMARY_ID = 1_000_000
        private const val FAILURE_ID_BASE = 2_000_000
        private const val MAX_HISTORY = 8
        private val DEFAULT_ACCENT = Color.parseColor("#1A73E8")
    }
}
