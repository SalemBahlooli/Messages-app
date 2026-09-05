package com.claude.messages.notifications

import com.claude.messages.data.db.ContentMatch
import com.claude.messages.data.db.NotificationRule
import com.claude.messages.data.db.SenderMatch
import com.claude.messages.util.PhoneNumbers

/** Everything the engine needs to know about an incoming message. */
data class IncomingMessage(
    val address: String,
    val body: String,
    /** Whether the sender is present in the phone book. */
    val senderIsKnownContact: Boolean = false,
)

/** The rule that won, plus why — the "why" is surfaced in the rule tester UI. */
data class RuleMatch(
    val rule: NotificationRule,
    val reason: String,
)

/**
 * Pure matching logic for [NotificationRule]s. Deliberately free of Android
 * dependencies so the whole decision table can be unit-tested.
 */
object RuleEngine {

    /**
     * Returns the first enabled rule that matches, honouring [NotificationRule.order].
     * Rules with `stopOnMatch = false` are skipped over as "annotations": they
     * match but let a later, more specific rule take over if one exists.
     */
    fun match(rules: List<NotificationRule>, message: IncomingMessage): RuleMatch? {
        var softMatch: RuleMatch? = null
        for (rule in rules.sortedWith(compareBy({ it.order }, { it.id }))) {
            if (!rule.enabled) continue
            val reason = evaluate(rule, message) ?: continue
            val hit = RuleMatch(rule, reason)
            if (rule.stopOnMatch) return hit
            if (softMatch == null) softMatch = hit
        }
        return softMatch
    }

    /** Returns a human-readable reason when [rule] applies to [message], else null. */
    fun evaluate(rule: NotificationRule, message: IncomingMessage): String? {
        val senderReason = matchSender(rule, message) ?: return null
        val contentReason = matchContent(rule, message) ?: return null
        return listOf(senderReason, contentReason)
            .filter { it.isNotEmpty() }
            .joinToString(" and ")
            .ifEmpty { "matches every message" }
    }

    private fun matchSender(rule: NotificationRule, message: IncomingMessage): String? =
        when (rule.senderMatch) {
            SenderMatch.ANY -> ""
            SenderMatch.NUMBERS -> rule.senderNumbers
                .firstOrNull { PhoneNumbers.sameNumber(it, message.address) }
                ?.let { "sender is $it" }

            SenderMatch.IN_CONTACTS ->
                if (message.senderIsKnownContact) "sender is a saved contact" else null

            SenderMatch.NOT_IN_CONTACTS ->
                if (!message.senderIsKnownContact) "sender is not in contacts" else null
        }

    private fun matchContent(rule: NotificationRule, message: IncomingMessage): String? {
        val body = if (rule.caseSensitive) message.body else message.body.lowercase()
        fun norm(s: String) = if (rule.caseSensitive) s else s.lowercase()

        return when (rule.contentMatch) {
            ContentMatch.ANY -> ""

            ContentMatch.CONTAINS_ANY -> rule.keywords
                .firstOrNull { body.contains(norm(it)) }
                ?.let { "message contains \"$it\"" }

            ContentMatch.CONTAINS_ALL -> {
                val keywords = rule.keywords
                if (keywords.isNotEmpty() && keywords.all { body.contains(norm(it)) }) {
                    "message contains all of ${keywords.joinToString(", ")}"
                } else null
            }

            ContentMatch.STARTS_WITH -> rule.keywords.firstOrNull()
                ?.takeIf { body.startsWith(norm(it)) }
                ?.let { "message starts with \"$it\"" }

            ContentMatch.REGEX -> {
                val pattern = rule.contentValues.trim()
                if (pattern.isEmpty()) return null
                val options = if (rule.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                runCatching { Regex(pattern, options) }.getOrNull()
                    ?.takeIf { it.containsMatchIn(message.body) }
                    ?.let { "message matches /$pattern/" }
            }
        }
    }

    /** Validates a rule before saving; returns an error message or null when valid. */
    fun validate(rule: NotificationRule): String? = when {
        rule.name.isBlank() -> "Give the rule a name"

        rule.senderMatch == SenderMatch.NUMBERS && rule.senderNumbers.isEmpty() ->
            "Add at least one phone number"

        rule.contentMatch != ContentMatch.ANY && rule.contentMatch != ContentMatch.REGEX &&
            rule.keywords.isEmpty() -> "Add at least one keyword"

        rule.contentMatch == ContentMatch.REGEX &&
            runCatching { Regex(rule.contentValues.trim()) }.isFailure ->
            "That regular expression is not valid"

        rule.senderMatch == SenderMatch.ANY && rule.contentMatch == ContentMatch.ANY ->
            "A rule that matches everything would override all others — add a condition"

        else -> null
    }

    /** One-line summary of a rule's conditions, shown in the rules list. */
    fun describe(rule: NotificationRule): String {
        val sender = when (rule.senderMatch) {
            SenderMatch.ANY -> "Any sender"
            SenderMatch.NUMBERS -> rule.senderNumbers.joinToString(", ").ifEmpty { "No numbers yet" }
            SenderMatch.IN_CONTACTS -> "Saved contacts"
            SenderMatch.NOT_IN_CONTACTS -> "Unknown numbers"
        }
        val content = when (rule.contentMatch) {
            ContentMatch.ANY -> null
            ContentMatch.CONTAINS_ANY -> "contains any of ${rule.keywords.joinToString(", ")}"
            ContentMatch.CONTAINS_ALL -> "contains all of ${rule.keywords.joinToString(", ")}"
            ContentMatch.STARTS_WITH -> "starts with ${rule.keywords.firstOrNull().orEmpty()}"
            ContentMatch.REGEX -> "matches /${rule.contentValues.trim()}/"
        }
        return listOfNotNull(sender, content).joinToString(" · ")
    }
}
