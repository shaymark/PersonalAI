package com.personal.personalai.data.tools.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class GetInstalledAppsTool @Inject constructor(
    @ApplicationContext private val context: Context
) : AgentTool {

    override val name = "get_installed_apps"
    override val description = """
        Get a list of user-installed apps on the device. Returns app names and package names.
        Use this to check if a specific app is installed or to discover available apps.
    """.trimIndent()

    override fun parametersSchema(): JSONObject = JSONObject("""
        {
            "type": "object",
            "properties": {}
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject): ToolResult {
        return try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .map { resolveInfo ->
                    JSONObject().apply {
                        put("name", resolveInfo.loadLabel(pm).toString())
                        put("package", resolveInfo.activityInfo.packageName)
                    }
                }
                .distinctBy { it.getString("package") }
                .sortedBy { it.getString("name") }

            ToolResult.Success(JSONArray(apps).toString())
        } catch (e: Exception) {
            ToolResult.Error("Failed to get installed apps: ${e.message}")
        }
    }
}
