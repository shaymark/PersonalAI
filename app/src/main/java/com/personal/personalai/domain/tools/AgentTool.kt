package com.personal.personalai.domain.tools

import org.json.JSONObject

interface AgentTool {
    val name: String
    val description: String
    /** False for tools that require the app to be in the foreground (e.g. open_app, get_clipboard). */
    val supportsBackground: Boolean get() = true
    fun parametersSchema(): JSONObject
    suspend fun execute(params: JSONObject): ToolResult
}
