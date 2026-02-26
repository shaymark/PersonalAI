package com.personal.personalai

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.personal.personalai.domain.model.Message
import com.personal.personalai.domain.model.MessageRole
import com.personal.personalai.domain.usecase.GetChatHistoryUseCase
import com.personal.personalai.domain.usecase.SendMessageUseCase
import com.personal.personalai.presentation.chat.ChatScreen
import com.personal.personalai.presentation.chat.ChatViewModel
import com.personal.personalai.ui.theme.PersonalAITheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class ChatScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val sendMessageUseCase = mockk<SendMessageUseCase>(relaxed = true)
    private val getChatHistoryUseCase = mockk<GetChatHistoryUseCase>()

    @Before
    fun setup() {
        hiltRule.inject()
        every { getChatHistoryUseCase() } returns flowOf(emptyList())
    }

    @Test
    fun chatScreen_showsWelcomeMessage_whenEmpty() {
        val viewModel = ChatViewModel(sendMessageUseCase, getChatHistoryUseCase)
        composeRule.setContent {
            PersonalAITheme {
                ChatScreen(
                    innerPadding = PaddingValues(0.dp),
                    onNavigateToSettings = {},
                    viewModel = viewModel
                )
            }
        }

        composeRule.onNodeWithText("Hi! I'm your personal AI.").assertIsDisplayed()
    }

    @Test
    fun chatScreen_showsSettingsButton() {
        val viewModel = ChatViewModel(sendMessageUseCase, getChatHistoryUseCase)
        composeRule.setContent {
            PersonalAITheme {
                ChatScreen(
                    innerPadding = PaddingValues(0.dp),
                    onNavigateToSettings = {},
                    viewModel = viewModel
                )
            }
        }

        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun chatScreen_displaysUserAndAiMessages() {
        val messages = listOf(
            Message(id = 1, content = "Hello AI!", role = MessageRole.USER),
            Message(id = 2, content = "Hi! How can I help?", role = MessageRole.ASSISTANT)
        )
        every { getChatHistoryUseCase() } returns flowOf(messages)
        val viewModel = ChatViewModel(sendMessageUseCase, getChatHistoryUseCase)

        composeRule.setContent {
            PersonalAITheme {
                ChatScreen(
                    innerPadding = PaddingValues(0.dp),
                    onNavigateToSettings = {},
                    viewModel = viewModel
                )
            }
        }

        composeRule.onNodeWithText("Hello AI!").assertIsDisplayed()
        composeRule.onNodeWithText("Hi! How can I help?").assertIsDisplayed()
    }

    @Test
    fun chatScreen_micAndSendButtonsPresent() {
        val viewModel = ChatViewModel(sendMessageUseCase, getChatHistoryUseCase)
        composeRule.setContent {
            PersonalAITheme {
                ChatScreen(
                    innerPadding = PaddingValues(0.dp),
                    onNavigateToSettings = {},
                    viewModel = viewModel
                )
            }
        }

        composeRule.onNodeWithContentDescription("Voice input").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Send").assertIsDisplayed()
    }

    @Test
    fun chatScreen_navigatesToSettings_onSettingsClick() {
        var navigated = false
        val viewModel = ChatViewModel(sendMessageUseCase, getChatHistoryUseCase)
        composeRule.setContent {
            PersonalAITheme {
                ChatScreen(
                    innerPadding = PaddingValues(0.dp),
                    onNavigateToSettings = { navigated = true },
                    viewModel = viewModel
                )
            }
        }

        composeRule.onNodeWithContentDescription("Settings").performClick()
        assert(navigated)
    }
}
