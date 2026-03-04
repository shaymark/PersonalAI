package com.personal.personalai.data.tools.management

import com.personal.personalai.domain.model.OutputTarget
import com.personal.personalai.domain.model.RecurrenceType
import com.personal.personalai.domain.model.TaskInfo
import com.personal.personalai.domain.model.TaskType
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import com.personal.personalai.domain.usecase.CreateScheduledTaskUseCase
import org.json.JSONObject
import javax.inject.Inject

class ScheduleTaskTool @Inject constructor(
    private val createScheduledTaskUseCase: CreateScheduledTaskUseCase
) : AgentTool {

    override val name = "schedule_task"
    override val description = """
        Schedule a task or reminder on the user's device. Use this when the user asks to be
        reminded about something, or wants to schedule an AI task to run automatically at a
        future time. For scheduled_at, use ISO 8601 format (e.g. 2024-01-15T14:30:00).
        Default to 1 hour from now if no time is specified.
    """.trimIndent()

    override fun parametersSchema(): JSONObject = JSONObject("""
        {
            "type": "object",
            "properties": {
                "title": {
                    "type": "string",
                    "description": "Short, descriptive task title"
                },
                "description": {
                    "type": "string",
                    "description": "Brief description of the task"
                },
                "scheduled_at": {
                    "type": "string",
                    "description": "ISO 8601 datetime, e.g. 2024-01-15T14:30:00"
                },
                "task_type": {
                    "type": "string",
                    "enum": ["REMINDER", "AI_PROMPT"],
                    "description": "REMINDER shows a notification with the title/description. AI_PROMPT runs the ai_prompt automatically and delivers the result."
                },
                "ai_prompt": {
                    "type": "string",
                    "description": "Required when task_type is AI_PROMPT. The exact prompt the AI will run at the scheduled time."
                },
                "output_target": {
                    "type": "string",
                    "enum": ["NOTIFICATION", "CHAT", "BOTH"],
                    "description": "Where to deliver the AI result. Default: NOTIFICATION"
                },
                "recurrence_type": {
                    "type": "string",
                    "enum": ["NONE", "DAILY", "WEEKLY"],
                    "description": "How often to repeat. Default: NONE"
                }
            },
            "required": ["title", "scheduled_at", "task_type"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject): ToolResult {
        return try {
            val taskType = runCatching {
                TaskType.valueOf(params.optString("task_type", TaskType.REMINDER.name))
            }.getOrDefault(TaskType.REMINDER)

            val outputTarget = runCatching {
                OutputTarget.valueOf(params.optString("output_target", OutputTarget.NOTIFICATION.name))
            }.getOrDefault(OutputTarget.NOTIFICATION)

            val recurrenceType = runCatching {
                RecurrenceType.valueOf(params.optString("recurrence_type", RecurrenceType.NONE.name))
            }.getOrDefault(RecurrenceType.NONE)

            val taskInfo = TaskInfo(
                title = params.getString("title"),
                description = params.optString("description", ""),
                scheduledAtIso = params.getString("scheduled_at"),
                taskType = taskType,
                aiPrompt = if (params.has("ai_prompt")) params.getString("ai_prompt") else null,
                outputTarget = outputTarget,
                recurrenceType = recurrenceType
            )

            val task = createScheduledTaskUseCase(taskInfo)
            if (task != null) {
                ToolResult.Success("""{"scheduled":true,"task_id":${task.id},"title":"${task.title}"}""")
            } else {
                ToolResult.Error("Failed to schedule task")
            }
        } catch (e: Exception) {
            ToolResult.Error("Failed to schedule task: ${e.message}")
        }
    }
}
