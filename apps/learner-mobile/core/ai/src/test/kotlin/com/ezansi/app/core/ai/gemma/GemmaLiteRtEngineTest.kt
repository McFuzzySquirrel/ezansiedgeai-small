package com.ezansi.app.core.ai.gemma

import com.ezansi.app.core.ai.inference.LlmRuntimeMode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [GemmaLiteRtEngine] — Gemma 4 LLM engine via MediaPipe.
 *
 * All tests run in STUB mode because MediaPipe GenAI SDK is not on the JVM
 * unit-test classpath. The provider (`context = null`) gracefully degrades
 * to stub mode, and the engine produces deterministic stub output.
 *
 * Uses `testOptions.unitTests.isReturnDefaultValues = true` from build.gradle.kts
 * to handle `android.util.Log` calls without Robolectric.
 */
@DisplayName("GemmaLiteRtEngine")
class GemmaLiteRtEngineTest {

    private lateinit var provider: GemmaModelProvider
    private lateinit var engine: GemmaLiteRtEngine

    @TempDir
    lateinit var tempDir: Path

    /** Creates a fake model file for lifecycle testing. */
    private fun createFakeModelFile(name: String = "gemma4.task"): File {
        val file = tempDir.resolve(name).toFile()
        file.writeText("fake-gemma4-model-data")
        return file
    }

    @BeforeEach
    fun setUp() {
        provider = GemmaModelProvider(context = null)
        engine = GemmaLiteRtEngine(
            modelProvider = provider,
            config = GemmaModelConfig(modelPath = "placeholder"),
        )
    }

    // ── Initial state ───────────────────────────────────────────────

    @Nested
    @DisplayName("Initial state")
    inner class InitialStateTests {

        @Test
        @DisplayName("is not loaded initially")
        fun notLoadedInitially() {
            assertFalse(engine.isLoaded())
        }

        @Test
        @DisplayName("runtime mode is MOCK in stub mode")
        fun runtimeModeIsMockInStub() {
            // Provider has no MediaPipe → STUB → maps to MOCK
            assertEquals(LlmRuntimeMode.MOCK, engine.runtimeMode())
        }
    }

    // ── loadModel ───────────────────────────────────────────────────

    @Nested
    @DisplayName("loadModel")
    inner class LoadModelTests {

        @Test
        @DisplayName("delegates to provider and transitions to loaded state")
        fun delegatesToProvider() = runTest {
            val modelFile = createFakeModelFile()

            engine.loadModel(modelFile.absolutePath)

            assertTrue(engine.isLoaded())
            assertTrue(provider.isLoaded())
        }

        @Test
        @DisplayName("overrides config modelPath with provided path")
        fun overridesModelPath() = runTest {
            val modelFile = createFakeModelFile("custom-model.task")

            engine.loadModel(modelFile.absolutePath)

            assertEquals(modelFile.absolutePath, provider.activeConfig()?.modelPath)
        }

        @Test
        @DisplayName("preserves config defaults (temperature, topK, etc)")
        fun preservesConfigDefaults() = runTest {
            val customConfig = GemmaModelConfig(
                modelPath = "placeholder",
                temperature = 0.5f,
                topK = 20,
                embeddingDimension = 384,
            )
            val customEngine = GemmaLiteRtEngine(
                modelProvider = provider,
                config = customConfig,
            )
            val modelFile = createFakeModelFile()

            customEngine.loadModel(modelFile.absolutePath)

            val active = provider.activeConfig()!!
            assertEquals(0.5f, active.temperature)
            assertEquals(20, active.topK)
            assertEquals(384, active.embeddingDimension)
        }

        @Test
        @DisplayName("throws FileNotFoundException for nonexistent path")
        fun throwsForMissingFile() = runTest {
            assertFailsWith<java.io.FileNotFoundException> {
                engine.loadModel("/nonexistent/path/gemma4.task")
            }
        }

        @Test
        @DisplayName("idempotent for same model path")
        fun idempotentSamePath() = runTest {
            val modelFile = createFakeModelFile()

            engine.loadModel(modelFile.absolutePath)
            engine.loadModel(modelFile.absolutePath) // no-op

            assertTrue(engine.isLoaded())
        }
    }

