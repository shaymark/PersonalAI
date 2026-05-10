package com.personal.personalai.data.datasource.ai.usage

import org.json.JSONObject

/**
 * Token counts extracted from an OpenAI Responses API `usage` object.
 *
 * Responses API field names:
 *  - `usage.input_tokens`
 *  - `usage.output_tokens`
 *  - `usage.input_tokens_details.cached_tokens` (KV-cache hit count)
 */
data class TokenCost(
    val inputTokens: Int,
    val outputTokens: Int,
    val cachedInputTokens: Int,
) {
    val totalTokens: Int get() = inputTokens + outputTokens
}

/**
 * Per-model pricing rates (USD per 1M tokens). Cached input gets a discount
 * applied on top of the base input rate.
 *
 * Rates are intentionally embedded in code (not user-configurable) — they
 * change rarely and a wrong default is easier to fix in source than in DB.
 */
private data class ModelRate(
    val inputPerMillion: Double,
    val outputPerMillion: Double,
    val cachedInputPerMillion: Double,
)

private val OPENAI_RATES: Map<String, ModelRate> = mapOf(
    // gpt-4o pricing as of 2026: $2.50 / 1M input, $10.00 / 1M output, $1.25 / 1M cached.
    "gpt-4o" to ModelRate(2.50, 10.00, 1.25),
)

object OpenAiPricing {

    /** $0.006 per minute of audio. */
    private const val WHISPER_USD_PER_MINUTE = 0.006

    /**
     * Estimates USD cost for a chat-completion call.
     *
     * Formula: non-cached input tokens are billed at the full input rate,
     * cached input tokens at the discounted cached rate, output tokens at the
     * output rate. Returns 0.0 for unknown models so we never crash on a
     * model rename — the row is still saved with a $0 estimate.
     */
    fun estimateCostUsd(model: String, tokens: TokenCost): Double {
        val rate = OPENAI_RATES[model] ?: return 0.0
        val nonCachedInput = (tokens.inputTokens - tokens.cachedInputTokens).coerceAtLeast(0)
        return (nonCachedInput * rate.inputPerMillion +
                tokens.cachedInputTokens * rate.cachedInputPerMillion +
                tokens.outputTokens * rate.outputPerMillion) / 1_000_000.0
    }

    fun estimateWhisperCostUsd(durationSeconds: Double): Double =
        (durationSeconds / 60.0) * WHISPER_USD_PER_MINUTE

    /**
     * Parses the `usage` block from a raw Responses API response body.
     * Returns null when no `usage` object is present (e.g. some Ollama
     * responses don't include it).
     */
    fun parseUsageFromResponseBody(responseBody: String): TokenCost? {
        val root = runCatching { JSONObject(responseBody) }.getOrNull() ?: return null
        val usage = root.optJSONObject("usage") ?: return null
        val cached = usage.optJSONObject("input_tokens_details")?.optInt("cached_tokens", 0)
            ?: usage.optJSONObject("prompt_tokens_details")?.optInt("cached_tokens", 0)
            ?: 0
        // Responses API uses input_tokens / output_tokens; chat-completions style
        // uses prompt_tokens / completion_tokens. Fall through both.
        val input = usage.optInt("input_tokens", usage.optInt("prompt_tokens", 0))
        val output = usage.optInt("output_tokens", usage.optInt("completion_tokens", 0))
        if (input == 0 && output == 0 && cached == 0) return null
        return TokenCost(inputTokens = input, outputTokens = output, cachedInputTokens = cached)
    }
}
