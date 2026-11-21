package com.claude.messages.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLinksTest {

    private fun targets(text: String) = TextLinks.find(text).map { it.kind to it.target }

    @Test fun `full url with trailing punctuation`() = assertEquals(
        listOf(TextLink.Kind.URL to "https://example.com/a?b=1"),
        targets("See https://example.com/a?b=1."),
    )

    @Test fun `bare domain gets a scheme`() = assertEquals(
        listOf(TextLink.Kind.URL to "https://bit.ly/x9"),
        targets("Track it: bit.ly/x9"),
    )

    @Test fun `email is not also a url`() = assertEquals(
        listOf(TextLink.Kind.EMAIL to "mailto:help@bank.com.sa"),
        targets("Write to help@bank.com.sa"),
    )

    @Test fun `saudi mobile number`() = assertEquals(
        listOf(TextLink.Kind.PHONE to "tel:0551234567"),
        targets("اتصل على 0551234567"),
    )

    @Test fun `international number with spaces`() = assertEquals(
        listOf(TextLink.Kind.PHONE to "tel:+966551234567"),
        targets("Call +966 55 123 4567 now"),
    )

    @Test fun `amounts are not phone numbers`() =
        assertTrue(TextLinks.find("Purchase 1,250.500 SAR balance 98,000.00").isEmpty())

    @Test fun `short codes are not phone numbers`() =
        assertTrue(TextLinks.find("Your code 482913").isEmpty())

    @Test fun `ranges point at the right text`() {
        val text = "Go to www.site.com today"
        val link = TextLinks.find(text).single()
        assertEquals("www.site.com", text.substring(link.start, link.end))
    }
}
