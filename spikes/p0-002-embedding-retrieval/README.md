# P0-002: Local Embedding + Retrieval Spike

> **Goal:** Prove that a tiny embedding model + local vector store can run
> on-device alongside the Qwen2.5-1.5B LLM, achieve ≥ 80% retrieval accuracy
> on curriculum content, and fit within the updated RAM and storage budgets.
>
> **Expanded scope:** Also validates the full on-device storage footprint
> (absorbed from P0-003) and documents a revised storage budget.

## Acceptance Criteria (from backlog)

- [ ] Embedding model loads and produces vectors in < 2 seconds for a single query.
- [ ] Top-3 retrieval accuracy ≥ 80% on 20 hand-crafted test queries against the sample set.
- [ ] Vector search completes in < 500 ms.
- [ ] Document embedding model candidates, vector DB options, and retrieval quality results.
- [ ] Total on-device storage footprint documented with per-component breakdown.
- [ ] Revised storage budget defined (APK + models + content packs).

## Output

Spike report + recommendation on embedding model and local vector store + revised storage budget.

---

## Candidate Embedding Models

| Model | Size (est.) | Dimensions | Source |
|-------|-------------|------------|--------|
| all-MiniLM-L6-v2 | ~80 MB | 384 | sentence-transformers |
| bge-small-en-v1.5 | ~130 MB | 384 | BAAI |
| gte-small | ~70 MB | 384 | Alibaba DAMO |

## Vector Store Candidates

| Store | Type | Notes |
|-------|------|-------|
| FAISS Flat | Brute-force exact | Baseline comparison |
| FAISS IVF-Flat | Approximate | Faster for larger datasets |
| HNSWlib | Graph-based ANN | Good recall at speed |
| NumPy cosine | Pure computation | No external dependency baseline |

## How to Run

### 1. Set Up Environment

```bash
cd spikes/p0-002-embedding-retrieval
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

### 2. Download Models

```bash
python scripts/download_models.py --all
# or download a specific model:
python scripts/download_models.py --model all-minilm-l6-v2
```

Models are downloaded to `models/` (git-ignored).

### 3. Run Benchmarks

```bash
# Benchmark all downloaded models × all vector stores
python scripts/benchmark.py --all

# Benchmark a specific model
python scripts/benchmark.py --model all-minilm-l6-v2

# With RAM headroom simulation
python scripts/benchmark.py --all --memory-limit 1161
```

### 4. Generate Report

```bash
python scripts/report_generator.py --results results/ --output reports/spike-report.md
```

## Directory Structure

```
p0-002-embedding-retrieval/
├── README.md                    # This file
├── requirements.txt             # Python dependencies
├── config.yaml                  # Model candidates & benchmark parameters
├── content/
│   ├── chunks.json              # 50 Grade 6 CAPS maths content chunks
│   └── test_queries.json        # 20 hand-crafted test queries with expected results
├── scripts/
│   ├── download_models.py       # Download candidate embedding models
│   ├── benchmark.py             # Main benchmark harness
│   └── report_generator.py      # Generate spike report from results
├── models/                      # Downloaded models (git-ignored)
├── results/                     # Benchmark results (git-ignored)
└── reports/
    ├── spike-report-template.md # Template
    └── spike-report.md          # Final report (generated)
```

## What This Spike Does NOT Do

- Does NOT build an Android app — this validates embedding + retrieval feasibility.
- Does NOT test LLM inference — that was P0-001.
- Does NOT test on actual Android hardware — deferred to P0-003 (Battery & Thermal).
- Does NOT build a content pack — that is P0-004 (follows immediately after).
- Does NOT make a final model choice — it produces data for a decision.

## Context from P0-001

- **LLM chosen:** Qwen2.5-1.5B-Instruct Q4_K_M via llama.cpp (ADR 0006)
- **LLM peak RAM:** 1,839 MB
- **LLM disk size:** 1,066 MB
- **Device RAM floor:** 4 GB marketed / ~3 GB usable (raised from 3 GB)
- **RAM headroom for embedding + vector DB + app:** ~1,161 MB

## References

- [Backlog — P0-002](../../docs/development/backlog-v1.md)
- [Phone Architecture — AI Layer](../../docs/architecture/phone-architecture.md)
- [ADR 0006 — Qwen2.5-1.5B](../../ejs-docs/adr/0006-qwen25-1.5b-as-on-device-llm.md)
- [P0-001 Spike Report](../p0-001-llm-inference/reports/spike-report.md)
