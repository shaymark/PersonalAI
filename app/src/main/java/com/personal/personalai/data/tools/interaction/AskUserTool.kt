package com.personal.personalai.data.tools.interaction

import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import com.personal.personalai.domain.tools.UserInputBroker
import org.json.JSONObject
import javax.inject.Inject

/**
 * Pauses the agent loop and asks the user a question. The loop resumes once the user
 * submits their answer via the chat input bar.
 *
 * Not background-safe: there is no UI to receive the answer when running from WorkManager.
 */
class AskUserTool @Inject constructor(
    private val userInputBroker: UserInputBroker
) : AgentTool {

    override val name = "ask_user"
    override val description = """
        Ask the user a question and wait for their answer before continuing.
        Use this when you need specific information to complete a task — e.g. a preference,
        a date, a name, or a confirmation. Do NOT use this for simple clarifications you can
        infer from context. The user's answer is returned so you can act on it.
    """.trimIndent()

    override val supportsBackground = false

    override fun parametersSchema(): JSONObject = JSONObject(
        """
        {
            "type": "object",
            "properties": {
                "question": {
                    "type": "string",
                    "description": "The question to ask the user"
                }
            },
            "required": ["question"]
        }
        """.trimIndent()
    )

    override suspend fun execute(params: JSONObject): ToolResult {
        val question = params.optString("question", "").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("question parameter is required")

        val answer = userInputBroker.askAndAwait(question)
        return ToolResult.Success("""{"answer":${JSONObject.quote(answer)}}""")
    }
}
