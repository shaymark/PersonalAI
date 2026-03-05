package com.llmengine

/**
 * Parameters controlling text generation.
 *
 * @param maxTokens  Maximum number of tokens to generate.
 * @param temperature Sampling temperature (0 = deterministic, 1 = default, >1 = creative).
 * @param topP        Nucleus-sampling probability mass cutoff (0–1).
 * @param seed        Random seed for reproducibility; -1 = random seed each run.
 * @param stopStrings Generation stops early when the output ends with any of these strings.
 */
data class GenerationParams(
    val maxTokens: Int = 512,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val seed: Int = -1,
    val stopStrings: List<String> = emptyList()
)

/** Sealed state emitted by [ModelManager.download]. */
sealed class DownloadState {
    /** Download is in progress. [fraction] is in [0, 1]. */
    data class Progress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val fraction: Float
    ) : DownloadState()

    /** Download completed and the model file is ready at [file]. */
    data class Done(val file: java.io.File) : DownloadState()

    /** Download failed with the given [error]. */
    data class Failed(val error: Exception) : DownloadState()
}
