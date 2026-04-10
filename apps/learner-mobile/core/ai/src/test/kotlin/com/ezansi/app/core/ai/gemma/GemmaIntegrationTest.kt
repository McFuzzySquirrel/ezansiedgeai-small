package com.ezansi.app.core.ai.gemma

import com.ezansi.app.core.ai.embedding.EmbeddingRuntimeMode
import com.ezansi.app.core.ai.inference.LlmRuntimeMode
import com.ezansi.app.core.ai.prompt.ChatFormat
import com.ezansi.app.core.ai.prompt.PromptBuilder
import com.ezansi.app.core.ai.retrieval.RetrievalResult
import com.ezansi.app.core.data.ContentChunk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Integration tests verifying the unified Gemma 4 engine stack works
 * end-to-end in stub mode (no real MediaPipe, no Android Context).
 *
 * These tests validate the wiring pattern used by [AppContainer][com.ezansi.app.di.AppContainer]:
 *
 * ```
 * GemmaModelProvider (shared)
 *   ├── GemmaLiteRtEngine   (LLM generation)
 *   └── GemmaEmbeddingModel (embedding)
 * ```
 *
 * Both engines share a single [GemmaModelProvider] so the model is loaded once
 * into memory, keeping RAM ≤ 1,200 MB on constrained devices. The tests verify
 * that lifecycle delegation, state sharing, and runtime mode mapping all work
 * correctly across the two engines.
 *
 * All tests run offline in JVM stub mode (`GemmaModelProvider(context = null)`).
 * Uses `testOptions.unitTests.isReturnDefaultValues = true` from build.gradle.kts
 * to handle `android.util.Log` calls without Robolectric.
 */
@DisplayName("Gemma 4 Integration")
class GemmaIntegrationTest {

    private lateinit var provider: GemmaModelProvider
    private lateinit var config: GemmaModelConfig
    private lateinit var llmEngine: GemmaLiteRtEngine
    private lateinit var embeddingModel: GemmaEmbeddingModel

    @TempDir
    lateinit var tempDir: Path

    /** Creates a fake model file for lifecycle testing. */
    private fun createFakeModelFile(name: String = "gemma4-1b.task"): File {
        val file = tempDir.resolve(name).toFile()
        file.writeText("fake-gemma4-model-data")
        return file
    }

    /** Creates a [RetrievalResult] for prompt-building tests. */
    private fun createChunk(
        chunkId: String = "ch-1",
        title: String = "Test Title",
        content: String = "Test content about Grade 6 maths.",
        topicPath: String = "term1.fractions.addition",
        score: Float = 0.9f,
    ): RetrievalResult {
        return RetrievalResult(
            chunkId = chunkId,
            score = score,
            chunk = ContentChunk(
                chunkId = chunkId,
                packId = "test-pack",
                title = title,
                topicPath = topicPath,
                content = content,
            ),
        )
    }

    @BeforeEach
    fun setUp() {
        provider = GemmaModelProvider(context = null)
        config = GemmaModelConfig(modelPath = "placeholder")
        llmEngine = GemmaLiteRtEngine(
            modelProvider = provider,
            config = config,
        )
        embeddingModel = GemmaEmbeddingModel(
            modelProvider = provider,
            config = config,
        )
    }

    // ── Shared provider — both engines see same load state ──────────

