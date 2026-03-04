package com.personal.personalai.data.tools.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject

class OpenAppTool @Inject constructor(
    @ApplicationContext private val context: Context
) : AgentTool {

    override val name = "open_app"
    override val description = """
        Open an installed app on the device. Provide either the app name (e.g. "YouTube",
        "Maps", "Spotify") or the package name (e.g. "com.google.android.youtube").
        The app name will be matched case-insensitively against installed apps.
    """.trimIndent()
    override val supportsBackground = false

    override fun parametersSchema(): JSONObject = JSONObject("""
        {
            "type": "object",
            "properties": {
                "app_name": {
                    "type": "string",
                    "description": "Human-readable app name, e.g. 'YouTube', 'Maps', 'Spotify'"
                },
                "package_name": {
                    "type": "string",
                    "description": "App package name, e.g. 'com.google.android.youtube'"
                }
            }
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject): ToolResult {
        val packageName = params.optString("package_name", "").takeIf { it.isNotBlank() }
        val appName = params.optString("app_name", "").takeIf { it.isNotBlank() }

        if (packageName == null && appName == null) {
            return ToolResult.Error("Provide at least app_name or package_name")
        }

        val resolvedPackage = packageName ?: findPackageByName(appName!!)
            ?: return ToolResult.Error("App '$appName' not found on this device")

        val launchIntent = context.packageManager.getLaunchIntentForPackage(resolvedPackage)
            ?: return ToolResult.Error("Cannot launch '$resolvedPackage' — no launcher activity found")

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launchIntent)
            val displayName = appName ?: getAppLabel(resolvedPackage)
            ToolResult.Success("""{"opened":true,"app":"$displayName","package":"$resolvedPackage"}""")
        } catch (e: Exception) {
            ToolResult.Error("Failed to open app: ${e.message}")
        }
    }

    private fun findPackageByName(name: String): String? {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .firstOrNull { resolveInfo ->
                resolveInfo.loadLabel(pm).toString().contains(name, ignoreCase = true)
            }
            ?.activityInfo?.packageName
    }

    private fun getAppLabel(packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: Exception) {
        packageName
    }
}
