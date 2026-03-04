package com.personal.personalai.data.tools.management

import com.personal.personalai.domain.repository.MemoryRepository
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class ForgetMemoryTool @Inject constructor(
    private val memoryRepository: MemoryRepository
) : AgentTool {

    override val name = "forget_memory"
    override val description = """
        Delete stored memories for a specific topic. Use this when the user asks you to
        forget something specific (e.g. "forget my name", "forget my preferences").
    """.trimIndent()

    override fun parametersSchema(): JSONObject = JSONObject("""
        {
            "type": "object",
            "properties": {
                "topic": {
                    "type": "string",
                    "description": "The topic/category of memories to delete"
                }
            },
            "required": ["topic"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject): ToolResult {
        return try {
            val topic = params.getString("topic")
            memoryRepository.deleteByTopic(topic)
            ToolResult.Success("""{"forgotten":true,"topic":"$topic"}""")
        } catch (e: Exception) {
            ToolResult.Error("Failed to forget memory: ${e.message}")
        }
    }
}
