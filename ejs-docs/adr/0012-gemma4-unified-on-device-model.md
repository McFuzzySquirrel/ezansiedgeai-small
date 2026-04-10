---
ejs:
  type: journey-adr
  version: 1.1
  adr_id: "0012"
  title: Gemma 4 1B as unified on-device model (supersedes ADR-0006 and ADR-0007)
  date: 2026-04-11
  status: accepted
  session_id: ejs-session-2026-04-11-01
  session_journey: ejs-docs/journey/2026/ejs-session-2026-04-11-01.md

actors:
  humans:
    - id: Doug McCusker
      role: Project Lead
  agents:
    - id: GitHub Copilot (project-orchestrator)
      role: Feature execution orchestrator
    - id: ai-pipeline-engineer
      role: Engine integration, search API, architecture docs
    - id: content-pack-engineer
      role: Pack re-embedding, schema v2
    - id: android-ui-engineer
      role: Search UI components
    - id: project-architect
      role: Dependency management
    - id: qa-test-engineer
      role: Regression, privacy, accessibility audits

context:
  repo: ezansiedgeai-small
  branch: feature/gemma4
---

# ADR 0012 — Gemma 4 1B as Unified On-Device Model

> **Supersedes**: ADR-0006 (Qwen2.5-1.5B as on-device LLM), ADR-0007 (all-MiniLM-L6-v2 embedding model)

## Session Journey

[ejs-session-2026-04-11-01](../journey/2026/ejs-session-2026-04-11-01.md)

---

# Context

eZansiEdgeAI Phase 1 shipped with a **dual-model AI stack**:
- **Generation**: Qwen2.5-1.5B (GGUF) via llama.cpp JNI (~850 MB, CPU-only)
- **Embedding**: all-MiniLM-L6-v2 via ONNX Runtime (~90 MB, 384-dim)

This dual-stack had three problems:
1. **Sequential model loading** — could not run both models concurrently; required unloading one to load the other, adding latency
2. **No GPU acceleration** — llama.cpp on Android required custom JNI builds with no stable GPU delegate path
3. **Large combined footprint** — ~940 MB for two separate model files on constrained devices (4 GB RAM target)

Google released **Gemma 4 1B** (June 2025) with official MediaPipe GenAI SDK support, INT4 quantization (~600 MB), and built-in GPU delegation via LiteRT.

---

# Session Intent

Migrate from the dual-model stack to Gemma 4 1B as a single unified model for both generation and embedding, and add user-facing semantic search as the first feature leveraging the new embedding capability.

---

# Collaboration Summary

The feature was executed across 5 phases (F1–F5) over a single orchestrated session:
- Research spike validated Gemma 4 feasibility (F1)
- Decision gate chose UNIFIED path (Gemma 4 for both gen + embed) over HYBRID or ABORT
- 6 specialist agents coordinated by project-orchestrator built 38 tasks
- Rubber-duck critiques at 3 key milestones caught blind spots (embedding parity, model contract, audit gaps)
- Privacy and accessibility audits identified and fixed 6 issues before merge

---

# Decision Trigger / Significance

This decision fundamentally changes the AI runtime stack — the core technology that powers all AI features in the app. It affects:
- Model files distributed with the app
- Content pack format (embedding dimensions)
- Runtime inference pipeline
- Memory and performance characteristics
- Every future AI feature built on top

This warrants an ADR because it supersedes two prior ADRs (0006, 0007) and changes the project's technical foundation.

---

# Considered Options

## Option A: UNIFIED — Gemma 4 for both generation and embedding
- Single ~600 MB model file
- MediaPipe GenAI SDK with GPU delegation
- 768-dim embeddings from the same model
- No sequential loading — shared model instance

## Option B: HYBRID — Gemma 4 for generation, keep MiniLM for embedding
- Gemma 4 for generation (GPU-accelerated)
- Keep all-MiniLM-L6-v2 for embedding (proven retrieval accuracy)
- Still requires two model files, sequential loading for embedding

## Option C: ABORT — Keep current dual stack
- No migration risk
- No GPU acceleration
- No semantic search (MiniLM embedding not exposed to users)
- Technical debt continues

---

# Decision

**Option A: UNIFIED** — Use Gemma 4 1B INT4 as the single model for both generation and embedding via MediaPipe GenAI SDK.

---

# Rationale

1. **Reduced footprint**: ~600 MB vs ~940 MB (36% smaller)
2. **GPU acceleration**: MediaPipe LiteRT provides GPU delegate → NNAPI → CPU fallback chain
3. **No sequential loading**: Single model instance shared between generation and embedding eliminates the unload/reload latency
4. **Simpler architecture**: One model provider, one runtime, one dependency
5. **Embedding quality**: 768-dim embeddings (vs 384-dim) provide richer semantic representation
6. **Official support**: Google's MediaPipe SDK is maintained, GMS-free, Apache 2.0 licensed
7. **Semantic search enabled**: Unified embedding makes user-facing search feasible without a second model

