package com.claude.messages.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-conversation state the system SMS provider has nowhere to store:
 * pinning, archiving, muting, a saved draft, and a rule override.
 */
@Entity(tableName = "thread_meta")
data class ThreadMeta(
    @PrimaryKey val threadId: Long,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val muted: Boolean = false,
    val draft: String = "",
    /** Forces a specific rule for this conversation regardless of its conditions. */
    val forcedRuleId: Long? = null,
    val customName: String? = null,
)
