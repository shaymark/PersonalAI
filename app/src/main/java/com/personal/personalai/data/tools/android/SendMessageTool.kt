package com.personal.personalai.data.tools.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.personal.personalai.data.tools.files.WriteFileTool
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

class SendMessageTool @Inject constructor(
    @ApplicationContext private val context: Context
) : AgentTool {

    override val name = "send_message"
    override val description = """
        Open the share / compose sheet to send a message via any installed app — email, WhatsApp, Telegram, SMS, and more.
        Optionally attach one or more files stored on the device.
        Email-specific fields (to, subject) are used by email apps and silently ignored by others.
        At least one of: text, filenames, or to must be provided.
    """.trimIndent()
    override val supportsBackground = false

    override fun parametersSchema(): JSONObject = JSONObject(
        """
        {
            "type": "object",
            "properties": {
                "text": {
                    "type": "string",
                    "description": "The message body or content to send"
                },
                "filenames": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": "Names of files in the device storage to attach, e.g. ['shopping.txt', 'notes.txt']"
                },
                "subject": {
                    "type": "string",
                    "description": "Subject line — used by email apps, ignored by messaging apps"
                },
                "to": {
                    "type": "string",
                    "description": "Recipient email address — used by email apps, ignored by messaging apps"
                }
            }
        }
        """.trimIndent()
    )

    override suspend fun execute(params: JSONObject): ToolResult {
        val text = params.optString("text", "").takeIf { it.isNotBlank() }
        val subject = params.optString("subject", "").takeIf { it.isNotBlank() }
        val to = params.optString("to", "").takeIf { it.isNotBlank() }
        val filenamesJson: JSONArray? = params.optJSONArray("filenames")

        if (text == null && to == null && (filenamesJson == null || filenamesJson.length() == 0)) {
            return ToolResult.Error("Provide at least one of: text, to, or filenames")
        }

        // Resolve file URIs
        val fileUris = mutableListOf<Uri>()
        if (filenamesJson != null) {
            val dir = context.getExternalFilesDir("ai_files")
                ?: return ToolResult.Error("Storage not available")

            for (i in 0 until filenamesJson.length()) {
                val filename = filenamesJson.optString(i, "")
                if (filename.isBlank() || filename.contains("/") || filename.contains("..") || filename == WriteFileTool.INDEX_FILE) {
                    return ToolResult.Error("Invalid filename: $filename")
                }
                val file = File(dir, filename)
                if (!file.exists()) return ToolResult.Error("File not found: $filename")
                fileUris.add(
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                )
            }
        }

        return try {
            val intent: Intent = when {
                fileUris.size >= 2 -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(fileUris))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                fileUris.size == 1 -> Intent(Intent.ACTION_SEND).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_STREAM, fileUris[0])
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                else -> Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                }
            }

            if (text != null) intent.putExtra(Intent.EXTRA_TEXT, text)
            if (subject != null) intent.putExtra(Intent.EXTRA_SUBJECT, subject)
            if (to != null) intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(to))

            context.startActivity(
                Intent.createChooser(intent, "Send message")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )

            ToolResult.Success(
                """{"status":"share_dialog_opened","attachments":${fileUris.size}}"""
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to open share dialog: ${e.message}")
        }
    }
}
