package com.claude.messages.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpDetectorTest {

    @Test fun `english code after keyword`() =
        assertEquals("482913", OtpDetector.find("Your verification code is 482913. Do not share it."))

    @Test fun `code before keyword`() =
        assertEquals("7731", OtpDetector.find("7731 is your OTP for login"))

    @Test fun `arabic message`() =
        assertEquals("5521", OtpDetector.find("رمز التحقق الخاص بك: 5521"))

    @Test fun `arabic indic digits are normalised`() =
        assertEquals("1234", OtpDetector.find("رمز الدخول ١٢٣٤"))

    @Test fun `ignores messages without a keyword`() =
        assertNull(OtpDetector.find("Meet me at 1830 near gate 4521"))

    @Test fun `ignores amounts and years`() =
        assertNull(OtpDetector.find("Security notice: 2025 card fee 1,250.500 SAR"))

    @Test fun `prefers the anchored number over an earlier one`() =
        assertEquals("998877", OtpDetector.find("Order 2024 ready. Code: 998877"))

    @Test fun `does not pick a card fragment`() =
        assertEquals("4410", OtpDetector.find("Card *5532 one-time password 4410"))

    @Test fun `blank is null`() = assertNull(OtpDetector.find("  "))
}
