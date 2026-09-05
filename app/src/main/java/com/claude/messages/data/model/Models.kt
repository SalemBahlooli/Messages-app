package com.claude.messages.data.model

/** A single SMS/MMS message row. */
data class Message(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val dateSent: Long,
    val type: Int,
    val read: Boolean,
    val subscriptionId: Int = -1,
    val status: Int = STATUS_NONE,
    val isMms: Boolean = false,
) {
    val isIncoming: Boolean get() = type == TYPE_INBOX
    val isOutgoing: Boolean get() = !isIncoming
    val isFailed: Boolean get() = type == TYPE_FAILED
    val isSending: Boolean get() = type == TYPE_OUTBOX || type == TYPE_QUEUED

    companion object {
        const val TYPE_INBOX = 1
        const val TYPE_SENT = 2
        const val TYPE_DRAFT = 3
        const val TYPE_OUTBOX = 4
        const val TYPE_FAILED = 5
        const val TYPE_QUEUED = 6

        const val STATUS_NONE = -1
        const val STATUS_COMPLETE = 0
        const val STATUS_PENDING = 32
        const val STATUS_FAILED = 64
    }
}

/** One conversation (thread) in the list. */
data class Conversation(
    val threadId: Long,
    val addresses: List<String>,
    val displayName: String,
    val snippet: String,
    val date: Long,
    val unreadCount: Int,
    val messageCount: Int,
    val photoUri: String? = null,
    val isGroup: Boolean = false,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val muted: Boolean = false,
    /** Name of the notification rule that currently applies, if any. */
    val ruleLabel: String? = null,
) {
    val hasUnread: Boolean get() = unreadCount > 0
    val primaryAddress: String get() = addresses.firstOrNull().orEmpty()
}

/** A phone-book entry used by the recipient picker. */
data class Contact(
    val id: Long,
    val name: String,
    val number: String,
    val photoUri: String? = null,
)
