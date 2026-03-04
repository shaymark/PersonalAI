package com.personal.personalai.data.tools.management

import com.personal.personalai.domain.repository.MemoryRepository
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class ForgetAllMemoriesTool @Inject constructor(
    private val memoryRepository: MemoryRepository
) : AgentTool {

    override val name = "forget_all_memories"
    override val description = """
        Clear all stored memories about the user. Use this when the user asks you to forget
        everything about them (e.g. "forget everything", "clear all my data").
    """.trimIndent()

    override fun parametersSchema(): JSONObject = JSONObject("""
        {
            "type": "object",
            "properties": {}
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject): ToolResult {
        return try {
            memoryRepository.clearAllMemories()
            ToolResult.Success("""{"cleared":true}""")
        } catch (e: Exception) {
            ToolResult.Error("Failed to clear memories: ${e.message}")
        }
    }
}
