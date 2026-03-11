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
| Storage (models) | 1.1 GB | 1.2 GB | LLM (~1,066 MB) + embedding (~80 MB), downloaded on first launch |
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

```
┌──────────────────────────────────────────────┐
│                  AI Layer                     │
│                                              │
│  ┌──────────────────────────────────────┐    │
│  │ Explanation Engine (orchestrator)    │    │
│  └──────────┬───────────────┬───────────┘    │
│             │               │                │
│  ┌──────────▼──────┐ ┌─────▼────────────┐   │
│  │ Embedding Model │ │ Quantized LLM    │   │
│  │ (tiny, ~50 MB)  │ │ (GGUF/ONNX,      │   │
│  │                 │ │  1–2 GB)          │   │
│  └──────────┬──────┘ └─────▲────────────┘   │
│             │               │                │
│  ┌──────────▼──────────────┐│                │
│  │ Prompt Template Engine  ││                │
│  │ + Retrieval Formatter   │┘                │
│  └─────────────────────────┘                 │
│                                              │
└──────────────────────────────────────────────┘
```

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

### Embedding Model

| Property | Target |
|----------|--------|
| Architecture | all-MiniLM-L6-v2 or similar |
| Quantisation | INT8 / ONNX |
| Size | ≤ 100 MB |
| Dimension | 384 |
| Latency | < 50 ms per query on mid-range phone |

Embeddings for content chunks are **pre-computed** at pack build time and
shipped inside the pack. Only the query needs to be embedded at runtime.

### Quantized LLM

| Property | Target |
|----------|--------|
| Base model | Phi-3-mini, Qwen2-1.5B, or Gemma-2B class |
| Quantisation | Q4_K_M (GGUF) or INT4 (ONNX) |
| Size | 1–2 GB |
| Context window | 2048 tokens (sufficient for retrieval-grounded prompts) |
| Inference runtime | llama.cpp (Android NDK) or ONNX Runtime Mobile |
| Target latency | < 8 s first token, ~15 tok/s generation on Snapdragon 680 |

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
| CPU inference | llama.cpp / ONNX Runtime compiled with ARM NEON |
| NPU delegation | NNAPI delegate (auto-detected, graceful fallback) |
| Memory budget | Model loaded with mmap; inference uses streaming decode |
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
