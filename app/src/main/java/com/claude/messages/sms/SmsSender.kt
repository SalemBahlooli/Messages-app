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
import com.claude.messages.util.DefaultSmsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Outcome of a send attempt, so the UI can explain exactly what happened. */
sealed interface SendResult {
    /** The radio accepted the message. [warning] flags a non-fatal problem. */
    data class Success(val threadId: Long, val warning: String? = null) : SendResult
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

        // Check what would stop the radio before touching anything.
        DefaultSmsHelper.sendBlocker(context)?.let {
            return@withContext SendResult.Failure(it.message)
        }

        val repo = ServiceLocator.smsRepository(context)
        var lastThreadId = -1L
        var failure: String? = null
        var couldNotStore = false

        for (recipient in clean) {
            // Writing to the SMS provider requires being the default SMS app,
            // but SEND_SMS alone is enough to transmit. So a failed write must
            // never stop the send — it only means we can't keep our own copy.
            val stored = repo.insertOutgoing(recipient, body, subId)
            if (stored == null) couldNotStore = true
            stored?.let { lastThreadId = it.threadId }

            val sent = runCatching {
                val manager = smsManager(subId)
                    ?: error("SMS service unavailable on this device")
                val parts = manager.divideMessage(body)
                val sentIntents = ArrayList<PendingIntent?>(parts.size)
                val deliveredIntents = ArrayList<PendingIntent?>(parts.size)
                parts.indices.forEach { i ->
                    // Only the last part reports the final state of the message.
                    val isLast = i == parts.size - 1
                    sentIntents += stored?.let { sentIntent(it.uri, recipient, i, isLast) }
                    deliveredIntents += stored?.let { deliveredIntent(it.uri, i, isLast) }
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
                val error = sent.exceptionOrNull()
                Log.e(TAG, "Sending to $recipient failed", error)
                stored?.let { repo.updateMessageType(it.uri, Message.TYPE_FAILED) }
                failure = describe(error)
            }
        }

        ServiceLocator.notifyDataChanged()
        when {
            failure != null -> SendResult.Failure(failure)
            couldNotStore -> SendResult.Success(
                lastThreadId,
                warning = "Sent, but it wasn't saved to your conversations — " +
                    "Messages isn't your default SMS app.",
            )

            else -> SendResult.Success(lastThreadId)
        }
    }

    /** Turns a platform exception into something worth showing a person. */
    private fun describe(error: Throwable?): String = when {
        error is SecurityException ->
            "Android blocked the send. Check that Messages has the SMS permission."

        error?.message?.contains("permission", ignoreCase = true) == true ->
            "Android blocked the send: ${error.message}"

        error?.message.isNullOrBlank() -> "The system rejected the message."
        else -> "Couldn't send: ${error?.message}"
    }

    @Suppress("DEPRECATION")
    private fun smsManager(subId: Int): SmsManager? = runCatching {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                context.getSystemService(SmsManager::class.java)?.let {
                    if (subId >= 0) it.createForSubscriptionId(subId) else it
                }

            subId >= 0 -> SmsManager.getSmsManagerForSubscriptionId(subId)
            else -> SmsManager.getDefault()
        }
    }.getOrNull()

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
