package com.personal.personalai

import com.personal.personalai.domain.model.Message
import com.personal.personalai.domain.model.MessageRole
import com.personal.personalai.domain.model.ScheduledTask
import com.personal.personalai.domain.model.TaskInfo
import com.personal.personalai.domain.repository.AiRepository
import com.personal.personalai.domain.repository.ChatRepository
import com.personal.personalai.domain.usecase.CreateScheduledTaskUseCase
import com.personal.personalai.domain.usecase.SendMessageUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SendMessageUseCaseTest {

    private lateinit var useCase: SendMessageUseCase
    private lateinit var aiRepository: AiRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var createScheduledTaskUseCase: CreateScheduledTaskUseCase

    @Before
    fun setup() {
        aiRepository = mockk()
        chatRepository = mockk()
        createScheduledTaskUseCase = mockk()
        every { chatRepository.getMessages() } returns flowOf(emptyList())
        coEvery { chatRepository.saveMessage(any()) } returns 1L
        useCase = SendMessageUseCase(aiRepository, chatRepository, createScheduledTaskUseCase, mockk(), mockk(), mockk())
    }

    @Test
    fun `happy path - plain response is saved and returned`() = runTest {
        coEvery { aiRepository.sendMessage("Hello", any()) } returns Result.success("Hi there!")

        val result = useCase("Hello")

        assertTrue(result.isSuccess)
        assertEquals("Hi there!", result.getOrNull()?.message?.content)
        assertEquals(MessageRole.ASSISTANT, result.getOrNull()?.message?.role)
        assertFalse(result.getOrNull()?.message?.hasCreatedTask ?: true)
    }

    @Test
    fun `response with TASK tag creates a scheduled task`() = runTest {
        val taskJson = """{"title":"Buy milk","description":"","scheduledAt":"2099-01-01T09:00:00"}"""
        val rawResponse = "Sure! I'll remind you.\n[TASK:$taskJson]"
        val createdTask = ScheduledTask(
            id = 1L, title = "Buy milk", description = "", scheduledAt = 4070908800000L
        )
        coEvery { aiRepository.sendMessage(any(), any()) } returns Result.success(rawResponse)
        coEvery { createScheduledTaskUseCase(any()) } returns createdTask

        val result = useCase("Remind me to buy milk")

        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull()?.createdTask)
        assertEquals("Buy milk", result.getOrNull()?.createdTask?.title)
        assertTrue(result.getOrNull()?.message?.hasCreatedTask ?: false)
        assertEquals("Buy milk", result.getOrNull()?.message?.createdTaskTitle)
    }

    @Test
    fun `TASK tag is stripped from visible message content`() = runTest {
        val taskJson = """{"title":"Test","description":"","scheduledAt":"2099-01-01T09:00:00"}"""
        val rawResponse = "Done! [TASK:$taskJson]"
        val createdTask = ScheduledTask(1L, "Test", "", 4070908800000L)
        coEvery { aiRepository.sendMessage(any(), any()) } returns Result.success(rawResponse)
        coEvery { createScheduledTaskUseCase(any()) } returns createdTask

        val result = useCase("Test")

        val content = result.getOrNull()?.message?.content ?: ""
        assertFalse("Message should not contain [TASK:", content.contains("[TASK:"))
        assertEquals("Done!", content)
    }

    @Test
    fun `AI failure propagates as failure result`() = runTest {
        coEvery { aiRepository.sendMessage(any(), any()) } returns
            Result.failure(Exception("Timeout"))

        val result = useCase("Hello")

        assertTrue(result.isFailure)
        assertEquals("Timeout", result.exceptionOrNull()?.message)
    }

    @Test
    fun `user message is always saved before AI call`() = runTest {
        coEvery { aiRepository.sendMessage("Hi", any()) } returns Result.success("Hello!")
        val savedMessages = mutableListOf<Message>()
        coEvery { chatRepository.saveMessage(capture(savedMessages)) } returns 1L

        useCase("Hi")

        assertTrue(savedMessages.isNotEmpty())
        assertEquals(MessageRole.USER, savedMessages.first().role)
        assertEquals("Hi", savedMessages.first().content)
    }

    @Test
    fun `task creation use case is called with parsed task info`() = runTest {
        val taskJson = """{"title":"Walk the dog","description":"Evening walk","scheduledAt":"2099-06-15T18:00:00"}"""
        coEvery { aiRepository.sendMessage(any(), any()) } returns
            Result.success("Scheduled! [TASK:$taskJson]")
        val capturedInfo = slot<TaskInfo>()
        coEvery { createScheduledTaskUseCase(capture(capturedInfo)) } returns
            ScheduledTask(1L, "Walk the dog", "Evening walk", 4070908800000L)

        useCase("Remind me to walk the dog")

        coVerify { createScheduledTaskUseCase(any()) }
        assertEquals("Walk the dog", capturedInfo.captured.title)
        assertEquals("Evening walk", capturedInfo.captured.description)
    }
}
