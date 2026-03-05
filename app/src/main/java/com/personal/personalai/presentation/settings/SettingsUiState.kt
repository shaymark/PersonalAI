package com.personal.personalai.presentation.settings

import com.llmengine.ModelDescriptor
import com.llmengine.Models

/** Which AI backend the user has selected. */
enum class AiProvider { OPENAI, LOCAL_LLM }

data class SettingsUiState(
    // ── OpenAI backend ────────────────────────────────────────────────────────
    val apiKey: String = "",
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,

    // ── Common ────────────────────────────────────────────────────────────────
    val showClearHistoryDialog: Boolean = false,
    val aiProvider: AiProvider = AiProvider.OPENAI,

    // ── Local LLM backend ─────────────────────────────────────────────────────
    /** HuggingFace access token — sent as `Authorization: Bearer` when downloading gated models. */
    val hfToken: String = "",
    val isHfTokenSaving: Boolean = false,
    val hfTokenSavedSuccessfully: Boolean = false,
    /** All model presets available for download (sourced from [Models.all]). */
    val availableModels: List<ModelDescriptor> = Models.all,
    /** IDs of models whose GGUF files are already present on disk. */
    val downloadedModelIds: Set<String> = emptySet(),
    /** ID of the model the user has selected for inference. */
    val selectedModelId: String = "",
    /** Download progress per model: modelId → fraction in 0..1 */
    val downloadProgress: Map<String, Float> = emptyMap(),
    /** Non-null while a model is being downloaded; contains the model's ID. */
    val downloadingModelId: String? = null,
    /** Set when a download fails; shown in an error dialog. */
    val downloadError: String? = null
)
