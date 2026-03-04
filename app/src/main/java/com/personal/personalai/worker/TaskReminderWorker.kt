package com.personal.personalai.worker

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.personal.personalai.domain.model.Message
import com.personal.personalai.domain.model.MessageRole
import com.personal.personalai.domain.model.OutputTarget
import com.personal.personalai.domain.model.RecurrenceType
import com.personal.personalai.domain.model.TaskType
import com.personal.personalai.domain.repository.ChatRepository
import com.personal.personalai.domain.repository.TaskRepository
import com.personal.personalai.domain.usecase.AgentLoopUseCase
import com.personal.personalai.domain.usecase.AgentStep
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class TaskReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val agentLoopUseCase: AgentLoopUseCase,
    private val chatRepository: ChatRepository,
    private val taskRepository: TaskRepository,
    private val workManager: WorkManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TASK_TITLE) ?: return Result.failure()
        val taskType = runCatching {
            TaskType.valueOf(inputData.getString(KEY_TASK_TYPE) ?: TaskType.REMINDER.name)
        }.getOrDefault(TaskType.REMINDER)

        val workResult = when (taskType) {
            TaskType.REMINDER -> {
                val description = inputData.getString(KEY_TASK_DESCRIPTION) ?: ""
                showSimpleNotification(title, description)
                Result.success()
            }
            TaskType.AI_PROMPT -> handleAiTask(title)
        }

        if (workResult == Result.success()) {
            val recurrenceType = runCatching {
                RecurrenceType.valueOf(inputData.getString(KEY_RECURRENCE_TYPE) ?: RecurrenceType.NONE.name)
            }.getOrDefault(RecurrenceType.NONE)
            val taskId = inputData.getLong(KEY_TASK_ID, -1L)
            if (recurrenceType != RecurrenceType.NONE && taskId != -1L) {
                scheduleNextOccurrence(taskId, recurrenceType)
            }
        }

        return workResult
    }

    private suspend fun scheduleNextOccurrence(taskId: Long, recurrenceType: RecurrenceType) {
        val currentScheduledAt = inputData.getLong(KEY_SCHEDULED_AT, System.currentTimeMillis())
        val intervalMs = if (recurrenceType == RecurrenceType.DAILY) 86_400_000L else 604_800_000L
        val nextScheduledAt = currentScheduledAt + intervalMs
        val delayMs = nextScheduledAt - System.currentTimeMillis()
        if (delayMs <= 0) return

        val taskTypeName = inputData.getString(KEY_TASK_TYPE) ?: TaskType.REMINDER.name
        val constraints = when (runCatching { TaskType.valueOf(taskTypeName) }.getOrDefault(TaskType.REMINDER)) {
            TaskType.AI_PROMPT -> Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            TaskType.REMINDER -> Constraints.NONE
        }

        val newRequest = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setInputData(
                Data.Builder()
                    .putAll(inputData)
                    .putLong(KEY_SCHEDULED_AT, nextScheduledAt)
                    .build()
            )
            .build()

        workManager.enqueue(newRequest)
        taskRepository.updateNextOccurrence(taskId, nextScheduledAt, newRequest.id.toString())
    }

    private suspend fun handleAiTask(title: String): Result {
        val aiPrompt = inputData.getString(KEY_AI_PROMPT)
        if (aiPrompt.isNullOrBlank()) {
            Log.e(TAG, "AI task \"$title\" has no prompt — aborting")
            showFailureNotification(title)
            return Result.failure()
        }

        val outputTarget = runCatching {
            OutputTarget.valueOf(inputData.getString(KEY_OUTPUT_TARGET) ?: OutputTarget.NOTIFICATION.name)
        }.getOrDefault(OutputTarget.NOTIFICATION)

        var finalText: String? = null

        agentLoopUseCase(aiPrompt, backgroundMode = true).collect { step ->
            when (step) {
                is AgentStep.ToolCalling -> Log.d(TAG, "Agent tool call: ${step.toolName}")
                is AgentStep.Complete -> finalText = step.result.getOrNull()
            }
        }

        val response = finalText
        return if (response != null) {
            when (outputTarget) {
                OutputTarget.NOTIFICATION -> showAiNotification(title, response)
                OutputTarget.CHAT         -> saveToChat(title, response)
                OutputTarget.BOTH         -> {
                    showAiNotification(title, response)
                    saveToChat(title, response)
                }
            }
            Result.success()
        } else {
            Log.e(TAG, "AI task \"$title\" produced no response")
            showFailureNotification(title)
            Result.failure()
        }
    }

    // ── Notification helpers ─────────────────────────────────────────────────

    private fun showSimpleNotification(title: String, description: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(description.ifEmpty { "Your scheduled AI reminder" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notify(notification)
    }

    private fun showAiNotification(title: String, aiResponse: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(aiResponse.take(100))
            .setStyle(NotificationCompat.BigTextStyle().bigText(aiResponse))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notify(notification)
    }

    private fun showFailureNotification(title: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("AI task failed to run")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notify(notification)
    }

    private fun notify(notification: android.app.Notification) {
        context.getSystemService(NotificationManager::class.java)
            .notify(System.currentTimeMillis().toInt(), notification)
    }

    // ── Chat output ──────────────────────────────────────────────────────────

    private suspend fun saveToChat(title: String, aiResponse: String) {
        val message = Message(
            content = "📅 **Scheduled: $title**\n\n$aiResponse",
            role = MessageRole.ASSISTANT
        )
        chatRepository.saveMessage(message)
    }

    companion object {
        private const val TAG = "TaskReminderWorker"
        const val CHANNEL_ID = "task_reminders"
        const val KEY_TASK_TITLE = "task_title"
        const val KEY_TASK_DESCRIPTION = "task_description"
        const val KEY_TASK_TYPE = "task_type"
        const val KEY_AI_PROMPT = "ai_prompt"
        const val KEY_OUTPUT_TARGET = "output_target"
        const val KEY_TASK_ID = "task_id"
        const val KEY_SCHEDULED_AT = "scheduled_at"
        const val KEY_RECURRENCE_TYPE = "recurrence_type"
    }
}
