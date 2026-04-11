# Phone Architecture — eZansiEdgeAI

## Overview

The learner's phone is the primary — and often only — compute surface. Every
feature must run entirely on-device. The phone stack is organised into four
layers: App, AI, Data, and Hardware.

---

## Hardware Constraints (Design Envelope)

| Parameter | Minimum | Target | Notes |
|-----------|---------|--------|-------|
| Android version | 10 (API 29) | 12+ | Broad device coverage |
| RAM | 4 GB | 4–6 GB | ~3 GB usable; LLM uses ~1.8 GB, ~1.2 GB for embedding + app. Raised from 3 GB after P0-001 |
| Storage (APK) | 50 MB | 50 MB | App code only, no models bundled |
| Storage (models) | 600 MB | 1.2 GB | Gemma 4 unified (~600 MB); legacy LLM (~1,066 MB) + embedding (~87 MB) |
| Storage (packs) | 200 MB | 1 GB | Per content pack, including pre-computed embeddings |
| CPU | ARMv8-A (64-bit) | Cortex-A75+ | NEON SIMD required for inference |
| NPU | Not assumed | Delegated if present | NNAPI / Qualcomm QNN |
| Network | None | WiFi (LAN) | Internet never required |

---

## Layer 1 — App Layer

```
┌──────────────────────────────────────────────┐
│                  App Layer                    │
│                                              │
│  ┌────────────┐  ┌──────────────────────┐    │
│  │ Learning   │  │ Chat Interface       │    │
│  │ UI         │  │ (text input/output)  │    │
│  └────────────┘  └──────────────────────┘    │
│  ┌────────────┐  ┌──────────────────────┐    │
│  │ Voice UI   │  │ Content Library      │    │
│  │ (V2 stub)  │  │ Browser             │    │
│  └────────────┘  └──────────────────────┘    │
│  ┌──────────────────────────────────────┐    │
│  │ Personal Learning Profile Manager   │    │
│  └──────────────────────────────────────┘    │
│                                              │
└──────────────────────────────────────────────┘
```

### Learning UI
- Displays curriculum topics organised by CAPS structure.
- Lets learner browse available content before asking questions.
- Shows worked examples with step-by-step breakdown.

### Chat Interface
- Single-turn and multi-turn text conversation.
- Input: typed text (V1). Voice via edge STT (V2 stub wired but inactive).
- Output: formatted markdown — headings, bullet lists, LaTeX-lite math
  notation rendered inline.

### Personal Learning Profile Manager
- Learner-controlled preferences stored locally:
  - Explanation style: `concise | detailed | step-by-step`
  - Example type: `real-world | abstract | visual-description`
  - Language preference: `en | af` (V1)
  - Reading level: `basic | intermediate`
- Editable at any time. No login required.
- Exported as encrypted JSON; never leaves device without explicit action.

### Content Library Browser
- Lists installed content packs with version and size.
- Shows per-topic coverage status.
- Manages pack installation, update, and deletion.

---

## Layer 2 — AI Layer

The AI Layer exposes two public APIs — `ExplanationEngine` (RAG pipeline) and
`ContentSearchEngine` (semantic search) — and forbids direct model access from
the App Layer.

### Primary Stack: Gemma 4 (Unified Model)

| Property | Value |
|----------|-------|
| Model | Gemma 4 1B INT4 |
| Runtime | MediaPipe GenAI SDK / LiteRT |
| Size | ~600 MB (single model for both modes) |
| Embedding dimension | 768 (configurable: 256/384/512/768) |
| Context window | 2048 tokens |
| Inference target | ≤5 s end-to-end on Snapdragon 680-class GPU |
| GPU delegation | LiteRT GPU delegate, NNAPI fallback, CPU fallback |
| Peak RAM (models) | ≤1,200 MB |

The unified model serves both embedding and generation — no sequential
unload/reload cycle. This halves peak RAM vs. the legacy two-model approach.

### Legacy Fallback Stack

Retained behind the `useGemma` flag in `AppContainer` for rollback safety:

| Component | Choice | Size |
|-----------|--------|------|
| LLM | Qwen2.5-1.5B-Instruct Q4_K_M (GGUF, llama.cpp) | ~1,066 MB |
| Embedding | all-MiniLM-L6-v2 (ONNX Runtime) | ~87 MB |

Legacy engines use sequential loading (embed → unload → load LLM → generate)
with ≤2 GB peak RAM. They will be deprecated after F5 device validation passes.

### Retrieval-First Approach

The LLM never answers from its parametric memory alone. Every response follows
this pipeline:

1. **Embed** the learner's question using the local embedding model.
2. **Search** the local vector DB for the top-k relevant content chunks
   (k = 3–5, configurable).
3. **Score** retrieval relevance. If below threshold → tell the learner the
   topic is not in the current content pack (do not hallucinate).
4. **Build prompt** from template:

```
[SYSTEM]
You are a Grade 6 Maths tutor. Answer ONLY using the provided content.
If the content does not cover the question, say so.
Adapt your explanation to the learner's preferences.

[PREFERENCES]
Style: {learner.explanation_style}
Examples: {learner.example_type}
Level: {learner.reading_level}

[CONTENT]
{retrieved_chunks}

[QUESTION]
{learner_question}
```

