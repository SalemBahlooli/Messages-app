package com.claude.messages.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import com.claude.messages.data.model.Message
import com.claude.messages.di.ServiceLocator
import com.claude.messages.notifications.MessageNotifier

/** Flips an outgoing message from OUTBOX to SENT (or FAILED) once the radio replies. */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.getStringExtra(SmsSender.EXTRA_MESSAGE_URI)?.let(Uri::parse) ?: return
        val isLastPart = intent.getBooleanExtra(SmsSender.EXTRA_IS_LAST_PART, true)
        val app = context.applicationContext
        val repo = ServiceLocator.smsRepository(app)

        if (resultCode == Activity.RESULT_OK) {
            // Wait for the final part so a long message isn't marked sent early.
            if (isLastPart) repo.updateMessageType(uri, Message.TYPE_SENT)
        } else {
            repo.updateMessageType(uri, Message.TYPE_FAILED)
            val recipient = intent.getStringExtra(SmsSender.EXTRA_RECIPIENT).orEmpty()
            if (recipient.isNotBlank()) {
                val threadId = repo.threadIdFor(setOf(recipient))
                MessageNotifier(app).notifySendFailure(threadId, recipient)
            }
        }
        ServiceLocator.notifyDataChanged()
    }
}

/** Records the carrier's delivery report so the thread can show "Delivered". */
class SmsDeliveredReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.getStringExtra(SmsSender.EXTRA_MESSAGE_URI)?.let(Uri::parse) ?: return
        if (!intent.getBooleanExtra(SmsSender.EXTRA_IS_LAST_PART, true)) return
        val status = if (resultCode == Activity.RESULT_OK) {
            Telephony.Sms.STATUS_COMPLETE
        } else {
            Telephony.Sms.STATUS_FAILED
        }
        ServiceLocator.smsRepository(context.applicationContext).updateMessageStatus(uri, status)
        ServiceLocator.notifyDataChanged()
    }
}

/** Re-creates notification channels after a reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        com.claude.messages.notifications.ChannelManager(context.applicationContext)
            .ensureBaseChannels()
    }
}
