package com.personal.personalai.domain.usecase

import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.personal.personalai.domain.model.ScheduledTask
import com.personal.personalai.domain.model.TaskInfo
import com.personal.personalai.domain.repository.TaskRepository
import com.personal.personalai.worker.TaskReminderWorker
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class CreateScheduledTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val workManager: WorkManager
) {
    suspend operator fun invoke(taskInfo: TaskInfo): ScheduledTask? {
        return try {
            val scheduledAt = parseIsoToEpochMilli(taskInfo.scheduledAtIso)
            val task = ScheduledTask(
                title = taskInfo.title,
                description = taskInfo.description,
                scheduledAt = scheduledAt,
                taskType = taskInfo.taskType,
                aiPrompt = taskInfo.aiPrompt,
                outputTarget = taskInfo.outputTarget
            )
            val taskId = taskRepository.insertTask(task)

            val delayMillis = scheduledAt - System.currentTimeMillis()
            if (delayMillis > 0) {
                val workRequest = OneTimeWorkRequestBuilder<TaskReminderWorker>()
                    .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .setInputData(
                        workDataOf(
                            TaskReminderWorker.KEY_TASK_TITLE to taskInfo.title,
                            TaskReminderWorker.KEY_TASK_DESCRIPTION to taskInfo.description,
                            TaskReminderWorker.KEY_TASK_TYPE to taskInfo.taskType.name,
                            TaskReminderWorker.KEY_AI_PROMPT to taskInfo.aiPrompt,
                            TaskReminderWorker.KEY_OUTPUT_TARGET to taskInfo.outputTarget.name
                        )
                    )
                    .build()
                workManager.enqueue(workRequest)
                taskRepository.updateWorkerId(taskId, workRequest.id.toString())
            }

            task.copy(id = taskId)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseIsoToEpochMilli(isoString: String): Long {
        return try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            val localDateTime = LocalDateTime.parse(isoString, formatter)
            localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            try {
                java.time.Instant.parse(isoString).toEpochMilli()
            } catch (e2: Exception) {
                System.currentTimeMillis() + 60 * 60 * 1000L
            }
        }
    }
}
