# Feature: Gemma 4 Model Migration & Semantic Search

## 1. Feature Overview

**Feature Name:** Gemma 4 Model Migration & User-Facing Semantic Search  
**Parent PRD:** [docs/product/prd-v1.md](prd-v1.md)  
**Status:** Draft  
**Summary:** Replace the current Qwen2.5-1.5B (llama.cpp) + all-MiniLM-L6-v2 (ONNX) dual-model stack with Google Gemma 4 1B (INT4) via MediaPipe/LiteRT — a single model that handles both text generation and embedding. This halves disk usage (~600 MB vs 1,153 MB), halves RAM (~800 MB vs 1,839 MB), enables GPU acceleration (2–4× faster generation), and eliminates sequential model loading. Simultaneously, expose the existing RAG retrieval capability as a user-facing semantic search feature — allowing learners to find content in < 100 ms without triggering full LLM generation.  
**Scope:**
- **In scope:** Gemma 4 1B validation spike, GemmaLiteRtEngine + GemmaEmbeddingModel implementation, ContentSearchEngine + search UI, prompt template migration, content pack re-embedding, updated build tooling, offline model distribution via sideload bundle
- **Out of scope:** Gemma 4 4B/12B for edge device (Phase 3), voice features, multi-language generation, keyword-based FTS5 interim search, search history, search autocomplete/suggestions

**Offline Model Distribution:** The Gemma 4 1B model file (~600 MB) is bundled into the sideload distribution package alongside the APK and content packs — consistent with the project's zero-network constraint. There is no first-launch download. The model is pre-installed via the same Bluetooth/USB/SD card distribution channel used for the APK itself (see constraints.md §3, prd-v1.md §6.2).

**Hybrid Fallback Strategy:** Phase F1 (spike) produces a go/no-go decision with three possible outcomes:
1. Gemma 4 generation ✅ + Gemma 4 embedding ✅ → **Unified path** (proceed as planned)
2. Gemma 4 generation ✅ + Gemma 4 embedding ❌ → **Hybrid path** (Gemma 4 for LLM + keep MiniLM for embedding; search still viable)
3. Gemma 4 generation ❌ → **No migration** (feature paused; complete ONNX integration for MiniLM and build search with existing models)

---

## 2. Context: Existing System State

**Completed PRD Phases:**
- ✅ Phase 0: Feasibility Validation — all spikes complete (P0-001 through P0-005)
- ✅ Phase 1: Offline Learning Loop — Android app scaffold, chat interface, content pack loader, AI pipeline integration, learner profiles, topic browser, prompt template engine, onboarding

**Relevant Existing Components:**

| Component | File/Module | Relevance |
|-----------|-------------|-----------|
| LLM engine interface | `core/ai/.../llm/LlmEngine.kt` | New `GemmaLiteRtEngine` implements this interface |
| LlamaCppEngine | `core/ai/.../llm/LlamaCppEngine.kt` | Deprecated (kept as fallback) |
| Embedding model interface | `core/ai/.../embedding/EmbeddingModel.kt` | New `GemmaEmbeddingModel` implements this interface |
| OnnxEmbeddingModel | `core/ai/.../embedding/OnnxEmbeddingModel.kt` | Deprecated (kept as fallback) |
| ExplanationEngineImpl | `core/ai/.../ExplanationEngineImpl.kt` | Remove sequential loading logic |
| ContentRetriever | `core/ai/.../retrieval/ContentRetriever.kt` | Reused by new ContentSearchEngine |
| AppContainer (DI) | `app/.../di/AppContainer.kt` | Wire new engine implementations |
| TopicsScreen | `feature/topics/.../TopicsScreen.kt` | Add search bar |
| PromptBuilder / templates | `core/ai/.../prompt/` | Update for Gemma 4 chat template format |
| build_pack.py | `tools/content-pack-builder/` | Switch embedding model |
| Content packs | `content-packs/` | Re-embed with Gemma 4 embeddings |

**Existing Agents Involved:** ai-pipeline-engineer, android-ui-engineer, content-pack-engineer, project-architect, qa-test-engineer

