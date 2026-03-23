package com.personal.personalai.data.tools.files

import android.content.Context
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

class ReadFileTool @Inject constructor(
    @ApplicationContext private val context: Context
) : AgentTool {

    override val name = "read_file"
    override val description = """
        Read the full text content of a file stored on the device.
        Use list_files first to discover available files, then call this only when you need the actual content.
    """.trimIndent()
    override val supportsBackground = true

    override fun parametersSchema(): JSONObject = JSONObject(
        """
        {
            "type": "object",
            "properties": {
                "filename": {
                    "type": "string",
                    "description": "The filename to read, e.g. 'shopping.txt'"
                }
            },
            "required": ["filename"]
        }
        """.trimIndent()
    )

    override suspend fun execute(params: JSONObject): ToolResult {
        val filename = params.optString("filename", "").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("filename is required")

        if (filename.contains("/") || filename.contains("..") || filename == WriteFileTool.INDEX_FILE) {
            return ToolResult.Error("Invalid filename")
        }

        return try {
            val dir = context.getExternalFilesDir("ai_files")
                ?: return ToolResult.Error("Storage not available")
            val file = File(dir, filename)

            if (!file.exists()) return ToolResult.Error("File not found: $filename")

            val content = file.readText(Charsets.UTF_8)
            val result = JSONObject().apply {
                put("filename", filename)
                put("content", content)
                put("size_bytes", file.length())
            }
            ToolResult.Success(result.toString())
        } catch (e: Exception) {
            ToolResult.Error("Failed to read file: ${e.message}")
        }
    }
}
