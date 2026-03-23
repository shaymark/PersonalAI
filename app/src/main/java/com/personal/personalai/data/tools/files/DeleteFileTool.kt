package com.personal.personalai.data.tools.files

import android.content.Context
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

class DeleteFileTool @Inject constructor(
    @ApplicationContext private val context: Context
) : AgentTool {

    override val name = "delete_file"
    override val description = "Delete a text file stored on the device. Also removes it from the file index."
    override val supportsBackground = true

    override fun parametersSchema(): JSONObject = JSONObject(
        """
        {
            "type": "object",
            "properties": {
                "filename": {
                    "type": "string",
                    "description": "The filename to delete, e.g. 'shopping.txt'"
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

            file.delete()
            removeFromIndex(dir, filename)

            ToolResult.Success("""{"status":"deleted","filename":"$filename"}""")
        } catch (e: Exception) {
            ToolResult.Error("Failed to delete file: ${e.message}")
        }
    }

    private fun removeFromIndex(dir: File, filename: String) {
        val indexFile = File(dir, WriteFileTool.INDEX_FILE)
        if (!indexFile.exists()) return

        val array = try { JSONArray(indexFile.readText()) } catch (_: Exception) { return }
        val updated = JSONArray()
        for (i in 0 until array.length()) {
            val entry = array.getJSONObject(i)
            if (entry.optString("name") != filename) updated.put(entry)
        }
        indexFile.writeText(updated.toString())
    }
}
