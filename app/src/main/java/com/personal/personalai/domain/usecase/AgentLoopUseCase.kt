package com.personal.personalai.domain.usecase

import com.personal.personalai.domain.model.Message
import com.personal.personalai.domain.model.MessageRole
import com.personal.personalai.domain.repository.AiRepository
import com.personal.personalai.domain.repository.ChatRepository
import com.personal.personalai.domain.repository.MemoryRepository
import com.personal.personalai.domain.tools.AgentResponse
import com.personal.personalai.domain.tools.FunctionCall
import com.personal.personalai.domain.tools.PermissionBroker
import com.personal.personalai.domain.tools.ToolRegistry
import com.personal.personalai.domain.tools.ToolResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

sealed class AgentStep {
    /** Emitted each time the LLM decides to call a tool. */
    data class ToolCalling(val toolName: String, val humanReadable: String) : AgentStep()
    /** Emitted when the agent produces a final text response or encounters an error. */
    data class Complete(val result: Result<String>) : AgentStep()
}

private const val MAX_ITERATIONS = 8

/**
 * Orchestrates the multi-turn agent loop:
 * 1. Build conversation from history + memories + tool definitions
 * 2. Call AI → if tool calls: execute, append results, loop
 * 3. If text response: save to DB (if !backgroundMode), emit Complete
 *
 * @param backgroundMode When true, skips saving the trigger message to chat history and
 *   uses only background-safe tools. Used by TaskReminderWorker.
 */
class AgentLoopUseCase @Inject constructor(
    private val aiRepository: AiRepository,
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository,
    private val toolRegistry: ToolRegistry,
    private val permissionBroker: PermissionBroker
) {
    operator fun invoke(
        message: String,
        backgroundMode: Boolean = false
    ): Flow<AgentStep> = flow {
        // 1. Save user message to chat (foreground only)
        if (!backgroundMode) {
            chatRepository.saveMessage(Message(content = message, role = MessageRole.USER))
        }

        // 2. Load memories and available tools
        val memories = memoryRepository.getMemories().first()
        val tools = if (backgroundMode) toolRegistry.getBackgroundSafeTools()
                    else toolRegistry.getTools()

        // 3. Build initial conversationItems from recent history
        val conversationItems = JSONArray()
        if (!backgroundMode) {
            // Include recent history for context (the user message we just saved is last)
            chatRepository.getMessages().first()
                .takeLast(20)
                .forEach { msg ->
                    conversationItems.put(JSONObject().apply {
                        put("role", if (msg.role == MessageRole.USER) "user" else "assistant")
                        put("content", msg.content)
                    })
                }
        } else {
            // Background: just the AI prompt as the single user turn
            conversationItems.put(JSONObject().apply {
                put("role", "user")
                put("content", message)
            })
        }

        // 4. Agent loop
        repeat(MAX_ITERATIONS) { iteration ->
            val response = aiRepository.sendMessageWithTools(conversationItems, memories, tools)
                .getOrElse { e ->
                    emit(AgentStep.Complete(Result.failure(e)))
                    return@flow
                }

            when (response) {
                is AgentResponse.ToolCalls -> {
                    response.calls.forEach { call ->
                        emit(AgentStep.ToolCalling(call.name, humanReadable(call.name, call.arguments)))

                        // Append the function_call item to conversation
                        conversationItems.put(JSONObject().apply {
                            put("type", "function_call")
                            put("call_id", call.id)
                            put("name", call.name)
                            put("arguments", call.arguments)
                        })

                        // Execute the tool
                        val rawResult = toolRegistry.execute(call.name, call.arguments)

                        // If the tool requires a permission that hasn't been granted, request it
                        val toolResult = if (rawResult is ToolResult.PermissionDenied) {
                            if (backgroundMode) {
                                // No UI available in background — fail descriptively
                                ToolResult.Error(
                                    "Permission '${rawResult.permission}' not granted. Open the app to grant it."
                                )
                            } else {
                                val granted = permissionBroker.requestAndAwait(rawResult.permission)
                                if (granted) {
                                    toolRegistry.execute(call.name, call.arguments)
                                } else {
                                    ToolResult.Error(
                                        "User denied permission '${rawResult.permission}'. " +
                                        "Tell the user they can grant it in Settings > Apps > PersonalAI > Permissions."
                                    )
                                }
                            }
                        } else rawResult

                        // Append the function_call_output item
                        conversationItems.put(JSONObject().apply {
                            put("type", "function_call_output")
                            put("call_id", call.id)
                            put("output", toolResult.toJson())
                        })
                    }
                    // Continue loop
                }

                is AgentResponse.Text -> {
                    // Save assistant response to DB (foreground only)
                    if (!backgroundMode) {
                        chatRepository.saveMessage(
                            Message(content = response.text, role = MessageRole.ASSISTANT)
                        )
                    }
                    emit(AgentStep.Complete(Result.success(response.text)))
                    return@flow
                }
            }
        }

        // Exceeded max iterations without a text response
        emit(AgentStep.Complete(Result.failure(Exception("Agent loop exceeded $MAX_ITERATIONS iterations"))))
    }

    private fun humanReadable(toolName: String, arguments: String): String {
        val args = runCatching { JSONObject(arguments) }.getOrDefault(JSONObject())
        return when (toolName) {
            "schedule_task" -> {
                val title = args.optString("title", "")
                if (title.isNotBlank()) "📅 Scheduling: $title…" else "📅 Scheduling task…"
            }
            "save_memory" -> "🧠 Saving memory…"
            "forget_memory" -> "🗑️ Forgetting memory…"
            "forget_all_memories" -> "🗑️ Clearing all memories…"
            "open_app" -> {
                val name = args.optString("app_name", args.optString("package_name", ""))
                if (name.isNotBlank()) "🔧 Opening $name…" else "🔧 Opening app…"
            }
            "get_installed_apps" -> "📱 Checking installed apps…"
            "read_contacts" -> "📋 Reading contacts…"
            "get_clipboard" -> "📋 Reading clipboard…"
            "ask_user" -> {
                val q = args.optString("question", "")
                if (q.isNotBlank()) "❓ $q" else "❓ Asking you a question…"
            }
            "send_sms" -> {
                val to = args.optString("phone_number", "")
                if (to.isNotBlank()) "💬 Sending SMS to $to…" else "💬 Sending SMS…"
            }
            "dial_phone" -> {
                val num = args.optString("phone_number", "")
                if (num.isNotBlank()) "📞 Dialing $num…" else "📞 Opening dialer…"
            }
            "set_alarm" -> {
                val h = args.optInt("hour", -1)
                val m = args.optInt("minute", -1)
                if (h >= 0 && m >= 0) "⏰ Setting alarm for %02d:%02d…".format(h, m)
                else "⏰ Setting alarm…"
            }
            "get_battery_level" -> "🔋 Checking battery…"
            "send_notification" -> {
                val t = args.optString("title", "")
                if (t.isNotBlank()) "🔔 Sending notification: $t…" else "🔔 Sending notification…"
            }
            "get_location" -> "📍 Getting your location…"
            "add_calendar_event" -> {
                val t = args.optString("title", "")
                if (t.isNotBlank()) "📅 Adding to calendar: $t…" else "📅 Adding calendar event…"
            }
            "open_url" -> {
                val url = args.optString("url", "")
                if (url.isNotBlank()) "🌐 Opening $url…" else "🌐 Opening URL…"
            }
            else -> "🔧 Running $toolName…"
        }
    }
}
