package com.personal.personalai.localllm.api

/**
 * Supported on-device Gemma models available for download and inference.
 *
 * Models are downloaded from HuggingFace in LiteRT task format and stored in
 * the app's external files directory.
 */
enum class LocalModel(
    val modelId: String,
    val displayName: String,
    val huggingFaceRepo: String,
    val fileName: String,
    /** Expected download size in bytes (used for progress estimation). */
    val approximateSizeBytes: Long
) {
    GEMMA_4_E2B(
        modelId              = "gemma-4-E2B-it",
        displayName          = "Gemma 4 E2B (~2.6 GB)",
        huggingFaceRepo      = "litert-community/gemma-4-E2B-it-litert-lm",
        fileName             = "gemma-4-E2B-it.litertlm",
        approximateSizeBytes = 2_772_123_648L
    ),
    GEMMA_4_E4B(
        modelId              = "gemma-4-E4B-it",
        displayName          = "Gemma 4 E4B (~3.7 GB)",
        huggingFaceRepo      = "litert-community/gemma-4-E4B-it-litert-lm",
        fileName             = "gemma-4-E4B-it.litertlm",
        approximateSizeBytes = 3_921_479_680L
    );

    companion object {
        fun fromId(modelId: String): LocalModel? = entries.find { it.modelId == modelId }
    }
}
