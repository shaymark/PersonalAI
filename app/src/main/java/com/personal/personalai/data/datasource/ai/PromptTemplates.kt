package com.personal.personalai.data.datasource.ai

import com.personal.personalai.domain.model.Memory
import com.personal.personalai.domain.model.Message
import com.personal.personalai.domain.model.MessageRole
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Shared prompt building utilities used by [LocalLlmDataSource].
 *
 * The system prompt mirrors the tag-based contract that [OpenAiDataSource] uses in its
 * legacy [OpenAiDataSource.sendMessage] path, so [SendMessageUseCase]'s tag parser
 * ([TASK:{...}], [MEMORY:{...}], etc.) works without changes.
 *
 * Prompt format: ChatML — understood by Qwen 2.5, Llama 3.2, and Phi 3.5.
 */
object PromptTemplates {

    /**
     * Builds the system prompt text for on-device inference.
     * Includes memory injection and the current date/time.
     */
    fun buildLocalSystemPrompt(memories: List<Memory>): String {
        val now = java.time.Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))

        val memoriesSection = if (memories.isEmpty()) {
            ""
        } else buildString {
            append("\n\n--- User Memories ---\n")
            memories.forEach { memory ->
                if (memory.topic.isNotBlank()) append("[${memory.topic}] ")
                append(memory.content)
                append("\n")
            }
            append("--- End Memories ---")
        }

        return """
            You are a helpful personal AI assistant running on this Android device.
            You can answer questions and have natural conversations.
            $memoriesSection
            IMPORTANT — Reminder scheduling: When the user wants a simple reminder or notification
            (e.g. "remind me to...", "set a reminder for...", "notify me when..."),
            respond helpfully AND append this tag at the very END of your response, on its own line:
            [TASK:{"title":"<short title>","description":"<brief description>","scheduledAt":"<ISO datetime like 2024-01-15T14:30:00>"}]
            For recurring reminders (e.g. "every day", "daily", "every morning", "every week", "every Monday"),
            add "recurrenceType":"DAILY" or "recurrenceType":"WEEKLY" to the tag.

            IMPORTANT — Memory: When the user asks you to remember something,
            respond naturally AND append at the very END of your response, on its own line:
            [MEMORY:{"content":"<what to remember>","topic":"<short category>"}]

            IMPORTANT — Forget: When the user asks you to forget a topic, append:
            [FORGET:{"topic":"<the topic to delete>"}]
            When the user asks you to forget everything, append:
            [FORGET_ALL]

            Never include more than one of these tags per response.
            For all other messages, respond normally with no tags.
            Current date and time: $now
        """.trimIndent()
    }

    /**
     * Assembles a full single-string prompt in ChatML format for GGUF inference.
     *
     * Format:
     * ```
     * <|im_start|>system
     * {system prompt}
     * <|im_end|>
     * <|im_start|>user
     * {message}
     * <|im_end|>
     * <|im_start|>assistant
     * ```
     * The model generates text starting after the final `<|im_start|>assistant` token.
     *
     * @param userMessage  The latest user message.
     * @param chatHistory  Previous conversation turns (up to last 6 are included).
     * @param memories     User memories to inject into the system prompt.
     */
    /**
     * Assembles a full single-string prompt in **Gemma instruction format** for MediaPipe inference.
     *
     * Format (Gemma 2 / Gemma 3):
     * ```
     * <start_of_turn>user
     * {system prompt}
     *
     * {oldest history message}
     * <end_of_turn>
     * <start_of_turn>model
     * {response}
     * <end_of_turn>
     * ...
     * <start_of_turn>user
     * {current message}
     * <end_of_turn>
     * <start_of_turn>model
     * ```
     * The system prompt is injected into the first user turn.
     * OpenAI never calls this function — it uses the Chat Completions API directly.
     *
     * @param userMessage  The latest user message.
     * @param chatHistory  Previous conversation turns (up to last 6 are included).
     * @param memories     User memories to inject into the system prompt.
     */
    fun buildGemmaPrompt(
        userMessage: String,
        chatHistory: List<Message>,
        memories: List<Memory>
    ): String = buildString {
        val systemPrompt = buildLocalSystemPrompt(memories)
        val history = chatHistory.takeLast(6)
        var systemInjected = false

        // Walk through history, prepending system prompt to the first user turn
        history.forEach { msg ->
            val role = if (msg.role == MessageRole.USER) "user" else "model"
            if (role == "user" && !systemInjected) {
                // Inject system prompt into the first user turn
                append("<start_of_turn>user\n${systemPrompt}\n\n${msg.content}<end_of_turn>\n")
                systemInjected = true
            } else {
                append("<start_of_turn>$role\n${msg.content}<end_of_turn>\n")
            }
        }

        // Current user message
        if (!systemInjected) {
            // No history: system prompt + current message as a single first user turn
            append("<start_of_turn>user\n${systemPrompt}\n\n${userMessage}<end_of_turn>\n")
        } else {
            append("<start_of_turn>user\n${userMessage}<end_of_turn>\n")
        }

        // Prompt the model to begin its response
        append("<start_of_turn>model\n")
    }

    /**
     * Assembles a full single-string prompt in ChatML format for GGUF inference.
     * Kept for future use with llama.cpp / any ChatML-compatible model.
     * OpenAI uses the Chat Completions API directly and never calls this.
     */
    fun buildLocalPrompt(
        userMessage: String,
        chatHistory: List<Message>,
        memories: List<Memory>
    ): String = buildString {
        append("<|im_start|>system\n")
        append(buildLocalSystemPrompt(memories))
        append("\n<|im_end|>\n")

        // Include up to last 6 turns for context (to stay within the 2048-token context window)
        chatHistory.takeLast(6).forEach { msg ->
            val role = if (msg.role == MessageRole.USER) "user" else "assistant"
            append("<|im_start|>$role\n${msg.content}\n<|im_end|>\n")
        }

        append("<|im_start|>user\n$userMessage\n<|im_end|>\n")
        // Pre-fill an empty <think> block so Qwen3.5 skips its chain-of-thought monologue
        // and responds directly. The model treats a closed </think> tag as "thinking done".
        append("<|im_start|>assistant\n<think>\n\n</think>\n")
    }
}
