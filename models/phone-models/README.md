# Phone-Side AI Models

Quantized Small Language Models (SLMs) optimised for **on-device inference** on learner Android phones.

## Purpose

These models power the core AI experience in eZansiEdgeAI:

- **Answer generation** — respond to learner curriculum questions with step-by-step explanations.
- **Semantic search** — embedding models enable vector similarity search over local content packs.
- **Content understanding** — comprehend CAPS-aligned Grade 6 Mathematics content to provide contextually accurate help.

All models run **entirely on the phone** with no network dependency.

## Requirements

| Constraint            | Target                                          |
| --------------------- | ----------------------------------------------- |
| Total model size      | ~1–2 GB combined (SLM + embeddings)             |
| Format                | GGUF (llama.cpp) or ONNX                        |
| Compute               | CPU-only baseline; NPU/GPU acceleration optional|
| RAM budget            | ≤1.5 GB runtime footprint on a 3 GB device      |
| Inference latency     | <5 seconds for a typical answer (target)        |
| Quality bar           | Factually correct for Grade 6 Maths (CAPS)      |

Models must produce **acceptable educational output** at these constraints — perfect prose is not required, but mathematical correctness is non-negotiable.

## What Goes Here

This directory will contain (after Phase 0 spikes):

- **Quantized SLM files** — the primary language model in GGUF or ONNX format (e.g., `phi-3-mini-Q4_K_M.gguf`)
- **Model configuration** — inference parameters, prompt templates, system prompts
- **Embedding model** — a small embedding model for vector search (e.g., quantized all-MiniLM or similar)
- **Benchmark results** — latency, quality, and memory measurements from device testing

> ⚠️ **Large model files are git-ignored.** See `models/.gitignore`. Model files must be downloaded separately or built via the tooling in `tools/`.

## Phase 0 Task: Validate On-Device Inference

The critical Phase 0 spike is to **prove that useful educational inference is possible** on target hardware:

1. Select 2–3 candidate SLMs (e.g., Phi-3 Mini, Qwen2-1.5B, TinyLlama)
2. Quantize to Q4/Q5 GGUF format
3. Deploy to a representative Android device (3 GB RAM, mid-range SoC)
4. Measure: latency, RAM usage, answer quality on Grade 6 Maths questions
5. Document results and make a go/no-go recommendation

Spike results will be captured as an ADR in `ejs-docs/adr/`.

## Git & Large Files

Large model files are excluded from version control via `models/.gitignore`. Only README files, configuration, and lightweight metadata should be committed. Model binaries should be distributed via:

- Direct download links (documented here)
- Content pack builder output
- LFS (if the team adopts Git LFS later)
