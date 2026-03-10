package com.personal.personalai.data.tools.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject

/**
 * Opens a URL in the device's default browser using ACTION_VIEW.
 * No special permissions required.
 */
class OpenUrlTool @Inject constructor(
    @ApplicationContext private val context: Context
) : AgentTool {

    override val name = "open_url"
    override val description = """
        Open a URL in the device's default browser. The URL must start with http:// or https://.
        Use this to show the user a web page, article, search result, or any other web resource.
    """.trimIndent()

    override val supportsBackground = false

    override fun parametersSchema(): JSONObject = JSONObject(
        """
        {
            "type": "object",
            "properties": {
                "url": {
                    "type": "string",
                    "description": "The full URL to open, e.g. 'https://example.com'"
                }
            },
            "required": ["url"]
        }
        """.trimIndent()
    )

    override suspend fun execute(params: JSONObject): ToolResult {
        val url = params.optString("url", "").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("url parameter is required")

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolResult.Error("URL must start with http:// or https://")
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ToolResult.Success("""{"opened":true,"url":"$url"}""")
        } catch (e: Exception) {
            ToolResult.Error("Failed to open URL: ${e.message}")
        }
    }
}
