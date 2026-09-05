package com.claude.messages.util

/**
 * Pure phone-number comparison helpers, free of Android dependencies so the
 * rule engine (and its unit tests) can use them directly.
 */
object PhoneNumbers {

    /**
     * Comparison key for a number. Keeps the last nine digits so that local and
     * international spellings of the same number compare equal
     * (e.g. 0555 123 4567 and +1 555 123 4567).
     */
    fun normalize(number: String): String {
        val digits = number.filter { it.isDigit() }
        return if (digits.length > 9) digits.takeLast(9) else digits
    }

    /** True when two numbers refer to the same line, ignoring formatting. */
    fun sameNumber(a: String, b: String): Boolean {
        // A blank address is "unknown", never a match — otherwise a rule with an
        // empty number entry would fire on every message that lacks a sender.
        if (a.isBlank() || b.isBlank()) return false
        if (a.equals(b, ignoreCase = true)) return true
        val na = normalize(a)
        val nb = normalize(b)
        return na.isNotEmpty() && na == nb
    }

    /** Short-code senders (banks, carriers) never appear in the phone book. */
    fun isShortCode(number: String): Boolean =
        number.filter { it.isDigit() }.length in 1..5 && !number.startsWith("+")
}
