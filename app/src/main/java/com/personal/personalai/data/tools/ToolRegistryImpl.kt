package com.personal.personalai.data.tools

import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolRegistry
import com.personal.personalai.domain.tools.ToolResult
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolRegistryImpl @Inject constructor(
    private val tools: Set<@JvmSuppressWildcards AgentTool>
) : ToolRegistry {

    override fun getTools(): List<AgentTool> = tools.toList()

    override fun getBackgroundSafeTools(): List<AgentTool> =
        tools.filter { it.supportsBackground }

    override suspend fun execute(name: String, arguments: String): ToolResult =
        tools.find { it.name == name }
            ?.execute(runCatching { JSONObject(arguments) }.getOrDefault(JSONObject()))
            ?: ToolResult.Error("Unknown tool: $name")
}
