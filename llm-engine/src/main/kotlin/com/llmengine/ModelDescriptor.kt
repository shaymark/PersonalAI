package com.llmengine

/**
 * Describes a MediaPipe-format model that can be downloaded and loaded on-device.
 *
 * @param id              Unique identifier used for persistence and comparison.
 * @param displayName     Human-readable name shown in the Settings UI.
 * @param huggingFaceRepo HuggingFace repository in the form "owner/repo-name".
 * @param fileName        Exact filename of the model file inside the repo.
 * @param sizeBytes       Approximate size used for progress display.
 * @param requiresHfAuth  True if the HuggingFace repo is gated (needs license acceptance).
 */
data class ModelDescriptor(
    val id: String,
    val displayName: String,
    val huggingFaceRepo: String,
    val fileName: String,
    val sizeBytes: Long,
    val requiresHfAuth: Boolean = false
)

/**
 * Pre-defined MediaPipe model descriptors.
 *
 * ⚠️  IMPORTANT — These models are gated on HuggingFace.
 * To download them you must:
 *   1. Create a free HuggingFace account at https://huggingface.co
 *   2. Accept the Gemma licence on the model page (one-time, takes ~10 seconds)
 *   3. Generate a Read access token at https://huggingface.co/settings/tokens
 *
 * Download URL format:
 *   https://huggingface.co/{huggingFaceRepo}/resolve/main/{fileName}
 *   with header  Authorization: Bearer <your_token>
 */
object Models {

    /**
     * Gemma 3 1B Instruct — int4 — ~555 MB.
     * Best choice: small, fast, GPU-accelerated via MediaPipe.
     * Repo: https://huggingface.co/litert-community/Gemma3-1B-IT
     */
    val GEMMA3_1B_INT4 = ModelDescriptor(
        id              = "gemma3-1b-it-int4",
        displayName     = "Gemma 3 1B int4 (~555 MB) ⚡",
        huggingFaceRepo = "litert-community/Gemma3-1B-IT",
        fileName        = "gemma3-1b-it-int4.task",
        sizeBytes       = 555_000_000L,
        requiresHfAuth  = true
    )

    /**
     * Gemma 3 4B Instruct — int4 — ~2.6 GB.
     * Higher quality responses; needs ≥ 4 GB free RAM.
     * Repo: https://huggingface.co/litert-community/Gemma3-4B-IT
     */
    val GEMMA3_4B_INT4 = ModelDescriptor(
        id              = "gemma3-4b-it-int4",
        displayName     = "Gemma 3 4B int4 (~2.6 GB)",
        huggingFaceRepo = "litert-community/Gemma3-4B-IT",
        fileName        = "gemma3-4b-it-int4-web.task",
        sizeBytes       = 2_600_000_000L,
        requiresHfAuth  = true
    )

    /** All built-in model presets as a list, for use in UI enumerations. */
    val all: List<ModelDescriptor> get() = listOf(
        GEMMA3_1B_INT4,
        GEMMA3_4B_INT4
    )
}
