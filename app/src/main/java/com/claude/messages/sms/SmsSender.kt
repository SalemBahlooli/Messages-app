package com.claude.messages.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import com.claude.messages.data.model.Message
import com.claude.messages.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sends SMS through the platform, splitting long bodies and tracking status. */
class SmsSender(private val context: Context) {

    suspend fun send(recipients: List<String>, body: String, subId: Int = -1): Boolean =
        withContext(Dispatchers.IO) {
            if (recipients.isEmpty() || body.isEmpty()) return@withContext false
            val repo = ServiceLocator.smsRepository(context)
            var allOk = true

            for (recipient in recipients) {
                val uri = repo.insertOutgoing(recipient, body, subId)
                val ok = runCatching {
                    val manager = smsManager(subId)
                    val parts = manager.divideMessage(body)
                    val sentIntents = ArrayList<PendingIntent>(parts.size)
                    val deliveredIntents = ArrayList<PendingIntent>(parts.size)
                    parts.indices.forEach { i ->
                        sentIntents += sentIntent(uri, recipient, i)
                        deliveredIntents += deliveredIntent(uri, i)
                    }
                    if (parts.size == 1) {
                        manager.sendTextMessage(
                            recipient, null, parts[0], sentIntents[0], deliveredIntents[0],
                        )
                    } else {
                        manager.sendMultipartTextMessage(
                            recipient, null, parts, sentIntents, deliveredIntents,
                        )
                    }
                }.isSuccess

                if (!ok) {
                    allOk = false
                    uri?.let { repo.updateMessageType(it, Message.TYPE_FAILED) }
                }
            }
            ServiceLocator.notifyDataChanged()
            allOk
        }

    @Suppress("DEPRECATION")
    private fun smsManager(subId: Int): SmsManager = when {
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ->
            context.getSystemService(SmsManager::class.java).let {
                if (subId >= 0) it.createForSubscriptionId(subId) else it
            }

        subId >= 0 -> SmsManager.getSmsManagerForSubscriptionId(subId)
        else -> SmsManager.getDefault()
    }

    private fun sentIntent(uri: Uri?, recipient: String, part: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (uri.toString() + "sent" + part).hashCode(),
            Intent(context, SmsSentReceiver::class.java).apply {
                putExtra(EXTRA_MESSAGE_URI, uri?.toString())
                putExtra(EXTRA_RECIPIENT, recipient)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun deliveredIntent(uri: Uri?, part: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (uri.toString() + "delivered" + part).hashCode(),
            Intent(context, SmsDeliveredReceiver::class.java).apply {
                putExtra(EXTRA_MESSAGE_URI, uri?.toString())
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val EXTRA_MESSAGE_URI = "message_uri"
        const val EXTRA_RECIPIENT = "recipient"
    }
}
