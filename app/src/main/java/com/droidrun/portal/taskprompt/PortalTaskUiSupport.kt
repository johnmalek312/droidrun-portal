package com.droidrun.portal.taskprompt

import android.content.Context
import androidx.annotation.StringRes
import com.droidrun.portal.R
import com.droidrun.portal.state.ConnectionState
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class PortalTaskStatusAppearance {
    INFO,
    SUCCESS,
    ERROR,
}

object PortalTaskUiSupport {
    fun shouldShowTaskSurface(
        connectionState: ConnectionState,
        authToken: String,
    ): Boolean {
        return connectionState == ConnectionState.CONNECTED &&
            authToken.trim().isNotBlank()
    }

    fun statusLabel(context: Context, status: String): String {
        return context.getString(statusLabelRes(status))
    }

    @StringRes
    fun statusLabelRes(status: String): Int {
        return when (status) {
            PortalTaskTracking.STATUS_CREATED -> R.string.task_prompt_status_created
            PortalTaskTracking.STATUS_RUNNING -> R.string.task_prompt_status_running
            PortalTaskTracking.STATUS_PAUSED -> R.string.task_prompt_status_paused
            PortalTaskTracking.STATUS_CANCELLING -> R.string.task_prompt_status_cancelling
            PortalTaskTracking.STATUS_COMPLETED -> R.string.task_prompt_status_completed
            PortalTaskTracking.STATUS_FAILED -> R.string.task_prompt_status_failed
            PortalTaskTracking.STATUS_CANCELLED -> R.string.task_prompt_status_cancelled
            PortalTaskTracking.STATUS_TRACKING_TIMEOUT -> R.string.task_prompt_status_tracking_timeout
            else -> R.string.task_prompt_status_running
        }
    }

    fun statusAppearance(status: String): PortalTaskStatusAppearance {
        return when (status) {
            PortalTaskTracking.STATUS_COMPLETED -> PortalTaskStatusAppearance.SUCCESS
            PortalTaskTracking.STATUS_FAILED,
            PortalTaskTracking.STATUS_CANCELLED,
            -> PortalTaskStatusAppearance.ERROR

            else -> PortalTaskStatusAppearance.INFO
        }
    }

    fun buildSummary(
        context: Context,
        status: String,
        summary: String?,
        steps: Int?,
    ): String? {
        val normalizedSummary = summary?.trim()?.takeIf { it.isNotBlank() }
        return when (status) {
            PortalTaskTracking.STATUS_CREATED -> normalizedSummary
                ?: context.getString(R.string.task_prompt_created_generic)

            PortalTaskTracking.STATUS_RUNNING -> normalizedSummary
                ?: context.getString(R.string.task_prompt_running_generic)

            PortalTaskTracking.STATUS_PAUSED -> normalizedSummary
                ?: context.getString(R.string.task_prompt_paused_generic)

            PortalTaskTracking.STATUS_CANCELLING -> normalizedSummary
                ?: context.getString(R.string.task_prompt_cancelling_generic)

            PortalTaskTracking.STATUS_COMPLETED -> normalizedSummary
                ?: steps?.let { context.getString(R.string.task_prompt_completed_steps, it) }
                ?: context.getString(R.string.task_prompt_completed_generic)

            PortalTaskTracking.STATUS_FAILED -> normalizedSummary
                ?: steps?.let { context.getString(R.string.task_prompt_failed_steps, it) }
                ?: context.getString(R.string.task_prompt_failed_generic)

            PortalTaskTracking.STATUS_CANCELLED -> normalizedSummary
                ?: context.getString(R.string.task_prompt_cancelled_generic)

            PortalTaskTracking.STATUS_TRACKING_TIMEOUT ->
                context.getString(R.string.task_prompt_timeout_stopped)

            else -> normalizedSummary
        }
    }

    fun formatTimestamp(raw: String?): String? {
        val normalized = raw?.trim().orEmpty()
        if (normalized.isEmpty()) return null

        val zoneId = ZoneId.systemDefault()
        val zonedDateTime = try {
            OffsetDateTime.parse(normalized).atZoneSameInstant(zoneId)
        } catch (_: Exception) {
            try {
                Instant.parse(normalized).atZone(zoneId)
            } catch (_: Exception) {
                try {
                    LocalDateTime.parse(normalized).atZone(zoneId)
                } catch (_: Exception) {
                    try {
                        LocalDateTime.parse(
                            normalized.removeSuffix("Z"),
                            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                        ).atOffset(ZoneOffset.UTC).atZoneSameInstant(zoneId)
                    } catch (_: Exception) {
                        return normalized
                    }
                }
            }
        }

        val pattern = if (zonedDateTime.year == LocalDateTime.now(zoneId).year) {
            "MMM d, HH:mm"
        } else {
            "MMM d, yyyy, HH:mm"
        }
        return zonedDateTime.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    fun booleanLabel(context: Context, value: Boolean?): String {
        return when (value) {
            true -> context.getString(R.string.task_details_boolean_enabled)
            false -> context.getString(R.string.task_details_boolean_disabled)
            null -> context.getString(R.string.task_details_value_unavailable)
        }
    }
}