5. **Infer** using the local quantized LLM.
6. **Post-process** — extract worked example steps, format math notation,
   apply length limits.

### Embedding

| Property | Gemma 4 (primary) | MiniLM (legacy fallback) |
|----------|-------------------|--------------------------|
| Architecture | Gemma 4 1B embedding mode | all-MiniLM-L6-v2 |
| Runtime | MediaPipe GenAI SDK | ONNX Runtime |
| Size | Shared (~600 MB total) | ~87 MB |
| Dimension | 768 (configurable) | 384 |
| Latency | < 100 ms per query | < 50 ms per query |

Embeddings for content chunks are **pre-computed** at pack build time and
shipped inside the pack (schema v2 = 768-dim, schema v1 = 384-dim).
Only the query needs to be embedded at runtime.

### Generation

| Property | Gemma 4 (primary) | Qwen2.5 (legacy fallback) |
|----------|-------------------|---------------------------|
| Model | Gemma 4 1B INT4 | Qwen2.5-1.5B-Instruct Q4_K_M |
| Runtime | MediaPipe GenAI SDK / LiteRT | llama.cpp (Android NDK) |
| Size | Shared (~600 MB total) | ~1,066 MB |
| Context window | 2048 tokens | 2048 tokens |
| GPU delegation | LiteRT GPU delegate → NNAPI → CPU | CPU only (ARM NEON) |
| Target latency | ≤ 5 s end-to-end (GPU) | < 8 s first token (CPU) |

### ContentSearchEngine (New)

Semantic search without LLM generation:

1. **Embed** the search query using the embedding model.
2. **Search** FAISS index for top-K results (< 100 ms).
3. **Rank** by cosine similarity score.
4. **Return** structured results: chunk text, topic, subtopic, CAPS alignment.
5. **(Optional)** "Ask AI" hands the selected result + query to `ExplanationEngine`.

### Prompt Template Engine

- Templates are versioned alongside content packs.
- Subject-specific templates (maths templates include step-by-step worked
  example instructions).
- Learner preferences are injected as system-prompt variables.

---

## Layer 3 — Data Layer

```
┌──────────────────────────────────────────────┐
│                 Data Layer                    │
│                                              │
│  ┌────────────────┐ ┌─────────────────────┐  │
│  │ Local Vector   │ │ Content Pack Store  │  │
│  │ DB             │ │ (versioned bundles) │  │
│  │ (SQLite +      │ │                     │  │
│  │  vec extension)│ │  chunks/            │  │
│  └────────────────┘ │  embeddings.bin     │  │
│                     │  metadata.json      │  │
│  ┌────────────────┐ │  manifest.sha256    │  │
│  │ Learner Profile│ └─────────────────────┘  │
│  │ (encrypted)    │                          │
│  └────────────────┘                          │
│                                              │
└──────────────────────────────────────────────┘
```

### Local Vector DB

- **Implementation:** SQLite with `sqlite-vec` extension (single-file,
  no server, Android-compatible).
- **Index:** Pre-built IVF-flat index shipped in pack; loaded into memory
  on pack activation.
- **Query:** Cosine similarity, top-k retrieval, filtered by topic metadata.

### Content Pack Store

Each pack is a self-contained directory:

```
maths-grade6-caps-v1.0/
├── metadata.json          # subject, grade, version, CAPS alignment map
├── manifest.sha256        # integrity hashes for all files
├── chunks/
│   ├── topic-001.md       # curriculum content in markdown
│   ├── topic-002.md
│   └── ...
├── embeddings.bin         # pre-computed vectors (384-dim, float16)
├── embedding_index.json   # chunk_id → offset mapping
└── templates/
    └── maths-explain.txt  # subject-specific prompt template
```

### Learner Profile

- Stored as encrypted JSON in app-private storage.
- Encryption: AES-256-GCM, key held in Android Keystore.
- Schema: preferences + last-used topic + pack versions installed.
- No PII beyond optional display name (learner's choice).

---

## Layer 4 — Hardware Abstraction

| Concern | Strategy |
|---------|----------|
| GPU inference | LiteRT GPU delegate (primary); fastest path for Gemma 4 |
| NPU delegation | NNAPI delegate (second fallback, auto-detected) |
| CPU inference | ARM NEON SIMD (final fallback); llama.cpp path is CPU-only |
| Memory budget | Gemma 4 unified: ≤1,200 MB; Legacy sequential: ≤2,000 MB |
| Battery | Inference capped at 30 s wall-time; background processing disabled |
| Thermal | Token generation throttled if device temp exceeds threshold |
| Storage | Packs stored on adoptable storage if available |

---

## Edge Discovery & Offload (When Available)

1. **Discovery:** mDNS service `_ezansi._tcp.local` on the school LAN.
2. **Capability query:** Phone queries edge `/capabilities` endpoint for
   available services (STT, TTS, larger model, content versions).
3. **Offload decision:** App-layer logic decides what to offload:
   - STT → always offload if available (phone has no local STT in V1).
   - TTS → offload if available.
   - LLM → only if phone inference confidence is below threshold (V2).
4. **Fallback:** If edge becomes unreachable mid-session, phone continues
   with local-only pipeline. No user-visible error.

---

## Related Documents

- [System Overview](system-overview.md)
- [Edge Device Architecture](edge-device-architecture.md)
- [Deployment Modes](deployment-modes.md)
