package com.personal.personalai.data.tools.android

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.personal.personalai.MainActivity
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import com.personal.personalai.worker.TaskReminderWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject

class SendNotificationTool @Inject constructor(
    @ApplicationContext private val context: Context
) : AgentTool {

    override val name = "send_notification"
    override val description = """
        Post a notification to the device notification tray with a title and message.
        Useful for delivering information or reminders directly without opening the app.
    """.trimIndent()

    override fun parametersSchema(): JSONObject = JSONObject(
        """
        {
            "type": "object",
            "properties": {
                "title": {
                    "type": "string",
                    "description": "The notification title"
                },
                "message": {
                    "type": "string",
                    "description": "The notification body text"
                }
            },
            "required": ["title", "message"]
        }
        """.trimIndent()
    )

    override suspend fun execute(params: JSONObject): ToolResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return ToolResult.PermissionDenied(permission)
            }
        }

        val title = params.optString("title", "").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("title parameter is required")
        val message = params.optString("message", "").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("message parameter is required")

        return try {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, TaskReminderWorker.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context)
                .notify(System.currentTimeMillis().toInt(), notification)

            ToolResult.Success("""{"sent":true,"title":${JSONObject.quote(title)}}""")
        } catch (e: Exception) {
            ToolResult.Error("Failed to send notification: ${e.message}")
        }
    }
}