**Established Conventions:**
- Interface-based abstractions for all AI components (LlmEngine, EmbeddingModel, ContentRetriever)
- Manual DI via AppContainer (ADR-0009)
- JUnit 5 + kotlin-test for all tests; Robolectric for Android-dependent tests
- 4-layer architecture (App → AI → Data → Hardware) with strict boundary rules
- Evidence-based decisions via spike → benchmark → ADR process
- Sequential model loading constraint (AI-08) — to be eliminated by this feature
- 2 GB RAM budget, < 10 s generation target, fully offline

---

## 3. Feature Goals and Non-Goals

### 3.1 Goals

- **Halve storage footprint** — single ~600 MB model replaces two models (1,153 MB combined)
- **Halve RAM usage** — ~800 MB peak vs 1,839 MB, freeing headroom on 4 GB devices
- **Double+ generation speed** — ~2–4 s (GPU-accelerated) vs ~8 s (CPU-only) for 150-token generation
- **Enable GPU acceleration** — LiteRT GPU delegate with NNAPI fallback on target hardware
- **Eliminate sequential model loading** — single model serves both embedding and generation
- **Simplify dependency graph** — MediaPipe replaces llama.cpp JNI + ONNX Runtime
- **Deliver user-facing semantic search** — learners find content in < 100 ms by typing natural language queries
- **Reduce battery drain** — faster inference + search-without-LLM dramatically reduce CPU usage
- **Maintain quality bar** — Gemma 4 must pass P0-001 generation criteria AND P0-002 retrieval criteria
- **Re-embed all content packs** — ensure content packs use Gemma 4 embeddings for consistent retrieval

### 3.2 Non-Goals

- Changing the overall app architecture or navigation structure
- Adding Gemma 4 4B/12B for edge devices (separate Phase 3 concern)
- Implementing search history or search autocomplete/suggestions
- Adding keyword-based FTS5 search as an interim solution
- Changing learner data storage, profiles, or preferences
- Modifying the content pack SQLite schema (only embeddings change)
- Adding multi-language generation
- Removing Qwen2.5/MiniLM code entirely (kept as fallback during transition)

---

## 4. User Stories

| ID | As a... | I want to... | So that... | Priority |
|----|---------|-------------|-----------|----------|
| FT-US-01 | Learner (Thandiwe) | Get AI explanations in 3–5 seconds instead of 15–25 seconds | I don't lose focus waiting and can study more effectively | Must |
| FT-US-02 | Learner (Sipho) | Type "how to share things equally" in a search bar and see matching topics instantly | I can find the fractions content without knowing CAPS terminology | Must |
| FT-US-03 | Learner (Thandiwe) | Tap a search result to ask the AI about that specific topic | I can go from discovery to explanation in two taps | Should |
| FT-US-04 | Learner (Sipho) | See search results in < 1 second without waiting for AI generation | I can browse content quickly on my shared phone | Must |
| FT-US-05 | Teacher (Ms. Dlamini) | Distribute an app that uses half the storage of the current version | More of my learners' phones have enough space for it | Should |
| FT-US-06 | Content Creator (Dr. Ndaba) | Re-embed content packs with the new model using build_pack.py | My packs work with the new search and AI engine | Must |
| FT-US-07 | Learner (Thandiwe) | Use the app for a 30-minute session with less battery drain | My phone still has battery for the walk home | Should |

---

## 5. Technical Approach

### 5.1 Impact on Existing Architecture

**AI Layer Changes:**

| File | Change | Detail |
|------|--------|--------|
| `core/ai/build.gradle.kts` | Dependency swap | Remove llama.cpp JNI + ONNX Runtime deps; add MediaPipe GenAI SDK |
| `LlamaCppEngine.kt` | Deprecate | Keep as fallback; annotate `@Deprecated` |
| `OnnxEmbeddingModel.kt` | Deprecate | Keep as fallback; annotate `@Deprecated` |
| `ExplanationEngineImpl.kt` | Modify | Remove sequential load/unload cycle; use unified model |
| `PromptBuilder.kt` / templates | Modify | Update to Gemma 4 chat template format (`<start_of_turn>` markers) |
| `AppContainer.kt` | Modify | Wire `GemmaLiteRtEngine` + `GemmaEmbeddingModel` + `ContentSearchEngine` |

