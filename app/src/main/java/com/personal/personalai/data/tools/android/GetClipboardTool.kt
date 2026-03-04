package com.personal.personalai.data.tools.android

import android.content.ClipboardManager
import android.content.Context
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject

class GetClipboardTool @Inject constructor(
    @ApplicationContext private val context: Context
) : AgentTool {

    override val name = "get_clipboard"
    override val description = """
        Read the current text content of the device clipboard. Use this when the user
        asks about what they copied or wants you to use their clipboard content.
    """.trimIndent()
    override val supportsBackground = false

    override fun parametersSchema(): JSONObject = JSONObject("""
        {
            "type": "object",
            "properties": {}
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject): ToolResult {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            if (text == null) {
                ToolResult.Success("""{"text":null,"empty":true}""")
            } else {
                val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"")
                ToolResult.Success("""{"text":"$escaped"}""")
            }
        } catch (e: Exception) {
            ToolResult.Error("Failed to read clipboard: ${e.message}")
        }
    }
}
