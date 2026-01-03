package com.intagri.mtgleader.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeFormatUtils {
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

    fun formatDurationSeconds(seconds: Long): String {
        val safeSeconds = seconds.coerceAtLeast(0L)
        val hours = safeSeconds / 3600
        val minutes = (safeSeconds % 3600) / 60
        val remainingSeconds = safeSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, remainingSeconds)
        } else {
            String.format("%d:%02d", minutes, remainingSeconds)
        }
    }

    fun formatEpochDate(epochMillis: Long): String {
        val instant = Instant.ofEpochMilli(epochMillis)
        return dateFormatter.withZone(ZoneId.systemDefault()).format(instant)
    }
}
