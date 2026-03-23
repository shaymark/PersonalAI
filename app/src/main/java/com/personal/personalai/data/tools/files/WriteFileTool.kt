package com.personal.personalai.data.tools.files

import android.content.Context
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.time.Instant
import javax.inject.Inject

class WriteFileTool @Inject constructor(
    @ApplicationContext private val context: Context
) : AgentTool {

    override val name = "write_file"
    override val description = """
        Create a new text file or write to an existing one on the device.
        Use mode "overwrite" (default) to replace the file's content, or "append" to add text to the end.
        Provide a short description so the agent can understand the file's purpose without reading it.
        Files are stored in the app's private storage directory.
    """.trimIndent()
    override val supportsBackground = true

    override fun parametersSchema(): JSONObject = JSONObject(
        """
        {
            "type": "object",
            "properties": {
                "filename": {
                    "type": "string",
                    "description": "The filename including extension, e.g. 'shopping.txt'"
                },
                "content": {
                    "type": "string",
                    "description": "Text content to write to the file"
                },
                "mode": {
                    "type": "string",
                    "enum": ["overwrite", "append"],
                    "description": "Write mode: 'overwrite' replaces existing content (default), 'append' adds to the end"
                },
                "description": {
                    "type": "string",
                    "description": "One-line summary of what this file contains, e.g. 'grocery shopping list'. Used by list_files so the agent can find the right file quickly."
                }
            },
            "required": ["filename", "content"]
        }
        """.trimIndent()
    )

    override suspend fun execute(params: JSONObject): ToolResult {
        val filename = params.optString("filename", "").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("filename is required")
        val content = params.optString("content", "")
        val mode = params.optString("mode", "overwrite")
        val description = params.optString("description", "").takeIf { it.isNotBlank() }

        if (filename.contains("/") || filename.contains("..") || filename == INDEX_FILE) {
            return ToolResult.Error("Invalid filename")
        }

        return try {
            val dir = context.getExternalFilesDir("ai_files")
                ?: return ToolResult.Error("Storage not available")
            dir.mkdirs()

            val file = File(dir, filename)
            if (mode == "append") {
                FileWriter(file, true).use { it.write(content) }
            } else {
                file.writeText(content, Charsets.UTF_8)
            }

            updateIndex(dir, filename, description, file.length())

            ToolResult.Success(
                """{"status":"success","filename":"$filename","bytes_written":${file.length()}}"""
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to write file: ${e.message}")
        }
    }

    private fun updateIndex(dir: File, filename: String, description: String?, fileSize: Long) {
        val indexFile = File(dir, INDEX_FILE)
        val array = if (indexFile.exists()) {
            try { JSONArray(indexFile.readText()) } catch (_: Exception) { JSONArray() }
        } else {
            JSONArray()
        }

        val updated = JSONArray()
        for (i in 0 until array.length()) {
            val entry = array.getJSONObject(i)
            if (entry.optString("name") != filename) updated.put(entry)
        }

        val entry = JSONObject().apply {
            put("name", filename)
            put("description", description ?: "")
            put("size_bytes", fileSize)
            put("last_modified", Instant.now().toString())
        }
        updated.put(entry)

        indexFile.writeText(updated.toString())
    }

    companion object {
        const val INDEX_FILE = "_index.json"
    }
}
