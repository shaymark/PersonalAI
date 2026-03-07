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
        jstring jpath,
        jint    nCtx,
        jint    nThreads)
{
    llama_backend_init();
    llama_log_set(log_callback, nullptr);

    const char *path = env->GetStringUTFChars(jpath, nullptr);
    LOGi("Loading model: %s", path);

    llama_model_params mparams = llama_model_default_params();
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

    // ── Decode the prompt ─────────────────────────────────────────────────────
    for (int i = 0; i < static_cast<int>(tokens.size()); i++) {
        batch_add(batch, tokens[i], i, false);
    }
    if (batch.n_tokens > 0)
        batch.logits[batch.n_tokens - 1] = 1;  // need logits for last prompt token

    if (llama_decode(handle->ctx, batch) != 0) {
        LOGe("llama_decode() failed during prompt processing");
        llama_batch_free(batch);
        return;
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
