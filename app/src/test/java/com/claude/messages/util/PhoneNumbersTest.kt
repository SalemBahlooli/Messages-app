package com.claude.messages.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {

    @Test
    fun `identical numbers match`() {
        assertTrue(PhoneNumbers.sameNumber("+15551234567", "+15551234567"))
    }

    @Test
    fun `formatting differences are ignored`() {
        assertTrue(PhoneNumbers.sameNumber("+1 (555) 123-4567", "+15551234567"))
    }

    @Test
    fun `local and international forms match`() {
        assertTrue(PhoneNumbers.sameNumber("05551234567", "+15551234567"))
        assertTrue(PhoneNumbers.sameNumber("0501234567", "+966501234567"))
    }

    @Test
    fun `different numbers do not match`() {
        assertFalse(PhoneNumbers.sameNumber("+15551234567", "+15559999999"))
    }

    @Test
    fun `an empty number never matches`() {
        assertFalse(PhoneNumbers.sameNumber("", ""))
        assertFalse(PhoneNumbers.sameNumber("", "+15551234567"))
    }

    @Test
    fun `short codes compare exactly and are recognised`() {
        assertTrue(PhoneNumbers.isShortCode("4567"))
        assertFalse(PhoneNumbers.isShortCode("+15551234567"))
        assertTrue(PhoneNumbers.sameNumber("4567", "4567"))
    }

    @Test
    fun `normalize keeps the last nine digits`() {
        assertEquals("551234567", PhoneNumbers.normalize("+1 555 123 4567"))
        assertEquals("4567", PhoneNumbers.normalize("4567"))
    }
}
