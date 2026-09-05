package com.claude.messages.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationRuleDao {

    @Query("SELECT * FROM notification_rules ORDER BY `order` ASC, id ASC")
    fun observeAll(): Flow<List<NotificationRule>>

    @Query("SELECT * FROM notification_rules ORDER BY `order` ASC, id ASC")
    suspend fun getAll(): List<NotificationRule>

    @Query("SELECT * FROM notification_rules WHERE enabled = 1 ORDER BY `order` ASC, id ASC")
    suspend fun getEnabled(): List<NotificationRule>

    @Query("SELECT * FROM notification_rules WHERE id = :id")
    suspend fun getById(id: Long): NotificationRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: NotificationRule): Long

    @Update
    suspend fun update(rule: NotificationRule)

    @Delete
    suspend fun delete(rule: NotificationRule)

    @Query("SELECT COALESCE(MAX(`order`), -1) + 1 FROM notification_rules")
    suspend fun nextOrder(): Int
}

@Dao
interface ThreadMetaDao {

    @Query("SELECT * FROM thread_meta")
    fun observeAll(): Flow<List<ThreadMeta>>

    @Query("SELECT * FROM thread_meta WHERE threadId = :threadId")
    suspend fun get(threadId: Long): ThreadMeta?

    @Query("SELECT * FROM thread_meta WHERE threadId = :threadId")
    fun observe(threadId: Long): Flow<ThreadMeta?>

    @Upsert
    suspend fun upsert(meta: ThreadMeta)

    @Query("DELETE FROM thread_meta WHERE threadId = :threadId")
    suspend fun delete(threadId: Long)

    @Query("UPDATE thread_meta SET forcedRuleId = NULL WHERE forcedRuleId = :ruleId")
    suspend fun clearForcedRule(ruleId: Long)
}
