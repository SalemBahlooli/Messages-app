package com.claude.messages.util

/** Something tappable inside a message. */
data class TextLink(val start: Int, val end: Int, val kind: Kind, val target: String) {
    enum class Kind { URL, PHONE, EMAIL }
}

/**
 * Finds links, phone numbers and e-mail addresses in a message body.
 *
 * Written by hand rather than with android.util.Patterns: Patterns.PHONE
 * happily matches amounts like "1,250.500" in bank messages, and this way the
 * rules are unit-testable on the JVM.
 */
object TextLinks {

    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

    private val URL = Regex(
        "(?:https?://|www\\.)[^\\s<>\"]+" +
            "|(?<![@\\w.])[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\." +
            "(?:com|net|org|sa|io|me|ly|co|app|info|link|gov|edu|ae|qa|kw|om|bh|eg)" +
            "(?:/[^\\s<>\"]*)?(?![\\w@])",
        RegexOption.IGNORE_CASE,
    )

    /** Starts like a phone number would (+, 0, 9, 8) and has 8–14 digits. */
    private val PHONE = Regex("(?<![\\w+.,])\\+?[0-9][0-9 \\-]{6,16}[0-9](?![\\w.,%])")

    private const val TRAILING = ".,;:!?)]}'\"،؟"

    fun find(text: String): List<TextLink> {
        if (text.isEmpty()) return emptyList()
        val found = mutableListOf<TextLink>()

        fun overlaps(start: Int, end: Int) = found.any { start < it.end && end > it.start }

        EMAIL.findAll(text).forEach { m ->
            found += TextLink(m.range.first, m.range.last + 1, TextLink.Kind.EMAIL, "mailto:${m.value}")
        }

        URL.findAll(text).forEach { m ->
            var value = m.value
            while (value.isNotEmpty() && value.last() in TRAILING) value = value.dropLast(1)
            val start = m.range.first
            val end = start + value.length
            if (value.isEmpty() || overlaps(start, end)) return@forEach
            val target = if (value.startsWith("http", ignoreCase = true)) value else "https://$value"
            found += TextLink(start, end, TextLink.Kind.URL, target)
        }

        PHONE.findAll(text).forEach { m ->
            val value = m.value.trim()
            val digits = value.count { it.isDigit() }
            val first = value.firstOrNull { it.isDigit() || it == '+' }
            val plausible = digits in 8..14 && (first == '+' || value.first() in "089")
            if (!plausible || overlaps(m.range.first, m.range.last + 1)) return@forEach
            found += TextLink(
                m.range.first,
                m.range.first + value.length,
                TextLink.Kind.PHONE,
                "tel:" + value.filter { it.isDigit() || it == '+' },
            )
        }

        return found.sortedBy { it.start }
    }
}
