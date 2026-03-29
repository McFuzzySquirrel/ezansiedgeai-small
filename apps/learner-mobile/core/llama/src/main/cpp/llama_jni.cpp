/**
 * JNI bridge for llama.cpp — eZansiEdgeAI on-device LLM inference.
 *
 * Provides native functions for the Kotlin LlamaAndroid class to:
 * - Load a GGUF model with mmap
 * - Create an inference context
 * - Tokenize prompts
 * - Run decode (prompt eval + token generation)
 * - Sample the next token (temperature-based)
 * - Detokenize tokens back to text
 * - Check for end-of-generation tokens
 * - Free resources
 */

#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

#include "llama.h"
#include "common.h"

#define TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// JNI package: com.ezansi.app.core.llama.LlamaAndroid

static bool backend_initialized = false;

static void ensure_backend_init() {
    if (!backend_initialized) {
        llama_backend_init();
        backend_initialized = true;
        LOGI("llama.cpp backend initialized");
    }
}

extern "C" {

// ── Model Loading ────────────────────────────────────────────────

JNIEXPORT jlong JNICALL
Java_com_ezansi_app_core_llama_LlamaAndroid_nativeLoadModel(
        JNIEnv *env, jobject /* thiz */,
        jstring jpath, jint context_size, jboolean use_mmap) {

    ensure_backend_init();

    const char *path = env->GetStringUTFChars(jpath, nullptr);
    if (!path) {
        LOGE("Failed to get model path string");
        return 0;
    }

    LOGI("Loading GGUF model from: %s (ctx=%d, mmap=%d)", path, context_size, use_mmap);

    llama_model_params params = llama_model_default_params();
    params.use_mmap = use_mmap;

    llama_model *model = llama_model_load_from_file(path, params);
    env->ReleaseStringUTFChars(jpath, path);

    if (!model) {
        LOGE("Failed to load GGUF model");
        return 0;
    }

    LOGI("Model loaded successfully");
    return reinterpret_cast<jlong>(model);
}

// ── Context Creation ─────────────────────────────────────────────

JNIEXPORT jlong JNICALL
Java_com_ezansi_app_core_llama_LlamaAndroid_nativeCreateContext(
        JNIEnv * /* env */, jobject /* thiz */,
        jlong jmodel, jint context_size, jint n_threads) {

    auto *model = reinterpret_cast<llama_model *>(jmodel);
    if (!model) {
        LOGE("Cannot create context: model is null");
        return 0;
    }

    llama_context_params params = llama_context_default_params();
    params.n_ctx = static_cast<uint32_t>(context_size);
    params.n_threads = n_threads;
    params.n_threads_batch = n_threads;

    LOGI("Creating context (n_ctx=%d, n_threads=%d)", context_size, n_threads);

    llama_context *ctx = llama_init_from_model(model, params);
    if (!ctx) {
        LOGE("Failed to create llama context");
        return 0;
    }

    LOGI("Context created successfully");
    return reinterpret_cast<jlong>(ctx);
}

// ── Tokenization ─────────────────────────────────────────────────

JNIEXPORT jintArray JNICALL
Java_com_ezansi_app_core_llama_LlamaAndroid_nativeTokenize(
        JNIEnv *env, jobject /* thiz */,
        jlong jmodel, jstring jtext, jboolean add_special) {

    auto *model = reinterpret_cast<llama_model *>(jmodel);
    if (!model) {
        LOGE("Cannot tokenize: model is null");
        return nullptr;
    }

    const char *text = env->GetStringUTFChars(jtext, nullptr);
    if (!text) {
        LOGE("Failed to get text string for tokenization");
        return nullptr;
    }

    int text_len = static_cast<int>(strlen(text));
    const llama_vocab *vocab = llama_model_get_vocab(model);

    // First call to get required token count
    int n_tokens = llama_tokenize(vocab, text, text_len, nullptr, 0, add_special, true);
    if (n_tokens < 0) {
        n_tokens = -n_tokens;
    }

    std::vector<llama_token> tokens(n_tokens);
    int actual = llama_tokenize(vocab, text, text_len, tokens.data(), n_tokens, add_special, true);

    env->ReleaseStringUTFChars(jtext, text);

    if (actual < 0) {
        LOGE("Tokenization failed");
        return nullptr;
    }

    jintArray result = env->NewIntArray(actual);
    env->SetIntArrayRegion(result, 0, actual, reinterpret_cast<jint *>(tokens.data()));

    LOGI("Tokenized %d tokens", actual);
    return result;
}

// ── Batch Decode (prompt eval) ───────────────────────────────────

JNIEXPORT jint JNICALL
Java_com_ezansi_app_core_llama_LlamaAndroid_nativeDecode(
        JNIEnv *env, jobject /* thiz */,
        jlong jctx, jintArray jtokens) {

    auto *ctx = reinterpret_cast<llama_context *>(jctx);
    if (!ctx) {
        LOGE("Cannot decode: context is null");
        return -1;
    }

    int n_tokens = env->GetArrayLength(jtokens);
    std::vector<llama_token> tokens(n_tokens);
    env->GetIntArrayRegion(jtokens, 0, n_tokens, reinterpret_cast<jint *>(tokens.data()));

    llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);
    int result = llama_decode(ctx, batch);

    if (result != 0) {
        LOGE("llama_decode failed: %d", result);
    }

    return result;
}

