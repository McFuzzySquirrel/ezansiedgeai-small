# P0-006: Gemma 4 Model Evaluation — Spike Report

> **Date:** YYYY-MM-DD
> **Model:** Gemma 4 1B IT (INT4, LiteRT)
> **Runtime:** MediaPipe GenAI SDK
> **Device:** [target device name]

---

## 1. Executive Summary

| Metric | Target | Actual | Status |
|--------|--------|--------|--------|
| Generation latency (GPU) | ≤ 5s | — | ⏳ |
| Generation latency (CPU) | ≤ 10s | — | ⏳ |
| Generation quality | ≥ 70% concept coverage | — | ⏳ |
| Peak RAM | ≤ 1,200 MB | — | ⏳ |
| Embedding latency | ≤ 100ms | — | ⏳ |
| Retrieval accuracy (top-3) | ≥ 80% | — | ⏳ |
| APK size impact | ≤ 15 MB | — | ⏳ |
| GMS-free | Yes | — | ⏳ |
| Cross-platform parity | cos > 0.99 | — | ⏳ |

**Recommendation:** ⏳ PENDING

---

## 2. Generation Benchmarks

_Fill in after running `python scripts/benchmark_generation.py` on real device._

## 3. Embedding Benchmarks

_Fill in after running `python scripts/benchmark_embedding.py` on real device._

## 4. Platform Audit

_Fill in after running `python scripts/platform_audit.py`._

## 5. Real-Device Results

_Fill in after running on target device (see README.md checklist)._

## 6. Decision Gate

| Outcome | Condition | Recommendation |
|---------|-----------|----------------|
| **Unified** | Gen ✅ + Embed ✅ | Proceed with F2–F5 |
| **Hybrid** | Gen ✅ + Embed ❌ | Gemma 4 for LLM + keep MiniLM |
| **No migration** | Gen ❌ | Pause; complete ONNX integration |

**Selected path:** _______________

## 7. Notes

- 
