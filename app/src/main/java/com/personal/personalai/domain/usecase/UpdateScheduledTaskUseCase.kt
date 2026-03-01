package com.personal.personalai.domain.usecase

import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.personal.personalai.domain.model.ScheduledTask
import com.personal.personalai.domain.model.TaskType
import com.personal.personalai.domain.repository.TaskRepository
import com.personal.personalai.worker.TaskReminderWorker
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class UpdateScheduledTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val workManager: WorkManager
) {
    suspend operator fun invoke(updatedTask: ScheduledTask): Boolean {
        return try {
            // 1. Cancel the existing WorkManager job if one exists
            updatedTask.workerId?.let { id ->
                runCatching { workManager.cancelWorkById(UUID.fromString(id)) }
            }

            // 2. Persist the updated task (clear old workerId + reset completion)
            taskRepository.updateTask(updatedTask.copy(isCompleted = false, workerId = null))

            // 3. Schedule a new WorkManager job
            val delayMillis = updatedTask.scheduledAt - System.currentTimeMillis()
            if (delayMillis > 0) {
                val constraints = when (updatedTask.taskType) {
                    TaskType.AI_PROMPT -> Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                    TaskType.REMINDER -> Constraints.NONE
                }

                val workRequest = OneTimeWorkRequestBuilder<TaskReminderWorker>()
                    .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .setConstraints(constraints)
                    .setInputData(
                        workDataOf(
                            TaskReminderWorker.KEY_TASK_TITLE to updatedTask.title,
                            TaskReminderWorker.KEY_TASK_DESCRIPTION to updatedTask.description,
                            TaskReminderWorker.KEY_TASK_TYPE to updatedTask.taskType.name,
                            TaskReminderWorker.KEY_AI_PROMPT to updatedTask.aiPrompt,
                            TaskReminderWorker.KEY_OUTPUT_TARGET to updatedTask.outputTarget.name,
                            TaskReminderWorker.KEY_TASK_ID to updatedTask.id,
                            TaskReminderWorker.KEY_SCHEDULED_AT to updatedTask.scheduledAt,
                            TaskReminderWorker.KEY_RECURRENCE_TYPE to updatedTask.recurrenceType.name
                        )
                    )
                    .build()

                workManager.enqueue(workRequest)
                taskRepository.updateWorkerId(updatedTask.id, workRequest.id.toString())
            }

            true
        } catch (e: Exception) {
            false
        }
    }
}
