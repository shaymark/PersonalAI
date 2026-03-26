package com.personal.personalai.presentation.settings

/** Which AI backend the user has selected. */
enum class AiProvider { OPENAI, OLLAMA }

data class SettingsUiState(
    // ── OpenAI backend ────────────────────────────────────────────────────────
    val apiKey: String = "",
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,

    // ── Common ────────────────────────────────────────────────────────────────
    val showClearHistoryDialog: Boolean = false,
    val aiProvider: AiProvider = AiProvider.OPENAI,

    // ── Ollama Dev Mode backend ───────────────────────────────────────────────
    /** Base URL of the Ollama server, e.g. "http://10.100.102.75:11434". */
    val ollamaUrl: String = "",
    /** Ollama model tag to use for inference, e.g. "qwen3.5:4b". */
    val ollamaModel: String = "",
    val isOllamaSaving: Boolean = false,
    val ollamaSavedSuccessfully: Boolean = false,

    // ── Web Search (Serper.dev) ───────────────────────────────────────────────
    /** Serper.dev API key — used by WebSearchTool for real Google results. */
    val serperApiKey: String = "",
    val isSerperSaving: Boolean = false,
    val serperSavedSuccessfully: Boolean = false
)
