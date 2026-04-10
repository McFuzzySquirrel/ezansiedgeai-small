# P0-006: Gemma 4 Model Evaluation Spike

> **Goal:** Validate that Gemma 4 1B (INT4, LiteRT format) via MediaPipe GenAI SDK
> can replace the current Qwen2.5-1.5B (llama.cpp) + all-MiniLM-L6-v2 (ONNX) dual-model
> stack — achieving comparable or better generation quality, embedding/retrieval accuracy,
> lower latency (GPU-accelerated), and reduced RAM/disk footprint.

## Context

- **Feature PRD:** `docs/product/feature-gemma4-semantic-search.md`
- **Research:** `docs/research/gemma4-model-evaluation-and-semantic-search.md`
- **P0-001 baseline:** Qwen2.5-1.5B generation benchmarks (12 CAPS prompts × 3 runs)
- **P0-002 baseline:** all-MiniLM-L6-v2 embedding/retrieval benchmarks (20 test queries)

## Acceptance Criteria

### Generation (must match or exceed P0-001 baseline)
- [ ] Gemma 4 1B loads in < 5 seconds on emulator
- [ ] Generates coherent 150-token response in ≤ 5 seconds (GPU), < 10 seconds (CPU fallback)
- [ ] Peak RAM ≤ 1,200 MB during inference
- [ ] Quality passes P0-001's 12 CAPS prompt criteria (coherent, curriculum-aligned, grounded)

### Embedding (must match or exceed P0-002 baseline)
- [ ] Produces embeddings in < 100 ms per query
- [ ] Top-3 retrieval accuracy ≥ 80% on P0-002's 20 test queries
- [ ] Embedding dimensions configurable (256/384/512/768)

### Platform
- [ ] GPU delegate works on ARM64 emulator (OpenCL/Vulkan)
- [ ] Graceful CPU fallback when GPU unavailable
- [ ] MediaPipe SDK APK size impact ≤ 15 MB
- [ ] Zero runtime network calls from MediaPipe SDK
- [ ] No GMS dependency (works on Huawei/AOSP)

### Cross-Platform Parity
- [ ] Gemma 4 embeddings reproducible between Android (MediaPipe) and Python (offline builder)
- [ ] Same query produces same/similar vectors on both platforms (cosine similarity > 0.99)

## Decision Gate (3-way)

After benchmarks complete on **real target device** (Snapdragon 680-class, 4 GB RAM):

| Outcome | Condition | Action |
|---------|-----------|--------|
| **Unified path** | Gen ✅ + Embed ✅ | Proceed with F2–F5 as planned |
| **Hybrid path** | Gen ✅ + Embed ❌ | Gemma 4 for LLM + keep MiniLM for embedding |
| **No migration** | Gen ❌ | Feature paused; complete ONNX integration |

## How to Run

### 1. Set Up Environment

```bash
cd spikes/p0-006-gemma4-evaluation
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

### 2. Download Model

```bash
python scripts/download_model.py
```

Downloads Gemma 4 1B INT4 (LiteRT format) to `models/` (git-ignored).

### 3. Run Generation Benchmarks

```bash
# Benchmark generation quality + speed (uses P0-001 prompts)
python scripts/benchmark_generation.py

# With specific device constraints
python scripts/benchmark_generation.py --memory-limit 1200
```

### 4. Run Embedding Benchmarks

```bash
# Benchmark embedding quality + retrieval accuracy (uses P0-002 queries)
python scripts/benchmark_embedding.py

# Test embedding parity with Android
python scripts/benchmark_embedding.py --parity-check
```

### 5. Run Platform Checks

```bash
# Check MediaPipe SDK size impact, permissions, network calls
python scripts/platform_audit.py
```

### 6. Generate Report

```bash
python scripts/report_generator.py
```

Generates `reports/spike-report.md` from results.

## Real-Device Validation Checklist

⚠️ **Emulator benchmarks are insufficient for the go/no-go decision.**

Run on a representative 4 GB RAM ARM device (Samsung A04s, Redmi 10C, or equivalent):

1. [ ] Install APK with MediaPipe SDK + Gemma 4 model
2. [ ] Run generation benchmark — record tok/s, latency, peak RAM
3. [ ] Run embedding benchmark — record ms/query, retrieval accuracy
4. [ ] Test GPU delegate — record GPU vs CPU performance delta
5. [ ] Monitor thermal throttling during 15-min sustained use
6. [ ] Measure battery drain during 30-min mixed use
7. [ ] Verify app functions on Huawei/no-GMS device
8. [ ] Record all results in `results/device-benchmarks.json`

## Output

- `reports/spike-report.md` — Structured spike report with go/no-go recommendation
- `results/generation-benchmarks.json` — Raw generation benchmark data
- `results/embedding-benchmarks.json` — Raw embedding/retrieval data
- `results/platform-audit.json` — SDK size, permissions, network audit
- `results/device-benchmarks.json` — Real-device results (manual)
