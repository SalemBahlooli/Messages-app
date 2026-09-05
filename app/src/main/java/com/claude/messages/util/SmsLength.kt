package com.claude.messages.util

import android.telephony.SmsMessage

/** Segment counting for the composer's hint. */
object SmsLength {

    /**
     * A short line describing how a draft will be sent, or null while it still
     * fits in a single segment and the detail would just be noise.
     *
     * Uses the platform's own calculation so GSM-7 versus UCS-2 encoding — which
     * halves the per-segment limit for Arabic and emoji — is accounted for.
     */
    fun describe(draft: String): String? {
        if (draft.isEmpty()) return null
        val counts = runCatching { SmsMessage.calculateLength(draft, false) }.getOrNull()
            ?: return null
        val segments = counts[0]
        val remaining = counts[2]
        return when {
            segments > 1 -> "$segments messages · $remaining left in this one"
            remaining <= 20 -> "$remaining characters left"
            else -> null
        }
    }
}
