# P0-002 Spike Report: Local Embedding + Retrieval

> **Generated:** (pending — run `python scripts/report_generator.py --results results/`)
> **Branch:** `spike/p0-002-embedding-retrieval`

---

## 1. Objective

Validate that a tiny embedding model + local vector store can run on-device
alongside the Qwen2.5-1.5B LLM, achieve ≥ 80% retrieval accuracy on
curriculum content, and fit within the updated RAM and storage budgets.

Also validates the full on-device storage footprint (absorbed from P0-003).

## 2. Acceptance Criteria

From [backlog-v1.md — P0-002](../../../docs/development/backlog-v1.md):

| Criterion | Target |
|-----------|--------|
| Embedding model load + first vector | < 2 seconds |
| Top-3 retrieval accuracy | ≥ 80% on 20 test queries |
| Vector search latency | < 500 ms |
| Peak RAM (embedding + vector DB) | < 1,161 MB (headroom after LLM) |
| Embedding model size on disk | ≤ 100 MB |
| Documentation | Model candidates, vector DB options, retrieval quality, storage budget |

## 3. Models Tested

| Model | Size | Dimensions | Load Time | Query Time | Peak RAM | Verdict |
|-------|------|-----------|-----------|------------|----------|---------|
| (run benchmarks to populate) | | | | | | |

## 4. Vector Store Comparison

| Store | Index Build Time | Search Time | Accuracy | Index Size |
|-------|-----------------|-------------|----------|------------|
| (run benchmarks to populate) | | | | |

## 5. Storage Footprint

| Component | Size | Notes |
|-----------|------|-------|
| APK (estimated) | ~50 MB | Without models |
| LLM (Qwen2.5-1.5B Q4_K_M) | 1,066 MB | From P0-001 |
| Embedding model | (measured) | |
| Vector index (50 chunks) | (measured) | |
| Content pack (estimated full) | ~200 MB | Grade 6 CAPS full |
| **Total** | **(calculated)** | |

## 6. Recommendation

(pending benchmark execution)

## 7. Verdict

**Overall:** (pending)

- [ ] GO — proceed with recommended model + vector store to P0-004/P0-005
- [ ] CONDITIONAL — proceed with caveats documented
- [ ] NO-GO — pivot required, document alternatives

## 8. Next Steps

1. Build sample content pack (P0-004) using chosen embedding model
2. Wire into end-to-end pipeline (P0-005)
3. Battery & thermal testing on real hardware (P0-003, renamed)

---

*This template will be replaced by `scripts/report_generator.py` after benchmarks run.*
