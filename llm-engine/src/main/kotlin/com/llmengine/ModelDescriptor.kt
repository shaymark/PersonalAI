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