# Edge Device Models

Larger AI models intended for the **optional school edge node**, providing enhanced inference quality when learners are connected to school WiFi.

## Purpose

Edge models complement the phone-side SLMs by offering:

- **Higher-quality answers** — a larger model (7B+ parameters) can produce more detailed, nuanced explanations than the phone's ≤2B model.
- **Shared STT/TTS** — speech models (e.g., Whisper small/medium) that are too large for most phones but run comfortably on a Raspberry Pi 5 with 8 GB RAM.
- **Richer semantic search** — larger embedding models for improved content retrieval accuracy.

These models are **never required** for basic functionality. The phone must work independently. Edge models are an enhancement layer.

## Design Constraints

| Constraint            | Target                                          |
| --------------------- | ----------------------------------------------- |
| Device                | Raspberry Pi 4/5 (4–8 GB RAM)                   |
| Model size            | Up to ~4 GB per model                            |
| Format                | GGUF / ONNX                                     |
| Compute               | ARM CPU; optional Coral TPU or similar           |
| Inference latency     | <10 seconds for answer generation                |
| Concurrent users      | Support 5–15 learners on school WiFi             |

## What Goes Here

This directory will contain (Phase 3):

- **Quantized inference model** — a larger SLM for edge inference (e.g., Phi-3 Small, Mistral 7B Q4)
- **Speech models** — Whisper for STT, a lightweight TTS model
- **Edge embedding model** — larger/higher-quality embedding model for semantic search
- **Model configuration** — serving parameters, batching config, resource limits

> ⚠️ **Large model files are git-ignored.** See `models/.gitignore`.

## Scope

> **Phase 3** — edge model selection and deployment is not part of the initial release.

Model selection will depend on:

- Phase 0 spike results (what works on-phone informs edge model choices)
- Actual school hardware procured
- Bandwidth and concurrency requirements observed during Phase 1–2 pilots

## Model Selection Placeholder

Candidate models for evaluation (Phase 3):

- **Inference:** TBD — likely a 7B-class model quantized to Q4/Q5
- **STT:** Whisper small or medium (multilingual for SA language support)
- **TTS:** TBD — must support English and ideally Afrikaans
- **Embeddings:** TBD — larger variant of phone embedding model

Selection criteria and benchmark results will be documented as ADRs in `ejs-docs/adr/`.
