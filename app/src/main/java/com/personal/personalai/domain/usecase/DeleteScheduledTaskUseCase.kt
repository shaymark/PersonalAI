package com.personal.personalai.domain.usecase

import androidx.work.WorkManager
import com.personal.personalai.domain.model.ScheduledTask
import com.personal.personalai.domain.repository.TaskRepository
import java.util.UUID
import javax.inject.Inject

class DeleteScheduledTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val workManager: WorkManager
) {
    suspend operator fun invoke(task: ScheduledTask) {
        task.workerId?.let { workerId ->
            runCatching { workManager.cancelWorkById(UUID.fromString(workerId)) }
        }
        taskRepository.deleteTask(task)
    }
}
