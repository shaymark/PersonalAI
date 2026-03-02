package com.personal.personalai.data.datasource.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

/**
 * Handles audio transcription via the OpenAI Whisper API.
 *
 * Sends a multipart POST to `/v1/audio/transcriptions` and returns the
 * transcribed text. Uses the same [OkHttpClient] as [OpenAiDataSource] —
 * no additional networking dependency is required.
 */
class WhisperDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    companion object {
        private const val WHISPER_URL = "https://api.openai.com/v1/audio/transcriptions"
        private const val MODEL = "whisper-1"
        private val MEDIA_TYPE_AUDIO = "audio/mp4".toMediaType()
    }

    /**
     * Transcribes [audioFile] using the given [apiKey].
     * @return [Result.success] containing the transcribed text, or
     *         [Result.failure] with a descriptive error message.
     */
    suspend fun transcribe(apiKey: String, audioFile: File): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        name = "file",
                        filename = audioFile.name,
                        body = audioFile.asRequestBody(MEDIA_TYPE_AUDIO)
                    )
                    .addFormDataPart("model", MODEL)
                    .build()

                val request = Request.Builder()
                    .url(WHISPER_URL)
                    .header("Authorization", "Bearer $apiKey")
                    .post(requestBody)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response from Whisper"))

                if (!response.isSuccessful) {
                    val errorMsg = runCatching {
                        JSONObject(responseBody).getJSONObject("error").getString("message")
                    }.getOrDefault("Whisper error: HTTP ${response.code}")
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val text = JSONObject(responseBody).getString("text").trim()
                Result.success(text)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
