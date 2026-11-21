package com.claude.messages.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A message the user asked to send later. It lives here, not in the SMS
 * provider, until its alarm fires: the provider has no notion of "not yet".
 */
@Entity(tableName = "scheduled_messages")
data class ScheduledMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: Long,
    /** Comma-separated recipients. */
    val recipients: String,
    val body: String,
    val sendAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val recipientList: List<String>
        get() = recipients.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * A starred message. The text is copied in so the starred list still reads
 * correctly after the original is deleted from the conversation.
 */
@Entity(tableName = "starred_messages")
data class StarredMessage(
    @PrimaryKey val messageId: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val outgoing: Boolean,
    val starredAt: Long = System.currentTimeMillis(),
)

/** A sender whose messages are stored silently and kept out of the inbox. */
@Entity(tableName = "blocked_numbers")
data class BlockedNumber(
    /** [com.claude.messages.util.PhoneNumbers.normalize]d, so spellings compare equal. */
    @PrimaryKey val key: String,
    /** The number as the user saw it, for display. */
    val display: String,
    val addedAt: Long = System.currentTimeMillis(),
)

@Dao
interface ScheduledMessageDao {
    @Query("SELECT * FROM scheduled_messages ORDER BY sendAt ASC")
    suspend fun getAll(): List<ScheduledMessage>

    @Query("SELECT * FROM scheduled_messages WHERE threadId = :threadId ORDER BY sendAt ASC")
    suspend fun forThread(threadId: Long): List<ScheduledMessage>

    @Query("SELECT * FROM scheduled_messages WHERE id = :id")
    suspend fun get(id: Long): ScheduledMessage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ScheduledMessage): Long

    @Query("DELETE FROM scheduled_messages WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface StarredMessageDao {
    @Query("SELECT * FROM starred_messages ORDER BY date DESC")
    fun observeAll(): Flow<List<StarredMessage>>

    @Query("SELECT messageId FROM starred_messages WHERE threadId = :threadId")
    suspend fun idsForThread(threadId: Long): List<Long>

    @Upsert
    suspend fun upsert(message: StarredMessage)

    @Query("DELETE FROM starred_messages WHERE messageId = :messageId")
    suspend fun delete(messageId: Long)

    @Query("SELECT COUNT(*) FROM starred_messages WHERE messageId = :messageId")
    suspend fun isStarred(messageId: Long): Int
}

@Dao
interface BlockedNumberDao {
    @Query("SELECT * FROM blocked_numbers ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BlockedNumber>>

    @Query("SELECT * FROM blocked_numbers")
    suspend fun getAll(): List<BlockedNumber>

    @Query("SELECT COUNT(*) FROM blocked_numbers WHERE `key` = :key")
    suspend fun count(key: String): Int

    @Upsert
    suspend fun upsert(number: BlockedNumber)

    @Query("DELETE FROM blocked_numbers WHERE `key` = :key")
    suspend fun delete(key: String)
}