    @Nested
    @DisplayName("Shared provider lifecycle")
    inner class SharedProviderLifecycleTests {

        @Test
        @DisplayName("loading via embedding makes LLM engine report isLoaded")
        fun loadViaEmbeddingMakesLlmLoaded() = runTest {
            val modelFile = createFakeModelFile()

            // Load through embedding model
            embeddingModel.loadModel(modelFile.absolutePath)

            // Both engines must see the shared loaded state
            assertTrue(embeddingModel.isLoaded(), "Embedding model must report loaded")
            assertTrue(llmEngine.isLoaded(), "LLM engine must also report loaded (shared provider)")
            assertTrue(provider.isLoaded(), "Provider must report loaded")
        }

        @Test
        @DisplayName("loading via LLM engine makes embedding model report isLoaded")
        fun loadViaLlmMakesEmbeddingLoaded() = runTest {
            val modelFile = createFakeModelFile()

            // Load through LLM engine
            llmEngine.loadModel(modelFile.absolutePath)

            // Both engines must see the shared loaded state
            assertTrue(llmEngine.isLoaded(), "LLM engine must report loaded")
            assertTrue(embeddingModel.isLoaded(), "Embedding model must also report loaded (shared provider)")
        }

        @Test
        @DisplayName("provider activeConfig is set after loading from either engine")
        fun activeConfigSetAfterLoad() = runTest {
            val modelFile = createFakeModelFile()

            embeddingModel.loadModel(modelFile.absolutePath)

            val activeConfig = provider.activeConfig()
            assertTrue(activeConfig != null, "Provider must have an active config after load")
            assertEquals(modelFile.absolutePath, activeConfig!!.modelPath)
        }
    }

    // ── Shared provider — embedding then generation pipeline ────────

    @Nested
    @DisplayName("Embedding then generation pipeline")
    inner class EmbeddingThenGenerationTests {

        @Test
        @DisplayName("embed and generate both succeed on shared provider")
        fun embedAndGenerateSucceed() = runTest {
            val modelFile = createFakeModelFile()
            embeddingModel.loadModel(modelFile.absolutePath)

            // Step 1: embed
            val embedding = embeddingModel.embed("What is a fraction?")
            assertTrue(embedding.isNotEmpty(), "Embedding must not be empty")

            // Step 2: generate
            val tokens = llmEngine.generate("Explain fractions", maxTokens = 100).toList()
            assertTrue(tokens.isNotEmpty(), "Generation must produce at least one token")
        }

        @Test
        @DisplayName("generate and embed both succeed on shared provider")
        fun generateAndEmbedSucceed() = runTest {
            val modelFile = createFakeModelFile()
            llmEngine.loadModel(modelFile.absolutePath)

            // Step 1: generate first
            val tokens = llmEngine.generate("What is 2+2?", maxTokens = 100).toList()
            assertTrue(tokens.isNotEmpty(), "Generation must produce at least one token")

            // Step 2: embed after generation
            val embedding = embeddingModel.embed("What is 2+2?")
            assertTrue(embedding.isNotEmpty(), "Embedding after generation must succeed")
        }
    }

    // ── Shared provider — unload does not unload shared model ───────

    @Nested
    @DisplayName("Unload isolation (shared model)")
    inner class UnloadIsolationTests {

        @Test
        @DisplayName("embedding unload does NOT unload shared provider model")
        fun embeddingUnloadKeepsProviderLoaded() = runTest {
            val modelFile = createFakeModelFile()
            embeddingModel.loadModel(modelFile.absolutePath)

            embeddingModel.unload()

            // Provider and LLM engine should still be loaded
            assertTrue(
                provider.isLoaded(),
                "Provider must stay loaded after embedding unload",
            )
            assertTrue(
                llmEngine.isLoaded(),
                "LLM engine must still report loaded after embedding unload",
            )
        }

        @Test
        @DisplayName("LLM unload does NOT unload shared provider model")
        fun llmUnloadKeepsProviderLoaded() = runTest {
            val modelFile = createFakeModelFile()
            llmEngine.loadModel(modelFile.absolutePath)

            llmEngine.unload()

            // Provider and embedding model should still be loaded
            assertTrue(
                provider.isLoaded(),
                "Provider must stay loaded after LLM unload",
            )
            assertTrue(
                embeddingModel.isLoaded(),
                "Embedding model must still report loaded after LLM unload",
            )
        }

        @Test
        @DisplayName("both engines unload — provider still loaded, mode is IDLE")
        fun bothEnginesUnloadKeepsProviderLoaded() = runTest {
            val modelFile = createFakeModelFile()
            embeddingModel.loadModel(modelFile.absolutePath)

            embeddingModel.unload()
            llmEngine.unload()

            // Shared model stays loaded in provider
            assertTrue(provider.isLoaded(), "Provider must stay loaded after both engine unloads")
            assertEquals(GemmaModelProvider.ModelMode.IDLE, provider.currentMode())
        }
    }

