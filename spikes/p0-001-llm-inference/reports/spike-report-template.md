# P0-001 Spike Report: On-Device LLM Inference

> **Generated:** (pending — run `python scripts/report_generator.py --results results/`)
> **Branch:** `spike/p0-001-llm-inference`

---

## 1. Objective

Validate that a quantized SLM (≤ 2 GB, INT4/INT8) can run on target hardware
and generate coherent curriculum-aligned explanations within acceptable latency
and memory budgets.

## 2. Acceptance Criteria

From [backlog-v1.md — P0-001](../../../docs/development/backlog-v1.md):

| Criterion | Target |
|-----------|--------|
| Model load time | < 5 seconds |
| 150-token generation | < 10 seconds |
| Peak RAM usage | < 2 GB |
| Documentation | Model candidates, latency, RAM profiles |

## 3. Models Tested

| Model | Quantisation | Size | Load Time | Avg Gen Time | Peak RAM | tok/s | Verdict |
|-------|-------------|------|-----------|-------------|----------|-------|---------|
| (run benchmarks to populate) | | | | | | | |

## 4. Recommendation

(pending benchmark execution)

## 5. Verdict

**Overall:** (pending)

- [ ] GO — proceed with recommended model to P0-005
- [ ] CONDITIONAL — proceed with caveats documented
- [ ] NO-GO — pivot required, document alternatives

## 6. Next Steps

1. Validate on Android emulator (API 29, 2 GB RAM)
2. Manual review of output quality for CAPS alignment
3. Thermal behaviour measurement during sustained inference
4. Feed model recommendation into P0-003 (storage footprint) and P0-005 (e2e pipeline)

---

*This template will be replaced by `scripts/report_generator.py` after benchmarks run.*
