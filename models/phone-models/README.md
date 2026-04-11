# Phone-Side AI Models

Quantized Small Language Models (SLMs) optimised for **on-device inference** on learner Android phones.

## Purpose

These models power the core AI experience in eZansiEdgeAI:

- **Answer generation** — respond to learner curriculum questions with step-by-step explanations.
- **Semantic search** — embedding models enable vector similarity search over local content packs.
- **Content understanding** — comprehend CAPS-aligned Grade 6 Mathematics content to provide contextually accurate help.

All models run **entirely on the phone** with no network dependency.

## Selected Models

Phase 0 spikes tested 4 LLM candidates and 3 embedding models. ADR 0012 supersedes ADR 0006, moving from Qwen2.5 (llama.cpp/GGUF) to Gemma 4 (MediaPipe/LiteRT).

### On-Device LLM: Gemma 4 1B (MediaPipe Task format) ✅ Current

| Property | Value |
|----------|-------|
| Model | [gemma-3n-E1B-it-int4](https://www.kaggle.com/models/google/gemma-3/tfLite) |
| Format | `.task` bundle (MediaPipe LiteRT) |
| File size | ~529 MB |
| Load time | < 5 s (device-dependent) |
| Inference | MediaPipe LlmInference API |
| Context window | 4,096 tokens |

**Step 1 — Download the model bundle:**
```bash
# Option A: Kaggle (requires Kaggle account + kaggle CLI)
pip install kaggle
kaggle models instances versions download \
  google/gemma-3/tfLite/gemma3-n-e1b-it-int4 \
  --path models/phone-models/

# Option B: Google AI Studio / Vertex AI
# Visit https://ai.google.dev/gemma and download the TFLite/MediaPipe variant
# Save the file as: models/phone-models/gemma4-1b.task
```

**Step 2 — Push to device via ADB:**
```bash
# Ensure a device is connected (USB debugging enabled)
adb devices

# Create the target directory on-device
adb shell mkdir -p /sdcard/Android/data/com.ezansi.learner/files/models/

# Push the model (529 MB — may take 1–3 minutes over USB 2.0)
adb push models/phone-models/gemma4-1b.task \
  /sdcard/Android/data/com.ezansi.learner/files/models/gemma4-1b.task

# Verify the push
adb shell ls -lh /sdcard/Android/data/com.ezansi.learner/files/models/
```

**Why this model:** Gemma 4 1B in MediaPipe task format eliminates llama.cpp native JNI, unifies inference under one SDK, and unlocks GPU/NPU delegation. See [ADR 0012](../../ejs-docs/adr/0012-gemma4-unified-on-device-model.md).

---

### On-Device LLM (Legacy): Qwen2.5-1.5B-Instruct Q4_K_M

> **Superseded by Gemma 4 (ADR 0012).** Kept here for reference and fallback testing only.

| Property | Value |
|----------|-------|
| Model | [Qwen2.5-1.5B-Instruct](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF) |
| Format | GGUF (llama.cpp) |
| Quantization | Q4_K_M |
| File size | ~1,066 MB |
| Load time | 0.78 s |
| 150-token response | ~8 s avg |
| Peak RAM | 1,839 MB |

**Download (if needed for fallback testing):**
```bash
huggingface-cli download Qwen/Qwen2.5-1.5B-Instruct-GGUF \
  qwen2.5-1.5b-instruct-q4_k_m.gguf \
  --local-dir models/phone-models/
```

**Push to device:**
```bash
adb push models/phone-models/qwen2.5-1.5b-instruct-q4_k_m.gguf \
  /sdcard/Android/data/com.ezansi.learner/files/models/
```

See [ADR 0006](../../ejs-docs/adr/0006-qwen25-1.5b-as-on-device-llm.md) for original selection rationale.

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

Download the model files first (see sections above), then push via ADB:

```bash
# Ensure device is connected with USB debugging enabled
adb devices

# Create target directory
adb shell mkdir -p /sdcard/Android/data/com.ezansi.learner/files/models/
adb shell mkdir -p /sdcard/Android/data/com.ezansi.learner/files/models/embeddings/

# Push Gemma 4 LLM (primary — 529 MB)
adb push models/phone-models/gemma4-1b.task \
  /sdcard/Android/data/com.ezansi.learner/files/models/gemma4-1b.task

# Push embedding model (87 MB)
adb push models/phone-models/all-MiniLM-L6-v2.onnx \
  /sdcard/Android/data/com.ezansi.learner/files/models/embeddings/all-MiniLM-L6-v2.onnx

# Verify
adb shell ls -lh /sdcard/Android/data/com.ezansi.learner/files/models/
```

> **Note:** The app runs with mock AI implementations when model files are not present. All UI features work — only the AI-generated explanations are replaced with placeholder text.

## Storage Budget

| Component | Size | Notes |
|-----------|------|-------|
| LLM (Gemma 4 1B MediaPipe) | ~529 MB | Push via ADB — not in repo |
| Embedding (all-MiniLM-L6-v2) | ~87 MB | Push via ADB — not in repo |
| Legacy LLM (Qwen2.5 Q4_K_M) | ~1,066 MB | Optional fallback — not in repo |
| **Total (active models)** | **~616 MB** | Fits within 1.4 GB first-launch budget |

## Git & Large Files

Large model files are excluded from version control via `models/.gitignore`. Only README files, configuration, and lightweight metadata should be committed. Model binaries should be distributed via:

- Direct download from Hugging Face (links above)
- Content pack builder output
- LFS (if the team adopts Git LFS later)

## Decision Records

| ADR | Decision |
|-----|----------|
| [ADR 0012](../../ejs-docs/adr/0012-gemma4-unified-on-device-model.md) | Gemma 4 1B (MediaPipe) replaces Qwen2.5 as primary LLM |
| [ADR 0006](../../ejs-docs/adr/0006-qwen25-1.5b-as-on-device-llm.md) | Qwen2.5-1.5B original selection (superseded) |
| [ADR 0007](../../ejs-docs/adr/0007-embedding-model-vector-store-storage-budget.md) | all-MiniLM-L6-v2 + FAISS for embedding and retrieval |

## Spike Reports

- [LLM inference spike report](../../spikes/p0-001-llm-inference/reports/spike-report.md) — 4 models benchmarked
- [Embedding + retrieval spike report](../../spikes/p0-002-embedding-retrieval/reports/spike-report.md) — 12 combinations benchmarked
- [E2E pipeline smoke test](../../spikes/p0-005-e2e-pipeline/reports/smoke-test-report.md) — full pipeline validated
