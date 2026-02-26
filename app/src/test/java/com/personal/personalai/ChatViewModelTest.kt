package com.personal.personalai

import com.personal.personalai.domain.model.Message
import com.personal.personalai.domain.model.MessageRole
import com.personal.personalai.domain.model.SendMessageResult
import com.personal.personalai.domain.usecase.GetChatHistoryUseCase
import com.personal.personalai.domain.usecase.SendMessageUseCase
import com.personal.personalai.presentation.chat.ChatViewModel
import io.mockk.coEvery
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ChatViewModel
    private lateinit var sendMessageUseCase: SendMessageUseCase
    private lateinit var getChatHistoryUseCase: GetChatHistoryUseCase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sendMessageUseCase = mockk()
        getChatHistoryUseCase = mockk()
        every { getChatHistoryUseCase() } returns flowOf(emptyList())
        viewModel = ChatViewModel(sendMessageUseCase, getChatHistoryUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty messages and no loading`() = runTest {
        val state = viewModel.uiState.value
        assertTrue(state.messages.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals("", state.inputText)
    }

    @Test
    fun `sendMessage sets isLoading true then false on success`() = runTest {
        val aiMessage = Message(id = 2, content = "Hello!", role = MessageRole.ASSISTANT)
        val result = SendMessageResult(message = aiMessage, createdTask = null)
        coEvery { sendMessageUseCase("Hi") } returns Result.success(result)

        viewModel.onInputChanged("Hi")
        viewModel.sendMessage()

        assertTrue(viewModel.uiState.value.isLoading)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `sendMessage sets error on failure`() = runTest {
        coEvery { sendMessageUseCase(any()) } returns Result.failure(Exception("Network error"))

        viewModel.onInputChanged("Hello")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Network error", viewModel.uiState.value.error)
    }

    @Test
    fun `sendMessage does nothing when input is empty`() = runTest {
        viewModel.onInputChanged("")
        viewModel.sendMessage()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `sendMessage clears input text before sending`() = runTest {
        val aiMessage = Message(id = 2, content = "Reply", role = MessageRole.ASSISTANT)
        coEvery { sendMessageUseCase(any()) } returns Result.success(
            SendMessageResult(message = aiMessage, createdTask = null)
        )

        viewModel.onInputChanged("Test message")
        viewModel.sendMessage()

        assertEquals("", viewModel.uiState.value.inputText)
    }

    @Test
    fun `onVoiceInputReceived updates input text`() = runTest {
        viewModel.onVoiceInputReceived("Spoken text")
        assertEquals("Spoken text", viewModel.uiState.value.inputText)
    }

    @Test
    fun `messages are updated when chat history emits`() = runTest {
        val messages = listOf(
            Message(id = 1, content = "Hello", role = MessageRole.USER),
            Message(id = 2, content = "Hi there!", role = MessageRole.ASSISTANT)
        )
        every { getChatHistoryUseCase() } returns flowOf(messages)
        viewModel = ChatViewModel(sendMessageUseCase, getChatHistoryUseCase)

        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.messages.size)
    }

    @Test
    fun `dismissError clears the error state`() = runTest {
        coEvery { sendMessageUseCase(any()) } returns Result.failure(Exception("Error"))
        viewModel.onInputChanged("msg")
        viewModel.sendMessage()
        advanceUntilIdle()

        viewModel.dismissError()
        assertNull(viewModel.uiState.value.error)
    }
}
