package com.personal.personalai.data.tools.android

import android.content.Context
import android.location.Geocoder
import android.os.Build
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume

class GeocodeAddressTool @Inject constructor(
    @ApplicationContext private val context: Context
) : AgentTool {

    override val name = "geocode_address"
    override val description = """
        Convert a human-readable address or place name to GPS coordinates (latitude and longitude).
        Use this before set_location_task when the user describes a location by name instead of coordinates.
        Examples: "aria tegar 19 ashkelon", "Times Square New York", "Eiffel Tower Paris"
        If you mention coordinates to the user, prefer the tool's exact `summary`,
        `latitude_text`, `longitude_text`, and `coordinates_text` fields.
    """.trimIndent()
    override val supportsBackground = true

    override fun parametersSchema(): JSONObject = JSONObject(
        """
        {
            "type": "object",
            "properties": {
                "address": {
                    "type": "string",
                    "description": "The address or place name to geocode, e.g. 'aria tegar 19 ashkelon'"
                }
            },
            "required": ["address"]
        }
        """.trimIndent()
    )

    override suspend fun execute(params: JSONObject): ToolResult {
        val address = params.optString("address", "").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("address is required")

        if (!Geocoder.isPresent()) {
            return ToolResult.Error("Geocoder not available on this device")
        }

        return try {
            val geocoder = Geocoder(context)
            val result = getCoordinates(geocoder, address)
                ?: return ToolResult.Error("Could not find coordinates for: $address")

            ToolResult.Success(result.toString())
        } catch (e: Exception) {
            ToolResult.Error("Geocoding failed: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun getCoordinates(geocoder: Geocoder, address: String): JSONObject? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocationName(address, 1) { results ->
                    val loc = results.firstOrNull()
                    if (loc != null) {
                        val latitudeText = formatCoordinate(loc.latitude)
                        val longitudeText = formatCoordinate(loc.longitude)
                        cont.resume(JSONObject().apply {
                            put("latitude", loc.latitude)
                            put("longitude", loc.longitude)
                            put("lat", loc.latitude)
                            put("lng", loc.longitude)
                            put("latitude_text", latitudeText)
                            put("longitude_text", longitudeText)
                            put("coordinates_text", "$latitudeText, $longitudeText")
                            put("resolved_address", loc.getAddressLine(0) ?: address)
                            put(
                                "summary",
                                "The coordinates for ${loc.getAddressLine(0) ?: address} are latitude $latitudeText and longitude $longitudeText."
                            )
                        })
                    } else {
                        cont.resume(null)
                    }
                }
            }
        } else {
            val results = geocoder.getFromLocationName(address, 1)
            val loc = results?.firstOrNull() ?: return null
            val latitudeText = formatCoordinate(loc.latitude)
            val longitudeText = formatCoordinate(loc.longitude)
            JSONObject().apply {
                put("latitude", loc.latitude)
                put("longitude", loc.longitude)
                put("lat", loc.latitude)
                put("lng", loc.longitude)
                put("latitude_text", latitudeText)
                put("longitude_text", longitudeText)
                put("coordinates_text", "$latitudeText, $longitudeText")
                put("resolved_address", loc.getAddressLine(0) ?: address)
                put(
                    "summary",
                    "The coordinates for ${loc.getAddressLine(0) ?: address} are latitude $latitudeText and longitude $longitudeText."
                )
            }
        }
    }

    private fun formatCoordinate(value: Double): String =
        String.format(Locale.US, "%.5f", value)
}
