package com.personal.personalai.data.tools.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject

class SendSmsTool @Inject constructor(
    @ApplicationContext private val context: Context
) : AgentTool {

    override val name = "send_sms"
    override val description = """
        Send an SMS text message to a phone number. Use read_contacts first if you only have
        a contact name and need to look up their number.
    """.trimIndent()

    override fun parametersSchema(): JSONObject = JSONObject(
        """
        {
            "type": "object",
            "properties": {
                "phone_number": {
                    "type": "string",
                    "description": "The phone number to send to, e.g. '+15551234567' or '055-123-4567'"
                },
                "message": {
                    "type": "string",
                    "description": "The text message content to send"
                }
            },
            "required": ["phone_number", "message"]
        }
        """.trimIndent()
    )

    override suspend fun execute(params: JSONObject): ToolResult {
        val permission = Manifest.permission.SEND_SMS
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            return ToolResult.PermissionDenied(permission)
        }

        val phoneNumber = params.optString("phone_number", "").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("phone_number parameter is required")
        val message = params.optString("message", "").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("message parameter is required")

        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            ToolResult.Success("""{"sent":true,"to":"$phoneNumber"}""")
        } catch (e: Exception) {
            ToolResult.Error("Failed to send SMS: ${e.message}")
        }
    }
}
