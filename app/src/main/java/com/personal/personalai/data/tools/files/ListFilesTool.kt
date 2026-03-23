package com.personal.personalai.data.tools.files

import android.content.Context
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

class ListFilesTool @Inject constructor(
    @ApplicationContext private val context: Context
) : AgentTool {

    override val name = "list_files"
    override val description = """
        List all text files stored on the device along with their descriptions, sizes, and last-modified times.
        Call this before reading or editing files to discover what exists and what each file contains.
        This reads only a small index file — not the files themselves — so it is fast and lightweight.
    """.trimIndent()
    override val supportsBackground = true

    override fun parametersSchema(): JSONObject = JSONObject(
        """
        {
            "type": "object",
            "properties": {}
        }
        """.trimIndent()
    )

    override suspend fun execute(params: JSONObject): ToolResult {
        return try {
            val dir = context.getExternalFilesDir("ai_files")
            if (dir == null || !dir.exists()) {
                return ToolResult.Success("""{"files":[]}""")
            }

            val indexFile = File(dir, WriteFileTool.INDEX_FILE)
            val array = if (indexFile.exists()) {
                try { JSONArray(indexFile.readText()) } catch (_: Exception) { JSONArray() }
            } else {
                JSONArray()
            }

            ToolResult.Success(JSONObject().apply { put("files", array) }.toString())
        } catch (e: Exception) {
            ToolResult.Error("Failed to list files: ${e.message}")
        }
    }
}
