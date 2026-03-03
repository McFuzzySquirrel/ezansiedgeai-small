# P0-001: On-Device LLM Inference Spike

> **Goal:** Prove that a quantized SLM (≤ 2 GB, INT4/INT8) can run on target
> hardware and generate coherent curriculum-aligned explanations within
> acceptable latency and memory budgets.

## Acceptance Criteria (from backlog)

- [ ] Model loads in < 5 seconds on the target emulator.
- [ ] Generates a coherent 150-token response in < 10 seconds.
- [ ] Peak RAM usage stays below 2 GB.
- [ ] Document model candidates tested, latency numbers, and RAM profiles in a spike report.

## Output

Spike report + recommendation on model + runtime (llama.cpp vs ONNX Runtime vs other).

---

## Candidate Models

| Model | Quantisation | Size (est.) | Source |
|-------|-------------|-------------|--------|
| Phi-3.5-mini-instruct | Q4_K_M (GGUF) | ~2.2 GB | Microsoft |
| Qwen2.5-1.5B-Instruct | Q4_K_M (GGUF) | ~1.1 GB | Alibaba |
| Gemma-2-2B-it | Q4_K_M (GGUF) | ~1.5 GB | Google |
| SmolLM2-1.7B-Instruct | Q4_K_M (GGUF) | ~1.0 GB | HuggingFace |

## How to Run

### 1. Set Up Environment

```bash
cd spikes/p0-001-llm-inference
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

### 2. Download Models

```bash
python scripts/download_models.py --all
# or download a specific model:
python scripts/download_models.py --model qwen2.5-1.5b
```

Models are downloaded to `models/` (git-ignored).

### 3. Run Benchmarks

```bash
# Benchmark all downloaded models
python scripts/benchmark.py --all

# Benchmark a specific model
python scripts/benchmark.py --model models/qwen2.5-1.5b-instruct-q4_k_m.gguf

# With memory limit simulation (2 GB)
python scripts/benchmark.py --all --memory-limit 2048
```

### 4. Generate Report

```bash
python scripts/report_generator.py --results results/ --output reports/spike-report.md
```

## Directory Structure

```
p0-001-llm-inference/
├── README.md                    # This file
├── requirements.txt             # Python dependencies
├── config.yaml                  # Model candidates & benchmark parameters
├── prompts/
│   └── curriculum_prompts.json  # Grade 6 CAPS maths test prompts
├── scripts/
│   ├── download_models.py       # Download candidate GGUF models
│   ├── benchmark.py             # Main benchmark harness
│   └── report_generator.py      # Generate spike report from results
├── models/                      # Downloaded models (git-ignored)
├── results/                     # Benchmark results (git-ignored)
└── reports/
    ├── spike-report-template.md # Template
    └── spike-report.md          # Final report (generated)
```

## What This Spike Does NOT Do

- Does NOT build an Android app — this validates inference feasibility.
- Does NOT test embedding or retrieval — that is P0-002.
- Does NOT test on actual Android hardware — emulator testing follows
  once we have a model recommendation.
- Does NOT make a final model choice — it produces data for a decision.

## References

- [Backlog — P0-001](../../docs/development/backlog-v1.md)
- [Phone Architecture — AI Layer](../../docs/architecture/phone-architecture.md)
- [ADR 0001 — Phone-First Architecture](../../ejs-docs/adr/0001-phone-first-architecture.md)
