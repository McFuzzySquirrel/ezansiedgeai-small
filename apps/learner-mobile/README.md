# Learner Mobile App

The **primary runtime** for eZansiEdgeAI — an Android application that delivers offline AI-powered learning support directly on learners' personal or school-issued devices.

## Purpose

The learner mobile app provides:

- **Offline question & answer** — learners ask curriculum questions and receive AI-generated answers without internet connectivity.
- **Content retrieval** — search and browse curriculum-aligned content packs stored locally on the device.
- **Explanation generation** — the on-device SLM generates step-by-step explanations tailored to the learner's grade level.
- **Practice support** — guided problem-solving with hints and worked examples.

Everything runs **entirely on-device**. No cloud dependency. No data costs. No connectivity assumptions.

## Target Device Specs

| Requirement       | Specification                              |
| ----------------- | ------------------------------------------ |
| OS                | Android 10+ (API 29+)                      |
| RAM               | 3–6 GB (must function usably at 3 GB)      |
| Storage           | ~2–4 GB free for app + model + content     |
| Connectivity      | None required (offline-first by design)    |
| Optional          | School WiFi for content sync, edge node    |

The app **must** deliver an acceptable experience on low-end devices commonly found in South African township and rural schools.

## Key Components

1. **Learning UI** — simple, text-first interface for asking questions and viewing explanations.
2. **Chat Interface** — conversational interaction layer between the learner and the on-device AI.
3. **Local Content Library** — versioned, offline content packs containing curriculum material, embeddings, and metadata.
4. **AI Layer** — quantized Small Language Model (SLM) running inference locally via GGUF/ONNX runtime on CPU or NPU.
5. **Data Layer** — local vector database for semantic search over content pack embeddings, plus SQLite for app state and usage telemetry.

## V1 Scope

- **Subject:** Grade 6 Mathematics
- **Curriculum:** CAPS-aligned (South African national curriculum)
- **Interface:** Text-based (voice input/output deferred to Phase 3 edge node STT/TTS)
- **Language:** English (with Afrikaans support planned; African language phrase layer in roadmap)
- **Platform:** Android only

## Setup Instructions

> **Phase 1** — setup instructions will be added once the technology stack is finalised after Phase 0 spike results.

Placeholder steps:

```
1. Install prerequisites (TBD)
2. Clone repository
3. Download/place quantized model (see models/phone-models/)
4. Build and deploy to device/emulator
```

## Technology Decisions

> **To be decided in Phase 0 spikes.** Key open questions:
>
> - Android runtime: Kotlin native vs React Native vs Flutter
> - On-device inference engine: llama.cpp / ONNX Runtime / MLC LLM
> - Local vector DB: sqlite-vss / LanceDB / custom
> - Model selection: which SLM, what quantization level
>
> Spike results will be captured as ADRs in `ejs-docs/adr/`.