**UI Layer Changes:**

| File | Change | Detail |
|------|--------|--------|
| `TopicsScreen.kt` | Modify | Add search bar (TextField with search icon) above topic hierarchy |
| New: `SearchResultCard.kt` | Create | Composable showing chunk title, snippet, relevance score, CAPS path |
| New: `SearchViewModel.kt` | Create | Debounced query handling, StateFlow<SearchUiState> |
| `EzansiNavHost.kt` | Modify | Wire "Ask AI" navigation from search results to ChatScreen |

**Content Tooling Changes:**

| File | Change | Detail |
|------|--------|--------|
| `build_pack.py` | Modify | Switch embedding model from all-MiniLM-L6-v2 to Gemma 4 1B |
| `validate_pack.py` | Modify | Update embedding validation for new dimensions |
| All `.pack` files | Rebuild | Re-embed with Gemma 4 embeddings |

### 5.2 New Components

| Component | Proposed Path | Description |
|-----------|---------------|-------------|
| `GemmaLiteRtEngine` | `core/ai/.../llm/GemmaLiteRtEngine.kt` | `LlmEngine` implementation using MediaPipe GenAI API |
| `GemmaEmbeddingModel` | `core/ai/.../embedding/GemmaEmbeddingModel.kt` | `EmbeddingModel` implementation using Gemma 4 embedding mode |
| `ContentSearchEngine` | `core/ai/.../search/ContentSearchEngine.kt` | Interface for user-facing semantic search |
| `ContentSearchEngineImpl` | `core/ai/.../search/ContentSearchEngineImpl.kt` | Wraps EmbeddingModel + ContentRetriever for search-only queries |
| `SearchResult` | `core/ai/.../search/SearchResult.kt` | Data class: chunkId, packId, title, snippet, capsCode, relevanceScore |
| `SearchViewModel` | `feature/topics/.../SearchViewModel.kt` | ViewModel with debounced search, StateFlow |
| `SearchResultCard` | `feature/topics/.../SearchResultCard.kt` | Composable for search result display |
| `SearchUiState` | `feature/topics/.../SearchUiState.kt` | Sealed class: Idle, Loading, Results, Empty, Error |
| Spike scaffold | `spikes/p0-006-gemma4-evaluation/` | Benchmark scripts and report template |

### 5.3 Technology Additions

| Technology | Version | Purpose | Compatibility |
|-----------|---------|---------|---------------|
| **MediaPipe GenAI SDK** | Latest stable (2026) | LLM inference + embedding on Android via LiteRT | Min SDK 24 (our target: 29) ✅ |
| **Gemma 4 1B INT4** | google/gemma-4-1b-it (LiteRT format) | Unified LLM + embedding model (~600 MB) | Gemma ToU (permissive) ✅ |
| **LiteRT GPU delegate** | Bundled with MediaPipe | OpenCL/Vulkan GPU acceleration | Adreno/Mali/PowerVR GPUs on target devices |

**Removed dependencies:**
- llama.cpp JNI bindings (kept as fallback, not active)
- ONNX Runtime for Android (kept as fallback, not active)

---

## 6. Functional Requirements

