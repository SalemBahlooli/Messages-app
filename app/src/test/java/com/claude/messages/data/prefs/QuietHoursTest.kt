package com.claude.messages.data.prefs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {

    private val overnight = FeatureSettings(quietHoursEnabled = true, quietStart = 23 * 60, quietEnd = 7 * 60)
    private val daytime = FeatureSettings(quietHoursEnabled = true, quietStart = 13 * 60, quietEnd = 15 * 60)

    @Test fun `overnight span covers both sides of midnight`() {
        assertTrue(overnight.isQuietAt(23 * 60 + 30))
        assertTrue(overnight.isQuietAt(2 * 60))
        assertFalse(overnight.isQuietAt(7 * 60))
        assertFalse(overnight.isQuietAt(12 * 60))
    }

    @Test fun `daytime span`() {
        assertTrue(daytime.isQuietAt(14 * 60))
        assertFalse(daytime.isQuietAt(15 * 60))
        assertFalse(daytime.isQuietAt(9 * 60))
    }

    @Test fun `disabled is never quiet`() =
        assertFalse(overnight.copy(quietHoursEnabled = false).isQuietAt(2 * 60))
}
