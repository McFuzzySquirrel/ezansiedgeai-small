package com.ezansi.app.di

import android.content.Context
import com.ezansi.app.core.ai.ExplanationEngine
import com.ezansi.app.core.ai.ExplanationEngineImpl
import com.ezansi.app.core.ai.embedding.EmbeddingModel
import com.ezansi.app.core.ai.embedding.OnnxEmbeddingModel
import com.ezansi.app.core.ai.inference.LlmEngine
import com.ezansi.app.core.ai.inference.LlamaCppEngine
import com.ezansi.app.core.ai.prompt.PromptBuilder
import com.ezansi.app.core.ai.retrieval.ContentRetriever
import com.ezansi.app.core.ai.retrieval.CosineSimilarityRetriever
import com.ezansi.app.core.common.DefaultDispatcherProvider
import com.ezansi.app.core.common.DispatcherProvider
import com.ezansi.app.core.common.StorageManager
import com.ezansi.app.core.data.ContentPackRepository
import com.ezansi.app.core.data.PreferenceRepository
import com.ezansi.app.core.data.ProfileRepository
import com.ezansi.app.core.data.chat.ChatHistoryRepository
import com.ezansi.app.core.data.chat.ChatHistoryRepositoryImpl
import com.ezansi.app.core.data.chat.ChatHistoryStore
import com.ezansi.app.core.data.contentpack.ContentPackRepositoryImpl
import com.ezansi.app.core.data.contentpack.PackManager
import com.ezansi.app.core.data.contentpack.PackVerifier
import com.ezansi.app.core.data.encryption.ProfileEncryption
import com.ezansi.app.core.data.preference.PreferenceRepositoryImpl
import com.ezansi.app.core.data.preference.PreferenceStore
import com.ezansi.app.core.data.profile.ProfileRepositoryImpl
import com.ezansi.app.core.data.profile.ProfileStore

/**
 * Manual dependency injection container — no Hilt, no Dagger, no Koin.
 *
 * Why manual DI:
 * - Zero reflection overhead (matters on 2 GB RAM devices)
 * - Zero code-generation build time cost
 * - No annotation processor dependencies to audit for licenses
 * - Explicit wiring is easy to debug and understand
 * - This app has ~15 services total — a DI framework is overkill
 *
 * All dependencies are lazily initialised so app startup stays under
 * the 3-second cold-start budget. Services are created on first access,
 * not at Application.onCreate() time.
 *
 * Usage from an Activity or Composable:
 * ```kotlin
 * val container = (application as EzansiApplication).container
 * val engine = container.explanationEngine
 * ```
 *
 * Thread safety: lazy {} is thread-safe by default in Kotlin
 * (uses LazyThreadSafetyMode.SYNCHRONIZED).
 */
class AppContainer(private val context: Context) {

    // ── Core infrastructure ─────────────────────────────────────────

    /** Coroutine dispatchers — inject this instead of using Dispatchers.IO directly. */
    val dispatcherProvider: DispatcherProvider by lazy {
        DefaultDispatcherProvider()
    }

    /** Storage path resolution for models, packs, and profiles. */
    val storageManager: StorageManager by lazy {
        StorageManager(context)
    }

    // ── Data layer ──────────────────────────────────────────────────
    // These will be populated as agents implement their modules.

    /** Verifies content pack integrity via SHA-256 checksums. */
    val packVerifier: PackVerifier by lazy {
        PackVerifier()
    }

    /** Manages installed content packs on-device (install, uninstall, open). */
    val packManager: PackManager by lazy {
        PackManager(storageManager, packVerifier)
    }

    /** Repository for managing and querying content packs. */
    val contentPackRepository: ContentPackRepository by lazy {
        ContentPackRepositoryImpl(packManager, packVerifier, dispatcherProvider)
    }

    // ── Encryption ──────────────────────────────────────────────────

    /** AES-256-GCM encryption for all learner data — shared across stores. */
    private val profileEncryption: ProfileEncryption by lazy {
        ProfileEncryption()
    }

    // ── Learner data stores (internal — not exposed to feature modules) ──

    private val profileStore: ProfileStore by lazy {
        ProfileStore(
            profilesDir = storageManager.getProfilesDir(),
            encryption = profileEncryption,
        )
    }

    private val preferenceStore: PreferenceStore by lazy {
        PreferenceStore(
            profilesDir = storageManager.getProfilesDir(),
            encryption = profileEncryption,
        )
    }

    private val chatHistoryStore: ChatHistoryStore by lazy {
        ChatHistoryStore(
            profilesDir = storageManager.getProfilesDir(),
            encryption = profileEncryption,
        )
    }

    // ── Learner data repositories ───────────────────────────────────

    /** Learner profile management — encrypted, multi-profile, crash-safe. */
    val profileRepository: ProfileRepository by lazy {
        ProfileRepositoryImpl(
            profileStore = profileStore,
            preferenceStore = preferenceStore,
            chatHistoryStore = chatHistoryStore,
            dispatcherProvider = dispatcherProvider,
        )
    }

    /** Per-profile preference persistence — encrypted, immediate writes. */
    val preferenceRepository: PreferenceRepository by lazy {
        PreferenceRepositoryImpl(
            preferenceStore = preferenceStore,
            dispatcherProvider = dispatcherProvider,
        )
    }

    /** Chat history persistence — encrypted SQLite with WAL mode. */
    val chatHistoryRepository: ChatHistoryRepository by lazy {
        ChatHistoryRepositoryImpl(
            chatHistoryStore = chatHistoryStore,
            dispatcherProvider = dispatcherProvider,
        )
    }

    // ── AI layer ────────────────────────────────────────────────────

    /**
     * On-device embedding model for converting questions to vectors.
     *
     * Default: OnnxEmbeddingModel.
     * Falls back to deterministic embeddings if ONNX runtime is unavailable.
     */
    private val embeddingModel: EmbeddingModel by lazy {
        OnnxEmbeddingModel()
    }

    /**
     * Content retriever using cosine similarity over pre-computed embeddings.
     *
     * Uses pure-Kotlin brute-force cosine similarity. For packs with ~200
     * chunks this is < 1 ms — FAISS would only help for 1000+ chunks.
     */
    private val contentRetriever: ContentRetriever by lazy {
        CosineSimilarityRetriever(packManager)
    }

    /** Prompt template builder for constructing grounded LLM prompts. */
    private val promptBuilder: PromptBuilder by lazy {
        PromptBuilder()
    }

    /**
     * On-device LLM engine for generating explanations.
     *
     * Default: LlamaCppEngine.
     * If llama native bindings are unavailable, returns an explicit
     * runtime-unavailable response instead of silently using mocks.
     */
    private val llmEngine: LlmEngine by lazy {
        LlamaCppEngine()
    }

    /** ExplanationEngine — sole public API for the AI layer (§7.3). */
    val explanationEngine: ExplanationEngine by lazy {
        ExplanationEngineImpl(
            embeddingModel = embeddingModel,
            contentRetriever = contentRetriever,
            promptBuilder = promptBuilder,
            llmEngine = llmEngine,
            contentPackRepository = contentPackRepository,
            preferenceRepository = preferenceRepository,
            storageManager = storageManager,
            dispatcherProvider = dispatcherProvider,
        )
    }
}
