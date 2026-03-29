package com.ezansi.app.core.ai.inference

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Mock LLM engine for development and testing without native libraries.
 *
 * Simulates token-by-token generation with realistic timing (50 ms per token)
 * to let developers test the streaming UI, loading states, and pipeline
 * integration without downloading the 1 GB Qwen2.5 model file.
 *
 * ## Mock Output
 *
 * The mock engine produces a fixed educational-sounding explanation that
 * demonstrates the expected output format including:
 * - Step-by-step explanation structure
 * - Mathematical notation placeholders
 * - Source attribution references
 *
 * ## Timing
 *
 * Each token is emitted with a 50 ms delay to simulate on-device inference
 * speed. The total mock generation takes ~2.5 seconds for ~50 tokens, which
 * is within the 10-second end-to-end budget (NF-01).
 */
class MockLlmEngine : LlmEngine {

    companion object {
        private const val TAG = "MockLlmEngine"

        /** Simulated delay between tokens in milliseconds. */
        private const val TOKEN_DELAY_MS = 50L

        /** Mock response that demonstrates the expected output format. */
        private const val MOCK_RESPONSE =
            "Let me explain this step by step.\n\n" +
                "**Step 1:** First, let's look at what the question is asking. " +
                "Based on the curriculum content, we need to understand the concept.\n\n" +
                "**Step 2:** According to the content, we can see that this topic " +
                "involves working with numbers in a structured way.\n\n" +
                "**Step 3:** Here's a simple example: If we have 3/4 of a pizza, " +
                "that means we divided the pizza into 4 equal pieces and took 3.\n\n" +
                "**Remember:** Always check your answer by working backwards!\n\n" +
                "*[This is a mock response — real explanations will be generated " +
                "by the Qwen2.5 model once llama.cpp is integrated.]*"
    }

    private var loaded = false

    override suspend fun loadModel(modelPath: String) {
        if (loaded) return
        Log.i(TAG, "Mock LLM engine loaded (no actual model file needed)")
        loaded = true
    }

    override fun isLoaded(): Boolean = loaded

    override fun generate(prompt: String, maxTokens: Int): Flow<String> = flow {
        check(loaded) {
            "Mock LLM engine is not loaded. Call loadModel() first."
        }

        Log.d(TAG, "Generating mock response (prompt length: ${prompt.length} chars)")

        // Split the mock response into word-level tokens to simulate
        // realistic streaming behaviour
        val tokens = MOCK_RESPONSE.splitIntoTokens()
        var tokenCount = 0

        for (token in tokens) {
            if (tokenCount >= maxTokens) break

            delay(TOKEN_DELAY_MS)
            emit(token)
            tokenCount++
        }

        Log.d(TAG, "Mock generation complete: $tokenCount tokens emitted")
    }

    override fun unload() {
        loaded = false
        Log.i(TAG, "Mock LLM engine unloaded")
    }

    override fun runtimeMode(): LlmRuntimeMode = LlmRuntimeMode.MOCK
}

/**
 * Splits text into token-like chunks that approximate real LLM tokenisation.
 *
 * Real BPE tokenisers produce sub-word tokens, but for mock purposes
 * word-level splitting with preserved whitespace gives a realistic
 * streaming experience in the UI.
 */
private fun String.splitIntoTokens(): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()

    for (char in this) {
        if (char == ' ' || char == '\n') {
            if (current.isNotEmpty()) {
                tokens.add(current.toString())
                current.clear()
            }
            tokens.add(char.toString())
        } else {
            current.append(char)
        }
    }

    if (current.isNotEmpty()) {
        tokens.add(current.toString())
    }

    return tokens
}
