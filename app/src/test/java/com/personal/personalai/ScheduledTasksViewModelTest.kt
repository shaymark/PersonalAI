package com.personal.personalai

import com.personal.personalai.domain.model.ScheduledTask
import com.personal.personalai.domain.model.TaskInfo
import com.personal.personalai.domain.usecase.CreateScheduledTaskUseCase
import com.personal.personalai.domain.usecase.DeleteScheduledTaskUseCase
import com.personal.personalai.domain.usecase.GetScheduledTasksUseCase
import com.personal.personalai.presentation.schedule.ScheduledTasksViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduledTasksViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ScheduledTasksViewModel
    private lateinit var getTasksUseCase: GetScheduledTasksUseCase
    private lateinit var createTaskUseCase: CreateScheduledTaskUseCase
    private lateinit var deleteTaskUseCase: DeleteScheduledTaskUseCase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        getTasksUseCase = mockk()
        createTaskUseCase = mockk()
        deleteTaskUseCase = mockk()
        every { getTasksUseCase() } returns flowOf(emptyList())
        viewModel = ScheduledTasksViewModel(getTasksUseCase, createTaskUseCase, deleteTaskUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty tasks`() = runTest {
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.tasks.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `showAddDialog sets showAddDialog to true`() {
        viewModel.showAddDialog()
        assertTrue(viewModel.uiState.value.showAddDialog)
    }

    @Test
    fun `dismissAddDialog resets dialog state`() {
        viewModel.showAddDialog()
        viewModel.onTitleChanged("Test")
        viewModel.dismissAddDialog()

        assertFalse(viewModel.uiState.value.showAddDialog)
        assertEquals("", viewModel.uiState.value.newTaskTitle)
    }

    @Test
    fun `createTask calls use case and closes dialog`() = runTest {
        val task = ScheduledTask(
            id = 1L,
            title = "Test Task",
            description = "desc",
            scheduledAt = System.currentTimeMillis() + 3600_000L
        )
        coEvery { createTaskUseCase(any()) } returns task

        viewModel.showAddDialog()
        viewModel.onTitleChanged("Test Task")
        viewModel.onDescriptionChanged("desc")
        viewModel.createTask()

        advanceUntilIdle()

        coVerify { createTaskUseCase(any<TaskInfo>()) }
        assertFalse(viewModel.uiState.value.showAddDialog)
        assertEquals("", viewModel.uiState.value.newTaskTitle)
    }

    @Test
    fun `createTask does nothing when title is blank`() = runTest {
        viewModel.showAddDialog()
        viewModel.onTitleChanged("   ")
        viewModel.createTask()
        advanceUntilIdle()

        coVerify(exactly = 0) { createTaskUseCase(any()) }
        assertTrue(viewModel.uiState.value.showAddDialog)
    }

    @Test
    fun `deleteTask calls use case`() = runTest {
        val task = ScheduledTask(
            id = 1L, title = "Task", description = "", scheduledAt = System.currentTimeMillis()
        )
        coEvery { deleteTaskUseCase(task) } returns Unit

        viewModel.deleteTask(task)
        advanceUntilIdle()

        coVerify { deleteTaskUseCase(task) }
    }

    @Test
    fun `tasks are updated when flow emits`() = runTest {
        val tasks = listOf(
            ScheduledTask(1L, "Task 1", "", System.currentTimeMillis()),
            ScheduledTask(2L, "Task 2", "", System.currentTimeMillis())
        )
        every { getTasksUseCase() } returns flowOf(tasks)
        viewModel = ScheduledTasksViewModel(getTasksUseCase, createTaskUseCase, deleteTaskUseCase)

        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.tasks.size)
    }
}
