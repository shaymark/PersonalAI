#include <android/log.h>
#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include <cstring>
#include "llama.h"

// NOTE: we intentionally do NOT include common.h — it requires the 'common'
// library which is only compiled when LLAMA_BUILD_EXAMPLES is ON.  All the
// utilities we need are re-implemented below using the public llama.h API.

#define TAG "llama-bridge"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGd(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

// ── Helpers (llama.h only) ────────────────────────────────────────────────────

static bool is_valid_utf8(const char *s) {
    if (!s) return true;
    const auto *b = reinterpret_cast<const unsigned char *>(s);
    while (*b) {
        int n;
        if      ((*b & 0x80) == 0x00) n = 1;
        else if ((*b & 0xE0) == 0xC0) n = 2;
        else if ((*b & 0xF0) == 0xE0) n = 3;
        else if ((*b & 0xF8) == 0xF0) n = 4;
        else return false;
        for (int i = 1; i < n; i++) {
            if ((*(++b) & 0xC0) != 0x80) return false;
        }
        b++;
    }
    return true;
}

/** Tokenize text using llama.h API (replaces common_tokenize). */
static std::vector<llama_token> tokenize_text(
        const llama_vocab *vocab,
        const std::string &text,
        bool add_bos,
        bool parse_special)
{
    // First call: pass null buffer to get required size (returns negative count)
    int n = -llama_tokenize(vocab,
                            text.c_str(), static_cast<int32_t>(text.size()),
                            nullptr, 0,
                            add_bos, parse_special);
    if (n <= 0) return {};
    std::vector<llama_token> tokens(n);
    llama_tokenize(vocab,
                   text.c_str(), static_cast<int32_t>(text.size()),
                   tokens.data(), n,
                   add_bos, parse_special);
    return tokens;
}

/** Convert a token id to its string piece (replaces common_token_to_piece). */
static std::string token_to_piece(const llama_vocab *vocab, llama_token token, bool special) {
    char buf[256];
    int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, special);
    if (n < 0) {
        // Buffer too small — allocate the exact size
        std::string s(static_cast<size_t>(-n), '\0');
        llama_token_to_piece(vocab, token, s.data(), static_cast<int32_t>(s.size()), 0, special);
        return s;
    }
    return {buf, static_cast<size_t>(n)};
}

/** Add one token to a batch (replaces common_batch_add). */
static void batch_add(llama_batch &batch, llama_token id, llama_pos pos, bool logit) {
    batch.token   [batch.n_tokens] = id;
    batch.pos     [batch.n_tokens] = pos;
    batch.n_seq_id[batch.n_tokens] = 1;
    batch.seq_id  [batch.n_tokens][0] = 0;
    batch.logits  [batch.n_tokens] = logit ? 1 : 0;
    batch.n_tokens++;
}

// ── Android log callback ──────────────────────────────────────────────────────

static void log_callback(ggml_log_level level, const char *fmt, void *) {
    switch (level) {
        case GGML_LOG_LEVEL_ERROR: __android_log_print(ANDROID_LOG_ERROR,   TAG, "%s", fmt); break;
        case GGML_LOG_LEVEL_WARN:  __android_log_print(ANDROID_LOG_WARN,    TAG, "%s", fmt); break;
        case GGML_LOG_LEVEL_INFO:  __android_log_print(ANDROID_LOG_INFO,    TAG, "%s", fmt); break;
        default:                   __android_log_print(ANDROID_LOG_DEFAULT, TAG, "%s", fmt); break;
    }
}

// ── Native handle ─────────────────────────────────────────────────────────────

struct LlamaHandle {
    llama_model   *model = nullptr;
    llama_context *ctx   = nullptr;
};

// ── nativeLoad ────────────────────────────────────────────────────────────────

