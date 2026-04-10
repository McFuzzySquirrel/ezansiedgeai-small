# P0-006: Device Validation Checklist

> **Purpose:** Guide for running Gemma 4 benchmarks on a real target device.
> Emulator results are insufficient for the F1.6 decision gate.

## Prerequisites

- [ ] Target device: 4 GB RAM, ARM64, Snapdragon 680-class or equivalent
- [ ] Android 10+ (API 29+)
- [ ] USB debugging enabled
- [ ] ADB connection verified (`adb devices`)
- [ ] Gemma 4 1B INT4 model file (`.task` format) available on device
- [ ] Debug APK with MediaPipe SDK built and installed

## Setup

```bash
# Build debug APK
cd apps/learner-mobile
./gradlew assembleDebug

# Install on device
adb install -r app/build/outputs/apk/debug/ezansi-v0.1.0-debug.apk

# Push model to device storage
adb push models/gemma4-1b-it-int4.task /sdcard/Download/ezansi-models/
```

## Test 1: Generation Performance

Run each test and record results below.

### 1.1 Model Loading
| Metric | Target | Actual | Pass? |
|--------|--------|--------|-------|
| Load time (cold start) | < 5s | ___s | ☐ |
| Load time (warm start) | < 3s | ___s | ☐ |
| Memory after load | < 800 MB | ___MB | ☐ |

### 1.2 Text Generation (GPU)
Run the 12 CAPS prompts (from spike config.yaml). Record for each:

| Prompt | Latency (s) | Tokens | Quality (1-5) | Notes |
|--------|-------------|--------|---------------|-------|
| fractions-add | | | | |
| decimals-multiply | | | | |
| area-rectangle | | | | |
| ratio-simplify | | | | |
| percentage-convert | | | | |
| symmetry-line | | | | |
| data-mean | | | | |
| volume-cube | | | | |
| number-patterns | | | | |
| time-elapsed | | | | |
| capacity-convert | | | | |
| geometry-angles | | | | |

**Averages:**
| Metric | Target | Actual | Pass? |
|--------|--------|--------|-------|
| Avg latency (GPU) | ≤ 5s | ___s | ☐ |
| Peak RAM during gen | ≤ 1,200 MB | ___MB | ☐ |
| Avg quality score | ≥ 3.5/5 | ___/5 | ☐ |

### 1.3 Text Generation (CPU fallback)
Disable GPU delegate and repeat 3 representative prompts:

| Metric | Target | Actual | Pass? |
|--------|--------|--------|-------|
| Avg latency (CPU) | ≤ 10s | ___s | ☐ |
| CPU fallback works | Yes | ☐ | ☐ |

## Test 2: Embedding Performance

### 2.1 Embedding Generation
| Metric | Target | Actual | Pass? |
|--------|--------|--------|-------|
| Embed time per query | ≤ 100ms | ___ms | ☐ |
| Embedding dimensions | 768 | ___ | ☐ |
| Embeddings normalized | Yes | ☐ | ☐ |

### 2.2 Retrieval Accuracy
Embed the 12 CAPS content chunks, then query with their questions:

| Metric | Target | Actual | Pass? |
|--------|--------|--------|-------|
| Top-3 retrieval accuracy | ≥ 80% | __% | ☐ |
| Top-1 retrieval accuracy | ≥ 60% | __% | ☐ |

## Test 3: Platform Validation

| Check | Target | Actual | Pass? |
|-------|--------|--------|-------|
| APK size (with MediaPipe) | ≤ 15 MB increase | ___MB delta | ☐ |
| GMS-free (test on AOSP/Huawei) | Works | ☐ | ☐ |
| No network calls | 0 calls | ☐ | ☐ |
| No new permissions | 0 added | ☐ | ☐ |

## Test 4: Stress Tests

| Check | Target | Actual | Pass? |
|-------|--------|--------|-------|
| 15-min sustained generation | No thermal throttle >20% | ☐ | ☐ |
| 30-min mixed use battery drain | < 10% | __% | ☐ |
| Memory after 20 generations | No leak (stable ±50 MB) | ☐ | ☐ |
| Model reload after backgrounding | < 5s | ___s | ☐ |

## Test 5: Cross-Platform Parity

| Check | Target | Actual | Pass? |
|-------|--------|--------|-------|
| Export Android embeddings JSON | ☐ | ☐ | ☐ |
| Run `validate_parity.py` | cos > 0.99 | ___ | ☐ |

## Decision

Based on results above:

- [ ] **UNIFIED** — Gen ✅ + Embed ✅ → Proceed with F2–F5
- [ ] **HYBRID** — Gen ✅ + Embed ❌ → Gemma 4 for LLM, keep MiniLM for embedding
- [ ] **NO MIGRATION** — Gen ❌ → Pause feature, complete ONNX integration

**Decision date:** _______________
**Decision maker:** _______________
**Notes:** _______________

## Exporting Results

After completing all tests:

```bash
# Copy results from device
adb pull /sdcard/Download/ezansi-results/ results/

# Or manually create results/device-benchmarks.json with the format:
{
  "device": "Samsung A04s",
  "android_version": "13",
  "generation": { "avg_latency_gpu_s": X, "avg_latency_cpu_s": X, "peak_ram_mb": X },
  "embedding": { "avg_time_ms": X, "retrieval_accuracy_top3": X },
  "platform": { "apk_size_delta_mb": X, "gms_free": true, "network_calls": 0 },
  "stress": { "thermal_throttle_pct": X, "battery_drain_pct": X }
}

# Generate final report
python scripts/report_generator.py
```
