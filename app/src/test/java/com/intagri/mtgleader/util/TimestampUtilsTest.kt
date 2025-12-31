package com.intagri.mtgleader.util

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimestampUtilsTest {
    @Test
    fun nowRfc3339Millis_includesMillisPrecision() {
        val value = TimestampUtils.nowRfc3339Millis()
        val regex = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")
        assertTrue("Expected RFC3339 millis format", regex.matches(value))
    }

    @Test
    fun parseInstantSafe_handlesValidTimestamp() {
        val value = TimestampUtils.nowRfc3339Millis()
        val parsed = TimestampUtils.parseInstantSafe(value)
        assertNotNull(parsed)
    }
}
