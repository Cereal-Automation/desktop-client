package com.cereal.client.presentation.notification

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Formats [timestampMillis] as a short, human-readable relative time (e.g. "2m ago", "1h ago",
 * "Yesterday"). Falls back to an absolute date for anything older than a week. [nowMillis] is
 * injectable so the result is deterministic in tests.
 */
fun formatRelativeTime(
    timestampMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
): String {
    val diff = nowMillis - timestampMillis
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)

    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        minutes < MINUTES_PER_HOUR -> "${minutes}m ago"
        hours < HOURS_PER_DAY -> "${hours}h ago"
        days == 1L -> "Yesterday"
        days < DAYS_PER_WEEK -> "${days}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestampMillis))
    }
}

private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L
private const val DAYS_PER_WEEK = 7L
