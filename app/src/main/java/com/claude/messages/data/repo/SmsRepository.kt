package com.claude.messages.data.repo

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import com.claude.messages.data.model.Conversation
import com.claude.messages.data.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads and writes the system SMS/MMS provider. All of the conversation and
 * message state lives in the platform provider (as it does for the stock
 * Messages app) so that other apps and a future default-app switch keep working.
 */
class SmsRepository(private val context: Context) {

    private val resolver get() = context.contentResolver

    // ---------------------------------------------------------------- reads

    suspend fun loadConversations(): List<Conversation> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            Telephony.Sms.Conversations.THREAD_ID,
            Telephony.Sms.Conversations.SNIPPET,
            Telephony.Sms.Conversations.MESSAGE_COUNT,
        )
        val threads = mutableListOf<Conversation>()
        resolver.query(
            Telephony.Sms.Conversations.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DEFAULT_SORT_ORDER}",
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(Telephony.Sms.Conversations.THREAD_ID)
            val snippetIdx = c.getColumnIndexOrThrow(Telephony.Sms.Conversations.SNIPPET)
            val countIdx = c.getColumnIndexOrThrow(Telephony.Sms.Conversations.MESSAGE_COUNT)
            while (c.moveToNext()) {
                val threadId = c.getLong(idIdx)
                val addresses = addressesForThread(threadId)
                threads += Conversation(
                    threadId = threadId,
                    addresses = addresses,
                    displayName = addresses.joinToString(", "),
                    snippet = c.getString(snippetIdx).orEmpty(),
                    date = lastMessageDate(threadId),
                    unreadCount = unreadCount(threadId),
                    messageCount = c.getInt(countIdx),
                    isGroup = addresses.size > 1,
                )
            }
        }
        threads.sortedByDescending { it.date }
    }

    suspend fun loadMessages(threadId: Long, limit: Int = 500): List<Message> =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<Message>()
            resolver.query(
                Telephony.Sms.CONTENT_URI,
                SMS_PROJECTION,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} DESC LIMIT $limit",
            )?.use { c -> while (c.moveToNext()) out += c.toMessage() }
            out.sortedBy { it.date }
        }

    /** Full-text search across message bodies; returns the newest match per thread. */
    suspend fun searchMessages(query: String, limit: Int = 100): List<Message> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val out = mutableListOf<Message>()
            resolver.query(
                Telephony.Sms.CONTENT_URI,
                SMS_PROJECTION,
                "${Telephony.Sms.BODY} LIKE ?",
                arrayOf("%${query.trim()}%"),
                "${Telephony.Sms.DATE} DESC LIMIT $limit",
            )?.use { c -> while (c.moveToNext()) out += c.toMessage() }
            out
        }

    suspend fun latestMessage(threadId: Long): Message? = withContext(Dispatchers.IO) {
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            SMS_PROJECTION,
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC LIMIT 1",
        )?.use { c -> if (c.moveToFirst()) c.toMessage() else null }
    }

    suspend fun unreadMessages(): List<Message> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Message>()
        resolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            SMS_PROJECTION,
            "${Telephony.Sms.READ} = 0",
            null,
            "${Telephony.Sms.DATE} ASC",
        )?.use { c -> while (c.moveToNext()) out += c.toMessage() }
        out
    }

    private fun addressesForThread(threadId: Long): List<String> {
        val addresses = linkedSetOf<String>()
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS),
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC LIMIT 50",
        )?.use { c ->
            val idx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            while (c.moveToNext()) c.getString(idx)?.takeIf { it.isNotBlank() }?.let(addresses::add)
        }
        return addresses.toList()
    }

    private fun lastMessageDate(threadId: Long): Long =
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.DATE),
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC LIMIT 1",
        )?.use { if (it.moveToFirst()) it.getLong(0) else 0L } ?: 0L

    private fun unreadCount(threadId: Long): Int =
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0 AND ${Telephony.Sms.TYPE} = ?",
            arrayOf(threadId.toString(), Message.TYPE_INBOX.toString()),
            null,
        )?.use { it.count } ?: 0

    // --------------------------------------------------------------- writes

    /** Persists an incoming message delivered to us as the default SMS app. */
    fun insertIncoming(address: String, body: String, timestamp: Long, subId: Int): Uri? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timestamp)
            put(Telephony.Sms.DATE_SENT, timestamp)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Message.TYPE_INBOX)
            if (subId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subId)
        }
        return runCatching { resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) }.getOrNull()
    }

    fun insertOutgoing(address: String, body: String, subId: Int): Uri? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Message.TYPE_OUTBOX)
            if (subId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subId)
        }
        return runCatching { resolver.insert(Telephony.Sms.Sent.CONTENT_URI, values) }.getOrNull()
    }

    fun updateMessageType(uri: Uri, type: Int) {
        runCatching {
            resolver.update(uri, ContentValues().apply { put(Telephony.Sms.TYPE, type) }, null, null)
        }
    }

    fun updateMessageStatus(uri: Uri, status: Int) {
        runCatching {
            resolver.update(uri, ContentValues().apply { put(Telephony.Sms.STATUS, status) }, null, null)
        }
    }

    suspend fun markThreadRead(threadId: Long) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        runCatching {
            resolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString()),
            )
        }
        Unit
    }

    suspend fun deleteThread(threadId: Long) = withContext(Dispatchers.IO) {
        runCatching {
            resolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
            )
        }
        Unit
    }

    suspend fun deleteMessage(messageId: Long) = withContext(Dispatchers.IO) {
        runCatching {
            resolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms._ID} = ?",
                arrayOf(messageId.toString()),
            )
        }
        Unit
    }

    /** Resolves (creating if needed) the thread id for a set of recipients. */
    fun threadIdFor(recipients: Set<String>): Long =
        runCatching { Telephony.Threads.getOrCreateThreadId(context, recipients) }.getOrDefault(-1L)

    // --------------------------------------------------------------- helpers

    private fun Cursor.toMessage(): Message = Message(
        id = getLong(getColumnIndexOrThrow(Telephony.Sms._ID)),
        threadId = getLong(getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)),
        address = getString(getColumnIndexOrThrow(Telephony.Sms.ADDRESS)).orEmpty(),
        body = getString(getColumnIndexOrThrow(Telephony.Sms.BODY)).orEmpty(),
        date = getLong(getColumnIndexOrThrow(Telephony.Sms.DATE)),
        dateSent = getLong(getColumnIndexOrThrow(Telephony.Sms.DATE_SENT)),
        type = getInt(getColumnIndexOrThrow(Telephony.Sms.TYPE)),
        read = getInt(getColumnIndexOrThrow(Telephony.Sms.READ)) == 1,
        status = getInt(getColumnIndexOrThrow(Telephony.Sms.STATUS)),
    )

    private companion object {
        val SMS_PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.DATE_SENT,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms.STATUS,
        )
    }
}
