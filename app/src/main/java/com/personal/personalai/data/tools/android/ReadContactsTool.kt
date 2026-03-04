package com.personal.personalai.data.tools.android

import android.Manifest
import android.content.Context
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class ReadContactsTool @Inject constructor(
    @ApplicationContext private val context: Context
) : AgentTool {

    override val name = "read_contacts"
    override val description = """
        Search the user's contacts for a name or phone number. Returns matching contacts
        with their names and phone numbers. Provide a query to search, or omit it to
        return the first 10 contacts.
    """.trimIndent()

    override fun parametersSchema(): JSONObject = JSONObject("""
        {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "Optional name or partial name to search for"
                }
            }
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject): ToolResult {
        val permission = Manifest.permission.READ_CONTACTS
        if (ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return ToolResult.PermissionDenied(permission)
        }

        return try {
            val query = params.optString("query", "").takeIf { it.isNotBlank() }
            val contacts = mutableListOf<JSONObject>()
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = if (query != null)
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            else null
            val selectionArgs = if (query != null) arrayOf("%$query%") else null

            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val seen = mutableSetOf<String>()
                while (cursor.moveToNext() && contacts.size < 10) {
                    val name = cursor.getString(nameIndex) ?: continue
                    val number = cursor.getString(numberIndex) ?: continue
                    val key = "$name|$number"
                    if (seen.add(key)) {
                        contacts.add(JSONObject().apply {
                            put("name", name)
                            put("phone", number)
                        })
                    }
                }
            }
            ToolResult.Success(JSONArray(contacts).toString())
        } catch (e: Exception) {
            ToolResult.Error("Failed to read contacts: ${e.message}")
        }
    }
}
