package com.personal.personalai.localllm.api

/**
 * A tool definition passed to [LiteRtLlmEngine] for native function calling.
 *
 * The engine wraps each [LocalLlmTool] in a [DynamicOpenApiTool] so the Gemma model
 * can call it through LiteRT-LM's ToolProvider API.
 *
 * @param name Tool name as it will appear in the model's function call.
 * @param description Human-readable description for the model.
 * @param parametersSchemaJson JSON Schema string for the tool's parameters object
 *   (i.e., the output of [AgentTool.parametersSchema().toString()]).
 */
data class LocalLlmTool(
    val name: String,
    val description: String,
    val parametersSchemaJson: String
)
