package com.claude.messages.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.claude.messages.data.model.Message
import com.claude.messages.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Outcome of a send attempt, so the UI can explain what went wrong. */
sealed interface SendResult {
    data class Success(val threadId: Long) : SendResult
    data class Failure(val reason: String) : SendResult
}

/** Sends SMS through the platform, splitting long bodies and tracking status. */
class SmsSender(private val context: Context) {

    suspend fun send(
        recipients: List<String>,
        body: String,
        subId: Int = -1,
    ): SendResult = withContext(Dispatchers.IO) {
        val clean = recipients.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return@withContext SendResult.Failure("No recipient for this message")
        if (body.isEmpty()) return@withContext SendResult.Failure("Message is empty")

        val repo = ServiceLocator.smsRepository(context)
        var lastThreadId = -1L
        var failure: String? = null

        for (recipient in clean) {
            val stored = repo.insertOutgoing(recipient, body, subId)
            if (stored == null) {
                failure = "Couldn't save the message. Is Messages your default SMS app?"
                continue
            }
            lastThreadId = stored.threadId

            val sent = runCatching {
                val manager = smsManager(subId)
                val parts = manager.divideMessage(body)
                val sentIntents = ArrayList<PendingIntent>(parts.size)
                val deliveredIntents = ArrayList<PendingIntent>(parts.size)
                parts.indices.forEach { i ->
                    // Only the last part reports the final state of the message.
                    val isLast = i == parts.size - 1
                    sentIntents += sentIntent(stored.uri, recipient, i, isLast)
                    deliveredIntents += deliveredIntent(stored.uri, i, isLast)
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
            }

            if (sent.isFailure) {
                Log.e(TAG, "sendTextMessage failed", sent.exceptionOrNull())
                repo.updateMessageType(stored.uri, Message.TYPE_FAILED)
                failure = sent.exceptionOrNull()?.message
                    ?: "The system rejected the message. Check the SMS permission."
            }
        }

        ServiceLocator.notifyDataChanged()
        failure?.let { SendResult.Failure(it) } ?: SendResult.Success(lastThreadId)
    }

    @Suppress("DEPRECATION")
    private fun smsManager(subId: Int): SmsManager = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            context.getSystemService(SmsManager::class.java).let {
                if (subId >= 0) it.createForSubscriptionId(subId) else it
            }

        subId >= 0 -> SmsManager.getSmsManagerForSubscriptionId(subId)
        else -> SmsManager.getDefault()
    }

    private fun sentIntent(uri: Uri, recipient: String, part: Int, isLast: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (uri.toString() + "sent" + part).hashCode(),
            Intent(context, SmsSentReceiver::class.java).apply {
                setData(uri)
                putExtra(EXTRA_MESSAGE_URI, uri.toString())
                putExtra(EXTRA_RECIPIENT, recipient)
                putExtra(EXTRA_IS_LAST_PART, isLast)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun deliveredIntent(uri: Uri, part: Int, isLast: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (uri.toString() + "delivered" + part).hashCode(),
            Intent(context, SmsDeliveredReceiver::class.java).apply {
                setData(uri)
                putExtra(EXTRA_MESSAGE_URI, uri.toString())
                putExtra(EXTRA_IS_LAST_PART, isLast)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val EXTRA_MESSAGE_URI = "message_uri"
        const val EXTRA_RECIPIENT = "recipient"
        const val EXTRA_IS_LAST_PART = "is_last_part"
        private const val TAG = "SmsSender"
    }
}
