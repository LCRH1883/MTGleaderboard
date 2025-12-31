package com.intagri.mtgleader.util

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object TimestampUtils {
    private const val RFC3339_MILLIS_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    private val formatter = DateTimeFormatter.ofPattern(RFC3339_MILLIS_FORMAT)
        .withZone(ZoneOffset.UTC)

    fun nowRfc3339Millis(): String {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        return formatter.format(now)
    }

    fun formatInstantRfc3339Millis(value: Instant): String {
        return formatter.format(value.truncatedTo(ChronoUnit.MILLIS))
    }

    fun parseInstantSafe(value: String?): Instant? {
        if (value.isNullOrBlank()) {
            return null
        }
        return try {
            Instant.parse(value)
        } catch (_: Exception) {
            null
        }
    }
}
