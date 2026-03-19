package com.droidrun.portal.triggers

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TriggerUiSupport {
    private val timestampFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun sourceLabel(source: TriggerSource): String {
        return when (source) {
            TriggerSource.TIME_DELAY -> "After a delay"
            TriggerSource.TIME_ABSOLUTE -> "At a specific time"
            TriggerSource.TIME_DAILY -> "Repeat every day"
            TriggerSource.TIME_WEEKLY -> "Repeat on selected weekdays"
            else -> source.name
                .lowercase(Locale.US)
                .split('_')
                .joinToString(" ") { it.replaceFirstChar { char -> char.titlecase(Locale.US) } }
        }
    }

    fun summary(rule: TriggerRule): String {
        return buildList {
            add(sourceLabel(rule.source))
            rule.packageName?.takeIf { it.isNotBlank() }?.let { add("pkg=$it") }
            rule.titleFilter?.takeIf { it.isNotBlank() }?.let { add("title=$it") }
            rule.textFilter?.takeIf { it.isNotBlank() }?.let { add("text=$it") }
            rule.activityFilter?.takeIf { it.isNotBlank() }?.let { add("activity=$it") }
            rule.networkType?.let { add("network=${it.name}") }
            rule.thresholdValue?.let { add("threshold=${rule.thresholdComparison.name.lowercase()} $it") }
            rule.phoneNumberFilter?.takeIf { it.isNotBlank() }?.let { add("phone=$it") }
            rule.callState?.let { add("call=${it.name}") }
            rule.delayMinutes?.let { add("delay=${it}m") }
            if (rule.source == TriggerSource.TIME_ABSOLUTE) {
                rule.absoluteTimeMillis?.let { add("at=${formatTimestamp(it)}") }
            }
            if (rule.source == TriggerSource.TIME_DAILY || rule.source == TriggerSource.TIME_WEEKLY) {
                val hour = rule.dailyHour ?: 0
                val minute = rule.dailyMinute ?: 0
                add(String.format(Locale.US, "%02d:%02d", hour, minute))
                if (rule.source == TriggerSource.TIME_WEEKLY) {
                    val labels = rule.resolvedWeeklyDaysOfWeek()
                        .mapNotNull { dayOfWeekLabel(it) }
                    if (labels.isNotEmpty()) {
                        add(labels.joinToString(", "))
                    }
                }
            }
        }.joinToString(" • ")
    }

    fun dispositionLabel(disposition: TriggerRunDisposition): String {
        return disposition.name
            .lowercase(Locale.US)
            .split('_')
            .joinToString(" ") { it.replaceFirstChar { char -> char.titlecase(Locale.US) } }
    }

    fun formatTimestamp(timestampMs: Long): String {
        return timestampFormatter.format(Date(timestampMs))
    }

    fun dayOfWeekEntries(): List<Pair<String, Int>> {
        return listOf(
            "Sunday" to java.util.Calendar.SUNDAY,
            "Monday" to java.util.Calendar.MONDAY,
            "Tuesday" to java.util.Calendar.TUESDAY,
            "Wednesday" to java.util.Calendar.WEDNESDAY,
            "Thursday" to java.util.Calendar.THURSDAY,
            "Friday" to java.util.Calendar.FRIDAY,
            "Saturday" to java.util.Calendar.SATURDAY,
        )
    }

    fun dayOfWeekLabel(dayOfWeek: Int): String? {
        return dayOfWeekEntries().firstOrNull { it.second == dayOfWeek }?.first
    }
}
