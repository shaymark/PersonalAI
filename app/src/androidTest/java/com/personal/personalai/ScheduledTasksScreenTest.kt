package com.personal.personalai

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.personal.personalai.domain.model.ScheduledTask
import com.personal.personalai.domain.usecase.CreateScheduledTaskUseCase
import com.personal.personalai.domain.usecase.DeleteScheduledTaskUseCase
import com.personal.personalai.domain.usecase.GetScheduledTasksUseCase
import com.personal.personalai.presentation.schedule.ScheduledTasksScreen
import com.personal.personalai.presentation.schedule.ScheduledTasksViewModel
import com.personal.personalai.ui.theme.PersonalAITheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class ScheduledTasksScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val getTasksUseCase = mockk<GetScheduledTasksUseCase>()
    private val createTaskUseCase = mockk<CreateScheduledTaskUseCase>(relaxed = true)
    private val deleteTaskUseCase = mockk<DeleteScheduledTaskUseCase>(relaxed = true)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun scheduledTasksScreen_showsEmptyState_whenNoTasks() {
        every { getTasksUseCase() } returns flowOf(emptyList())
        val viewModel = ScheduledTasksViewModel(getTasksUseCase, createTaskUseCase, deleteTaskUseCase, mockk())

        composeRule.setContent {
            PersonalAITheme {
                ScheduledTasksScreen(
                    innerPadding = PaddingValues(0.dp),
                    viewModel = viewModel
                )
            }
        }

        composeRule.onNodeWithText("No scheduled tasks").assertIsDisplayed()
    }

    @Test
    fun scheduledTasksScreen_showsFab() {
        every { getTasksUseCase() } returns flowOf(emptyList())
        val viewModel = ScheduledTasksViewModel(getTasksUseCase, createTaskUseCase, deleteTaskUseCase, mockk())

        composeRule.setContent {
            PersonalAITheme {
                ScheduledTasksScreen(
                    innerPadding = PaddingValues(0.dp),
                    viewModel = viewModel
                )
            }
        }

        composeRule.onNodeWithContentDescription("Add task").assertIsDisplayed()
    }

    @Test
    fun scheduledTasksScreen_displaysTasks_whenNonEmpty() {
        val tasks = listOf(
            ScheduledTask(1L, "Buy groceries", "Milk and eggs", System.currentTimeMillis() + 3600_000L),
            ScheduledTask(2L, "Call dentist", "", System.currentTimeMillis() + 7200_000L)
        )
        every { getTasksUseCase() } returns flowOf(tasks)
        val viewModel = ScheduledTasksViewModel(getTasksUseCase, createTaskUseCase, deleteTaskUseCase, mockk())

        composeRule.setContent {
            PersonalAITheme {
                ScheduledTasksScreen(
                    innerPadding = PaddingValues(0.dp),
                    viewModel = viewModel
                )
            }
        }

        composeRule.onNodeWithText("Buy groceries").assertIsDisplayed()
        composeRule.onNodeWithText("Call dentist").assertIsDisplayed()
    }

    @Test
    fun scheduledTasksScreen_opensAddDialog_onFabClick() {
        every { getTasksUseCase() } returns flowOf(emptyList())
        val viewModel = ScheduledTasksViewModel(getTasksUseCase, createTaskUseCase, deleteTaskUseCase, mockk())

        composeRule.setContent {
            PersonalAITheme {
                ScheduledTasksScreen(
                    innerPadding = PaddingValues(0.dp),
                    viewModel = viewModel
                )
            }
        }

        composeRule.onNodeWithContentDescription("Add task").performClick()
        composeRule.onNodeWithText("Add Scheduled Task").assertIsDisplayed()
    }

    @Test
    fun scheduledTasksScreen_showsTopAppBarTitle() {
        every { getTasksUseCase() } returns flowOf(emptyList())
        val viewModel = ScheduledTasksViewModel(getTasksUseCase, createTaskUseCase, deleteTaskUseCase, mockk())

        composeRule.setContent {
            PersonalAITheme {
                ScheduledTasksScreen(
                    innerPadding = PaddingValues(0.dp),
                    viewModel = viewModel
                )
            }
        }

        composeRule.onNodeWithText("Scheduled Tasks").assertIsDisplayed()
    }
}
