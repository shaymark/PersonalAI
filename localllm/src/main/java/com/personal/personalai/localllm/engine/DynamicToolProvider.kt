package com.personal.personalai.localllm.engine

import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.tool
import com.personal.personalai.localllm.api.LocalLlmTool

/**
 * Creates a list of native [ToolProvider]s from our runtime [LocalLlmTool] list.
 *
 * LiteRT-LM exposes `tool(OpenApiTool): ToolProvider` — a top-level factory that wraps
 * a single [OpenApiTool] into a [ToolProvider] compatible with [ConversationConfig].
 * We call it once per tool so each tool is its own provider in the list.
 *
 * The [syncExecutor] is provided by [LocalLlmDataSource] in `:app` and bridges our async
 * [AgentTool.execute] via `runBlocking(Dispatchers.Default)` — the same pattern used by
 * the Google AI Edge Gallery's AgentTools.kt.
 */
internal fun buildToolProviders(
    tools: List<LocalLlmTool>,
    syncExecutor: (name: String, argsJson: String) -> String
): List<ToolProvider> = tools.map { localTool ->
    tool(DynamicOpenApiTool(localTool, syncExecutor))
}

/**
 * Wraps a single [LocalLlmTool] as a [OpenApiTool] for LiteRT-LM.
 *
 * - [getToolDescriptionJsonString] returns an OpenAPI-compatible JSON schema that the engine
 *   sends to the model so it knows how to call the tool.
 * - [execute] is called synchronously by the inference engine when the model invokes the tool.
 */
private class DynamicOpenApiTool(
    private val localTool: LocalLlmTool,
    private val executor: (name: String, argsJson: String) -> String
) : OpenApiTool {

    override fun getToolDescriptionJsonString(): String =
        """{"name":"${esc(localTool.name)}","description":"${esc(localTool.description)}","parameters":${localTool.parametersSchemaJson}}"""

    override fun execute(jsonArgs: String): String =
        executor(localTool.name, jsonArgs)

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
