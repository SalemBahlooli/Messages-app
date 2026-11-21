package com.claude.messages.util

/**
 * Finds the one-time code in a verification message, so it can be copied with
 * one tap instead of being memorised digit by digit.
 *
 * Pure Kotlin, no Android dependencies: fully unit-tested.
 */
object OtpDetector {

    /** English markers, matched as whole words so "pin" doesn't fire on "shopping". */
    private val ENGLISH = Regex(
        "\\b(code|otp|passcode|password|pin|verification|verify|one[- ]time|" +
            "security|authentication|login|sign[- ]in)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** Arabic markers; Arabic attaches prefixes, so a substring match is right here. */
    private val ARABIC = listOf("رمز", "كود", "التحقق", "تحقق", "كلمة المرور", "الدخول", "تفعيل", "سري")

    /** A run of 4–8 digits not glued to other digits, letters or a decimal point. */
    private val CANDIDATE = Regex("(?<![\\d.,/:A-Za-z*#])(\\d{4,8})(?![\\d,/:A-Za-z%]|\\.\\d)")

    /** Strong hints that the number right after them is the code. */
    private val ANCHORED = Regex(
        "(?:\\b(?:code|otp|pin|passcode|password)\\b|رمز|كود)[^\\d\\n]{0,24}?(\\d{4,8})(?!\\d)",
        RegexOption.IGNORE_CASE,
    )

    fun find(body: String): String? {
        if (body.isBlank()) return null
        val text = normalizeDigits(body)
        if (!ENGLISH.containsMatchIn(text) && ARABIC.none { text.contains(it) }) return null

        ANCHORED.find(text)?.let { return it.groupValues[1] }

        // Otherwise the first standalone 4–8 digit run that doesn't look like a
        // year, an amount or part of a card number.
        return CANDIDATE.findAll(text)
            .map { it.groupValues[1] }
            .firstOrNull { !looksLikeYear(it) }
    }

    private fun looksLikeYear(value: String): Boolean =
        value.length == 4 && value.toIntOrNull()?.let { it in 1990..2099 } == true

    /** Arabic-Indic and Persian digits to ASCII, so "١٢٣٤" is found as "1234". */
    fun normalizeDigits(text: String): String = buildString(text.length) {
        text.forEach { ch ->
            append(
                when (ch) {
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    else -> ch
                }
            )
        }
    }
}
