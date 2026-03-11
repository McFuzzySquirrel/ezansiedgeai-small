# P0-002 Spike Report: Local Embedding + Retrieval

> **Generated:** 2026-03-04 19:24 UTC
> **Branch:** `spike/p0-002-embedding-retrieval`

---

## 1. Objective

Validate that a tiny embedding model + local vector store can run on-device
alongside the Qwen2.5-1.5B LLM, achieve ≥ 80% retrieval accuracy on
curriculum content, and fit within the updated RAM and storage budgets.

Also validates the full on-device storage footprint (absorbed from P0-003).

## 2. Acceptance Criteria Results

| Criterion | Target | Best Result | Status |
|-----------|--------|-------------|--------|
| Embedding model load time | < 2.0s | 0.202s | ✅ |
| Query embedding time | < 500ms | 10.28ms | ✅ |
| Vector search time | < 500ms | 0.06ms | ✅ |
| Top-3 retrieval accuracy | ≥ 80% | 100.0% | ✅ |
| Peak RAM (embedding + store) | < 1161 MB | 553.5 MB | ✅ |
| Model size on disk | ≤ 100 MB | 87.3 MB | ✅ |

## 3. Model × Store Comparison

| Model | Store | Size | Load | Embed | Search | Accuracy | Peak RAM | Verdict |
|-------|-------|------|------|-------|--------|----------|----------|---------|
| all-minilm-l6-v2 | faiss-flat | 87.3 MB | 0.202s | 10.28ms | 0.06ms | 100.0% | 553.5 MB | PASS |
| all-minilm-l6-v2 | faiss-ivf | 87.3 MB | 0.135s | 10.06ms | 0.07ms | 100.0% | 544.1 MB | PASS |
| all-minilm-l6-v2 | hnswlib | 87.3 MB | 0.132s | 10.51ms | 0.06ms | 100.0% | 570.6 MB | PASS |
| all-minilm-l6-v2 | numpy-cosine | 87.3 MB | 0.132s | 10.07ms | 0.07ms | 100.0% | 567.8 MB | PASS |
| bge-small-en-v1.5 | faiss-flat | 128.1 MB | 0.173s | 18.11ms | 0.06ms | 95.0% | 613.6 MB | FAIL |
| bge-small-en-v1.5 | faiss-ivf | 128.1 MB | 0.181s | 18.48ms | 0.08ms | 95.0% | 613.6 MB | FAIL |
| bge-small-en-v1.5 | hnswlib | 128.1 MB | 0.184s | 17.38ms | 0.07ms | 95.0% | 613.8 MB | FAIL |
| bge-small-en-v1.5 | numpy-cosine | 128.1 MB | 0.175s | 17.31ms | 0.07ms | 95.0% | 614.1 MB | FAIL |
| gte-small | faiss-flat | 64.4 MB | 0.187s | 31.76ms | 0.06ms | 95.0% | 552.8 MB | PASS |
| gte-small | faiss-ivf | 64.4 MB | 0.175s | 30.09ms | 0.07ms | 95.0% | 552.9 MB | PASS |
| gte-small | hnswlib | 64.4 MB | 0.172s | 29.67ms | 0.05ms | 95.0% | 553.3 MB | PASS |
| gte-small | numpy-cosine | 64.4 MB | 0.169s | 30.04ms | 0.07ms | 95.0% | 553.3 MB | PASS |


## 4. Per-Query Retrieval Results (Best Model)


**Model:** all-minilm-l6-v2 | **Store:** faiss-flat

