/**
 * llama_jni.cpp — JNI bridge between Kotlin and llama.cpp (b5620 API)
 *
 * Key API notes for b5620:
 *   - llama_tokenize / llama_token_to_piece now take llama_vocab* (not llama_model*)
 *   - KV cache cleared via llama_memory_clear(llama_get_memory(ctx), true)
 *   - llama_vocab_is_eog replaces llama_token_is_eog
 *   - llama_model_free replaces llama_free_model (old name still compiles but is deprecated)
 */

#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "LlamaJNI"
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...)  __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ── Native handle stored as a jlong ──────────────────────────────────────────

struct LlamaHandle {
    llama_model*        model = nullptr;
    llama_context*      ctx   = nullptr;
    const llama_vocab*  vocab = nullptr;  // owned by model, do not free separately
};

// ── One-time backend initialisation ──────────────────────────────────────────

static void ensure_backend() {
    static bool initialized = false;
    if (!initialized) {
        llama_backend_init();
        initialized = true;
        LOGI("llama backend initialized");
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

static LlamaHandle* to_handle(jlong j) {
    return reinterpret_cast<LlamaHandle*>(static_cast<uintptr_t>(j));
}

static jlong from_handle(LlamaHandle* h) {
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(h));
}

// ═════════════════════════════════════════════════════════════════════════════
// JNI implementations
// ═════════════════════════════════════════════════════════════════════════════

extern "C" {

// ── nativeLoadModel ───────────────────────────────────────────────────────────

JNIEXPORT jlong JNICALL
Java_com_llmengine_LlamaJni_nativeLoadModel(
        JNIEnv* env,
        jobject /* thiz */,
        jstring j_path,
        jint    ctx_size,
        jint    n_threads,
        jint    n_gpu_layers)
{
    ensure_backend();

    const char* path = env->GetStringUTFChars(j_path, nullptr);
    LOGI("Loading model: %s  ctx=%d  threads=%d  gpu_layers=%d",
         path, ctx_size, n_threads, n_gpu_layers);

    // ── Load model ────────────────────────────────────────────────────────
    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = n_gpu_layers;

    llama_model* model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(j_path, path);

    if (!model) {
        LOGE("llama_load_model_from_file failed");
        return 0L;
    }

    // ── Create context ────────────────────────────────────────────────────
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx           = static_cast<uint32_t>(ctx_size);
    cparams.n_threads       = static_cast<uint32_t>(n_threads);
    cparams.n_threads_batch = static_cast<uint32_t>(n_threads) * 2;  // more parallelism during prompt decode
    cparams.flash_attn      = true;   // fused attention kernel — faster + less memory bandwidth

    llama_context* ctx = llama_init_from_model(model, cparams);
    if (!ctx) {
        LOGE("llama_new_context_with_model failed");
        llama_model_free(model);
        return 0L;
    }

    // ── Grab the vocab pointer (owned by model, no separate free needed) ──
    const llama_vocab* vocab = llama_model_get_vocab(model);
    if (!vocab) {
        LOGE("llama_model_get_vocab returned null");
        llama_free(ctx);
        llama_model_free(model);
        return 0L;
    }

    auto* h = new LlamaHandle{model, ctx, vocab};
    LOGI("Model loaded, handle=%p", h);
    return from_handle(h);
}

// ── nativeGenerate ────────────────────────────────────────────────────────────

JNIEXPORT jint JNICALL
Java_com_llmengine_LlamaJni_nativeGenerate(
        JNIEnv* env,
        jobject /* thiz */,
        jlong   j_handle,
        jstring j_prompt,
        jint    max_tokens,
        jfloat  temperature,
        jfloat  top_p,
        jint    seed,
        jobject callback)
{
    LlamaHandle* h = to_handle(j_handle);
    if (!h || !h->model || !h->ctx || !h->vocab) {
        LOGE("nativeGenerate: invalid handle");
        return -1;
    }

    // ── Resolve Kotlin callback method ────────────────────────────────────
    jclass    cb_class = env->GetObjectClass(callback);
    jmethodID on_token = env->GetMethodID(cb_class, "onToken", "(Ljava/lang/String;)V");
    if (!on_token) {
        LOGE("nativeGenerate: onToken method not found");
        return -1;
    }

    // ── Tokenise prompt ───────────────────────────────────────────────────
    const char* prompt_cstr = env->GetStringUTFChars(j_prompt, nullptr);
    std::string prompt(prompt_cstr);
    env->ReleaseStringUTFChars(j_prompt, prompt_cstr);

    const int n_ctx = static_cast<int>(llama_n_ctx(h->ctx));
    std::vector<llama_token> prompt_tokens(n_ctx);

    // b5620: llama_tokenize takes vocab* (not model*)
    int n_prompt = llama_tokenize(
            h->vocab,
            prompt.c_str(),
            static_cast<int32_t>(prompt.size()),
            prompt_tokens.data(),
            static_cast<int32_t>(prompt_tokens.size()),
            /*add_special=*/true,
            /*parse_special=*/true);

    if (n_prompt < 0) {
        LOGE("llama_tokenize failed: %d", n_prompt);
        return -1;
    }
    prompt_tokens.resize(static_cast<size_t>(n_prompt));
    LOGI("Prompt tokens: %d", n_prompt);

    // ── Clear KV/memory cache before generation ───────────────────────────
    // b5620: llama_memory_clear(mem, data) replaces llama_kv_cache_clear/llama_kv_self_clear
    llama_memory_t mem = llama_get_memory(h->ctx);
    llama_memory_clear(mem, /*data=*/true);

    // ── Decode prompt tokens ──────────────────────────────────────────────
    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), n_prompt);
    if (llama_decode(h->ctx, batch) != 0) {
        LOGE("llama_decode failed for prompt");
        return -1;
    }

    // ── Set up sampler chain: top-p → temp → seeded distribution ─────────
    llama_sampler_chain_params sp = llama_sampler_chain_default_params();
    llama_sampler* smpl = llama_sampler_chain_init(sp);
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(top_p, /*min_keep=*/1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));

    uint32_t rng_seed = (seed < 0)
            ? LLAMA_DEFAULT_SEED
            : static_cast<uint32_t>(seed);
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(rng_seed));

    // ── Auto-regressive generation loop ──────────────────────────────────
    int  n_generated = 0;
    char piece_buf[512];

    for (int i = 0; i < max_tokens; i++) {
        llama_token new_token = llama_sampler_sample(smpl, h->ctx, -1);
        llama_sampler_accept(smpl, new_token);

        // b5620: llama_vocab_is_eog replaces llama_token_is_eog
        if (llama_vocab_is_eog(h->vocab, new_token)) {
            LOGI("EOG at token %d", i);
            break;
        }

        // b5620: llama_token_to_piece takes vocab* (not model*)
        int n_chars = llama_token_to_piece(
                h->vocab, new_token,
                piece_buf, sizeof(piece_buf) - 1,
                /*lstrip=*/0,
                /*special=*/false);

        if (n_chars > 0) {
            piece_buf[n_chars] = '\0';
            jstring j_piece = env->NewStringUTF(piece_buf);
            env->CallVoidMethod(callback, on_token, j_piece);
            env->DeleteLocalRef(j_piece);

            if (env->ExceptionCheck()) {
                LOGW("Java exception in onToken — clearing and stopping");
                env->ExceptionClear();
                break;
            }
        }

        n_generated++;

        // Feed generated token back as the next input
        batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(h->ctx, batch) != 0) {
            LOGE("llama_decode failed at generated token %d", i);
            break;
        }
    }

    llama_sampler_free(smpl);
    LOGI("Generation complete: %d tokens", n_generated);
    return n_generated;
}

// ── nativeFree ────────────────────────────────────────────────────────────────

JNIEXPORT void JNICALL
Java_com_llmengine_LlamaJni_nativeFree(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jlong   j_handle)
{
    LlamaHandle* h = to_handle(j_handle);
    if (h) {
        // vocab is owned by model — do not free it separately
        if (h->ctx)   llama_free(h->ctx);
        if (h->model) llama_model_free(h->model);  // replaces deprecated llama_free_model
        delete h;
        LOGI("Native handle freed");
    }
}

} // extern "C"