    // ── Runtime modes are consistent ────────────────────────────────

    @Nested
    @DisplayName("Runtime mode consistency")
    inner class RuntimeModeConsistencyTests {

        @Test
        @DisplayName("provider reports STUB with null context")
        fun providerReportsStub() {
            assertEquals(
                GemmaRuntimeMode.STUB,
                provider.runtimeMode(),
                "Provider with null context must be in STUB mode",
            )
        }

        @Test
        @DisplayName("LLM engine reports MOCK in stub mode")
        fun llmReportsMock() {
            assertEquals(
                LlmRuntimeMode.MOCK,
                llmEngine.runtimeMode(),
                "LLM engine in STUB provider must report MOCK",
            )
        }

        @Test
        @DisplayName("embedding model reports MOCK in stub mode")
        fun embeddingReportsMock() {
            assertEquals(
                EmbeddingRuntimeMode.MOCK,
                embeddingModel.runtimeMode(),
                "Embedding model in STUB provider must report MOCK",
            )
        }

        @Test
        @DisplayName("runtime modes are consistent before and after load")
        fun modesConsistentAcrossLoad() = runTest {
            // Before load
            assertEquals(GemmaRuntimeMode.STUB, provider.runtimeMode())
            assertEquals(LlmRuntimeMode.MOCK, llmEngine.runtimeMode())
            assertEquals(EmbeddingRuntimeMode.MOCK, embeddingModel.runtimeMode())

            // After load
            val modelFile = createFakeModelFile()
            embeddingModel.loadModel(modelFile.absolutePath)

            assertEquals(GemmaRuntimeMode.STUB, provider.runtimeMode())
            assertEquals(LlmRuntimeMode.MOCK, llmEngine.runtimeMode())
            assertEquals(EmbeddingRuntimeMode.MOCK, embeddingModel.runtimeMode())
        }
    }

    // ── Embedding output properties ─────────────────────────────────

    @Nested
    @DisplayName("Embedding output properties")
    inner class EmbeddingOutputPropertiesTests {

        @Test
        @DisplayName("embed returns correct dimension from config (default 768)")
        fun embedReturnCorrectDimension() = runTest {
            val modelFile = createFakeModelFile()
            embeddingModel.loadModel(modelFile.absolutePath)

            val embedding = embeddingModel.embed("What is a fraction?")

            assertEquals(
                config.embeddingDimension,
                embedding.size,
                "Embedding dimension must match config (${config.embeddingDimension})",
            )
        }

        @Test
        @DisplayName("embed output is L2-normalised (dot product with self ≈ 1.0)")
        fun embedOutputIsNormalised() = runTest {
            val modelFile = createFakeModelFile()
            embeddingModel.loadModel(modelFile.absolutePath)

            val embedding = embeddingModel.embed("Explain addition to me")

            val dotProduct = embedding.map { it.toDouble() * it.toDouble() }.sum()
            assertTrue(
                abs(dotProduct - 1.0) < 1e-5,
                "Expected dot product ≈ 1.0 (L2-normalised), got $dotProduct",
            )
        }

        @Test
        @DisplayName("same text produces identical embeddings (deterministic)")
        fun sameTextSameEmbedding() = runTest {
            val modelFile = createFakeModelFile()
            embeddingModel.loadModel(modelFile.absolutePath)

            val text = "What is 1/2 + 1/4?"
            val embedding1 = embeddingModel.embed(text)
            val embedding2 = embeddingModel.embed(text)

            assertTrue(
                embedding1.contentEquals(embedding2),
                "Same input must produce identical embeddings",
            )
        }

        @Test
        @DisplayName("different text produces different embeddings")
        fun differentTextDifferentEmbedding() = runTest {
            val modelFile = createFakeModelFile()
            embeddingModel.loadModel(modelFile.absolutePath)

            val embedding1 = embeddingModel.embed("What is a fraction?")
            val embedding2 = embeddingModel.embed("How does multiplication work?")

            assertFalse(
                embedding1.contentEquals(embedding2),
                "Different input texts must produce different embeddings",
            )
        }

        @Test
        @DisplayName("embed with custom dimension (384) respects config")
        fun embedWithCustomDimension() = runTest {
            val customConfig = GemmaModelConfig(
                modelPath = "placeholder",
                embeddingDimension = 384,
            )
            val customProvider = GemmaModelProvider(context = null)
            val customEmbedding = GemmaEmbeddingModel(
                modelProvider = customProvider,
                config = customConfig,
            )
            val modelFile = createFakeModelFile()
            customEmbedding.loadModel(modelFile.absolutePath)

            val embedding = customEmbedding.embed("Test input")

            assertEquals(384, embedding.size, "Embedding must be 384-dimensional")
        }
    }

