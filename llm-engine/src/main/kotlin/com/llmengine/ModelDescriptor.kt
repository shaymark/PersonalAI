package com.llmengine

/**
 * Describes a GGUF model that can be downloaded from HuggingFace and loaded on-device.
 *
 * @param id              Unique identifier used for file naming and comparison.
 * @param displayName     Human-readable name shown in UIs.
 * @param huggingFaceRepo HuggingFace repository in the form "owner/repo-name".
 * @param fileName        Exact file name of the GGUF file inside the repo.
 * @param sizeBytes       Approximate size used for progress display when Content-Length is absent.
 */
data class ModelDescriptor(
    val id: String,
    val displayName: String,
    val huggingFaceRepo: String,
    val fileName: String,
    val sizeBytes: Long
)

/** Pre-defined model descriptors. Consumers can also create their own [ModelDescriptor]s. */
object Models {

    /** Qwen 2.5 1.5B Instruct — Q4_K_M — ~986 MB. Good for low-RAM devices. */
    val QWEN2_5_1_5B_Q4 = ModelDescriptor(
        id = "qwen2.5-1.5b-q4_k_m",
        displayName = "Qwen 2.5 1.5B (Q4_K_M, ~1 GB)",
        huggingFaceRepo = "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
        fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        sizeBytes = 986_000_000L
    )

    /** Qwen 2.5 3B Instruct — Q4_K_M — ~2 GB. Better quality, needs ≥4 GB RAM. */
    val QWEN2_5_3B_Q4 = ModelDescriptor(
        id = "qwen2.5-3b-q4_k_m",
        displayName = "Qwen 2.5 3B (Q4_K_M, ~2 GB)",
        huggingFaceRepo = "Qwen/Qwen2.5-3B-Instruct-GGUF",
        fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
        sizeBytes = 2_000_000_000L
    )

    /** Llama 3.2 1B Instruct — Q4_K_M — ~770 MB. Smallest footprint option. */
    val LLAMA3_2_1B_Q4 = ModelDescriptor(
        id = "llama3.2-1b-q4_k_m",
        displayName = "Llama 3.2 1B (Q4_K_M, ~770 MB)",
        huggingFaceRepo = "bartowski/Llama-3.2-1B-Instruct-GGUF",
        fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
        sizeBytes = 770_000_000L
    )

    /** Phi 3.5 Mini Instruct — Q4_K_M — ~2.2 GB. Strong reasoning in a compact model. */
    val PHI3_5_MINI_Q4 = ModelDescriptor(
        id = "phi3.5-mini-q4_k_m",
        displayName = "Phi 3.5 Mini (Q4_K_M, ~2.2 GB)",
        huggingFaceRepo = "bartowski/Phi-3.5-mini-instruct-GGUF",
        fileName = "Phi-3.5-mini-instruct-Q4_K_M.gguf",
        sizeBytes = 2_200_000_000L
    )

    /** All built-in model presets as a list, for use in UI enumerations. */
    val all: List<ModelDescriptor> get() = listOf(
        QWEN2_5_1_5B_Q4,
        QWEN2_5_3B_Q4,
        LLAMA3_2_1B_Q4,
        PHI3_5_MINI_Q4
    )
}