| ID | Requirement | Affects Existing | Priority |
|----|-------------|-----------------|----------|
| FT-FR-01 | Implement `GemmaLiteRtEngine : LlmEngine` using MediaPipe GenAI API for text generation | No (new implementation of existing interface) | Must |
| FT-FR-02 | Implement `GemmaEmbeddingModel : EmbeddingModel` using Gemma 4 embedding mode with configurable dimensions (384 default) | No (new implementation of existing interface) | Must |
| FT-FR-03 | Enable LiteRT GPU delegate with NNAPI fallback for hardware acceleration | No (new capability) | Must |
| FT-FR-04 | Update prompt templates to Gemma 4 chat format (`<start_of_turn>user`/`<start_of_turn>model` markers) | Yes (PromptBuilder.kt, template files) | Must |
| FT-FR-05 | Remove sequential model loading from ExplanationEngineImpl — single model handles embed + generate | Yes (ExplanationEngineImpl.kt) | Must |
| FT-FR-06 | Implement `ContentSearchEngine` interface with `search(query, maxResults): List<SearchResult>` | No (new component) | Must |
| FT-FR-07 | Implement `ContentSearchEngineImpl` wrapping EmbeddingModel + ContentRetriever for search-without-generation | No (new component) | Must |
| FT-FR-08 | Add search bar to TopicsScreen — TextField with search icon, triggers on submit or after 300 ms debounce | Yes (TopicsScreen.kt) | Must |
| FT-FR-09 | Display search results as ranked list showing title, snippet (~100 chars), CAPS path, and relevance score | No (new UI component) | Must |
| FT-FR-10 | Each search result has "Ask AI ▶" button that navigates to ChatScreen with topic pre-filled | Yes (EzansiNavHost.kt navigation) | Should |
| FT-FR-11 | Search across all installed content packs, merge and re-rank results | No (new capability) | Must |
| FT-FR-12 | Empty results state: "No matching topics found" with suggestion to try different words | No (new UI state) | Must |
| FT-FR-13 | Update `build_pack.py` to use Gemma 4 1B for embedding | Yes (build_pack.py) | Must |
| FT-FR-14 | Re-embed all existing content packs with Gemma 4 embeddings | Yes (content pack files) | Must |
| FT-FR-15 | Validate re-embedded packs pass P0-002's ≥ 80% top-3 retrieval accuracy | No (validation step) | Must |
| FT-FR-16 | Wire new implementations in AppContainer with model-selection config for fallback | Yes (AppContainer.kt) | Must |
| FT-FR-17 | Gemma 4 1B must pass P0-001's 12 CAPS prompt quality criteria before adoption | No (validation gate) | Must |
| FT-FR-18 | Support Gemma 4 INT4 quantisation with KV cache tuned for 2048-token context | No (new engine config) | Must |
| FT-FR-19 | Keep LlamaCppEngine and OnnxEmbeddingModel as deprecated fallbacks during transition | Yes (deprecation annotations) | Should |
| FT-FR-20 | Verify MediaPipe SDK makes zero runtime network calls — no DNS, no TLS, no HTTP, no analytics | No (compliance check) | Must |
| FT-FR-21 | Verify no new Android permissions introduced by MediaPipe dependency | No (compliance check) | Must |
| FT-FR-22 | Verify app functions on Huawei/no-GMS devices with MediaPipe SDK | No (compatibility check) | Must |
| FT-FR-23 | Preserve MockLlmEngine and MockEmbeddingModel for CI and local development (no real model required) | Yes (test infrastructure) | Must |
| FT-FR-24 | Bundle Gemma 4 model file in sideload distribution package — no first-launch download | No (distribution) | Must |
| FT-FR-25 | Detect incompatible pack embedding version at load time and display clear upgrade message | No (new capability) | Must |

---

## 7. Non-Functional Requirements

| ID | Requirement | Priority |
|----|-------------|----------|
| FT-NF-01 | End-to-end generation latency ≤ 5 seconds on target device (Gemma 4 1B + GPU delegate) | Must |
| FT-NF-02 | Semantic search returns results in < 100 ms (embed query + retrieve, no LLM) | Must |
| FT-NF-03 | Total model disk footprint ≤ 700 MB (single Gemma 4 1B INT4 model) | Must |
| FT-NF-04 | Peak RAM ≤ 1,200 MB during inference (down from 1,839 MB) — maintaining ≥ 2 GB free headroom on 4 GB devices | Must |
| FT-NF-05 | APK size increase from MediaPipe SDK ≤ 15 MB | Should |
| FT-NF-06 | Battery drain ≤ 2% per 30-minute session (improved from 3% target) | Should |
| FT-NF-07 | Graceful fallback to CPU-only inference if GPU delegate fails | Must |
| FT-NF-08 | Search UI frame rendering < 16 ms (60 fps) during result display | Must |
| FT-NF-09 | Cold start time not regressed (< 3 seconds) | Must |
| FT-NF-10 | No thermal throttling during normal search + occasional generation use pattern | Should |

---

## 8. Agent Impact Assessment

### 8.1 Existing Agents — Extended Responsibilities