| Query | Question | Hit? | Expected | Retrieved | Embed Time | Search Time |
|-------|----------|------|----------|-----------|------------|-------------|
| q01 | What is a fraction and what do the top and bottom ... | ✅ | fractions-basics-001, fractions-basics-002 | fractions-basics-001, fractions-decimals-001, fractions-basics-002 | 11.25ms | 0.07ms |
| q02 | How do I make equivalent fractions?... | ✅ | fractions-equivalent-001, fractions-simplifying-001 | fractions-equivalent-001, fractions-multiplication-001, fractions-addition-002 | 10.56ms | 0.06ms |
| q03 | How do I add 2/5 and 1/3 together?... | ✅ | fractions-addition-002, fractions-addition-001 | fractions-addition-001, fractions-basics-002, fractions-addition-002 | 10.9ms | 0.07ms |
| q04 | How do you divide fractions? What does keep change... | ✅ | fractions-division-001 | fractions-division-001, fractions-basics-001, fractions-basics-002 | 12.0ms | 0.06ms |
| q05 | Thandi has R80 and she spends 3/4 of it. How much ... | ✅ | fractions-word-problems-001, fractions-word-problems-002 | decimals-money-001, ratio-sharing-001, fractions-word-problems-002 | 11.53ms | 0.08ms |
| q06 | How do I change a fraction into a decimal?... | ✅ | fractions-decimals-001, fractions-percentages-001 | fractions-decimals-001, decimals-division-001, fractions-percentages-001 | 11.03ms | 0.06ms |
| q07 | What is the place value of each digit in 23.456?... | ✅ | decimals-place-value-001, decimals-comparing-001 | decimals-place-value-001, decimals-multiplication-001, decimals-rounding-001 | 16.31ms | 0.07ms |
| q08 | How do I multiply 4.5 by 100?... | ✅ | decimals-multiplication-001, decimals-multiplication-002 | decimals-multiplication-001, decimals-division-001, fractions-percentages-001 | 8.7ms | 0.05ms |
| q09 | How do I round 7.863 to one decimal place?... | ✅ | decimals-rounding-001 | decimals-rounding-001, decimals-division-001, decimals-place-value-001 | 9.42ms | 0.06ms |
| q10 | What types of angles are there? What is an obtuse ... | ✅ | geometry-angles-001, geometry-angles-002 | geometry-angles-001, geometry-triangles-001, geometry-angles-002 | 9.14ms | 0.06ms |
| q11 | What are the properties of a parallelogram and how... | ✅ | geometry-2d-shapes-001, geometry-quadrilaterals-001 | geometry-2d-shapes-001, geometry-quadrilaterals-001, measurement-perimeter-001 | 9.89ms | 0.06ms |
| q12 | How many lines of symmetry does a square have?... | ✅ | geometry-symmetry-001 | geometry-symmetry-001, geometry-quadrilaterals-001, geometry-angles-001 | 8.79ms | 0.06ms |
| q13 | How do I calculate the perimeter of a rectangle th... | ✅ | measurement-perimeter-001 | measurement-perimeter-001, measurement-area-001, measurement-area-002 | 9.68ms | 0.05ms |
| q14 | What is the area of a triangle with base 8 cm and ... | ✅ | measurement-area-002, measurement-area-001 | measurement-area-002, geometry-2d-shapes-001, geometry-triangles-001 | 9.43ms | 0.05ms |
| q15 | How do I convert 3.5 kilometres to metres?... | ✅ | measurement-conversions-001 | measurement-conversions-001, ratio-scale-001, measurement-perimeter-001 | 8.49ms | 0.06ms |
| q16 | How do I calculate the average of my test marks?... | ✅ | data-handling-mean-001, data-handling-median-mode-001 | data-handling-mean-001, decimals-multiplication-002, decimals-addition-001 | 9.21ms | 0.06ms |
| q17 | What is the next number in the pattern 3, 9, 27, 8... | ✅ | patterns-number-sequences-001 | patterns-number-sequences-001, patterns-number-sentences-001, decimals-place-value-001 | 9.27ms | 0.05ms |
| q18 | How do I solve the equation 2x + 5 = 17?... | ✅ | patterns-equations-001, patterns-algebraic-001 | patterns-equations-001, fractions-simplifying-001, patterns-number-sentences-001 | 9.09ms | 0.06ms |
| q19 | Share R200 between two people in the ratio 3:5... | ✅ | ratio-sharing-001, ratio-basics-001 | ratio-sharing-001, ratio-basics-001, fractions-word-problems-001 | 9.19ms | 0.06ms |
| q20 | If 6 apples cost R18 how much would 10 apples cost... | ✅ | ratio-proportion-001, ratio-rate-001 | fractions-word-problems-001, ratio-proportion-001, ratio-rate-001 | 11.63ms | 0.05ms |



## 5. Storage Footprint

