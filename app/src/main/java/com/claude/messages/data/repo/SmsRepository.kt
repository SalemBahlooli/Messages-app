package com.claude.messages.data.repo

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import com.claude.messages.data.model.Conversation
import com.claude.messages.data.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A message row that was just written to the provider. */
data class StoredMessage(val uri: Uri, val id: Long, val threadId: Long)

/**
 * Reads and writes the system SMS/MMS provider. All conversation and message
 * state lives in the platform provider (as it does for the stock Messages app)
 * so nothing is lost when the user switches default SMS apps.
 */
class SmsRepository(private val context: Context) {

    private val resolver get() = context.contentResolver

    // ---------------------------------------------------------------- reads

    /**
     * Loads every conversation in one pass over the threads table.
     *
     * The threads table already carries the snippet, date, message count and
     * recipient ids, so this avoids the per-thread queries an SMS-only listing
     * would need — and it also surfaces threads that contain no SMS rows yet.
     */
    suspend fun loadConversations(): List<Conversation> = withContext(Dispatchers.IO) {
        val addressBook = canonicalAddresses()
        val unread = unreadCountsByThread()
        val out = mutableListOf<Conversation>()

        resolver.query(THREADS_URI, null, null, null, "date DESC")?.use { c ->
            val idIdx = c.getColumnIndex("_id")
            val dateIdx = c.getColumnIndex("date")
            val countIdx = c.getColumnIndex("message_count")
            val recipientsIdx = c.getColumnIndex("recipient_ids")
            val snippetIdx = c.getColumnIndex("snippet")
            if (idIdx < 0) return@use

            while (c.moveToNext()) {
                val threadId = c.getLong(idIdx)
                val messageCount = if (countIdx >= 0) c.getInt(countIdx) else 0
                // Threads the user emptied linger in the table; skip them.
                if (messageCount <= 0) continue

                val addresses = if (recipientsIdx >= 0) {
                    c.getString(recipientsIdx).orEmpty()
                        .split(' ')
                        .mapNotNull { id -> id.toLongOrNull()?.let(addressBook::get) }
                        .filter { it.isNotBlank() }
                } else {
                    emptyList()
                }

                out += Conversation(
                    threadId = threadId,
                    addresses = addresses,
                    displayName = addresses.joinToString(", "),
                    snippet = if (snippetIdx >= 0) c.getString(snippetIdx).orEmpty() else "",
                    date = if (dateIdx >= 0) c.getLong(dateIdx) else 0L,
                    unreadCount = unread[threadId] ?: 0,
                    messageCount = messageCount,
                    isGroup = addresses.size > 1,
                )
            }
        }
        out.sortedByDescending { it.date }
    }