| Agent | New Responsibilities | Modified Boundaries |
|-------|---------------------|-------------------|
| **ai-pipeline-engineer** | Implement GemmaLiteRtEngine + GemmaEmbeddingModel + ContentSearchEngine. Configure LiteRT GPU delegate. Update prompt templates for Gemma 4 format. Remove sequential loading from ExplanationEngineImpl. Wire unified model in pipeline. **Provide reference embedding implementation and contract for content-pack-engineer's Python tooling** (embedding dimensions, normalisation, distance metric). | Now owns MediaPipe/LiteRT integration instead of llama.cpp + ONNX. Owns new ContentSearchEngine domain. Prompt template format changes. Co-owns embedding contract with content-pack-engineer. |
| **android-ui-engineer** | Add search bar to TopicsScreen. Create SearchResultCard composable. Create SearchViewModel with debounced query. Wire "Ask AI" navigation from search results. Handle SearchUiState (idle/loading/results/empty/error). | TopicsScreen gains search capability. New composables and ViewModel in topics feature module. |
| **content-pack-engineer** | Update build_pack.py to use Gemma 4 1B embeddings (Python-side — may require non-Android embedding path e.g. `gemma.cpp` or HuggingFace Transformers). Re-embed all existing content packs. Update validate_pack.py for new embedding dimensions. Version-bump re-embedded packs. **Coordinate with ai-pipeline-engineer on embedding contract.** | Embedding model dependency changes. Python tooling may need a different Gemma inference path than the Android MediaPipe SDK. |
| **project-architect** | Add MediaPipe SDK dependency to core/ai module. Remove (or deprecate) llama.cpp and ONNX Runtime dependencies. Audit MediaPipe license (verify no GPL transitive deps). Measure APK size impact. | Dependency graph changes significantly. Must verify MediaPipe compatibility with min SDK 29 and no-GMS constraint. |
| **qa-test-engineer** | Create P0-006 spike benchmark suite. Test GemmaLiteRtEngine against P0-001 criteria. Test GemmaEmbeddingModel against P0-002 criteria. Test semantic search (new test scenarios). Regression test existing chat + topic flows. Performance benchmark (latency, RAM, battery). | New spike testing domain. New search feature testing. Performance targets change (faster is expected). |

### 8.2 New Agents Required

No new agents required. All work falls within existing agent domains.

### 8.3 Existing Agents — No Changes

| Agent | Reason |
|-------|--------|
| **learner-data-engineer** | Reviewer role only — "Ask AI" → ChatScreen handoff may affect chat context/history behavior. No code changes needed, but should review the navigation contract. |
| **edge-node-engineer** | Edge device model selection (Gemma 4 4B/12B) is Phase 3 and out of scope for this feature. |

---

## 9. Implementation Phases

### Phase F1: Gemma 4 Validation Spike (P0-006)

- [ ] Create spike scaffold at `spikes/p0-006-gemma4-evaluation/` mirroring P0-001/P0-002 structure
- [ ] Download Gemma 4 1B INT4 in LiteRT format from Google/HuggingFace
- [ ] Review Gemma 4 license (Gemma Terms of Use) — confirm compatibility with sideload distribution and zero-cost constraints
- [ ] Benchmark generation quality: run P0-001's 12 CAPS prompts × 3 runs against Gemma 4 1B
- [ ] Benchmark generation speed: measure load time, 150-token generation time, throughput (tok/s), peak RAM
- [ ] Benchmark embedding quality: run P0-002's 20 test queries, measure top-3 retrieval accuracy (must ≥ 80%)
- [ ] Test LiteRT GPU delegate on ARM64 emulator — measure GPU vs CPU-only performance delta
- [ ] Measure MediaPipe SDK size impact on APK
- [ ] Produce spike report with go/no-go recommendation
- [ ] If go: draft ADR for model switch (ADR-0012 or next available)

**Exit criteria:** Gemma 4 1B passes both P0-001 generation criteria AND P0-002 embedding criteria. Decision gate:
- **Unified path** (gen ✅ + embed ✅): proceed with F2–F5 as planned — single Gemma 4 model
- **Hybrid path** (gen ✅ + embed ❌): proceed with F2 (LLM only), skip GemmaEmbeddingModel, complete ONNX integration for MiniLM, reorder F3/F4 for MiniLM embeddings
- **No migration** (gen ❌): feature paused; evaluate alternative models or complete current ONNX integration

