package com.personal.personalai.data.tools.android

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject

/**
 * Sets a device alarm via the AlarmClock intent. No permission required.
 * Opens the system clock app to confirm (skip_ui=false) or silently creates the alarm (skip_ui=true).
 * Note: This is different from schedule_task — it creates a real alarm in the Clock app.
 */
class SetAlarmTool @Inject constructor(
    @ApplicationContext private val context: Context
) : AgentTool {

    override val name = "set_alarm"
    override val description = """
        Set a device alarm in the system Clock app. Use this for wake-up alarms or
        time-sensitive reminders that need the device alarm system (sound, vibration).
        This is different from schedule_task which schedules AI tasks.
        Hour uses 24-hour format (0-23).
    """.trimIndent()

    override val supportsBackground = false

    override fun parametersSchema(): JSONObject = JSONObject(
        """
        {
            "type": "object",
            "properties": {
                "hour": {
                    "type": "integer",
                    "description": "Hour in 24-hour format (0-23)"
                },
                "minute": {
                    "type": "integer",
                    "description": "Minute (0-59)"
                },
                "label": {
                    "type": "string",
                    "description": "Optional alarm label shown on the alarm screen"
                },
                "skip_ui": {
                    "type": "boolean",
                    "description": "If true, create alarm silently without opening the clock app. Default: false"
                }
            },
            "required": ["hour", "minute"]
        }
        """.trimIndent()
    )

    override suspend fun execute(params: JSONObject): ToolResult {
        val hour = params.optInt("hour", -1).takeIf { it in 0..23 }
            ?: return ToolResult.Error("hour must be between 0 and 23")
        val minute = params.optInt("minute", -1).takeIf { it in 0..59 }
            ?: return ToolResult.Error("minute must be between 0 and 59")
        val label = params.optString("label", "").takeIf { it.isNotBlank() }
        val skipUi = params.optBoolean("skip_ui", false)

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
                if (label != null) putExtra(AlarmClock.EXTRA_MESSAGE, label)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val timeStr = "%02d:%02d".format(hour, minute)
            ToolResult.Success("""{"set":true,"time":"$timeStr","label":${JSONObject.quote(label ?: "")}}""")
        } catch (e: Exception) {
            ToolResult.Error("Failed to set alarm: ${e.message}")
        }
    }
}
