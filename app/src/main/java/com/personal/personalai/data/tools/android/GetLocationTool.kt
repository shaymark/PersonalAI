package com.personal.personalai.data.tools.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject

class GetLocationTool @Inject constructor(
    @ApplicationContext private val context: Context
) : AgentTool {

    override val name = "get_location"
    override val description = """
        Get the device's current approximate location (latitude and longitude).
        Useful for weather queries, finding nearby places, or location-aware tasks.
        Returns the last known cached location — fast with no GPS spin-up.
        If you mention the coordinates to the user, copy the tool's `summary`,
        `latitude_text`, and `longitude_text` values exactly without reformatting digits.
    """.trimIndent()

    override fun parametersSchema(): JSONObject = JSONObject(
        """{"type": "object", "properties": {}}"""
    )

    override suspend fun execute(params: JSONObject): ToolResult {
        val permission = Manifest.permission.ACCESS_COARSE_LOCATION
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            return ToolResult.PermissionDenied(permission)
        }

        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // Try network provider first (fast, battery-friendly), fall back to GPS
            val providers = listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )

            val location = providers
                .filter { locationManager.isProviderEnabled(it) }
                .mapNotNull { provider ->
                    @Suppress("MissingPermission")
                    locationManager.getLastKnownLocation(provider)
                }
                .maxByOrNull { it.time } // most recent

            if (location == null) {
                ToolResult.Error("No cached location available. Try enabling location services and moving outdoors.")
            } else {
                val latitudeText = formatCoordinate(location.latitude)
                val longitudeText = formatCoordinate(location.longitude)
                ToolResult.Success(
                    JSONObject()
                        .put("latitude", location.latitude)
                        .put("longitude", location.longitude)
                        .put("latitude_text", latitudeText)
                        .put("longitude_text", longitudeText)
                        .put("coordinates_text", "$latitudeText, $longitudeText")
                        .put(
                            "summary",
                            "Your current coordinates are latitude $latitudeText and longitude $longitudeText."
                        )
                        .put("accuracy", location.accuracy.toInt())
                        .put("accuracy_meters", location.accuracy.toInt())
                        .put("provider", location.provider)
                        .toString()
                )
            }
        } catch (e: Exception) {
            ToolResult.Error("Failed to get location: ${e.message}")
        }
    }

    private fun formatCoordinate(value: Double): String =
        String.format(Locale.US, "%.5f", value)
}