    /** id -> phone number, for resolving a thread's `recipient_ids`. */
    private fun canonicalAddresses(): Map<Long, String> {
        val map = mutableMapOf<Long, String>()
        runCatching {
            resolver.query(CANONICAL_URI, arrayOf("_id", "address"), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val address = c.getString(1).orEmpty()
                    if (address.isNotBlank()) map[c.getLong(0)] = address
                }
            }
        }
        return map
    }

    /** Unread inbox messages per thread, counted in a single query. */
    private fun unreadCountsByThread(): Map<Long, Int> {
        val counts = mutableMapOf<Long, Int>()
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.THREAD_ID),
            "${Telephony.Sms.READ} = 0 AND ${Telephony.Sms.TYPE} = ${Message.TYPE_INBOX}",
            null,
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val threadId = c.getLong(0)
                counts[threadId] = (counts[threadId] ?: 0) + 1
            }
        }
        return counts
    }

    /**
     * The numbers a thread is addressed to. Reads the thread's recipient list
     * rather than deriving it from messages, so an empty conversation the user
     * just started still knows who it is for.
     */
    suspend fun recipientsForThread(threadId: Long): List<String> = withContext(Dispatchers.IO) {
        if (threadId <= 0) return@withContext emptyList()

        val fromThreadTable = runCatching {
            val addressBook = canonicalAddresses()
            resolver.query(
                THREADS_URI,
                arrayOf("_id", "recipient_ids"),
                "_id = ?",
                arrayOf(threadId.toString()),
                null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(1).orEmpty()
                        .split(' ')
                        .mapNotNull { id -> id.toLongOrNull()?.let(addressBook::get) }
                        .filter { it.isNotBlank() }
                } else null
            }
        }.getOrNull()

        if (!fromThreadTable.isNullOrEmpty()) return@withContext fromThreadTable

        // Fallback for providers that don't expose recipient_ids.
        val addresses = linkedSetOf<String>()
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS),
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC LIMIT 50",
        )?.use { c ->
            while (c.moveToNext()) c.getString(0)?.takeIf { it.isNotBlank() }?.let(addresses::add)
        }
        addresses.toList()
    }

    suspend fun loadMessages(threadId: Long, limit: Int = 500): List<Message> =
        withContext(Dispatchers.IO) {
            if (threadId <= 0) return@withContext emptyList()
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

    suspend fun messageById(id: Long): Message? = withContext(Dispatchers.IO) {
        resolver.query(
            ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id),
            SMS_PROJECTION, null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.toMessage() else null }
    }

    // --------------------------------------------------------------- writes

    /** Persists an incoming message delivered to us as the default SMS app. */
    fun insertIncoming(
        address: String,
        body: String,
        timestamp: Long,
        subId: Int,
    ): StoredMessage? {
        val threadId = threadIdFor(setOf(address))
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timestamp)
            put(Telephony.Sms.DATE_SENT, timestamp)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Message.TYPE_INBOX)
            if (threadId > 0) put(Telephony.Sms.THREAD_ID, threadId)
            if (subId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subId)
        }
        val uri = runCatching { resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) }
            .getOrNull() ?: return null
        return StoredMessage(uri, ContentUris.parseId(uri), threadId)
    }

    /**
     * Records an outgoing message as OUTBOX (i.e. "sending") before it is handed
     * to the radio; [updateMessageType] promotes it to SENT or FAILED later.
     */
    fun insertOutgoing(address: String, body: String, subId: Int): StoredMessage? {
        val threadId = threadIdFor(setOf(address))
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, 0)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Message.TYPE_OUTBOX)
            put(Telephony.Sms.STATUS, Message.STATUS_NONE)
            if (threadId > 0) put(Telephony.Sms.THREAD_ID, threadId)
            if (subId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subId)
        }
        val uri = runCatching { resolver.insert(Telephony.Sms.CONTENT_URI, values) }
            .getOrNull() ?: return null
        return StoredMessage(uri, ContentUris.parseId(uri), threadId)
    }

    fun updateMessageType(uri: Uri, type: Int) {
        val values = ContentValues().apply {
            put(Telephony.Sms.TYPE, type)
            if (type == Message.TYPE_SENT) put(Telephony.Sms.DATE_SENT, System.currentTimeMillis())
        }
        runCatching { resolver.update(uri, values, null, null) }
    }

    fun updateMessageStatus(uri: Uri, status: Int) {
        runCatching {
            resolver.update(uri, ContentValues().apply { put(Telephony.Sms.STATUS, status) }, null, null)
        }
    }

    /** Returns true when the thread actually had unread messages to clear. */
    suspend fun markThreadRead(threadId: Long): Boolean = withContext(Dispatchers.IO) {
        if (threadId <= 0) return@withContext false
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        val updated = runCatching {
            resolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString()),
            )
        }.getOrDefault(0)
        updated > 0
    }

    suspend fun deleteThread(threadId: Long) = withContext(Dispatchers.IO) {
        runCatching {
            resolver.delete(
                ContentUris.withAppendedId(THREAD_DELETE_URI, threadId),
                null, null,
            )
        }.onFailure {
            runCatching {
                resolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms.THREAD_ID} = ?",
                    arrayOf(threadId.toString()),
                )
            }
        }
        Unit
    }

    suspend fun deleteMessage(messageId: Long) = withContext(Dispatchers.IO) {
        runCatching {
            resolver.delete(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId), null, null)
        }
        Unit
    }

    /** Resolves (creating if needed) the thread id for a set of recipients. */
    fun threadIdFor(recipients: Set<String>): Long {
        val clean = recipients.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (clean.isEmpty()) return -1L
        return runCatching { Telephony.Threads.getOrCreateThreadId(context, clean) }
            .getOrDefault(-1L)
    }

    /** Watches the SMS provider so open screens update when messages change. */
    fun observeChanges(onChange: () -> Unit): ContentObserver {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        runCatching {
            resolver.registerContentObserver(Telephony.MmsSms.CONTENT_URI, true, observer)
        }
        return observer
    }

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
        val THREADS_URI: Uri = Uri.parse("content://mms-sms/conversations?simple=true")
        val CANONICAL_URI: Uri = Uri.parse("content://mms-sms/canonical-addresses")
        val THREAD_DELETE_URI: Uri = Uri.parse("content://mms-sms/conversations")

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