### Phase F2: Gemma 4 Engine Integration

- [ ] Add MediaPipe GenAI SDK dependency to `core/ai/build.gradle.kts`
- [ ] Implement `GemmaLiteRtEngine : LlmEngine` — MediaPipe GenAI API for text generation
- [ ] Implement `GemmaEmbeddingModel : EmbeddingModel` — Gemma 4 embedding mode with configurable dimensions
- [ ] Configure LiteRT GPU delegate with NNAPI fallback and CPU-only fallback chain
- [ ] Update prompt templates for Gemma 4 chat format (`<start_of_turn>` markers)
- [ ] Remove sequential model loading from `ExplanationEngineImpl` — use unified model
- [ ] Update `AppContainer` — wire new Gemma implementations with config-based model selection
- [ ] Deprecate `LlamaCppEngine` and `OnnxEmbeddingModel` (keep as fallback, annotate `@Deprecated`)
- [ ] Preserve `MockLlmEngine` and `MockEmbeddingModel` for CI and local development (no real model needed)
- [ ] Verify zero runtime network calls from MediaPipe SDK (no analytics, no telemetry, no DNS)
- [ ] Verify no new Android permissions introduced by MediaPipe
- [ ] Test on Huawei/no-GMS device or emulator config
- [ ] Unit tests for GemmaLiteRtEngine (mock MediaPipe API)
- [ ] Unit tests for GemmaEmbeddingModel
- [ ] Integration test: end-to-end pipeline with Gemma 4 (embed → retrieve → generate)

### Phase F3: Content Pack Re-embedding & Validation

- [ ] Define embedding contract between ai-pipeline-engineer and content-pack-engineer (dimensions, normalisation, distance metric)
- [ ] Update `build_pack.py` — integrate Gemma 4 1B for embedding computation (Python-side: evaluate `gemma.cpp`, HuggingFace Transformers, or Google GenAI Python SDK)
- [ ] Update `validate_pack.py` — adjust embedding dimension validation
- [ ] Re-embed all existing content packs with Gemma 4 embeddings
- [ ] Run P0-002 retrieval accuracy test suite on re-embedded packs (must pass ≥ 80% top-3)
- [ ] Version-bump all re-embedded packs
- [ ] Archive original MiniLM-embedded packs (git tag before re-embedding)
- [ ] Update pack README / documentation
- [ ] End-to-end validation: generation pipeline with re-embedded packs

### Phase F4: Semantic Search

- [ ] Create `ContentSearchEngine` interface in `core/ai` — `search(query, maxResults): List<SearchResult>`
- [ ] Create `SearchResult` data class (chunkId, packId, title, snippet, capsCode, relevanceScore)
- [ ] Implement `ContentSearchEngineImpl` — wraps EmbeddingModel + ContentRetriever
- [ ] Implement cross-pack search — query all installed packs, merge and re-rank by relevance
- [ ] Wire `ContentSearchEngine` in `AppContainer`
- [ ] Create `SearchUiState` sealed class (Idle, Loading, Results, Empty, Error)
- [ ] Create `SearchViewModel` with debounced input (300 ms) and StateFlow<SearchUiState>
- [ ] Add search bar to `TopicsScreen` — TextField with search icon above topic hierarchy
- [ ] Create `SearchResultCard` composable — title, snippet, CAPS path, relevance score
- [ ] Implement "Ask AI ▶" navigation — passes selected topic to ChatScreen
- [ ] Implement empty results state with helpful messaging
- [ ] Unit tests for ContentSearchEngineImpl
- [ ] Unit tests for SearchViewModel
- [ ] UI tests for search bar + results display

### Phase F5: Performance Validation & Hardening

