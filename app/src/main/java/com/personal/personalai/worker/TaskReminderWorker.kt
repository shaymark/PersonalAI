package com.personal.personalai.worker

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personal.personalai.domain.model.Message
import com.personal.personalai.domain.model.MessageRole
import com.personal.personalai.domain.model.OutputTarget
import com.personal.personalai.domain.model.TaskType
import com.personal.personalai.domain.repository.AiRepository
import com.personal.personalai.domain.repository.ChatRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class TaskReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val aiRepository: AiRepository,
    private val chatRepository: ChatRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TASK_TITLE) ?: return Result.failure()
        val taskType = runCatching {
            TaskType.valueOf(inputData.getString(KEY_TASK_TYPE) ?: TaskType.REMINDER.name)
        }.getOrDefault(TaskType.REMINDER)

        return when (taskType) {
            TaskType.REMINDER -> {
                val description = inputData.getString(KEY_TASK_DESCRIPTION) ?: ""
                showSimpleNotification(title, description)
                Result.success()
            }
            TaskType.AI_PROMPT -> handleAiTask(title)
        }
    }

    private suspend fun handleAiTask(title: String): Result {
        val aiPrompt = inputData.getString(KEY_AI_PROMPT)
        if (aiPrompt.isNullOrBlank()) {
            showFailureNotification(title)
            return Result.failure()
        }

        val outputTarget = runCatching {
            OutputTarget.valueOf(inputData.getString(KEY_OUTPUT_TARGET) ?: OutputTarget.NOTIFICATION.name)
        }.getOrDefault(OutputTarget.NOTIFICATION)

        val aiResult = aiRepository.sendMessage(aiPrompt, emptyList())

        return if (aiResult.isSuccess) {
            val cleanResponse = stripTags(aiResult.getOrThrow())
            when (outputTarget) {
                OutputTarget.NOTIFICATION -> showAiNotification(title, cleanResponse)
                OutputTarget.CHAT         -> saveToChat(title, cleanResponse)
                OutputTarget.BOTH         -> {
                    showAiNotification(title, cleanResponse)
                    saveToChat(title, cleanResponse)
                }
            }
            Result.success()
        } else {
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

    // ── Tag stripping ────────────────────────────────────────────────────────

    private fun stripTags(response: String): String =
        response
            .replace(Regex("""\s*\[TASK:\{[^\]]*}]"""), "")
            .replace(Regex("""\s*\[MEMORY:\{[^\]]*}]"""), "")
            .replace(Regex("""\s*\[FORGET:\{[^\]]*}]"""), "")
            .replace(Regex("""\s*\[FORGET_ALL]"""), "")
            .trim()

    companion object {
        const val CHANNEL_ID = "task_reminders"
        const val KEY_TASK_TITLE = "task_title"
        const val KEY_TASK_DESCRIPTION = "task_description"
        const val KEY_TASK_TYPE = "task_type"
        const val KEY_AI_PROMPT = "ai_prompt"
        const val KEY_OUTPUT_TARGET = "output_target"
    }
}
