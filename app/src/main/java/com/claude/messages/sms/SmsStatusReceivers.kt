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
        val repo = ServiceLocator.smsRepository(context.applicationContext)
        if (resultCode == Activity.RESULT_OK) {
            repo.updateMessageType(uri, Message.TYPE_SENT)
        } else {
            repo.updateMessageType(uri, Message.TYPE_FAILED)
            val recipient = intent.getStringExtra(SmsSender.EXTRA_RECIPIENT).orEmpty()
            MessageNotifier(context.applicationContext)
                .notifySendFailure(threadIdOf(context, recipient), recipient)
        }
        ServiceLocator.notifyDataChanged()
    }

    private fun threadIdOf(context: Context, address: String): Long =
        if (address.isBlank()) -1L
        else ServiceLocator.smsRepository(context.applicationContext).threadIdFor(setOf(address))
}

/** Records the carrier's delivery report so the thread can show "Delivered". */
class SmsDeliveredReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.getStringExtra(SmsSender.EXTRA_MESSAGE_URI)?.let(Uri::parse) ?: return
        val status = if (resultCode == Activity.RESULT_OK) {
            Telephony.Sms.STATUS_COMPLETE
        } else {
            Telephony.Sms.STATUS_FAILED
        }
        ServiceLocator.smsRepository(context.applicationContext).updateMessageStatus(uri, status)
        ServiceLocator.notifyDataChanged()
    }
}

/** Re-creates notification channels after a reboot wipes nothing but is a good checkpoint. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        com.claude.messages.notifications.ChannelManager(context.applicationContext)
            .ensureBaseChannels()
    }
}
