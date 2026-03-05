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
 * Opens the system dialer with a pre-filled number. The user still has to tap "Call" —
 * no CALL_PHONE permission is needed since we use ACTION_DIAL (not ACTION_CALL).
 */
class DialPhoneTool @Inject constructor(
    @ApplicationContext private val context: Context
) : AgentTool {

    override val name = "dial_phone"
    override val description = """
        Open the phone dialer pre-filled with a number so the user can call it.
        Use read_contacts first if you only have a name and need to look up their number.
        The user must tap the Call button themselves — this just opens the dialer.
    """.trimIndent()

    override val supportsBackground = false

    override fun parametersSchema(): JSONObject = JSONObject(
        """
        {
            "type": "object",
            "properties": {
                "phone_number": {
                    "type": "string",
                    "description": "The phone number to dial, e.g. '+15551234567'"
                }
            },
            "required": ["phone_number"]
        }
        """.trimIndent()
    )

    override suspend fun execute(params: JSONObject): ToolResult {
        val phoneNumber = params.optString("phone_number", "").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("phone_number parameter is required")

        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ToolResult.Success("""{"dialing":true,"number":"$phoneNumber"}""")
        } catch (e: Exception) {
            ToolResult.Error("Failed to open dialer: ${e.message}")
        }
    }
}