// ── Single-token decode (for generation loop) ────────────────────

JNIEXPORT jint JNICALL
Java_com_ezansi_app_core_llama_LlamaAndroid_nativeDecodeSingle(
        JNIEnv * /* env */, jobject /* thiz */,
        jlong jctx, jint token_id) {

    auto *ctx = reinterpret_cast<llama_context *>(jctx);
    if (!ctx) {
        LOGE("Cannot decode single: context is null");
        return -1;
    }

    llama_token token = static_cast<llama_token>(token_id);
    llama_batch batch = llama_batch_get_one(&token, 1);
    int result = llama_decode(ctx, batch);

    if (result != 0) {
        LOGE("llama_decode (single) failed: %d", result);
    }

    return result;
}

// ── Sampling ─────────────────────────────────────────────────────

JNIEXPORT jlong JNICALL
Java_com_ezansi_app_core_llama_LlamaAndroid_nativeCreateSampler(
        JNIEnv * /* env */, jobject /* thiz */,
        jfloat temperature, jfloat top_p, jint top_k) {

    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler *smpl = llama_sampler_chain_init(sparams);

    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(42));

    LOGI("Sampler created (temp=%.2f, top_p=%.2f, top_k=%d)", temperature, top_p, top_k);
    return reinterpret_cast<jlong>(smpl);
}

JNIEXPORT jint JNICALL
Java_com_ezansi_app_core_llama_LlamaAndroid_nativeSample(
        JNIEnv * /* env */, jobject /* thiz */,
        jlong jsampler, jlong jctx) {

    auto *smpl = reinterpret_cast<llama_sampler *>(jsampler);
    auto *ctx = reinterpret_cast<llama_context *>(jctx);

    // Sample from the last token position (-1 = last logit)
    llama_token token = llama_sampler_sample(smpl, ctx, -1);
    return static_cast<jint>(token);
}

JNIEXPORT void JNICALL
Java_com_ezansi_app_core_llama_LlamaAndroid_nativeFreeSampler(
        JNIEnv * /* env */, jobject /* thiz */,
        jlong jsampler) {

    auto *smpl = reinterpret_cast<llama_sampler *>(jsampler);
    if (smpl) {
        llama_sampler_free(smpl);
    }
}

// ── Detokenization ───────────────────────────────────────────────

JNIEXPORT jstring JNICALL
Java_com_ezansi_app_core_llama_LlamaAndroid_nativeDetokenize(
        JNIEnv *env, jobject /* thiz */,
        jlong jmodel, jint token_id) {

    auto *model = reinterpret_cast<llama_model *>(jmodel);
    if (!model) {
        LOGE("Cannot detokenize: model is null");
        return env->NewStringUTF("");
    }

    const llama_vocab *vocab = llama_model_get_vocab(model);
    char buf[256];
    int n = llama_token_to_piece(vocab, static_cast<llama_token>(token_id),
                                  buf, sizeof(buf) - 1, 0, false);
    if (n < 0) {
        LOGW("Detokenize returned negative length for token %d", token_id);
        return env->NewStringUTF("");
    }

    buf[n] = '\0';
    return env->NewStringUTF(buf);
}

// ── End-of-generation check ──────────────────────────────────────

JNIEXPORT jboolean JNICALL
Java_com_ezansi_app_core_llama_LlamaAndroid_nativeIsEog(
        JNIEnv * /* env */, jobject /* thiz */,
        jlong jmodel, jint token_id) {

    auto *model = reinterpret_cast<llama_model *>(jmodel);
    if (!model) return JNI_TRUE;

    const llama_vocab *vocab = llama_model_get_vocab(model);
    return llama_vocab_is_eog(vocab, static_cast<llama_token>(token_id)) ? JNI_TRUE : JNI_FALSE;
}

// ── Cleanup ──────────────────────────────────────────────────────

JNIEXPORT void JNICALL
Java_com_ezansi_app_core_llama_LlamaAndroid_nativeFreeContext(
        JNIEnv * /* env */, jobject /* thiz */,
        jlong jctx) {

    auto *ctx = reinterpret_cast<llama_context *>(jctx);
    if (ctx) {
        llama_free(ctx);
        LOGI("Context freed");
    }
}

JNIEXPORT void JNICALL
Java_com_ezansi_app_core_llama_LlamaAndroid_nativeFreeModel(
        JNIEnv * /* env */, jobject /* thiz */,
        jlong jmodel) {

    auto *model = reinterpret_cast<llama_model *>(jmodel);
    if (model) {
        llama_model_free(model);
        LOGI("Model freed");
    }
}

JNIEXPORT void JNICALL
Java_com_ezansi_app_core_llama_LlamaAndroid_nativeBackendFree(
        JNIEnv * /* env */, jobject /* thiz */) {

    if (backend_initialized) {
        llama_backend_free();
        backend_initialized = false;
        LOGI("llama.cpp backend freed");
    }
}

} // extern "C"
