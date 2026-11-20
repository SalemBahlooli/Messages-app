package com.claude.messages.notifications

import android.content.Context
import com.claude.messages.R
import com.claude.messages.data.db.ContentMatch
import com.claude.messages.data.db.NotificationRule
import com.claude.messages.data.db.SenderMatch

/**
 * Turns the engine's structured results into text in the phone's language.
 * Kept out of [RuleEngine] so the matching logic stays free of Android and
 * testable, and so the same rule reads correctly in any locale.
 */
class RuleTextFormatter(private val context: Context) {

    /** One-line summary of a rule's conditions, for the rules list. */
    fun describe(rule: NotificationRule): String {
        val sender = context.getString(
            when (rule.senderMatch) {
                SenderMatch.ANY -> R.string.sender_any
                SenderMatch.NUMBERS -> R.string.sender_numbers
                SenderMatch.IN_CONTACTS -> R.string.sender_in_contacts
                SenderMatch.NOT_IN_CONTACTS -> R.string.sender_unknown
            }
        )
        val senderDetail = if (rule.senderMatch == SenderMatch.NUMBERS && rule.senderNumbers.isNotEmpty()) {
            rule.senderNumbers.joinToString(", ")
        } else {
            sender
        }

        val content = when (rule.contentMatch) {
            ContentMatch.ANY -> null
            ContentMatch.CONTAINS_ANY, ContentMatch.CONTAINS_ALL ->
                "${label(rule.contentMatch)}: ${rule.keywords.joinToString(", ")}"

            ContentMatch.STARTS_WITH ->
                "${label(rule.contentMatch)}: ${rule.keywords.firstOrNull().orEmpty()}"

            ContentMatch.REGEX -> "${label(rule.contentMatch)}: ${rule.contentValues.trim()}"
        }
        return listOfNotNull(senderDetail, content).joinToString(" · ")
    }

    /** Why a rule fired, e.g. for the rule tester. */
    fun explain(match: RuleMatch): String {
        val parts = listOf(match.senderReason, match.contentReason)
            .mapNotNull(::reasonText)
        return parts.joinToString(context.getString(R.string.reason_join))
    }

    private fun reasonText(reason: MatchReason): String? = when (reason) {
        MatchReason.Unconstrained -> null
        is MatchReason.SenderNumber -> context.getString(R.string.reason_sender_is, reason.number)
        MatchReason.SenderInContacts -> context.getString(R.string.reason_sender_saved)
        MatchReason.SenderUnknown -> context.getString(R.string.reason_sender_unknown)
        is MatchReason.ContainsKeyword ->
            context.getString(R.string.reason_contains, reason.keyword)

        is MatchReason.ContainsAll ->
            context.getString(R.string.reason_contains_all, reason.keywords.joinToString(", "))

        is MatchReason.StartsWith -> context.getString(R.string.reason_starts_with, reason.keyword)
        is MatchReason.MatchesRegex -> context.getString(R.string.reason_regex, reason.pattern)
    }

    fun problemText(problem: RuleProblem): String = context.getString(
        when (problem) {
            RuleProblem.NO_NAME -> R.string.problem_no_name
            RuleProblem.NO_NUMBERS -> R.string.problem_no_numbers
            RuleProblem.NO_KEYWORDS -> R.string.problem_no_keywords
            RuleProblem.BAD_REGEX -> R.string.problem_bad_regex
            RuleProblem.MATCHES_EVERYTHING -> R.string.problem_matches_everything
        }
    )

    fun label(match: SenderMatch): String = context.getString(
        when (match) {
            SenderMatch.ANY -> R.string.sender_any
            SenderMatch.NUMBERS -> R.string.sender_numbers
            SenderMatch.IN_CONTACTS -> R.string.sender_in_contacts
            SenderMatch.NOT_IN_CONTACTS -> R.string.sender_unknown
        }
    )

    fun label(match: ContentMatch): String = context.getString(
        when (match) {
            ContentMatch.ANY -> R.string.content_any
            ContentMatch.CONTAINS_ANY -> R.string.content_contains_any
            ContentMatch.CONTAINS_ALL -> R.string.content_contains_all
            ContentMatch.STARTS_WITH -> R.string.content_starts_with
            ContentMatch.REGEX -> R.string.content_regex
        }
    )
}