    // ── Generation output in stub mode ──────────────────────────────

    @Nested
    @DisplayName("Generation output (stub mode)")
    inner class GenerationOutputStubTests {

        @Test
        @DisplayName("generate emits at least one token")
        fun generateEmitsAtLeastOneToken() = runTest {
            val modelFile = createFakeModelFile()
            llmEngine.loadModel(modelFile.absolutePath)

            val tokens = llmEngine.generate("What is 2+2?", maxTokens = 100).toList()

            assertTrue(tokens.isNotEmpty(), "Stub generation must emit at least one token")
        }

        @Test
        @DisplayName("stub output contains part of the input prompt")
        fun stubOutputContainsPromptPreview() = runTest {
            val modelFile = createFakeModelFile()
            llmEngine.loadModel(modelFile.absolutePath)

            val prompt = "Explain fractions to a Grade 6 learner"
            val tokens = llmEngine.generate(prompt, maxTokens = 512).toList()
            val fullResponse = tokens.joinToString("")

            assertTrue(
                fullResponse.contains("Explain fractions"),
                "Stub output must contain part of the input prompt, got: $fullResponse",
            )
        }

        @Test
        @DisplayName("stub output starts with 'Gemma 4 stub:' prefix")
        fun stubOutputHasPrefix() = runTest {
            val modelFile = createFakeModelFile()
            llmEngine.loadModel(modelFile.absolutePath)

            val tokens = llmEngine.generate("Test prompt", maxTokens = 100).toList()
            val fullResponse = tokens.joinToString("")

            assertTrue(
                fullResponse.startsWith("Gemma 4 stub:"),
                "Stub response must start with 'Gemma 4 stub:', got: $fullResponse",
            )
        }
    }

    // ── Prompt format integration ───────────────────────────────────

