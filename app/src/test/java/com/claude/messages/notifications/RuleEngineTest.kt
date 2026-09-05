package com.claude.messages.notifications

import com.claude.messages.data.db.ContentMatch
import com.claude.messages.data.db.NotificationRule
import com.claude.messages.data.db.RuleImportance
import com.claude.messages.data.db.SenderMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    private fun rule(
        id: Long = 1,
        name: String = "Rule",
        order: Int = 0,
        enabled: Boolean = true,
        senderMatch: SenderMatch = SenderMatch.ANY,
        senderValues: String = "",
        contentMatch: ContentMatch = ContentMatch.ANY,
        contentValues: String = "",
        caseSensitive: Boolean = false,
        stopOnMatch: Boolean = true,
    ) = NotificationRule(
        id = id,
        name = name,
        order = order,
        enabled = enabled,
        senderMatch = senderMatch,
        senderValues = senderValues,
        contentMatch = contentMatch,
        contentValues = contentValues,
        caseSensitive = caseSensitive,
        stopOnMatch = stopOnMatch,
    )

    private fun message(address: String = "+15551234567", body: String = "hello", known: Boolean = false) =
        IncomingMessage(address = address, body = body, senderIsKnownContact = known)

    // ------------------------------------------------------------ sender

    @Test
    fun `number rule matches the listed sender`() {
        val r = rule(senderMatch = SenderMatch.NUMBERS, senderValues = "+15551234567")
        assertNotNull(RuleEngine.evaluate(r, message(address = "+15551234567")))
    }

    @Test
    fun `number rule matches across local and international formats`() {
        val r = rule(senderMatch = SenderMatch.NUMBERS, senderValues = "05551234567")
        assertNotNull(RuleEngine.evaluate(r, message(address = "+15551234567")))
    }

    @Test
    fun `number rule ignores a different sender`() {
        val r = rule(senderMatch = SenderMatch.NUMBERS, senderValues = "+15550000000")
        assertNull(RuleEngine.evaluate(r, message(address = "+15551234567")))
    }

    @Test
    fun `unknown-sender rule only fires for numbers not in contacts`() {
        val r = rule(senderMatch = SenderMatch.NOT_IN_CONTACTS)
        assertNotNull(RuleEngine.evaluate(r, message(known = false)))
        assertNull(RuleEngine.evaluate(r, message(known = true)))
    }

    // ----------------------------------------------------------- content

    @Test
    fun `contains-any matches one keyword and is case-insensitive by default`() {
        val r = rule(contentMatch = ContentMatch.CONTAINS_ANY, contentValues = "OTP\ncode")
        assertNotNull(RuleEngine.evaluate(r, message(body = "Your otp is 123456")))
    }

    @Test
    fun `case-sensitive rule respects the keyword casing`() {
        val r = rule(
            contentMatch = ContentMatch.CONTAINS_ANY,
            contentValues = "OTP",
            caseSensitive = true,
        )
        assertNull(RuleEngine.evaluate(r, message(body = "your otp is 1234")))
        assertNotNull(RuleEngine.evaluate(r, message(body = "your OTP is 1234")))
    }

    @Test
    fun `contains-all requires every keyword`() {
        val r = rule(contentMatch = ContentMatch.CONTAINS_ALL, contentValues = "delivery\ntoday")
        assertNotNull(RuleEngine.evaluate(r, message(body = "Your delivery arrives today")))
        assertNull(RuleEngine.evaluate(r, message(body = "Your delivery is delayed")))
    }

    @Test
    fun `starts-with only matches at the beginning`() {
        val r = rule(contentMatch = ContentMatch.STARTS_WITH, contentValues = "ALERT")
        assertNotNull(RuleEngine.evaluate(r, message(body = "ALERT: server down")))
        assertNull(RuleEngine.evaluate(r, message(body = "Server down, ALERT")))
    }

    @Test
    fun `regex rule matches a numeric code`() {
        val r = rule(contentMatch = ContentMatch.REGEX, contentValues = "\\b\\d{4,8}\\b")
        assertNotNull(RuleEngine.evaluate(r, message(body = "Code 483920 expires soon")))
        assertNull(RuleEngine.evaluate(r, message(body = "No digits here")))
    }

    @Test
    fun `an invalid regex never matches instead of crashing`() {
        val r = rule(contentMatch = ContentMatch.REGEX, contentValues = "([unclosed")
        assertNull(RuleEngine.evaluate(r, message(body = "anything")))
    }

    @Test
    fun `sender and content conditions must both hold`() {
        val r = rule(
            senderMatch = SenderMatch.NUMBERS,
            senderValues = "+15551234567",
            contentMatch = ContentMatch.CONTAINS_ANY,
            contentValues = "urgent",
        )
        assertNotNull(RuleEngine.evaluate(r, message(address = "+15551234567", body = "urgent!")))
        assertNull(RuleEngine.evaluate(r, message(address = "+15551234567", body = "hi")))
        assertNull(RuleEngine.evaluate(r, message(address = "+15559999999", body = "urgent!")))
    }

    // ------------------------------------------------------------ order

    @Test
    fun `the first matching rule in order wins`() {
        val first = rule(id = 1, name = "First", order = 0, contentMatch = ContentMatch.CONTAINS_ANY, contentValues = "code")
        val second = rule(id = 2, name = "Second", order = 1, contentMatch = ContentMatch.CONTAINS_ANY, contentValues = "code")
        val match = RuleEngine.match(listOf(second, first), message(body = "your code"))
        assertEquals("First", match?.rule?.name)
    }

    @Test
    fun `disabled rules are skipped`() {
        val disabled = rule(id = 1, name = "Off", order = 0, enabled = false, contentMatch = ContentMatch.CONTAINS_ANY, contentValues = "code")
        val enabled = rule(id = 2, name = "On", order = 1, contentMatch = ContentMatch.CONTAINS_ANY, contentValues = "code")
        val match = RuleEngine.match(listOf(disabled, enabled), message(body = "your code"))
        assertEquals("On", match?.rule?.name)
    }

    @Test
    fun `a non-stopping rule yields to a later match`() {
        val soft = rule(id = 1, name = "Soft", order = 0, contentMatch = ContentMatch.CONTAINS_ANY, contentValues = "code", stopOnMatch = false)
        val hard = rule(id = 2, name = "Hard", order = 1, contentMatch = ContentMatch.CONTAINS_ANY, contentValues = "bank")
        assertEquals("Hard", RuleEngine.match(listOf(soft, hard), message(body = "bank code"))?.rule?.name)
        // With nothing else matching, the soft rule still applies.
        assertEquals("Soft", RuleEngine.match(listOf(soft, hard), message(body = "your code"))?.rule?.name)
    }

    @Test
    fun `no rules means no match`() {
        assertNull(RuleEngine.match(emptyList(), message()))
    }

    // -------------------------------------------------------- validation

    @Test
    fun `a rule without a name is rejected`() {
        assertNotNull(RuleEngine.validate(rule(name = "")))
    }

    @Test
    fun `a catch-all rule is rejected`() {
        val r = rule(name = "Everything", senderMatch = SenderMatch.ANY, contentMatch = ContentMatch.ANY)
        assertNotNull(RuleEngine.validate(r))
    }

    @Test
    fun `a number rule with no numbers is rejected`() {
        assertNotNull(RuleEngine.validate(rule(senderMatch = SenderMatch.NUMBERS, senderValues = "")))
    }

    @Test
    fun `a keyword rule with no keywords is rejected`() {
        assertNotNull(RuleEngine.validate(rule(contentMatch = ContentMatch.CONTAINS_ANY, contentValues = "")))
    }

    @Test
    fun `a valid rule passes validation`() {
        val r = rule(
            name = "Bank codes",
            contentMatch = ContentMatch.CONTAINS_ANY,
            contentValues = "OTP",
        )
        assertNull(RuleEngine.validate(r))
    }

    // ----------------------------------------------------------- channel

    @Test
    fun `changing the sound changes the channel id`() {
        val base = rule().copy(soundUri = "content://media/1")
        val changed = base.copy(soundUri = "content://media/2")
        assertFalse(base.channelId == changed.channelId)
    }

    @Test
    fun `an unchanged rule keeps a stable channel id`() {
        val a = rule().copy(soundUri = "content://media/1", importance = RuleImportance.HIGH)
        val b = rule().copy(soundUri = "content://media/1", importance = RuleImportance.HIGH)
        assertTrue(a.channelId == b.channelId)
    }

    @Test
    fun `describe summarises the conditions`() {
        val r = rule(
            senderMatch = SenderMatch.NUMBERS,
            senderValues = "+15551234567",
            contentMatch = ContentMatch.CONTAINS_ANY,
            contentValues = "urgent",
        )
        val text = RuleEngine.describe(r)
        assertTrue(text.contains("+15551234567"))
        assertTrue(text.contains("urgent"))
    }
}
