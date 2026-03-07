package com.llmengine

/**
 * Pre-defined GGUF model descriptors for on-device inference via llama.cpp.
 *
 * Models are downloaded from HuggingFace and stored in external storage.
 * No auth token is required for ungated models.
 */
object Models {

    /**
     * Qwen3.5 4B Instruct — Q4_K_M quantization — ~2.5 GB.
     * Repo: https://huggingface.co/Qwen/Qwen3.5-4B
     */
    val QWEN_3_5_4B = ModelDescriptor(
        id              = "qwen3.5-4b-q4",
        displayName     = "Qwen3.5 4B Q4_K_M (~2.5 GB)",
        huggingFaceRepo = "unsloth/Qwen3.5-4B-GGUF",
        fileName        = "Qwen3.5-4B-Q4_K_M.gguf",
        sizeBytes       = 2_500_000_000L,
        requiresHfAuth  = false
    )

    /** All built-in model presets, shown in the Settings UI. */
    val all: List<ModelDescriptor> get() = listOf(QWEN_3_5_4B)
}