**Why not HYBRID?** The primary advantage of MiniLM was proven retrieval accuracy, but real Gemma 4 embedding inference isn't available yet (hash-based placeholder). When MediaPipe adds the embedding API, the unified path will be strictly better. Building for unified now avoids a second migration later.

**Why not ABORT?** The dual-stack's limitations (no GPU, sequential loading, large footprint) directly impact the user experience on target 4 GB RAM devices.

---

# Consequences

### Positive
- Single model file reduces storage by ~340 MB
- GPU delegation enables ≤5s generation (vs ~10s CPU-only)
- Peak RAM target ≤1,200 MB (vs ≤2,000 MB with sequential loading)
- Semantic search adds a new user-facing feature
- Content packs at 768-dim are forward-compatible with real Gemma 4 embeddings
- Legacy fallback preserved behind `useGemma` flag — zero-risk rollback

### Negative / Trade-offs
- **Embedding is currently hash-based** — real semantic embedding requires MediaPipe to expose an embedding API (or a custom approach). Search results are not yet truly semantic.
- **Content packs must be re-embedded** — schema v2 (768-dim) is incompatible with v1 (384-dim). PackVersionDetector handles detection, but old packs need rebuilding.
- **Android hash differs from Python hash** — `text.hashCode()` + `java.util.Random` (Android) vs SHA-256 + MT19937 (Python). Cross-platform parity will only be achieved with real model inference.
- **Real-device validation still pending** — all benchmarks run on emulator. Performance on actual 4 GB RAM Snapdragon 680-class devices is unconfirmed.

---

# Key Learnings

1. **Spike-first validation works** — The F1 spike + decision gate pattern gave confidence to commit to UNIFIED before building 30+ tasks on top of it.
2. **Hash-based embedding is a useful placeholder** — It enabled building and testing the full search pipeline (UI, ViewModel, API) without waiting for real model inference.
3. **Combined agent tasks reduce conflicts** — Merging F4.4+F4.5 into a single agent avoided file-level merge conflicts on TopicsScreen.kt and NavHost.kt.
4. **Audit early, fix early** — Running privacy/accessibility audits as part of the build (F5) caught issues that would have been harder to fix post-merge.

---

# Agent Guidance

Instructions for future agents working on this codebase:

- **Primary AI stack**: Gemma 4 1B via MediaPipe GenAI SDK (`GemmaLiteRtEngine`, `GemmaEmbeddingModel`)
- **Legacy fallback**: Qwen2.5 + MiniLM behind `useGemma` flag in `AppContainer` — remove after real-device validation passes
- **Embedding reality**: `OnnxEmbeddingModel` and `GemmaEmbeddingModel` both use hash-based deterministic embedding. Real semantic embedding is a TODO — watch for MediaPipe embedding API release.
- **Content packs**: Schema v2 (768-dim, model `gemma4-1b`). Use `PackVersionDetector` to check compatibility. Never assume v1 packs work with v2 engines.
- **Search pipeline**: `ContentSearchEngine` → embed → retrieve → merge → re-rank. Search accuracy will improve dramatically once real embeddings replace hash-based ones.
- **Privacy rule**: Never log query text. Log query length only (`"Search failed for query (${query.length} chars)"`).
- **Accessibility**: All search UI uses `label` (not `placeholder`), `defaultMinSize` (not fixed `size`), `LiveRegionMode.Polite` for state changes.

---

# Reuse Signals (Optional)

```yaml
reuse:
  patterns:
    - "Spike → decision gate → build pattern for high-risk migrations"
    - "Hash-based embedding placeholder to unblock pipeline development"
    - "PackVersionDetector for forward/backward content compatibility"
    - "useGemma flag for zero-risk legacy fallback during migration"
  prompts:
    - "When adding a new AI model, create a spike with benchmark harness first"
    - "When changing embedding dimensions, update both Python builder and Android loader"
  anti_patterns:
    - "Do not log user query text in any search/AI error path"
    - "Do not remove legacy deps before real-device validation confirms new stack"
    - "Do not use placeholder text instead of label on search inputs (accessibility)"
  future_considerations:
    - "Replace hash-based embedding with real Gemma 4 inference when MediaPipe adds API"
    - "Remove legacy deps (llama.cpp, ONNX Runtime) after F5.7 device validation passes"
    - "Consider ADR-0013 for embedding API integration when available"
```