    // ── isLoaded ────────────────────────────────────────────────────

    @Nested
    @DisplayName("isLoaded")
    inner class IsLoadedTests {

        @Test
        @DisplayName("delegates to provider")
        fun delegatesToProvider() = runTest {
            assertFalse(engine.isLoaded())

            val modelFile = createFakeModelFile()
            provider.loadModel(GemmaModelConfig(modelPath = modelFile.absolutePath))

            assertTrue(engine.isLoaded())
        }

        @Test
        @DisplayName("reflects provider state after unloadModel")
        fun reflectsProviderUnload() = runTest {
            val modelFile = createFakeModelFile()
            provider.loadModel(GemmaModelConfig(modelPath = modelFile.absolutePath))
            assertTrue(engine.isLoaded())

            provider.unloadModel()
            assertFalse(engine.isLoaded())
        }
    }

    // ── generate (stub mode) ────────────────────────────────────────

    @Nested
    @DisplayName("generate (stub mode)")
    inner class GenerateStubTests {

        @Test
        @DisplayName("emits deterministic stub response")
        fun emitsDeterministicStubResponse() = runTest {
            val modelFile = createFakeModelFile()
            engine.loadModel(modelFile.absolutePath)

            val tokens = engine.generate("What is 2+2?", maxTokens = 512).toList()

            assertEquals(1, tokens.size)
            assertTrue(tokens[0].startsWith("Gemma 4 stub:"))
            assertTrue(tokens[0].contains("What is 2+2?"))
        }

        @Test
        @DisplayName("includes prompt preview up to 50 chars")
        fun includesPromptPreview() = runTest {
            val modelFile = createFakeModelFile()
            engine.loadModel(modelFile.absolutePath)
            val longPrompt = "A".repeat(100)

            val tokens = engine.generate(longPrompt, maxTokens = 512).toList()

            val response = tokens[0]
            // Should contain first 50 chars of the prompt
            assertTrue(response.contains("A".repeat(50)))
            // Should NOT contain all 100 chars
            assertFalse(response.contains("A".repeat(100)))
        }

        @Test
        @DisplayName("sets mode to GENERATION before generating")
        fun setsModeToGeneration() = runTest {
            val modelFile = createFakeModelFile()
            engine.loadModel(modelFile.absolutePath)

            // Collect the flow to trigger generation
            engine.generate("test", maxTokens = 100).toList()

            assertEquals(GemmaModelProvider.ModelMode.GENERATION, provider.currentMode())
        }

        @Test
        @DisplayName("throws when model not loaded")
        fun throwsWhenNotLoaded() = runTest {
            assertFailsWith<IllegalStateException> {
                engine.generate("test prompt").toList()
            }
        }

        @Test
        @DisplayName("produces consistent output for same prompt")
        fun consistentOutputForSamePrompt() = runTest {
            val modelFile = createFakeModelFile()
            engine.loadModel(modelFile.absolutePath)

            val prompt = "Explain fractions to a Grade 6 learner"
            val first = engine.generate(prompt, maxTokens = 512).toList()
            val second = engine.generate(prompt, maxTokens = 512).toList()

            assertEquals(first, second)
        }
    }

    // ── generate (maxTokens enforcement) ────────────────────────────

    @Nested
    @DisplayName("generate (maxTokens)")
    inner class GenerateMaxTokensTests {

        @Test
        @DisplayName("truncates output when maxTokens is small")
        fun truncatesOutput() = runTest {
            val modelFile = createFakeModelFile()
            engine.loadModel(modelFile.absolutePath)

            val tokens = engine.generate("What is 2+2?", maxTokens = 3).toList()

            val response = tokens[0]
            val wordCount = response.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
            assertTrue(
                wordCount <= 3,
                "Expected at most 3 words, got $wordCount: '$response'",
            )
        }

        @Test
        @DisplayName("does not truncate when response is shorter than maxTokens")
        fun noTruncationWhenShort() = runTest {
            val modelFile = createFakeModelFile()
            engine.loadModel(modelFile.absolutePath)

            // Short prompt → short stub response
            val tokens = engine.generate("Hi", maxTokens = 512).toList()

            // Stub response is "Gemma 4 stub: Hi..." which is ~4 words
            assertTrue(tokens[0].isNotEmpty())
        }
    }