extern "C"
JNIEXPORT jlong JNICALL
Java_com_llmengine_LlamaCppSession_nativeLoad(
        JNIEnv *env, jobject,
        jstring  jpath,
        jint     nCtx,
        jint     nThreads,
        jboolean useGpu)
{
    llama_backend_init();
    llama_log_set(log_callback, nullptr);

    const char *path = env->GetStringUTFChars(jpath, nullptr);
    LOGi("Loading model: %s (gpu=%s)", path, useGpu ? "on" : "off");

    llama_model_params mparams = llama_model_default_params();
    // Qwen3.5 4B is a hybrid SSM+Transformer model (full_attention_interval=4):
    // only 8/32 layers are full-attention; the other 24 are Gated Delta Net (SSM) layers.
    // llama.cpp b8233 Vulkan backend does NOT support Gated Delta Net — those 24 layers
    // always fall back to CPU regardless of n_gpu_layers.  This creates 23 GPU↔CPU graph
    // splits per single-token decode: each split is a Vulkan command-buffer submission plus
    // a CPU/GPU synchronisation point.  Over ~450 tokens (56 s) the cumulative ~10 000
    // Vulkan submissions drive the Adreno 750 into a bad state (kgsl-timeline fences stop
    // signalling), causing "Failed to link shaders" → null pipeline → SIGSEGV fault addr 0x8
    // inside ggml_vk_mul_mat.  CPU-only via ARM NEON is stable and gives similar throughput
    // because the GPU was only accelerating the 8 attention layers anyway.
    // Flip useGpu=true only when using a pure-transformer model on a driver-stable device.
    mparams.n_gpu_layers = (useGpu == JNI_TRUE) ? 99 : 0;
    llama_model *model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(jpath, path);

    if (!model) {
        LOGe("llama_model_load_from_file() failed");
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Failed to load GGUF model");
        return 0L;
    }

    int threads = (nThreads > 0)
        ? nThreads
        : std::max(1, (int)std::thread::hardware_concurrency() - 2);

    llama_context_params cparams  = llama_context_default_params();
    cparams.n_ctx           = static_cast<uint32_t>(nCtx > 0 ? nCtx : 2048);
    cparams.n_threads       = threads;
    cparams.n_threads_batch = threads;

    llama_context *ctx = llama_new_context_with_model(model, cparams);
    if (!ctx) {
        LOGe("llama_new_context_with_model() failed");
        llama_model_free(model);
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Failed to create llama context");
        return 0L;
    }

    LOGi("Model loaded — n_ctx=%u, threads=%d", cparams.n_ctx, threads);

    auto *handle = new LlamaHandle{model, ctx};
    return reinterpret_cast<jlong>(handle);
}

// ── nativeGenerate ────────────────────────────────────────────────────────────