- [ ] Benchmark end-to-end latency on emulator (target: ≤ 5 s generation, < 100 ms search)
- [ ] Benchmark on **real target devices** (Samsung A04s, Redmi 10C, or equivalent 4 GB RAM ARM device) — GPU delegate, RAM pressure, thermal, battery
- [ ] Benchmark RAM usage (target: ≤ 1,200 MB peak)
- [ ] Benchmark disk footprint (target: ≤ 700 MB model)
- [ ] Battery drain test (target: ≤ 2% per 30-min session)
- [ ] GPU delegate failure fallback test — verify graceful degradation to CPU
- [ ] Thermal throttling test under sustained search + generation workload
- [ ] Regression test: all existing chat, topic, profile, library flows still work
- [ ] Verify mock/dev path: app builds and tests pass without real model files present
- [ ] Privacy/compliance audit: zero runtime network calls, no new permissions, no analytics SDKs, POPIA-compliant
- [ ] Huawei/no-GMS device compatibility test
- [ ] Accessibility audit on search UI (WCAG 2.1 AA, TalkBack, 48×48 dp targets)
- [ ] APK size audit — verify MediaPipe SDK impact ≤ 15 MB
- [ ] Verify offline model distribution: model bundled in sideload package, installs without network
- [ ] Update architecture documentation to reflect new model and pipeline

---

## 10. Testing Strategy

| Level | Scope | Approach |
|-------|-------|----------|
| **Spike Benchmarks** | Gemma 4 1B quality and performance | P0-001's 12 CAPS prompts (generation), P0-002's 20 queries (embedding/retrieval) |
| **Unit Tests** | GemmaLiteRtEngine, GemmaEmbeddingModel, ContentSearchEngineImpl, SearchViewModel | JUnit 5 + kotlin-test with mocked MediaPipe API; test edge cases (empty query, no results, model load failure) |
| **Integration Tests** | End-to-end pipeline with Gemma 4 | Embed → retrieve → generate flow; search → display flow |
| **UI Tests** | Search bar, SearchResultCard, navigation | Compose testing framework; verify debounce, empty states, "Ask AI" navigation |
| **Regression Tests** | Existing chat, topics, profiles, preferences, library | Re-run all existing 285 tests; verify no regressions |
| **Performance Tests** | Latency, RAM, battery, thermal | Benchmark suite on **emulator AND real 4 GB RAM target devices** against NFR targets (FT-NF-01 through FT-NF-10) |
| **Content Validation** | Re-embedded packs | P0-002 retrieval accuracy suite (20 queries) on all re-embedded packs |
| **Privacy/Compliance** | MediaPipe SDK behaviour | Zero network calls, no new permissions, no analytics, Huawei/no-GMS compat |

**Key test scenarios:**

1. Gemma 4 1B generates correct explanation for each of P0-001's 12 CAPS prompts
2. Gemma 4 1B embedding achieves ≥ 80% top-3 accuracy on P0-002's 20 queries
3. Search returns relevant results for "dividing fractions" in < 100 ms
4. Search returns relevant results for diverse queries beyond the P0-002 set (at least 10 additional natural-language queries)
5. Search returns empty state for gibberish input
6. Search across multiple installed packs returns merged, ranked results
7. "Ask AI" from search result navigates to ChatScreen with correct context
8. GPU delegate failure falls back to CPU without crash or ANR
9. App cold start not regressed (< 3 s)
10. Existing chat flow works identically with Gemma 4 engine
11. Re-embedded content packs load and retrieve correctly
12. App builds and all tests pass with mock engines (no real model files — CI path)
13. MediaPipe SDK makes zero runtime network calls (verified via network monitor)
14. App works on Huawei/no-GMS device configuration
15. Model installs from sideload bundle without requiring network

---

## 11. Rollback Considerations

**Modified existing files:**
- `ExplanationEngineImpl.kt` — sequential loading removed; restore if reverting
- `AppContainer.kt` — new engine wiring; restore old wiring
- `TopicsScreen.kt` — search bar added; remove search composables
- `EzansiNavHost.kt` — search navigation added; remove routes
- `core/ai/build.gradle.kts` — MediaPipe deps added; restore llama.cpp + ONNX deps
- Prompt template files — Gemma 4 format; restore Qwen2.5 format

**New files (safe to remove):**
- `GemmaLiteRtEngine.kt`, `GemmaEmbeddingModel.kt`
- `ContentSearchEngine.kt`, `ContentSearchEngineImpl.kt`, `SearchResult.kt`
- `SearchViewModel.kt`, `SearchResultCard.kt`, `SearchUiState.kt`
- `spikes/p0-006-gemma4-evaluation/` directory

