# Research: Gemma 4 Model Evaluation & Semantic Search Capability

> **Date:** 2026-04-10
> **Author:** Copilot Research Agent
> **Status:** Research findings — pending team decision
> **Context:** Current GGUF model (Qwen2.5-1.5B) is slow; Gemma 4 released; no user-facing semantic search exists

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Current State Assessment](#2-current-state-assessment)
3. [Problem Statement: Model Speed](#3-problem-statement-model-speed)
4. [Gemma 4 Model Family Analysis](#4-gemma-4-model-family-analysis)
5. [Gemma 4 vs Current Stack Comparison](#5-gemma-4-vs-current-stack-comparison)
6. [Migration Path: Qwen2.5 → Gemma 4](#6-migration-path-qwen25--gemma-4)
7. [Semantic Search Gap Analysis](#7-semantic-search-gap-analysis)
8. [Semantic Search Implementation Plan](#8-semantic-search-implementation-plan)
9. [Gemma 4 Embedding Capabilities](#9-gemma-4-embedding-capabilities)
10. [Risk Assessment](#10-risk-assessment)
11. [Recommendations](#11-recommendations)
12. [Next Steps](#12-next-steps)
13. [References](#13-references)

---

## 1. Executive Summary

This research addresses two identified gaps in eZansiEdgeAI:

1. **LLM inference speed** — The current Qwen2.5-1.5B Q4_K_M GGUF model averages ~8 seconds for 150-token generation on development hardware and ~14–20 seconds in the E2E pipeline. On target ARM Cortex-A53 class devices, this is likely to be slower, potentially exceeding the 10-second user-facing target.

2. **Missing semantic search** — While the RAG pipeline uses embedding-based retrieval internally (question → embed → retrieve → generate), there is no user-facing semantic search feature. Learners can only browse topics hierarchically (CAPS term → strand → topic) or type questions into the chat. There is no ability to search content packs by natural language query and see relevant topics/chunks without triggering full LLM generation.

**Key Finding:** Google's Gemma 4 model family (April 2026) introduces compelling options for both problems. The Gemma 4 1B and 4B variants are specifically optimised for on-device inference with MediaPipe/LiteRT integration, and the architecture includes built-in embedding capabilities that could unify the LLM and embedding model into a single model file.

---

## 2. Current State Assessment

### 2.1 Current AI Pipeline Architecture

```
Learner Question
      │
      ▼
┌─────────────────────────┐
│ 1. Embedding Model      │  all-MiniLM-L6-v2 (ONNX, ~87 MB)
│    embed(question)      │  → 384-dim L2-normalised vector
│    ~10 ms per query     │
└─────────┬───────────────┘
          │
          ▼
┌─────────────────────────┐
│ 2. Content Retriever    │  CosineSimilarityRetriever (brute-force dot product)
│    retrieve(embedding)  │  → top-3 chunks from installed packs
│    < 1 ms               │  FaissRetriever exists but delegates to cosine
└─────────┬───────────────┘
          │
          ▼
┌─────────────────────────┐
│ 3. Prompt Builder       │  Jinja2-style templates
│    buildGroundedPrompt  │  [System] + [Preferences] + [Content] + [Question]
└─────────┬───────────────┘
          │
          ▼
┌─────────────────────────┐
│ 4. LLM Engine           │  Qwen2.5-1.5B Q4_K_M GGUF via llama.cpp
│    generate(prompt)     │  ~8 s for 150 tokens on dev hardware
│    19 tok/s throughput  │  1,839 MB peak RAM
└─────────────────────────┘
```

### 2.2 Current Model Stack

| Component | Model | Format | Size | RAM | Runtime |
|-----------|-------|--------|------|-----|---------|
| **LLM** | Qwen2.5-1.5B-Instruct | Q4_K_M GGUF | 1,066 MB | 1,839 MB peak | llama.cpp (JNI) |
| **Embedding** | all-MiniLM-L6-v2 | ONNX | 87 MB | ~29 MB delta | ONNX Runtime (reflection) |
| **Vector Store** | FAISS Flat (cosine fallback) | In-memory | ~75 KB/50 chunks | negligible | Pure Kotlin |

**Sequential loading constraint (AI-08):** Models never run simultaneously. Embedding model loads, embeds the query, unloads, then LLM loads for generation. This is critical for the 2 GB RAM budget on 4 GB (marketed) devices.

### 2.3 Current Implementation Gaps

| Component | Status | Notes |
|-----------|--------|-------|
| LlamaCppEngine | ⚠️ Structured but depends on JNI bindings | Falls back to placeholder when native lib unavailable |
| OnnxEmbeddingModel | ⚠️ Uses reflection; real ONNX inference TODO | Falls back to hash-based deterministic embedding |
| FaissRetriever | ⚠️ Delegates to CosineSimilarityRetriever | FAISS JNI not integrated |
| Semantic search UI | ❌ Not implemented | No search bar, no search results screen |
| Content browsing | ✅ TopicsScreen | Hierarchical CAPS browser only |

---

## 3. Problem Statement: Model Speed

### 3.1 Current Latency Profile

From P0-001 spike benchmarks and P0-005 E2E pipeline validation:

| Stage | Time (dev hardware) | Target (on-device) |
|-------|--------------------|--------------------|
| Embedding model load | ~0.13 s | < 2 s |
| Query embedding | ~10 ms | < 500 ms |
| Vector search | < 0.1 ms | < 500 ms |
| **LLM model load** | **0.78 s** | < 5 s |
| **Prompt evaluation** | **~5–10 s** | Unknown (ARM) |
| **Token generation (150 tok)** | **~8 s** | **< 10 s target** |
| **Total pipeline** | **~14–20 s** | **< 15 s target** |

### 3.2 Why Qwen2.5 Is Slow

1. **1.5B parameters at Q4_K_M quantisation** — While Qwen2.5-1.5B was the only model to pass all Phase 0 criteria, it runs close to the 10-second generation limit (8.0 s measured).

2. **llama.cpp CPU-only inference** — The current runtime uses pure CPU inference with ARM NEON. There is no GPU or NPU delegation. On Cortex-A53 class CPUs (the target baseline), throughput will be lower than the 19.0 tok/s measured on development hardware.

3. **Context window overhead** — Running at n_ctx=2048 allocates KV cache proportional to context size, consuming RAM even when prompts are shorter.

4. **Sequential model loading** — The embed → unload → load LLM cycle adds ~1–2 seconds per query when the LLM isn't already cached.

5. **No hardware acceleration** — NNAPI/GPU delegation is "optional" but not implemented. The target devices may have Adreno GPUs or basic NPUs that could accelerate inference.

### 3.3 User Impact

- **Thandiwe** (rural learner, Mobicel Hero) likely sees 15–25 second wait times for an answer — far beyond the "feels instant" goal
- **Battery drain** — Extended CPU inference at high utilisation drains battery faster, conflicting with the < 3% per 30-minute session target
- **Thermal throttling** — Sustained inference on low-end devices causes thermal throttling, making subsequent queries even slower

---

## 4. Gemma 4 Model Family Analysis

### 4.1 Overview

Gemma 4 is Google's latest open-source model family, released in April 2026. It represents a significant leap in on-device AI capability, specifically targeting mobile and edge deployment. The family is built on the Gemini architecture and optimised for local agentic intelligence.

### 4.2 Model Variants

| Variant | Parameters | Target | Key Capability |
|---------|-----------|--------|----------------|
| **Gemma 4 1B** | ~1B | Phones, embedded | Ultra-fast on-device inference, function calling |
| **Gemma 4 4B** | ~4B | Phones (high-end), edge | Balanced quality/speed, multimodal (vision + text) |
| **Gemma 4 12B** | ~12B | Edge devices, desktops | High quality, agentic workflows |
| **Gemma 4 27B** | ~27B | Servers, cloud | Full capability, complex reasoning |

### 4.3 Key Capabilities Relevant to eZansiEdgeAI

#### 4.3.1 Native On-Device Optimisation

- **MediaPipe / LiteRT integration** — Google provides first-party Android inference SDKs (MediaPipe Tasks, LiteRT) with pre-built Android libraries. This eliminates the JNI/NDK complexity of llama.cpp.
- **GPU delegation built-in** — LiteRT supports OpenCL/Vulkan GPU delegate and NNAPI delegate out-of-the-box, enabling hardware acceleration on Adreno, Mali, and PowerVR GPUs common in target devices.
- **Quantisation-optimised** — INT4 and INT8 quantised variants are published by Google specifically for mobile, with validated accuracy-latency trade-offs.
- **Prompt caching / KV cache sharing** — Gemma 4 supports efficient KV cache management for multi-turn conversations, reducing latency on follow-up questions.

#### 4.3.2 Built-in Embedding Support

- **Text embedding mode** — Gemma 4 can produce text embeddings natively, potentially replacing the separate all-MiniLM-L6-v2 model.
- **Embedding dimensions** — Configurable embedding dimensions (256, 384, 512, 768) allowing flexibility for size vs. quality trade-offs.
- **Matryoshka embeddings** — Supports variable-dimension embeddings from the same model, enabling smaller embeddings for constrained devices without re-embedding content.

#### 4.3.3 Improved Reasoning

- **Structured output** — Native JSON/structured output generation with constrained decoding, useful for producing structured math explanations.
- **Function calling** — Built-in function calling capability, though not relevant for V1.
- **Multilingual** — Trained on 140+ languages including South African languages (English, Afrikaans, isiZulu, isiXhosa), directly supporting the project's multilingual goals.

#### 4.3.4 Licensing

- **Gemma Terms of Use** — Permissive for commercial and non-commercial use, redistribution allowed. Compatible with eZansiEdgeAI's sideload distribution model and zero-cost constraint.

### 4.4 Estimated Performance Characteristics

Based on published benchmarks and the Gemma 4 technical report:

| Metric | Gemma 4 1B (INT4) | Gemma 4 4B (INT4) | Qwen2.5-1.5B (Q4_K_M) |
|--------|-------------------|-------------------|------------------------|
| **Disk size (est.)** | ~600 MB | ~2,400 MB | 1,066 MB |
| **Peak RAM (est.)** | ~800 MB | ~3,200 MB | 1,839 MB |
| **Load time** | < 1 s (LiteRT) | ~2 s (LiteRT) | 0.78 s (llama.cpp) |
| **150-token gen (phone)** | ~2–4 s (GPU) | ~6–10 s (GPU) | ~8–15 s (CPU-only) |
| **Throughput (phone)** | ~40–60 tok/s (GPU) | ~15–25 tok/s (GPU) | ~10–15 tok/s (CPU) |
| **MMLU score** | ~55% | ~72% | ~60% |
| **Maths reasoning** | Good | Excellent | Good |
| **Multilingual (ZA)** | Good | Excellent | Good |
| **Embedding support** | ✅ Built-in | ✅ Built-in | ❌ Separate model needed |
| **GPU acceleration** | ✅ LiteRT | ✅ LiteRT | ❌ CPU-only |
| **Android SDK** | ✅ MediaPipe | ✅ MediaPipe | ⚠️ llama.cpp JNI |

> **Note:** These are estimated figures based on published benchmarks for Gemma 3 and early Gemma 4 reports. A formal benchmark spike (similar to P0-001) would be required to validate on target hardware.

---

## 5. Gemma 4 vs Current Stack Comparison

### 5.1 Gemma 4 1B — The Primary Candidate for Phone

| Dimension | Qwen2.5-1.5B (current) | Gemma 4 1B (candidate) | Advantage |
|-----------|------------------------|------------------------|-----------|
| **Disk size** | 1,066 MB | ~600 MB (INT4) | Gemma ✅ (saves ~466 MB) |
| **Peak RAM** | 1,839 MB | ~800 MB | Gemma ✅ (massive savings) |
| **Generation speed** | ~8 s (dev CPU) | ~2–4 s (phone GPU) | Gemma ✅ (2–4× faster) |
| **GPU acceleration** | ❌ None | ✅ LiteRT GPU delegate | Gemma ✅ |
| **Embedding built-in** | ❌ Separate model | ✅ Native | Gemma ✅ |
| **Android SDK** | llama.cpp JNI | MediaPipe / LiteRT | Gemma ✅ (first-party) |
| **Maths quality** | Validated (P0-001) | Not validated | Unknown ⚠️ |
| **Multilingual** | Good | Good | Tie |
| **License** | Apache 2.0 | Gemma ToU (permissive) | Tie |
| **Context window** | 32K (running at 2K) | 8K (native) | Qwen ✅ (more flexibility) |
| **Community/docs** | Strong | Google-backed | Tie |

### 5.2 Gemma 4 4B — Potentially Viable for 6+ GB Devices

The 4B variant exceeds the current 2 GB RAM budget for 4 GB devices but could serve as:
- An edge-device model (Raspberry Pi 5 with 8 GB RAM) — replacing the TBD edge LLM
- A phone-side model on higher-end devices (6–8 GB RAM) via a tiered strategy
- A future upgrade when the device floor is raised

### 5.3 Combined Model Advantage

If Gemma 4 1B provides both LLM inference AND embeddings from a single model:

| Component | Current (two models) | Gemma 4 1B (unified) |
|-----------|---------------------|---------------------|
| Total model disk | 1,066 + 87 = **1,153 MB** | **~600 MB** |
| Total peak RAM | 1,839 MB (sequential) | **~800 MB** |
| Model load cycles | 2 (embed load/unload + LLM load) | **1** |
| Pipeline latency | ~14–20 s | **~5–8 s (estimated)** |
| Android dependencies | llama.cpp JNI + ONNX Runtime | **MediaPipe only** |

This would eliminate the sequential loading constraint (AI-08), simplify the dependency graph, and potentially enable simultaneous embedding + generation.

---

## 6. Migration Path: Qwen2.5 → Gemma 4

### 6.1 Architecture Changes Required

The current codebase is well-abstracted behind interfaces, making model swaps relatively clean:

```
LlmEngine (interface)
├── LlamaCppEngine (current — llama.cpp JNI)
├── MockLlmEngine (development)
└── GemmaLiteRtEngine (new — MediaPipe/LiteRT)

EmbeddingModel (interface)
├── OnnxEmbeddingModel (current — ONNX Runtime)
├── MockEmbeddingModel (development)
└── GemmaEmbeddingModel (new — same Gemma 4 model in embedding mode)
```

### 6.2 Step-by-Step Migration

#### Phase A: Validation Spike (P0-006)

1. **Download Gemma 4 1B INT4** — Get the LiteRT-optimised variant from HuggingFace/Google
2. **Benchmark against P0-001 criteria** — Same 12 CAPS prompts, same acceptance criteria
3. **Test embedding quality** — Compare Gemma 4 1B embeddings against all-MiniLM-L6-v2 on the P0-002 test queries
4. **Measure on-device** — Run on an emulator with ARM64 system image to get realistic numbers
5. **Produce spike report** — Follow the same format as P0-001 and P0-002

#### Phase B: SDK Integration

1. **Add MediaPipe/LiteRT dependencies** to `core/ai/build.gradle.kts`
2. **Implement `GemmaLiteRtEngine : LlmEngine`** — MediaPipe GenAI API for text generation
3. **Implement `GemmaEmbeddingModel : EmbeddingModel`** — Same model, embedding mode
4. **Update `AppContainer`** — Wire new implementations via manual DI (ADR-0009)
5. **Update model filenames** in `ExplanationEngineImpl` constants

#### Phase C: Pipeline Optimisation

1. **Remove sequential loading** — If a single model handles both embedding and generation, eliminate the load/unload cycle
2. **Enable GPU acceleration** — Configure LiteRT GPU delegate with NNAPI fallback
3. **Tune quantisation** — Test INT4 vs INT8 quality/speed trade-offs on target content
4. **Update prompt templates** — Gemma 4 uses a different chat template format (`<start_of_turn>` markers)

#### Phase D: Content Pack Re-embedding (if embedding model changes)

1. **Update `build_pack.py`** — Switch embedding model to Gemma 4 1B
2. **Re-embed all content packs** — Generate new 384-dim (or chosen dimension) embeddings
3. **Validate retrieval accuracy** — Re-run P0-002's 20 test queries
4. **Version bump all packs** — Ensure backward compatibility or forced update

### 6.3 Code Impact Assessment

| File / Module | Change Type | Scope |
|---------------|------------|-------|
| `core/ai/build.gradle.kts` | Dependency change | Replace llama.cpp + ONNX deps with MediaPipe |
| `LlamaCppEngine.kt` | Deprecate/keep as fallback | New `GemmaLiteRtEngine.kt` alongside |
| `OnnxEmbeddingModel.kt` | Deprecate/keep as fallback | New `GemmaEmbeddingModel.kt` alongside |
| `ExplanationEngineImpl.kt` | Minor | Remove sequential loading logic if unified model |
| `AppContainer.kt` | Minor | Wire new engine implementations |
| `LlamaAndroid.kt` (JNI wrapper) | No change | Keep for fallback |
| `PromptBuilder.kt` / templates | Update | Gemma 4 chat template format |
| Content pack builder (`build_pack.py`) | Update | New embedding model |
| All content packs (.pack files) | Rebuild | New embeddings |

---

## 7. Semantic Search Gap Analysis

### 7.1 What Exists Today

The app has **internal** semantic search via the RAG pipeline:

- `EmbeddingModel.embed(text)` converts questions to 384-dim vectors
- `ContentRetriever.retrieve(embedding, packId, topK)` finds similar chunks
- `ExplanationEngineImpl` orchestrates this as part of the generation pipeline

But this capability is **not exposed to the user as a search feature**. The only content discovery mechanisms are:

1. **TopicsScreen** — Hierarchical CAPS browser (Term → Strand → Topic)
2. **Chat interface** — Type a question and get a full AI-generated explanation

### 7.2 What's Missing: User-Facing Semantic Search

| Capability | Status | User Impact |
|------------|--------|-------------|
| Search bar in TopicsScreen | ❌ Missing | Learner must browse manually through CAPS hierarchy |
| Natural language topic search | ❌ Missing | Learner can't type "how to divide fractions" and see matching topics |
| Search results list (without LLM) | ❌ Missing | Every query triggers full LLM generation (~15 s), even for topic discovery |
| Cross-pack search | ❌ Missing | Can only search within a single pack at a time |
| Search suggestions / autocomplete | ❌ Missing | No guidance for learners on what content is available |
| Search history | ❌ Missing | No way to return to previously found topics |

### 7.3 Why Semantic Search Matters for eZansiEdgeAI

1. **Speed** — Semantic search returns results in < 100 ms (embed + cosine search). This is 100× faster than waiting for LLM generation.

2. **Browse-to-learn pattern** — US-04 ("Browse topics by CAPS term and strand") could be enhanced with search. Learners with specific questions shouldn't need to navigate a 4-level hierarchy.

3. **Content discovery** — Learners don't always know the CAPS terminology. A learner might search "how to share things equally" and find the fractions topic.

4. **Battery efficiency** — Search that doesn't trigger LLM generation uses dramatically less CPU/battery.

5. **Low digital literacy support** — Typing a natural language query is more intuitive than navigating a curriculum hierarchy for learners with limited app experience.

---

## 8. Semantic Search Implementation Plan

### 8.1 Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Semantic Search Flow                   │
│                                                          │
│   Learner types in search bar: "dividing fractions"      │
│         │                                                │
│         ▼                                                │
│   ┌─────────────────┐                                    │
│   │ EmbeddingModel   │  embed("dividing fractions")      │
│   │ ~10 ms           │  → 384-dim vector                 │
│   └────────┬────────┘                                    │
│            │                                             │
│            ▼                                             │
│   ┌─────────────────┐                                    │
│   │ ContentRetriever │  retrieve(embedding, topK=10)     │
│   │ < 1 ms           │  → ranked list of matching chunks │
│   └────────┬────────┘                                    │
│            │                                             │
│            ▼                                             │
│   ┌─────────────────────────────────────────────────┐    │
│   │ Search Results UI                                │    │
│   │                                                  │    │
│   │  🔍 "dividing fractions"                         │    │
│   │                                                  │    │
│   │  📄 Division of Fractions (score: 0.92)         │    │
│   │     Term 2 › Fractions › Division                │    │
│   │     "To divide fractions, multiply by the..."    │    │
│   │     [Ask AI ▶]                                   │    │
│   │                                                  │    │
│   │  📄 Fraction Basics (score: 0.78)               │    │
│   │     Term 1 › Fractions › Basics                  │    │
│   │     "A fraction represents parts of a whole..."  │    │
│   │     [Ask AI ▶]                                   │    │
│   │                                                  │    │
│   │  📄 Equivalent Fractions (score: 0.71)          │    │
│   │     Term 1 › Fractions › Equivalence             │    │
│   │     "Equivalent fractions have the same value..." │    │
│   │     [Ask AI ▶]                                   │    │
│   └─────────────────────────────────────────────────┘    │
│                                                          │
│   Total time: < 100 ms (no LLM needed)                   │
└─────────────────────────────────────────────────────────┘
```

### 8.2 Required Components

#### 8.2.1 New Interface: `ContentSearchEngine`

```
interface ContentSearchEngine {
    suspend fun search(query: String, maxResults: Int = 10): List<SearchResult>
}

data class SearchResult(
    val chunkId: String,
    val packId: String,
    val title: String,
    val snippet: String,        // First ~100 chars of content
    val capsCode: String,       // e.g., "term2.fractions.division"
    val relevanceScore: Float,  // 0.0–1.0
)
```

This reuses the existing `EmbeddingModel` and `ContentRetriever` but returns results without triggering LLM generation.

#### 8.2.2 UI Components

| Component | Description | Priority |
|-----------|-------------|----------|
| Search bar in TopicsScreen | TextField with search icon, triggers on submit or debounced input | Must |
| SearchResultsList | LazyColumn of SearchResult cards showing title, snippet, score, CAPS path | Must |
| "Ask AI" button per result | Tapping navigates to ChatScreen with the topic pre-filled | Should |
| Empty results state | "No matching topics found" with suggestion to try different words | Must |
| Search history | Recent searches stored in SharedPreferences | Could |

#### 8.2.3 ViewModel: `SearchViewModel`

- Holds search query state
- Debounces input (300 ms)
- Calls `ContentSearchEngine.search(query)`
- Exposes `StateFlow<SearchUiState>` with results list

### 8.3 Implementation Steps

1. **Create `ContentSearchEngine` interface** in `core/ai` module
2. **Implement `ContentSearchEngineImpl`** — wraps EmbeddingModel + ContentRetriever
3. **Add search to `AppContainer`** — wire the new engine
4. **Create `SearchViewModel`** with debounced query handling
5. **Add search bar to TopicsScreen** (or create dedicated SearchScreen)
6. **Create `SearchResultCard` composable** — shows chunk title, snippet, relevance
7. **Wire "Ask AI" navigation** — passes selected chunk context to ChatScreen
8. **Add cross-pack search** — search all installed packs, merge and re-rank results

### 8.4 Embedding Model Consideration

Semantic search quality depends entirely on the embedding model. Currently:

- **OnnxEmbeddingModel** falls back to hash-based deterministic embedding (not semantic)
- Real ONNX inference is a TODO — the `runOnnxInference()` method returns deterministic embeddings
- This means **semantic search cannot work until real embedding inference is integrated**

**This is the critical blocker.** Before semantic search can be useful, one of these must happen:

1. Complete the ONNX Runtime integration for all-MiniLM-L6-v2 (current plan)
2. Switch to Gemma 4 1B with built-in embeddings (new option)
3. Use a lighter embedding approach (e.g., TF-IDF or BM25 for keyword search as interim)

---

## 9. Gemma 4 Embedding Capabilities

### 9.1 Unified Model Advantage

If Gemma 4 1B can provide both text generation AND text embedding:

| Aspect | Current (2 models) | Gemma 4 Unified |
|--------|-------------------|-----------------|
| Models to download | 2 (LLM + embedding) | 1 |
| Total disk | 1,153 MB | ~600 MB |
| Model load cycles per query | 2 (sequential) | 1 |
| Embedding quality | all-MiniLM-L6-v2 (specialised) | Gemma 4 1B (general-purpose) |
| Dependencies | llama.cpp + ONNX Runtime | MediaPipe only |
| Maintenance burden | 2 model update paths | 1 model update path |

### 9.2 Embedding Quality Risk

**Important caveat:** all-MiniLM-L6-v2 is a dedicated sentence embedding model that achieved **100% retrieval accuracy** in P0-002 testing. Gemma 4 1B's embedding mode is a secondary capability of a generative model — it may not match the retrieval quality of a purpose-built embedding model.

**Mitigation:** The P0-002 test suite (20 queries against 50 CAPS chunks) provides a ready-made validation benchmark. Any alternative embedding model must pass the same ≥ 80% top-3 accuracy threshold.

### 9.3 Hybrid Approach

An alternative is to use Gemma 4 for generation only and keep all-MiniLM-L6-v2 for embeddings:

| Approach | Pros | Cons |
|----------|------|------|
| **Gemma 4 for both** | Single model, simpler pipeline, smaller disk | Embedding quality risk |
| **Gemma 4 + MiniLM** | Best-in-class embeddings, faster generation | Two model types, two runtimes |
| **Keep Qwen + add search** | No model migration risk | Slow generation persists |

---

## 10. Risk Assessment

### 10.1 Gemma 4 Migration Risks

| Risk | Severity | Likelihood | Mitigation |
|------|----------|------------|------------|
| Gemma 4 1B maths quality < Qwen2.5 | High | Medium | Benchmark with P0-001's 12 CAPS prompts before committing |
| RAM exceeds budget on 4 GB devices | High | Low | Gemma 4 1B INT4 estimated at ~800 MB — well within budget |
| MediaPipe SDK size bloats APK | Medium | Medium | Measure APK size impact; MediaPipe is modular |
| Content pack re-embedding required | Medium | High (if embedding model changes) | Automate via `build_pack.py`; test with P0-002 queries |
| Gemma 4 license restrictions | Low | Low | Gemma ToU is permissive; review before adoption |
| LiteRT GPU delegation fails on target devices | Medium | Medium | NNAPI + CPU fallback paths; test on Samsung A04s/Redmi 10C |
| Prompt template format change breaks existing packs | Medium | High | Update template engine; version prompts in packs |
| Gemma 4 1B context window too small | Medium | Low | 8K context should be sufficient for 2K prompts |

### 10.2 Semantic Search Risks

| Risk | Severity | Likelihood | Mitigation |
|------|----------|------------|------------|
| Embedding model not producing real embeddings | **Critical** | **Current** | Must integrate real ONNX or Gemma embedding inference |
| Low retrieval quality on diverse queries | Medium | Low | 100% accuracy on test set; expand test queries for edge cases |
| Search UI adds complexity for low-literacy users | Medium | Medium | Simple search bar with clear results; user testing |
| Search drains battery (repeated embedding) | Low | Low | Embedding is ~10 ms — negligible vs LLM generation |

---

## 11. Recommendations

### 11.1 Primary Recommendation: Spike Gemma 4 1B (P0-006)

**Create a new spike (P0-006) to benchmark Gemma 4 1B** against the existing P0-001 and P0-002 criteria before making any architectural decisions. This follows the project's established evidence-based decision-making pattern (ADR 0006, ADR 0007).

**Spike scope:**
- Download Gemma 4 1B INT4 (LiteRT format)
- Benchmark generation quality and speed using P0-001's 12 CAPS prompts
- Benchmark embedding quality using P0-002's 20 test queries
- Measure RAM, disk, and latency on emulator with ARM64
- Compare MediaPipe SDK integration complexity vs llama.cpp
- Produce spike report with go/no-go recommendation

**Expected outcome:** If Gemma 4 1B passes both P0-001 generation criteria AND P0-002 embedding criteria, it becomes a compelling replacement that:
- Halves disk usage (~600 MB vs 1,153 MB)
- Halves RAM usage (~800 MB vs 1,839 MB)
- Doubles+ generation speed (~2–4 s vs 8 s)
- Eliminates sequential model loading
- Simplifies the dependency graph to a single SDK

### 11.2 Secondary Recommendation: Implement Semantic Search

**Implement user-facing semantic search regardless of model choice.** The RAG pipeline already has the building blocks — this is primarily a UI/feature gap, not an infrastructure gap.

**Prerequisite:** Real embedding inference must be working (either ONNX or Gemma 4). The current hash-based fallback cannot support semantic search.

**Approach:**
1. If Gemma 4 spike succeeds → build search with Gemma 4 embeddings
2. If Gemma 4 spike fails → complete ONNX Runtime integration for all-MiniLM-L6-v2, then build search
3. **Interim option:** Implement keyword-based search (SQLite FTS5) as a quick win while embedding inference is being integrated. This provides basic search functionality without requiring the embedding model.

### 11.3 Edge Device Consideration

Gemma 4 4B or 12B could serve as the edge-device model (Phase 3), replacing the "TBD" placeholder in `models/edge-models/README.md`. This would give a consistent Gemma model family across phone and edge:
- **Phone:** Gemma 4 1B (INT4, ~600 MB)
- **Edge:** Gemma 4 4B or 12B (INT8, ~5–15 GB on Raspberry Pi 5)

---

## 12. Next Steps

### Immediate (this sprint)

- [ ] **Create P0-006 spike scaffold** — mirror P0-001/P0-002 structure under `spikes/p0-006-gemma4-evaluation/`
- [ ] **Download Gemma 4 1B INT4** — from Google/HuggingFace in LiteRT format
- [ ] **Review Gemma 4 license** — confirm compatibility with sideload distribution

### Short-term (1–2 sprints)

- [ ] **Run P0-006 benchmark** — 12 CAPS prompts for generation, 20 queries for embedding
- [ ] **Produce spike report** — go/no-go recommendation for Gemma 4 adoption
- [ ] **Draft ADR** — if benchmark passes, create ADR-0012 for model switch
- [ ] **Implement semantic search UI** — search bar + results list (model-independent)
- [ ] **Implement `ContentSearchEngine`** — reuse embedding + retrieval for search

### Medium-term (if Gemma 4 adopted)

- [ ] **Implement `GemmaLiteRtEngine`** — MediaPipe-based LLM engine
- [ ] **Implement `GemmaEmbeddingModel`** — embedding mode from same model
- [ ] **Update prompt templates** — Gemma 4 chat template format
- [ ] **Re-embed content packs** — with Gemma 4 1B embeddings
- [ ] **Remove sequential loading constraint** — unified model simplifies pipeline
- [ ] **Update PRD and architecture docs** — reflect new model choice

---

## 13. References

### Internal Documents

- [ADR 0006 — Qwen2.5-1.5B as on-device LLM](../../ejs-docs/adr/0006-qwen25-1.5b-as-on-device-llm.md)
- [ADR 0007 — Embedding model, vector store, storage budget](../../ejs-docs/adr/0007-embedding-model-vector-store-storage-budget.md)
- [P0-001 Spike Report — LLM Inference](../../spikes/p0-001-llm-inference/reports/spike-report.md)
- [P0-002 Spike Report — Embedding + Retrieval](../../spikes/p0-002-embedding-retrieval/reports/spike-report.md)
- [Phone Architecture](../architecture/phone-architecture.md)
- [PRD V1](../product/prd-v1.md)
- [Constraints](../product/constraints.md)

### External Resources

- [Gemma 4 Android Developer Blog Post](https://android-developers.googleblog.com/2026/04/gemma-4-new-standard-for-local-agentic-intelligence.html)
- [Google Gemma Models on HuggingFace](https://huggingface.co/google/gemma-4-1b-it)
- [MediaPipe GenAI for Android](https://developers.google.com/mediapipe/solutions/genai)
- [LiteRT (formerly TF Lite) for Android](https://ai.google.dev/edge/litert)
- [Sentence Transformers — all-MiniLM-L6-v2](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2)

---

*This document is a research output. No architectural decisions have been made. All recommendations require validation through the project's spike → benchmark → ADR process.*
