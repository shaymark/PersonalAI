package com.llmengine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manages GGUF model files on the device: downloading from HuggingFace, listing, and deleting.
 *
 * Models are stored in `<external-files-dir>/llm-engine-models/` so they survive app updates
 * and are excluded from backups by default.
 *
 * Example:
 * ```kotlin
 * val manager = ModelManager(context)
 *
 * // Download
 * manager.download(Models.QWEN2_5_1_5B_Q4).collect { state ->
 *     when (state) {
 *         is DownloadState.Progress -> showProgress(state.fraction)
 *         is DownloadState.Done     -> loadModel(state.file)
 *         is DownloadState.Failed   -> showError(state.error)
 *     }
 * }
 *
 * // Load
 * val file = manager.getModelFile(Models.QWEN2_5_1_5B_Q4) ?: error("Not downloaded")
 * val session = withContext(Dispatchers.IO) { LlmEngine.load(file) }
 * ```
 */
class ModelManager(context: Context) {

    private val modelsDir: File = File(
        context.getExternalFilesDir(null), "llm-engine-models"
    ).also { it.mkdirs() }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)   // No read timeout — GGUF files can be several GB
        .build()

    /**
     * Download [model] from HuggingFace, emitting [DownloadState] updates.
     *
     * The download is written to a `.download` temp file and atomically renamed on completion
     * so a partially downloaded file is never returned by [getModelFile].
     *
     * If the file is already downloaded, this function immediately emits [DownloadState.Done].
     */
    fun download(model: ModelDescriptor): Flow<DownloadState> = flow {
        val destFile = File(modelsDir, model.fileName)

        // Already present — skip download
        if (destFile.exists() && destFile.length() > 0) {
            emit(DownloadState.Done(destFile))
            return@flow
        }

        val tempFile = File(modelsDir, "${model.fileName}.download")
        val url = "https://huggingface.co/${model.huggingFaceRepo}/resolve/main/${model.fileName}"

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "llm-engine/1.0")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                emit(DownloadState.Failed(Exception("HTTP ${response.code}: ${response.message}")))
                return@flow
            }

            val body = response.body
            if (body == null) {
                emit(DownloadState.Failed(Exception("Empty response body from $url")))
                return@flow
            }

            val totalBytes = body.contentLength().let { if (it > 0) it else model.sizeBytes }
            var downloaded = 0L
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)   // 8 192 bytes

            tempFile.outputStream().use { output ->
                body.byteStream().use { input ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        val fraction = if (totalBytes > 0) {
                            (downloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
                        } else 0f
                        emit(DownloadState.Progress(downloaded, totalBytes, fraction))
                    }
                }
            }

            // Atomic rename: swap temp → final
            if (tempFile.renameTo(destFile)) {
                emit(DownloadState.Done(destFile))
            } else {
                // renameTo can fail across filesystems; fall back to copy + delete
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
                emit(DownloadState.Done(destFile))
            }

        } catch (e: Exception) {
            tempFile.delete()
            emit(DownloadState.Failed(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Returns the [File] for [model] if it has been downloaded, or `null` otherwise.
     */
    fun getModelFile(model: ModelDescriptor): File? {
        val file = File(modelsDir, model.fileName)
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Returns all [ModelDescriptor]s from the built-in [Models] presets that are
     * currently downloaded on this device.
     */
    fun listDownloaded(): List<ModelDescriptor> =
        listOf(
            Models.QWEN2_5_1_5B_Q4,
            Models.QWEN2_5_3B_Q4,
            Models.LLAMA3_2_1B_Q4,
            Models.PHI3_5_MINI_Q4
        ).filter { getModelFile(it) != null }

    /**
     * Delete [model]'s downloaded file.
     * @return `true` if the file existed and was deleted, `false` if it was not found.
     */
    fun delete(model: ModelDescriptor): Boolean =
        File(modelsDir, model.fileName).let { f -> if (f.exists()) f.delete() else false }

    /** Absolute path of the directory where model files are stored. */
    val modelsDirPath: String get() = modelsDir.absolutePath
}
