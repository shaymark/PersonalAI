package com.personal.personalai.data.tools.files

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

class ShareFileTool @Inject constructor(
    @ApplicationContext private val context: Context
) : AgentTool {

    override val name = "share_file"
    override val description = """
        Share a text file via email or any other app on the device (WhatsApp, Notes, Drive, etc.).
        Opens the Android share sheet so the user can pick the destination app.
        Optionally pre-fill the email recipient, subject, and body text.
    """.trimIndent()
    override val supportsBackground = false

    override fun parametersSchema(): JSONObject = JSONObject(
        """
        {
            "type": "object",
            "properties": {
                "filename": {
                    "type": "string",
                    "description": "The filename to share, e.g. 'shopping.txt'"
                },
                "email": {
                    "type": "string",
                    "description": "Optional recipient email address to pre-fill"
                },
                "subject": {
                    "type": "string",
                    "description": "Optional email subject line"
                },
                "body": {
                    "type": "string",
                    "description": "Optional message body text to include alongside the file"
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

        val email = params.optString("email", "").takeIf { it.isNotBlank() }
        val subject = params.optString("subject", "").takeIf { it.isNotBlank() }
        val body = params.optString("body", "").takeIf { it.isNotBlank() }

        return try {
            val dir = context.getExternalFilesDir("ai_files")
                ?: return ToolResult.Error("Storage not available")
            val file = File(dir, filename)

            if (!file.exists()) return ToolResult.Error("File not found: $filename")

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                if (email != null) putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
                if (body != null) putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(
                Intent.createChooser(intent, "Share $filename")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )

            ToolResult.Success("""{"status":"share_dialog_opened","filename":"$filename"}""")
        } catch (e: Exception) {
            ToolResult.Error("Failed to share file: ${e.message}")
        }
    }
}
