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

/**
 * Why a rule applied, as data rather than prose.
 *
 * The engine deliberately produces no user-facing text: the same reason has to
 * render in whatever language the phone is set to, so formatting belongs in the
 * presentation layer (see RuleTextFormatter).
 */
sealed interface MatchReason {
    /** The condition was "anyone" / "any text" — it constrains nothing. */
    data object Unconstrained : MatchReason

    data class SenderNumber(val number: String) : MatchReason
    data object SenderInContacts : MatchReason
    data object SenderUnknown : MatchReason

    data class ContainsKeyword(val keyword: String) : MatchReason
    data class ContainsAll(val keywords: List<String>) : MatchReason
    data class StartsWith(val keyword: String) : MatchReason
    data class MatchesRegex(val pattern: String) : MatchReason
}

/** The rule that won, and the conditions that made it win. */
data class RuleMatch(
    val rule: NotificationRule,
    val senderReason: MatchReason,
    val contentReason: MatchReason,
)

/** Reasons a rule cannot be saved. The UI turns these into sentences. */
enum class RuleProblem {
    NO_NAME,
    NO_NUMBERS,
    NO_KEYWORDS,
    BAD_REGEX,
    MATCHES_EVERYTHING,
}

/**
 * Pure matching logic for [NotificationRule]s. Free of Android dependencies so
 * the whole decision table can be unit-tested.
 */
object RuleEngine {

    /**
     * Returns the first enabled rule that matches, honouring [NotificationRule.order].
     * Rules with `stopOnMatch = false` match but let a later, more specific rule
     * take over if one exists.
     */
    fun match(rules: List<NotificationRule>, message: IncomingMessage): RuleMatch? {
        var softMatch: RuleMatch? = null
        for (rule in rules.sortedWith(compareBy({ it.order }, { it.id }))) {
            if (!rule.enabled) continue
            val hit = evaluate(rule, message) ?: continue
            if (rule.stopOnMatch) return hit
            if (softMatch == null) softMatch = hit
        }
        return softMatch
    }

    /** The match for [rule] against [message], or null when it doesn't apply. */
    fun evaluate(rule: NotificationRule, message: IncomingMessage): RuleMatch? {
        val sender = matchSender(rule, message) ?: return null
        val content = matchContent(rule, message) ?: return null
        return RuleMatch(rule, sender, content)
    }

    private fun matchSender(rule: NotificationRule, message: IncomingMessage): MatchReason? =
        when (rule.senderMatch) {
            SenderMatch.ANY -> MatchReason.Unconstrained

            SenderMatch.NUMBERS -> rule.senderNumbers
                .firstOrNull { PhoneNumbers.sameNumber(it, message.address) }
                ?.let(MatchReason::SenderNumber)

            SenderMatch.IN_CONTACTS ->
                if (message.senderIsKnownContact) MatchReason.SenderInContacts else null

            SenderMatch.NOT_IN_CONTACTS ->
                if (!message.senderIsKnownContact) MatchReason.SenderUnknown else null
        }

    private fun matchContent(rule: NotificationRule, message: IncomingMessage): MatchReason? {
        val body = if (rule.caseSensitive) message.body else message.body.lowercase()
        fun norm(s: String) = if (rule.caseSensitive) s else s.lowercase()

        return when (rule.contentMatch) {
            ContentMatch.ANY -> MatchReason.Unconstrained

            ContentMatch.CONTAINS_ANY -> rule.keywords
                .firstOrNull { body.contains(norm(it)) }
                ?.let(MatchReason::ContainsKeyword)

            ContentMatch.CONTAINS_ALL -> {
                val keywords = rule.keywords
                if (keywords.isNotEmpty() && keywords.all { body.contains(norm(it)) }) {
                    MatchReason.ContainsAll(keywords)
                } else null
            }

            ContentMatch.STARTS_WITH -> rule.keywords.firstOrNull()
                ?.takeIf { body.startsWith(norm(it)) }
                ?.let(MatchReason::StartsWith)

            ContentMatch.REGEX -> {
                val pattern = rule.contentValues.trim()
                if (pattern.isEmpty()) return null
                val options = if (rule.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                runCatching { Regex(pattern, options) }.getOrNull()
                    ?.takeIf { it.containsMatchIn(message.body) }
                    ?.let { MatchReason.MatchesRegex(pattern) }
            }
        }
    }

    /** Checks a rule before saving; returns the problem or null when valid. */
    fun validate(rule: NotificationRule): RuleProblem? = when {
        rule.name.isBlank() -> RuleProblem.NO_NAME

        rule.senderMatch == SenderMatch.NUMBERS && rule.senderNumbers.isEmpty() ->
            RuleProblem.NO_NUMBERS

        rule.contentMatch != ContentMatch.ANY && rule.contentMatch != ContentMatch.REGEX &&
            rule.keywords.isEmpty() -> RuleProblem.NO_KEYWORDS

        rule.contentMatch == ContentMatch.REGEX &&
            runCatching { Regex(rule.contentValues.trim()) }.isFailure -> RuleProblem.BAD_REGEX

        rule.senderMatch == SenderMatch.ANY && rule.contentMatch == ContentMatch.ANY ->
            RuleProblem.MATCHES_EVERYTHING

        else -> null
    }
}