| Component | Size | Notes |
|-----------|------|-------|
| APK (estimated) | 50 MB | Without models |
| LLM (Qwen2.5-1.5B Q4_K_M) | 1066 MB | From P0-001 |
| Embedding model (all-minilm-l6-v2) | 87.3 MB | Measured |
| Embedding model (bge-small-en-v1.5) | 128.1 MB | Measured |
| Embedding model (gte-small) | 64.4 MB | Measured |
| Content pack (estimated full Grade 6) | 200 MB | Including embeddings + index |
| **Total (with smallest embedding model)** | **1380.4 MB** | **Downloaded on first launch** |
| **Total (with largest embedding model)** | **1444.1 MB** | **Downloaded on first launch** |

### Revised Storage Budget

| Component | Budget | Notes |
|-----------|--------|-------|
| APK | ≤ 50 MB | App code only, no models |
| LLM model | ~1066 MB | Downloaded on first launch |
| Embedding model | ≤ 100 MB | Downloaded on first launch |
| Content pack | ≤ 200 MB | Per pack, including embeddings |
| **Total first-launch download** | **~1416 MB** | |

> **Note:** Models downloaded on first launch, not bundled in APK

## 6. RAM Budget Analysis

| Component | RAM Usage | Notes |
|-----------|-----------|-------|
| Qwen2.5-1.5B LLM | 1839 MB | From P0-001 (peak during inference) |
| Embedding model (all-minilm-l6-v2) | 29.0 MB | Load-time delta |
| Peak during retrieval | 553.5 MB | Embedding + vector search |
| **Available headroom** | **1161 MB** | After LLM on 4 GB device |
| **Device RAM (usable)** | **3072 MB** | 4 GB marketed, ~3 GB usable |

> **RAM headroom relief:** Moving from 3 GB to 4 GB minimum device RAM increased
> headroom from ~209 MB to ~1161 MB — a
> 5.6× improvement that
> removes RAM as a project-killer risk.

## 7. System Info

| Property | Value |
|----------|-------|
| Total System RAM | 31937 MB |
| CPU Cores (physical) | 4 |
| CPU Cores (logical) | 8 |
| Content chunks | 50 |
| Test queries | 20 |
| Runs per query | 3 |

## 8. Target Device Comparison

| Property | Target Device | Benchmark Host |
|----------|--------------|----------------|
| RAM (total) | 4096 MB | 31937 MB |
| RAM (usable) | 3072 MB | — |
| RAM (after LLM) | 1161 MB | — |
| CPU | ARMv8-A (Cortex-A53 class) | Host CPU |
| Android API | 29 | N/A (host benchmark) |

> ⚠️ **Note:** These benchmarks ran on the development host, not on Android
> hardware. Absolute latency numbers will differ on target devices.
> Relative model rankings and RAM usage patterns are the primary signal.

## 9. Recommendation


### Passing Combinations (8/12)

- **all-minilm-l6-v2** + faiss-flat — 100.0% accuracy, 553.5 MB peak RAM, 87.3 MB model
- **all-minilm-l6-v2** + faiss-ivf — 100.0% accuracy, 544.1 MB peak RAM, 87.3 MB model
- **all-minilm-l6-v2** + hnswlib — 100.0% accuracy, 570.6 MB peak RAM, 87.3 MB model
- **all-minilm-l6-v2** + numpy-cosine — 100.0% accuracy, 567.8 MB peak RAM, 87.3 MB model
- **gte-small** + faiss-flat — 95.0% accuracy, 552.8 MB peak RAM, 64.4 MB model
- **gte-small** + faiss-ivf — 95.0% accuracy, 552.9 MB peak RAM, 64.4 MB model
- **gte-small** + hnswlib — 95.0% accuracy, 553.3 MB peak RAM, 64.4 MB model
- **gte-small** + numpy-cosine — 95.0% accuracy, 553.3 MB peak RAM, 64.4 MB model


### Suggested Next Steps

1. Build sample content pack (P0-004) using the recommended embedding model.
2. Wire into end-to-end pipeline (P0-005) with Qwen2.5-1.5B.
3. Battery & thermal testing on real hardware (P0-003).
4. Create ADR 0007 documenting embedding model + vector store + storage budget decisions.


## 10. Verdict

**Overall: GO ✅**

At least one embedding model + vector store combination meets all acceptance criteria. Proceed with the recommended combination to P0-004 (sample content pack) and P0-005 (end-to-end pipeline).

---

*Report generated by `scripts/report_generator.py` from benchmark results.*