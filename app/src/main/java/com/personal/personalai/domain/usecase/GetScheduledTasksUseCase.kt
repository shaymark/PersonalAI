package com.personal.personalai.domain.usecase

import com.personal.personalai.domain.model.ScheduledTask
import com.personal.personalai.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetScheduledTasksUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) {
    operator fun invoke(): Flow<List<ScheduledTask>> = taskRepository.getTasks()
}