    @Nested
    @DisplayName("Prompt format integration (Gemma turn format)")
    inner class PromptFormatIntegrationTests {

        @Test
        @DisplayName("Gemma prompt contains <start_of_turn>user delimiter")
        fun gemmaPromptContainsUserTurn() {
            val builder = PromptBuilder(chatFormat = ChatFormat.GEMMA_TURN)
            val chunks = listOf(
                createChunk(
                    chunkId = "ch-1",
                    title = "Adding Fractions",
                    content = "To add fractions, find the LCD.",
                ),
            )
            val prompt = builder.buildGroundedPromptFromRetrievedChunks(
                question = "How do I add fractions?",
                retrievedChunks = chunks,
                preferences = null,
            )

            assertTrue(
                prompt.contains("<start_of_turn>user"),
                "Gemma prompt must contain <start_of_turn>user",
            )
        }

        @Test
        @DisplayName("Gemma prompt contains <start_of_turn>model delimiter")
        fun gemmaPromptContainsModelTurn() {
            val builder = PromptBuilder(chatFormat = ChatFormat.GEMMA_TURN)
            val chunks = listOf(createChunk())
            val prompt = builder.buildGroundedPromptFromRetrievedChunks(
                question = "What is a fraction?",
                retrievedChunks = chunks,
                preferences = null,
            )

            assertTrue(
                prompt.contains("<start_of_turn>model"),
                "Gemma prompt must contain <start_of_turn>model",
            )
        }

        @Test
        @DisplayName("Gemma prompt does NOT contain ChatML delimiters")
        fun gemmaPromptNoCharML() {
            val builder = PromptBuilder(chatFormat = ChatFormat.GEMMA_TURN)
            val chunks = listOf(createChunk())
            val prompt = builder.buildGroundedPromptFromRetrievedChunks(
                question = "What is a fraction?",
                retrievedChunks = chunks,
                preferences = null,
            )

            assertFalse(
                prompt.contains("<|im_start|>"),
                "Gemma format must NOT contain ChatML <|im_start|> delimiter",
            )
            assertFalse(
                prompt.contains("<|im_end|>"),
                "Gemma format must NOT contain ChatML <|im_end|> delimiter",
            )
        }

        @Test
        @DisplayName("Gemma prompt includes question and chunk content")
        fun gemmaPromptIncludesContent() {
            val builder = PromptBuilder(chatFormat = ChatFormat.GEMMA_TURN)
            val chunks = listOf(
                createChunk(
                    chunkId = "ch-fractions",
                    title = "Understanding Fractions",
                    content = "A fraction represents a part of a whole.",
                ),
            )
            val prompt = builder.buildGroundedPromptFromRetrievedChunks(
                question = "What is a fraction?",
                retrievedChunks = chunks,
                preferences = null,
            )

            assertTrue(prompt.contains("What is a fraction?"), "Prompt must include the question")
            assertTrue(prompt.contains("Understanding Fractions"), "Prompt must include chunk title")
            assertTrue(prompt.contains("part of a whole"), "Prompt must include chunk content")
        }
    }

    // ── Full mini-pipeline ──────────────────────────────────────────

    @Nested
    @DisplayName("Full mini-pipeline (embed → prompt → generate)")
    inner class FullMiniPipelineTests {

        @Test
        @DisplayName("embed → build Gemma prompt → generate completes without exceptions")
        fun fullPipelineCompletes() = runTest {
            val modelFile = createFakeModelFile()
            embeddingModel.loadModel(modelFile.absolutePath)

            // Step 1: Embed a question
            val question = "How do I add fractions with different denominators?"
            val embedding = embeddingModel.embed(question)
            assertTrue(embedding.isNotEmpty(), "Embedding must be non-empty")
            assertEquals(config.embeddingDimension, embedding.size)

            // Step 2: Build a Gemma-format prompt (simulating retrieval result)
            val builder = PromptBuilder(chatFormat = ChatFormat.GEMMA_TURN)
            val retrievedChunks = listOf(
                createChunk(
                    chunkId = "ch-lcd",
                    title = "Adding Unlike Fractions",
                    content = "To add fractions with different denominators, first find the LCD (Lowest Common Denominator).",
                    score = 0.92f,
                ),
                createChunk(
                    chunkId = "ch-equiv",
                    title = "Equivalent Fractions",
                    content = "Equivalent fractions represent the same value. Multiply numerator and denominator by the same number.",
                    score = 0.85f,
                ),
            )
            val prompt = builder.buildGroundedPromptFromRetrievedChunks(
                question = question,
                retrievedChunks = retrievedChunks,
                preferences = null,
            )
            assertTrue(prompt.contains("<start_of_turn>"), "Prompt must use Gemma format")
            assertTrue(prompt.contains(question), "Prompt must include the original question")

            // Step 3: Generate a response
            val tokens = llmEngine.generate(prompt, maxTokens = 256).toList()
            assertTrue(tokens.isNotEmpty(), "Generation must produce at least one token")
            val response = tokens.joinToString("")
            assertTrue(response.isNotEmpty(), "Response must be non-empty")
        }

        @Test
        @DisplayName("full pipeline with preferences included")
        fun fullPipelineWithPreferences() = runTest {
            val modelFile = createFakeModelFile()
            embeddingModel.loadModel(modelFile.absolutePath)

            // Step 1: Embed
            val question = "What is multiplication?"
            val embedding = embeddingModel.embed(question)
            assertTrue(embedding.size == config.embeddingDimension)

            // Step 2: Build prompt with preferences
            val builder = PromptBuilder(chatFormat = ChatFormat.GEMMA_TURN)
            val chunks = listOf(
                createChunk(
                    chunkId = "ch-mult",
                    title = "Multiplication Basics",
                    content = "Multiplication is repeated addition. 3 × 4 = 4 + 4 + 4 = 12.",
                ),
            )
            val preferences = listOf(
                com.ezansi.app.core.data.LearnerPreference(
                    key = "reading_level",
                    value = "basic",
                    displayName = "Reading Level",
                    description = "Controls explanation complexity",
                ),
            )
            val prompt = builder.buildGroundedPromptFromRetrievedChunks(
                question = question,
                retrievedChunks = chunks,
                preferences = preferences,
            )

            // Step 3: Generate
            val tokens = llmEngine.generate(prompt, maxTokens = 256).toList()
            val response = tokens.joinToString("")
            assertTrue(response.isNotEmpty(), "Response with preferences must be non-empty")
        }

        @Test
        @DisplayName("pipeline steps do not interfere with each other")
        fun pipelineStepsDoNotInterfere() = runTest {
            val modelFile = createFakeModelFile()
            embeddingModel.loadModel(modelFile.absolutePath)

            // Run embed twice with different texts
            val emb1 = embeddingModel.embed("First question about fractions")
            val emb2 = embeddingModel.embed("Second question about geometry")

            // Different inputs → different embeddings
            assertFalse(
                emb1.contentEquals(emb2),
                "Different questions must produce different embeddings",
            )

            // Run generate twice with short distinct prompts (not Gemma-formatted,
            // so the stub preview captures the difference within the first 50 chars)
            val response1 = llmEngine.generate("AAAA fractions question", maxTokens = 100)
                .toList().joinToString("")
            val response2 = llmEngine.generate("BBBB geometry question", maxTokens = 100)
                .toList().joinToString("")

            // Different prompts → different stub responses (different prompt previews)
            assertNotEquals(
                response1,
                response2,
                "Different prompts must produce different stub responses",
            )
        }
    }