**Content pack rollback:**
- Re-embedded packs would need to be reverted to MiniLM embeddings
- Original packs are archived via git tag before re-embedding
- `build_pack.py` changes are reversible

**Pack migration strategy:**
- Old MiniLM-embedded packs are **not compatible** with Gemma 4 embeddings — app detects pack embedding model version at load time
- If incompatible pack detected: app displays clear message directing user to obtain updated packs via the same sideload channel
- Packs and model are distributed as an atomic bundle (APK + model + packs) to avoid version mismatch in offline/sideloaded environments

**Rollback safety:**
- Deprecated `LlamaCppEngine` and `OnnxEmbeddingModel` remain in codebase as fallbacks
- AppContainer config can switch between Gemma and legacy engines
- Git branch/tag before migration provides clean rollback point

---

## 12. Acceptance Criteria

1. **Gemma 4 1B passes validation spike** — generation quality ≥ P0-001 criteria, embedding retrieval ≥ 80% top-3 accuracy (P0-002 criteria)
2. **End-to-end generation latency ≤ 5 seconds** on target device class with GPU delegation
3. **Semantic search returns results in < 100 ms** for any natural language query
4. **Search results are relevant** — P0-002's 20 test queries + at least 10 additional natural-language queries pass corpus-level retrieval benchmarks
5. **"Ask AI" navigation works** — tapping a search result opens ChatScreen with topic context pre-filled
6. **Cross-pack search** — results from all installed packs are merged and ranked by relevance
7. **Empty state** — searching for content not in any pack shows clear "no results" message
8. **Peak RAM ≤ 1,200 MB** during inference (measured on emulator AND real 4 GB target device)
9. **Total model disk ≤ 700 MB** (single Gemma 4 1B INT4 file)
10. **Re-embedded packs pass P0-002 validation** — ≥ 80% top-3 retrieval accuracy
11. **No regressions** — all existing tests pass; existing chat/topic/profile/preference/library flows work identically
12. **GPU delegate fallback** — if GPU unavailable, falls back to CPU without crash
13. **Accessibility** — search UI meets WCAG 2.1 AA, 48×48 dp touch targets, TalkBack navigable
14. **ADR documented** — model switch decision recorded as ADR-0012 (or next available)
15. **Cold start not regressed** — app launches in < 3 seconds
16. **Privacy/compliance** — zero runtime network calls from MediaPipe SDK, no new Android permissions, no analytics/telemetry SDK behaviour, POPIA-compliant
17. **Huawei/no-GMS compatible** — app functions correctly on devices without Google Mobile Services
18. **Offline distribution** — model + packs distributed via sideload bundle; installs without any network connectivity
19. **Real-device validation** — latency, RAM, battery, thermal targets verified on representative 4 GB RAM ARM device (not emulator only)
20. **Mock/dev path preserved** — app builds and all tests pass with mock engines when real model files are absent (CI compatibility)

---

## 13. Open Questions

| # | Question | Default Assumption |
|---|----------|--------------------|
| 1 | What embedding dimension should Gemma 4 use? (256, 384, 512, 768) | 384 (matches current MiniLM dimensions, no pack schema change needed) |
| 2 | Should search be integrated into TopicsScreen or be a separate SearchScreen? | Integrated into TopicsScreen — search bar above topic hierarchy, results replace hierarchy when active |
| 3 | Should the minimum relevance score threshold filter out low-quality results? | Yes — results with relevance score < 0.3 are hidden; threshold tuned during testing |
| 4 | Should old packs with MiniLM embeddings still load (backward compatibility)? | No — app detects embedding version and prompts for updated packs via sideload channel |
| 5 | What Python-side Gemma inference approach should build_pack.py use? | Evaluate in order: Google GenAI Python SDK → HuggingFace Transformers → gemma.cpp; must run offline |
| 6 | What happens if the spike fails (Gemma gen ❌)? | Feature paused; complete ONNX Runtime integration for MiniLM; build search with existing embedding model |
| 7 | What happens if Gemma embedding fails but generation passes (hybrid path)? | Use Gemma 4 for LLM + keep MiniLM for embedding; search still viable; sequential loading retained |
