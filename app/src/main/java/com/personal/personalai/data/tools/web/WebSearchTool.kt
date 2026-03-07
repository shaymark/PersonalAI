package com.personal.personalai.data.tools.web

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.personal.personalai.domain.tools.AgentTool
import com.personal.personalai.domain.tools.ToolResult
import com.personal.personalai.presentation.settings.PreferencesKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * Searches the web using the Serper.dev Google Search API.
 *
 * Requires a free API key from serper.dev — 2,500 free searches/month, no credit card.
 * The key is stored in DataStore and configurable via Settings → Web Search.
 *
 * Works with all AI providers: OpenAI, Ollama, and Local LLM.
 * OpenAI also has its built-in server-side web_search_preview; both can coexist without conflict.
 */
class WebSearchTool @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val dataStore: DataStore<Preferences>
) : AgentTool {

    companion object {
        private const val SERPER_URL = "https://google.serper.dev/search"
        private val JSON_MEDIA_TYPE  = "application/json; charset=utf-8".toMediaType()
    }

    override val name        = "web_search"
    override val description =
        "Search the web for current information, news, facts, or anything that may not be in " +
        "training data. Returns titles, URLs, and snippets from search results. Use this " +
        "whenever the user asks about recent events, live data, or specific facts."
    override val supportsBackground: Boolean = true

    override fun parametersSchema(): JSONObject = JSONObject(
        """
        {
          "type": "object",
          "properties": {
            "query": {
              "type": "string",
              "description": "The search query to look up"
            }
          },
          "required": ["query"]
        }
        """.trimIndent()
    )

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            // Read the Serper API key from DataStore at call time (not construction time)
            val apiKey = dataStore.data.first()[PreferencesKeys.SERPER_API_KEY].orEmpty().trim()

            if (apiKey.isBlank()) {
                return@withContext ToolResult.Error(
                    "Web search requires a Serper API key. " +
                    "Get a free key at serper.dev (2,500 searches/month, no credit card) " +
                    "and add it in Settings → Web Search."
                )
            }

            val query = params.getString("query")

            val requestBody = JSONObject().apply {
                put("q",   query)
                put("num", 5)          // request up to 5 organic results
            }.toString().toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(SERPER_URL)
                .header("X-API-KEY",     apiKey)
                .header("Content-Type",  "application/json")
                .post(requestBody)
                .build()

            val bodyStr = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ToolResult.Error(
                        "Search API returned ${response.code}: ${response.message}. " +
                        "Check that your Serper API key in Settings is correct."
                    )
                }
                response.body?.string() ?: return@withContext ToolResult.Error(
                    "Empty response from search API"
                )
            }

            val root    = JSONObject(bodyStr)
            val results = JSONArray()

            // ── Answer box (direct answer, e.g. maths, conversions) ───────────
            root.optJSONObject("answerBox")?.let { box ->
                val answer = box.optString("answer").takeIf { it.isNotBlank() }
                    ?: box.optString("snippet").takeIf { it.isNotBlank() }
                if (answer != null) {
                    results.put(JSONObject().apply {
                        put("title",   box.optString("title", "Direct Answer"))
                        put("url",     box.optString("link", ""))
                        put("snippet", answer)
                    })
                }
            }

            // ── Organic results (up to 5) ──────────────────────────────────────
            val organic = root.optJSONArray("organic") ?: JSONArray()
            for (i in 0 until minOf(organic.length(), 5)) {
                val item = organic.optJSONObject(i) ?: continue
                results.put(JSONObject().apply {
                    put("title",   item.optString("title"))
                    put("url",     item.optString("link"))
                    put("snippet", item.optString("snippet"))
                })
            }

            if (results.length() == 0) {
                return@withContext ToolResult.Error("No results found for: $query")
            }

            ToolResult.Success(
                JSONObject().apply {
                    put("query",   query)
                    put("results", results)
                }.toString()
            )
        } catch (e: Exception) {
            ToolResult.Error("Web search failed: ${e.message}")
        }
    }
}
