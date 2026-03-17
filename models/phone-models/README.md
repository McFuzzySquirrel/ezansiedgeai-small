# Phone-Side AI Models

Quantized Small Language Models (SLMs) optimised for **on-device inference** on learner Android phones.

## Purpose

These models power the core AI experience in eZansiEdgeAI:

- **Answer generation** — respond to learner curriculum questions with step-by-step explanations.
- **Semantic search** — embedding models enable vector similarity search over local content packs.
- **Content understanding** — comprehend CAPS-aligned Grade 6 Mathematics content to provide contextually accurate help.

All models run **entirely on the phone** with no network dependency.

## Selected Models (Phase 0 Validated)

Phase 0 spikes tested 4 LLM candidates (12 prompts × 3 runs each) and 3 embedding models (12 combinations). The following models were selected:

### On-Device LLM: Qwen2.5-1.5B-Instruct Q4_K_M

| Property | Value |
|----------|-------|
| Model | [Qwen2.5-1.5B-Instruct](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF) |
| Format | GGUF (llama.cpp) |
| Quantization | Q4_K_M |
| File size | ~1,066 MB |
| Load time | 0.78 s |
| 150-token response | ~8 s avg |
| Peak RAM | 1,839 MB |
| Context window | 2,048 tokens (configured) |

**Download:**
```bash
# From Hugging Face (requires huggingface-cli)
huggingface-cli download Qwen/Qwen2.5-1.5B-Instruct-GGUF \
  qwen2.5-1.5b-instruct-q4_k_m.gguf \
  --local-dir models/phone-models/

# Or direct download
curl -L -o models/phone-models/qwen2.5-1.5b-instruct-q4_k_m.gguf \
  "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
```

**Why this model:** Only candidate that passed all Phase 0 criteria (load < 5 s, 150-token < 10 s, RAM < 2 GB). SmolLM2-1.7B was fastest but exceeded RAM by 8%. See [ADR 0006](../../ejs-docs/adr/0006-qwen25-1.5b-as-on-device-llm.md).

### Embedding Model: all-MiniLM-L6-v2

| Property | Value |
|----------|-------|
| Model | [all-MiniLM-L6-v2](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2) |
| Format | ONNX (via ONNX Runtime Android) |
| File size | ~87 MB |
| Load time | 0.13 s |
| Query embed time | ~10 ms |
| Embedding dimensions | 384 |
| Top-3 retrieval accuracy | 100% (on test queries) |

**Download:**
```bash
# Export to ONNX format using optimum
pip install optimum[onnxruntime]
optimum-cli export onnx \
  --model sentence-transformers/all-MiniLM-L6-v2 \
  models/phone-models/all-MiniLM-L6-v2-onnx/

# Or use the pre-exported ONNX model
huggingface-cli download sentence-transformers/all-MiniLM-L6-v2 \
  --local-dir models/phone-models/all-MiniLM-L6-v2/
```

**Why this model:** 100% Top-3 retrieval accuracy across all vector store options. 87 MB fits within the 100 MB embedding budget. bge-small-en-v1.5 eliminated (128 MB exceeds limit). gte-small passed but 3× slower. See [ADR 0007](../../ejs-docs/adr/0007-embedding-model-vector-store-storage-budget.md).

## Requirements

| Constraint | Target | Validated |
|------------|--------|-----------|
| Total model size | ~1–2 GB combined (SLM + embeddings) | ✅ 1,153 MB |
| LLM format | GGUF (llama.cpp) | ✅ |
| Embedding format | ONNX (ONNX Runtime Android 1.19.0) | ✅ |
| Compute | CPU-only baseline; NPU/GPU optional | ✅ CPU-only tested |
| RAM budget | ≤ 2 GB runtime on 4 GB device | ✅ 1,839 MB peak (sequential loading) |
| Inference latency | < 10 s for a typical answer | ✅ 8 s avg |
| Quality bar | Factually correct for Grade 6 Maths (CAPS) | ✅ 5/5 smoke test questions correct |

Models are loaded **sequentially** — the embedding model is unloaded before the LLM loads to stay within the 2 GB RAM budget. They are never in memory simultaneously.

## Loading Models onto a Phone

After downloading the model files, push them to the device:

```bash
# Push LLM model
adb push models/phone-models/qwen2.5-1.5b-instruct-q4_k_m.gguf \
  /sdcard/Android/data/com.ezansi.learner/files/models/

# Push embedding model
adb push models/phone-models/all-MiniLM-L6-v2-onnx/ \
  /sdcard/Android/data/com.ezansi.learner/files/models/embeddings/
```

> **Note:** The app runs with mock AI implementations when model files are not present. All UI features work — only the AI-generated explanations are replaced with placeholder text.

## Storage Budget

| Component | Size | Notes |
|-----------|------|-------|
| LLM (Qwen2.5-1.5B Q4_K_M) | ~1,066 MB | Downloaded on first setup |
| Embedding (all-MiniLM-L6-v2) | ~87 MB | Downloaded on first setup |
| **Total models** | **~1,153 MB** | Within 1.4 GB first-launch budget |

Peak RAM (sequential loading): ~554 MB active working set against a 1,161 MB budget on 4 GB devices.

## Git & Large Files

Large model files are excluded from version control via `models/.gitignore`. Only README files, configuration, and lightweight metadata should be committed. Model binaries should be distributed via:

- Direct download from Hugging Face (links above)
- Content pack builder output
- LFS (if the team adopts Git LFS later)

## Decision Records

| ADR | Decision |
|-----|----------|
| [ADR 0006](../../ejs-docs/adr/0006-qwen25-1.5b-as-on-device-llm.md) | Qwen2.5-1.5B selected as on-device LLM |
| [ADR 0007](../../ejs-docs/adr/0007-embedding-model-vector-store-storage-budget.md) | all-MiniLM-L6-v2 + FAISS for embedding and retrieval |

## Spike Reports

- [LLM inference spike report](../../spikes/p0-001-llm-inference/reports/spike-report.md) — 4 models benchmarked
- [Embedding + retrieval spike report](../../spikes/p0-002-embedding-retrieval/reports/spike-report.md) — 12 combinations benchmarked
- [E2E pipeline smoke test](../../spikes/p0-005-e2e-pipeline/reports/smoke-test-report.md) — full pipeline validated
