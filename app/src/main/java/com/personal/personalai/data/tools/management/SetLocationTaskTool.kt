package com.personal.personalai.data.tools.management

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.personal.personalai.data.geofence.GeofenceManager
import com.personal.personalai.domain.model.GeofenceTask
import com.personal.personalai.domain.model.GeofenceTransitionType
import com.personal.personalai.domain.model.OutputTarget
import com.personal.personalai.domain.model.TaskType
import com.personal.personalai.domain.repository.GeofenceTaskRepository
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject

class SetLocationTaskTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: GeofenceTaskRepository,
    private val geofenceManager: GeofenceManager
) : AgentTool {

    override val name = "set_location_task"
    override val description = """
        Create a task that triggers automatically when the device enters or exits a geographic location.
        Use geocode_address first to convert an address to coordinates if the user describes a place by name.
        The task can show a notification, run an AI prompt, or both — every time the location trigger fires.
    """.trimIndent()
    override val supportsBackground = false

    override fun parametersSchema(): JSONObject = JSONObject(
        """
        {
            "type": "object",
            "properties": {
                "title": {
                    "type": "string",
                    "description": "Name for this location task, e.g. 'Arrive at home'"
                },
                "latitude": {
                    "type": "number",
                    "description": "Latitude of the location center"
                },
                "longitude": {
                    "type": "number",
                    "description": "Longitude of the location center"
                },
                "radius_meters": {
                    "type": "number",
                    "description": "Radius of the geofence in meters (default 100)"
                },
                "transition": {
                    "type": "string",
                    "enum": ["enter", "exit", "both"],
                    "description": "When to trigger: on entering the area, leaving it, or both (default: enter)"
                },
                "task_type": {
                    "type": "string",
                    "enum": ["REMINDER", "AI_PROMPT"],
                    "description": "REMINDER shows a notification with a message; AI_PROMPT runs an AI agent prompt"
                },
                "description": {
                    "type": "string",
                    "description": "Message shown in the notification (for REMINDER type)"
                },
                "ai_prompt": {
                    "type": "string",
                    "description": "The prompt for the AI agent to run when triggered (required for AI_PROMPT type)"
                },
                "output_target": {
                    "type": "string",
                    "enum": ["NOTIFICATION", "CHAT", "BOTH"],
                    "description": "Where to deliver AI results: notification, chat screen, or both (default: NOTIFICATION)"
                },
                "location_name": {
                    "type": "string",
                    "description": "Human-readable location description, e.g. the resolved_address returned by geocode_address. Displayed to the user instead of raw coordinates."
                }
            },
            "required": ["title", "latitude", "longitude"]
        }
        """.trimIndent()
    )

    override suspend fun execute(params: JSONObject): ToolResult {
        val fineLocation = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(context, fineLocation) != PackageManager.PERMISSION_GRANTED) {
            return ToolResult.PermissionDenied(fineLocation)
        }

        val title = params.optString("title", "").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("title is required")
        val latitude = params.optDouble("latitude", Double.NaN)
            .takeIf { !it.isNaN() } ?: return ToolResult.Error("latitude is required")
        val longitude = params.optDouble("longitude", Double.NaN)
            .takeIf { !it.isNaN() } ?: return ToolResult.Error("longitude is required")
        val radiusMeters = params.optDouble("radius_meters", 100.0).toFloat()
        val taskType = when (params.optString("task_type", "REMINDER").uppercase()) {
            "AI_PROMPT" -> TaskType.AI_PROMPT
            else -> TaskType.REMINDER
        }
        val transition = when (params.optString("transition", "enter").lowercase()) {
            "exit" -> GeofenceTransitionType.EXIT
            "both" -> GeofenceTransitionType.BOTH
            else -> GeofenceTransitionType.ENTER
        }
        val locationName = params.optString("location_name", "")
        val description = params.optString("description", "")
        val aiPrompt = params.optString("ai_prompt", "").takeIf { it.isNotBlank() }
        val outputTarget = when (params.optString("output_target", "NOTIFICATION").uppercase()) {
            "CHAT" -> OutputTarget.CHAT
            "BOTH" -> OutputTarget.BOTH
            else -> OutputTarget.NOTIFICATION
        }

        if (taskType == TaskType.AI_PROMPT && aiPrompt == null) {
            return ToolResult.Error("ai_prompt is required for AI_PROMPT task type")
        }

        return try {
            val task = GeofenceTask(
                title = title,
                locationName = locationName,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters,
                transitionType = transition,
                taskType = taskType,
                description = description,
                aiPrompt = aiPrompt,
                outputTarget = outputTarget
            )
            val id = repository.insertTask(task)
            val savedTask = task.copy(id = id)
            geofenceManager.register(savedTask)
            ToolResult.Success("""{"status":"created","task_id":$id,"title":"$title"}""")
        } catch (e: Exception) {
            ToolResult.Error("Failed to create location task: ${e.message}")
        }
    }
}