extern "C"
JNIEXPORT void JNICALL
Java_com_llmengine_LlamaCppSession_nativeGenerate(
        JNIEnv *env, jobject thiz,
        jlong   jhandle,
        jstring jprompt,
        jint    maxTokens,
        jfloat  temperature,
        jfloat  topP)
{
    auto *handle = reinterpret_cast<LlamaHandle *>(jhandle);
    if (!handle || !handle->ctx || !handle->model) {
        LOGe("nativeGenerate: invalid handle");
        return;
    }

    const llama_vocab *vocab = llama_model_get_vocab(handle->model);

    // ── Tokenize prompt ───────────────────────────────────────────────────────
    const char *prompt_cstr = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(prompt_cstr);
    env->ReleaseStringUTFChars(jprompt, prompt_cstr);
    LOGd("nativeGenerate: prompt_len=%zu, maxTokens=%d", prompt.size(), maxTokens);

    // parse_special=true so ChatML tokens (<|im_start|> etc.) are handled
    auto tokens = tokenize_text(vocab, prompt, /*add_bos=*/true, /*parse_special=*/true);
    if (tokens.empty()) {
        LOGe("Tokenization produced no tokens");
        return;
    }

    const int n_ctx    = static_cast<int>(llama_n_ctx(handle->ctx));
    const int n_needed = static_cast<int>(tokens.size()) + maxTokens;
    if (n_needed > n_ctx) {
        LOGi("Truncating prompt: need %d tokens but n_ctx=%d", n_needed, n_ctx);
        int keep = n_ctx - maxTokens - 4;
        if (keep < 1) keep = 1;
        int drop = static_cast<int>(tokens.size()) - keep;
        if (drop > 0) tokens.erase(tokens.begin(), tokens.begin() + drop);
    }

    // ── Clear KV cache ────────────────────────────────────────────────────────
    llama_memory_clear(llama_get_memory(handle->ctx), true);

    // ── Allocate batch ────────────────────────────────────────────────────────
    // Using the same manual approach as the official llama.android example.
    const int max_batch = static_cast<int>(tokens.size()) + maxTokens + 8;
    llama_batch batch = llama_batch_init(max_batch, 0, 1);

    // ── Decode the prompt (chunked) ───────────────────────────────────────────
    // Adreno 750 (Snapdragon 8 Gen 3) crashes with VK_ERROR_DEVICE_LOST when a
    // Vulkan command buffer encodes more than ~32 operations at once (llama.cpp
    // issue #8743).  Splitting the prompt into chunks of ≤ 32 tokens keeps each
    // vkQueueSubmit within the driver's limit while still using the GPU.
    const int ADRENO_MAX_BATCH = 32;
    const int n_prompt = static_cast<int>(tokens.size());

    for (int chunk_start = 0; chunk_start < n_prompt; chunk_start += ADRENO_MAX_BATCH) {
        batch.n_tokens = 0;
        const int chunk_end     = std::min(chunk_start + ADRENO_MAX_BATCH, n_prompt);
        const bool is_last      = (chunk_end == n_prompt);

        for (int i = chunk_start; i < chunk_end; i++) {
            // Request logits only for the very last prompt token (needed for sampling)
            batch_add(batch, tokens[i], i, is_last && (i == chunk_end - 1));
        }

        if (llama_decode(handle->ctx, batch) != 0) {
            LOGe("llama_decode() failed during prompt chunk %d/%d", chunk_start, n_prompt);
            llama_batch_free(batch);
            return;
        }
    }

    // ── Build sampler: temperature + top-p ───────────────────────────────────
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    llama_sampler *smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    // ── JNI callback method ───────────────────────────────────────────────────
    jclass    clazz    = env->GetObjectClass(thiz);
    jmethodID onToken  = env->GetMethodID(clazz, "onToken", "(Ljava/lang/String;)V");

    // ── Generation loop ───────────────────────────────────────────────────────
    int n_cur = static_cast<int>(tokens.size());
    std::string pending;   // accumulates bytes for incomplete UTF-8 sequences

    for (int i = 0; i < maxTokens; i++) {
        llama_token new_token = llama_sampler_sample(smpl, handle->ctx, -1);

        if (llama_vocab_is_eog(vocab, new_token)) {
            LOGd("EOS/EOG at step %d", i);
            break;
        }

        pending += token_to_piece(vocab, new_token, /*special=*/true);

        if (is_valid_utf8(pending.c_str())) {
            jstring jt = env->NewStringUTF(pending.c_str());
            env->CallVoidMethod(thiz, onToken, jt);
            env->DeleteLocalRef(jt);
            pending.clear();
        }

        // Advance: single-token batch for next step
        batch.n_tokens = 0;
        batch_add(batch, new_token, n_cur++, true);

        if (llama_decode(handle->ctx, batch) != 0) {
            LOGe("llama_decode() failed at step %d", i);
            break;
        }
    }

    // Flush any residual bytes
    if (!pending.empty()) {
        jstring jt = env->NewStringUTF(pending.c_str());
        env->CallVoidMethod(thiz, onToken, jt);
        env->DeleteLocalRef(jt);
    }

    llama_sampler_free(smpl);
    llama_batch_free(batch);
    LOGd("nativeGenerate: done (n_cur=%d)", n_cur);
}

// ── nativeFree ────────────────────────────────────────────────────────────────

extern "C"
JNIEXPORT void JNICALL
Java_com_llmengine_LlamaCppSession_nativeFree(JNIEnv *, jobject, jlong jhandle)
{
    auto *handle = reinterpret_cast<LlamaHandle *>(jhandle);
    if (!handle) return;
    if (handle->ctx)   llama_free(handle->ctx);
    if (handle->model) llama_model_free(handle->model);
    delete handle;
    llama_backend_free();
    LOGi("nativeFree: done");
}