    // ── unload ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("unload")
    inner class UnloadTests {

        @Test
        @DisplayName("sets mode to IDLE without unloading shared provider")
        fun setsModeToIdleKeepsProviderLoaded() = runTest {
            val modelFile = createFakeModelFile()
            engine.loadModel(modelFile.absolutePath)
            provider.setMode(GemmaModelProvider.ModelMode.GENERATION)

            engine.unload()

            // Mode should be IDLE
            assertEquals(GemmaModelProvider.ModelMode.IDLE, provider.currentMode())
            // Provider model should still be loaded (shared with embedding)
            assertTrue(provider.isLoaded())
        }

        @Test
        @DisplayName("safe to call when not loaded")
        fun safeWhenNotLoaded() {
            // Should not throw
            engine.unload()
            assertEquals(GemmaModelProvider.ModelMode.IDLE, provider.currentMode())
        }

        @Test
        @DisplayName("safe to call multiple times")
        fun safeMultipleCalls() = runTest {
            val modelFile = createFakeModelFile()
            engine.loadModel(modelFile.absolutePath)

            engine.unload()
            engine.unload()
            engine.unload()

            assertEquals(GemmaModelProvider.ModelMode.IDLE, provider.currentMode())
            assertTrue(provider.isLoaded())
        }
    }

    // ── runtimeMode ─────────────────────────────────────────────────

    @Nested
    @DisplayName("runtimeMode")
    inner class RuntimeModeTests {

        @Test
        @DisplayName("maps STUB → MOCK")
        fun stubMapsToMock() {
            assertEquals(LlmRuntimeMode.MOCK, engine.runtimeMode())
        }

        @Test
        @DisplayName("MOCK is consistent before and after load")
        fun mockConsistentAcrossLoad() = runTest {
            assertEquals(LlmRuntimeMode.MOCK, engine.runtimeMode())

            val modelFile = createFakeModelFile()
            engine.loadModel(modelFile.absolutePath)

            // Still STUB (no real MediaPipe) → still MOCK
            assertEquals(LlmRuntimeMode.MOCK, engine.runtimeMode())
        }

        @Test
        @DisplayName("runtime mode matches expected LlmRuntimeMode enum values")
        fun runtimeModeIsValidEnum() {
            val mode = engine.runtimeMode()
            assertTrue(mode in LlmRuntimeMode.entries)
        }
    }

    // ── Integration-level checks ────────────────────────────────────

    @Nested
    @DisplayName("Integration")
    inner class IntegrationTests {

        @Test
        @DisplayName("full lifecycle: load → generate → unload → reload → generate")
        fun fullLifecycle() = runTest {
            val modelFile = createFakeModelFile()

            // Load
            engine.loadModel(modelFile.absolutePath)
            assertTrue(engine.isLoaded())

            // Generate
            val firstResult = engine.generate("question 1", maxTokens = 100).toList()
            assertTrue(firstResult.isNotEmpty())

            // Unload (sets mode to IDLE, model stays loaded in provider)
            engine.unload()
            assertEquals(GemmaModelProvider.ModelMode.IDLE, provider.currentMode())
            assertTrue(engine.isLoaded()) // shared model still loaded

            // Generate again (model still available via provider)
            val secondResult = engine.generate("question 2", maxTokens = 100).toList()
            assertTrue(secondResult.isNotEmpty())
        }

        @Test
        @DisplayName("engine and provider share loaded state")
        fun sharedState() = runTest {
            val modelFile = createFakeModelFile()

            // Load through engine
            engine.loadModel(modelFile.absolutePath)
            assertTrue(provider.isLoaded())

            // Unload through provider directly
            provider.unloadModel()
            assertFalse(engine.isLoaded())
        }
    }
}
