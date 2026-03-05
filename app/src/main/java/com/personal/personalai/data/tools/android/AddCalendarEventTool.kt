package com.personal.personalai.data.tools.android

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * Adds a calendar event by launching the system calendar app with pre-filled details.
 * No WRITE_CALENDAR permission needed — the calendar app handles saving.
 */
class AddCalendarEventTool @Inject constructor(
    @ApplicationContext private val context: Context
) : AgentTool {

    override val name = "add_calendar_event"
    override val description = """
        Add an event to the device calendar. Opens the calendar app pre-filled with the event
        details so the user can review and save it. Dates must be in ISO 8601 format
        (e.g. '2026-03-10T14:00:00').
    """.trimIndent()

    override val supportsBackground = false

    override fun parametersSchema(): JSONObject = JSONObject(
        """
        {
            "type": "object",
            "properties": {
                "title": {
                    "type": "string",
                    "description": "Event title / name"
                },
                "start_time": {
                    "type": "string",
                    "description": "Event start time in ISO 8601 format, e.g. '2026-03-10T14:00:00'"
                },
                "end_time": {
                    "type": "string",
                    "description": "Event end time in ISO 8601 format. Defaults to 1 hour after start."
                },
                "description": {
                    "type": "string",
                    "description": "Optional event notes or description"
                },
                "location": {
                    "type": "string",
                    "description": "Optional event location"
                }
            },
            "required": ["title", "start_time"]
        }
        """.trimIndent()
    )

    override suspend fun execute(params: JSONObject): ToolResult {
        val title = params.optString("title", "").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("title parameter is required")
        val startTimeStr = params.optString("start_time", "").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("start_time parameter is required")

        val startMillis = parseIso(startTimeStr)
            ?: return ToolResult.Error("Invalid start_time format. Use ISO 8601, e.g. '2026-03-10T14:00:00'")

        val endTimeStr = params.optString("end_time", "").takeIf { it.isNotBlank() }
        val endMillis = if (endTimeStr != null) {
            parseIso(endTimeStr) ?: (startMillis + 3_600_000L) // fallback: +1 hour
        } else {
            startMillis + 3_600_000L // default: +1 hour
        }

        val description = params.optString("description", "").takeIf { it.isNotBlank() }
        val location = params.optString("location", "").takeIf { it.isNotBlank() }

        return try {
            val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                putExtra(CalendarContract.Events.TITLE, title)
                if (description != null) putExtra(CalendarContract.Events.DESCRIPTION, description)
                if (location != null) putExtra(CalendarContract.Events.EVENT_LOCATION, location)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.Success("""{"opened":true,"title":${JSONObject.quote(title)},"start":"$startTimeStr"}""")
        } catch (e: Exception) {
            ToolResult.Error("Failed to open calendar: ${e.message}")
        }
    }

    private fun parseIso(iso: String): Long? = try {
        // Try with timezone offset first, then assume local time by appending 'Z'
        val normalized = if (iso.contains('Z') || iso.contains('+') || iso.last() == 'Z') iso
                         else "${iso}Z"
        Instant.parse(normalized).toEpochMilli()
    } catch (_: DateTimeParseException) {
        try {
            // Try without timezone (treat as UTC)
            Instant.parse("${iso}Z").toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
