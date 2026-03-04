package com.personal.personalai.data.tools.management

import com.personal.personalai.domain.model.Memory
import com.personal.personalai.domain.repository.MemoryRepository
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class SaveMemoryTool @Inject constructor(
    private val memoryRepository: MemoryRepository
) : AgentTool {

    override val name = "save_memory"
    override val description = """
        Remember something about the user across conversations. Use this when the user asks
        you to remember a fact, preference, or piece of information about them.
    """.trimIndent()

    override fun parametersSchema(): JSONObject = JSONObject("""
        {
            "type": "object",
            "properties": {
                "content": {
                    "type": "string",
                    "description": "The information to remember, e.g. 'User's name is John'"
                },
                "topic": {
                    "type": "string",
                    "description": "Short category label, e.g. 'name', 'preference', 'fact'"
                }
            },
            "required": ["content", "topic"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject): ToolResult {
        return try {
            val content = params.getString("content")
            val topic = params.getString("topic")
            memoryRepository.saveMemory(Memory(content = content, topic = topic))
            ToolResult.Success("""{"saved":true,"topic":"$topic"}""")
        } catch (e: Exception) {
            ToolResult.Error("Failed to save memory: ${e.message}")
        }
    }
}
