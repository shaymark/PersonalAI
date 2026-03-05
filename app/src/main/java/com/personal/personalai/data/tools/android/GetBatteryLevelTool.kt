package com.personal.personalai.data.tools.android

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject

class GetBatteryLevelTool @Inject constructor(
    @ApplicationContext private val context: Context
) : AgentTool {

    override val name = "get_battery_level"
    override val description = """
        Get the current battery level (percentage) and charging status of the device.
        Returns the charge level, whether it's charging, and the power source (AC, USB, wireless).
    """.trimIndent()

    override fun parametersSchema(): JSONObject = JSONObject(
        """{"type": "object", "properties": {}}"""
    )

    override suspend fun execute(params: JSONObject): ToolResult {
        return try {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                context.registerReceiver(null, filter)
            }

            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val chargePlug = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            val pluggedType = when (chargePlug) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                else -> "none"
            }

            ToolResult.Success(
                """{"level":$batteryPct,"charging":$isCharging,"plugged":"$pluggedType"}"""
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to get battery level: ${e.message}")
        }
    }
}
