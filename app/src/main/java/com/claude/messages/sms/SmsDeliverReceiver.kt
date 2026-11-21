package com.claude.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.claude.messages.data.model.Message
import com.claude.messages.data.db.ThreadMeta
import com.claude.messages.di.ServiceLocator
import com.claude.messages.util.PhoneNumbers
import com.claude.messages.notifications.MessageNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Entry point for incoming SMS while this app is the default SMS handler.
 * Only the default handler receives SMS_DELIVER, and it alone is responsible
 * for writing the message into the provider.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return

        // A long SMS arrives as several parts that must be stitched together.
        val address = parts.first().displayOriginatingAddress.orEmpty()
        val body = parts.joinToString("") { it.displayMessageBody.orEmpty() }
        val timestamp = parts.first().timestampMillis.takeIf { it > 0 }
            ?: System.currentTimeMillis()
        val subId = intent.getIntExtra("subscription", -1)
        if (address.isBlank() || body.isEmpty()) return

        val pendingResult = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = ServiceLocator.database(app)
                val blocked = runCatching {
                    db.blockedDao().count(PhoneNumbers.blockKey(address)) > 0
                }.getOrDefault(false)

                // A blocked sender's message is still written — the default SMS
                // app is the only one that can — but as read, archived and silent.
                val stored = ServiceLocator.smsRepository(app)
                    .insertIncoming(address, body, timestamp, subId, read = blocked)
                if (stored == null) {
                    Log.w(TAG, "Could not persist incoming message")
                    return@launch
                }
                if (blocked) {
                    if (stored.threadId > 0) {
                        val meta = db.threadMetaDao().get(stored.threadId)
                            ?: ThreadMeta(threadId = stored.threadId)
                        db.threadMetaDao().upsert(meta.copy(archived = true))
                    }
                    ServiceLocator.notifyDataChanged()
                    return@launch
                }
                MessageNotifier(app).notifyIncoming(
                    Message(
                        id = stored.id,
                        threadId = stored.threadId,
                        address = address,
                        body = body,
                        date = timestamp,
                        dateSent = timestamp,
                        type = Message.TYPE_INBOX,
                        read = false,
                        subscriptionId = subId,
                    )
                )
                ServiceLocator.notifyDataChanged()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed handling incoming SMS", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SmsDeliverReceiver"
    }
}