    // ── Mode tracking across engines ────────────────────────────────

    @Nested
    @DisplayName("Mode tracking across engines")
    inner class ModeTrackingTests {

        @Test
        @DisplayName("embed sets provider mode to EMBEDDING")
        fun embedSetsModeToEmbedding() = runTest {
            val modelFile = createFakeModelFile()
            embeddingModel.loadModel(modelFile.absolutePath)

            embeddingModel.embed("trigger embedding")

            assertEquals(
                GemmaModelProvider.ModelMode.EMBEDDING,
                provider.currentMode(),
                "Embed should set provider mode to EMBEDDING",
            )
        }

        @Test
        @DisplayName("generate sets provider mode to GENERATION")
        fun generateSetsModeToGeneration() = runTest {
            val modelFile = createFakeModelFile()
            llmEngine.loadModel(modelFile.absolutePath)

            llmEngine.generate("trigger generation", maxTokens = 100).toList()

            assertEquals(
                GemmaModelProvider.ModelMode.GENERATION,
                provider.currentMode(),
                "Generate should set provider mode to GENERATION",
            )
        }

        @Test
        @DisplayName("mode transitions from EMBEDDING to GENERATION correctly")
        fun modeTransitionsCorrectly() = runTest {
            val modelFile = createFakeModelFile()
            embeddingModel.loadModel(modelFile.absolutePath)

            // Start with embedding
            embeddingModel.embed("embed question")
            assertEquals(GemmaModelProvider.ModelMode.EMBEDDING, provider.currentMode())

            // Switch to generation
            llmEngine.generate("generate answer", maxTokens = 100).toList()
            assertEquals(GemmaModelProvider.ModelMode.GENERATION, provider.currentMode())

            // Back to embedding
            embeddingModel.embed("another question")
            assertEquals(GemmaModelProvider.ModelMode.EMBEDDING, provider.currentMode())
        }
    }
}
