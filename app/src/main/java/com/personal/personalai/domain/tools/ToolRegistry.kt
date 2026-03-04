package com.personal.personalai.domain.tools

interface ToolRegistry {
    fun getTools(): List<AgentTool>
    fun getBackgroundSafeTools(): List<AgentTool>
    suspend fun execute(name: String, arguments: String): ToolResult
}
