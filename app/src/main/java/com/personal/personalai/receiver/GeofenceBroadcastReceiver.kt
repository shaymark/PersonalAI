package com.personal.personalai.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.GeofencingEvent
import com.personal.personalai.domain.model.GeofenceTask
import com.personal.personalai.domain.model.Message
import com.personal.personalai.domain.model.MessageRole
import com.personal.personalai.domain.model.OutputTarget
import com.personal.personalai.domain.model.TaskType
import com.personal.personalai.domain.repository.ChatRepository
import com.personal.personalai.domain.repository.GeofenceTaskRepository
import com.personal.personalai.domain.usecase.AgentLoopUseCase
import com.personal.personalai.domain.usecase.AgentStep
import com.personal.personalai.worker.TaskReminderWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: GeofenceTaskRepository
    @Inject lateinit var agentLoopUseCase: AgentLoopUseCase
    @Inject lateinit var chatRepository: ChatRepository

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofencing error: ${geofencingEvent.errorCode}")
            return
        }

        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (geofence in triggeringGeofences) {
                    val taskId = geofence.requestId.toLongOrNull() ?: continue
                    val task = repository.getTaskById(taskId) ?: continue
                    executeTask(context, task)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling geofence event: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun executeTask(context: Context, task: GeofenceTask) {
        when (task.taskType) {
            TaskType.REMINDER -> showSimpleNotification(context, task.title, task.description)
            TaskType.AI_PROMPT -> handleAiTask(context, task)
        }
    }

    private suspend fun handleAiTask(context: Context, task: GeofenceTask) {
        val prompt = task.aiPrompt
        if (prompt.isNullOrBlank()) {
            Log.e(TAG, "Geofence task ${task.id} has no AI prompt")
            showFailureNotification(context, task.title)
            return
        }

        var finalText: String? = null
        agentLoopUseCase(prompt, backgroundMode = true).collect { step ->
            when (step) {
                is AgentStep.ToolCalling -> Log.d(TAG, "Agent tool: ${step.toolName}")
                is AgentStep.Complete    -> finalText = step.result.getOrNull()
            }
        }

        val response = finalText
        if (response != null) {
            when (task.outputTarget) {
                OutputTarget.NOTIFICATION -> showAiNotification(context, task.title, response)
                OutputTarget.CHAT         -> saveToChat(task.title, response)
                OutputTarget.BOTH         -> {
                    showAiNotification(context, task.title, response)
                    saveToChat(task.title, response)
                }
            }
        } else {
            Log.e(TAG, "Geofence task ${task.id} produced no AI response")
            showFailureNotification(context, task.title)
        }
    }

    private fun showSimpleNotification(context: Context, title: String, description: String) {
        val notification = NotificationCompat.Builder(context, TaskReminderWorker.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(description.ifEmpty { "Location reminder" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun showAiNotification(context: Context, title: String, aiResponse: String) {
        val notification = NotificationCompat.Builder(context, TaskReminderWorker.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(aiResponse.take(100))
            .setStyle(NotificationCompat.BigTextStyle().bigText(aiResponse))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun showFailureNotification(context: Context, title: String) {
        val notification = NotificationCompat.Builder(context, TaskReminderWorker.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("Location AI task failed to run")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(System.currentTimeMillis().toInt(), notification)
    }

    private suspend fun saveToChat(title: String, response: String) {
        chatRepository.saveMessage(
            Message(content = "📍 **Location: $title**\n\n$response", role = MessageRole.ASSISTANT)
        )
    }

    companion object {
        private const val TAG = "GeofenceBroadcastReceiver"
    }
}
