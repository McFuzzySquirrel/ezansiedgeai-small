#!/usr/bin/env python3
"""Generate P0-006 spike report from benchmark results.

Reads JSON results from results/ directory and generates a structured
Markdown report at reports/spike-report.md.

Usage:
    python scripts/report_generator.py
"""

import json
import sys
from datetime import datetime
from pathlib import Path

import yaml
from jinja2 import Template


def load_config() -> dict:
    config_path = Path(__file__).parent.parent / "config.yaml"
    with open(config_path) as f:
        return yaml.safe_load(f)


def load_results(results_dir: Path) -> dict:
    """Load all available result files."""
    results = {}
    for name in ["generation-benchmarks", "embedding-benchmarks", "platform-audit", "device-benchmarks"]:
        path = results_dir / f"{name}.json"
        if path.exists():
            with open(path) as f:
                results[name] = json.load(f)
        else:
            results[name] = None
    return results


REPORT_TEMPLATE = """# P0-006: Gemma 4 Model Evaluation — Spike Report

> **Generated:** {{ timestamp }}
> **Model:** {{ config.model.name }} ({{ config.model.format }})
> **Runtime:** {{ config.model.runtime }}

---

## 1. Executive Summary

{% if gen_results and embed_results %}
| Metric | Target | Actual | Status |
|--------|--------|--------|--------|
| Generation latency (GPU) | ≤ {{ config.generation_acceptance.generation_time_gpu_s }}s | {{ "%.2f"|format(gen_summary.avg_generation_time_s) }}s | {{ "✅" if gen_accept.generation_time_pass else "❌" }} |
| Generation quality | ≥ {{ "%.0f"|format(config.generation_acceptance.quality_score_min * 100) }}% | {{ "%.0f"|format(gen_summary.avg_quality_score * 100) }}% | {{ "✅" if gen_accept.quality_pass else "❌" }} |
| Peak RAM | ≤ {{ config.generation_acceptance.peak_ram_mb }} MB | {{ "%.0f"|format(gen_summary.peak_ram_mb) }} MB | {{ "✅" if gen_accept.peak_ram_pass else "❌" }} |
| Embedding latency | ≤ {{ config.embedding_acceptance.embedding_time_ms }}ms | {{ "%.1f"|format(embed_summary.avg_embed_time_ms) }}ms | {{ "✅" if embed_accept.embedding_time_pass else "❌" }} |
| Retrieval accuracy (top-3) | ≥ {{ "%.0f"|format(config.embedding_acceptance.retrieval_accuracy_top3 * 100) }}% | {{ "%.0f"|format(embed_summary.retrieval_accuracy_top3 * 100) }}% | {{ "✅" if embed_accept.retrieval_accuracy_pass else "❌" }} |
{% else %}
⚠️ **Incomplete results** — run all benchmarks before generating the final report.
{% endif %}

---

## 2. Generation Benchmarks

{% if gen_results %}
### 2.1 Summary
- **Model:** {{ gen_results.model }}
- **GPU enabled:** {{ gen_results.gpu_enabled }}
- **Engine available:** {{ gen_summary.engine_available }}
- **Avg generation time:** {{ "%.2f"|format(gen_summary.avg_generation_time_s) }}s
- **Avg quality score:** {{ "%.1f"|format(gen_summary.avg_quality_score * 100) }}%
- **Peak RAM:** {{ "%.0f"|format(gen_summary.peak_ram_mb) }} MB

### 2.2 Per-Prompt Results

| Prompt | Time (s) | Quality | Concepts Hit | Concepts Missed |
|--------|----------|---------|--------------|-----------------|
{% for b in gen_results.benchmarks %}| {{ b.prompt_id }} | {{ "%.2f"|format(b.avg_time_s) }} | {{ "%.0f"|format(b.quality_score * 100) }}% | {{ b.concept_hits|length }}/{{ b.concept_hits|length + b.concept_misses|length }} | {{ b.concept_misses|join(", ") or "—" }} |
{% endfor %}
{% else %}
⚠️ Generation benchmarks not yet run. Execute: `python scripts/benchmark_generation.py`
{% endif %}

---

## 3. Embedding Benchmarks

{% if embed_results %}
### 3.1 Summary
- **Embedding dimension:** {{ embed_results.embedding_dim }}
- **Engine available:** {{ embed_summary.engine_available }}
- **Avg embedding time:** {{ "%.1f"|format(embed_summary.avg_embed_time_ms) }}ms
- **Retrieval accuracy (top-3):** {{ "%.1f"|format(embed_summary.retrieval_accuracy_top3 * 100) }}%
- **Peak RAM:** {{ "%.0f"|format(embed_summary.peak_ram_mb) }} MB

### 3.2 Retrieval Results

| Query | Correct Doc Rank | In Top-3 | Query Time (ms) |
|-------|------------------|----------|-----------------|
{% for r in embed_results.retrieval_results %}| {{ r.query_id }} | {{ r.correct_doc_rank }} | {{ "✅" if r.in_top_k else "❌" }} | {{ "%.1f"|format(r.query_time_ms) }} |
{% endfor %}
{% else %}
⚠️ Embedding benchmarks not yet run. Execute: `python scripts/benchmark_embedding.py`
{% endif %}

---

## 4. Platform Audit

{% if platform_results %}
- **GMS-free:** {{ "✅" if platform_results.acceptance.gms_free else "❌" }}
- **No INTERNET permission:** {{ "✅" if platform_results.acceptance.no_internet_permission else "❌" }}
- **MediaPipe version:** {{ platform_results.dependencies.mediapipe_version or "N/A" }}
{% else %}
⚠️ Platform audit not yet run. Execute: `python scripts/platform_audit.py`
{% endif %}

---

## 5. Real-Device Results

{% if device_results %}
{{ device_results | tojson(indent=2) }}
{% else %}
⚠️ Real-device benchmarks pending. See README.md for the device validation checklist.
{% endif %}

---

## 6. Decision Gate

Based on the results above, the recommended path is:

{% if gen_results and embed_results %}
{% if gen_accept.generation_time_pass and gen_accept.quality_pass and embed_accept.retrieval_accuracy_pass %}
### ✅ Recommended: UNIFIED PATH
Both generation and embedding meet acceptance criteria. Proceed with F2–F5 as planned.
{% elif gen_accept.generation_time_pass and gen_accept.quality_pass %}
### ⚠️ Recommended: HYBRID PATH
Generation passes but embedding does not meet retrieval accuracy target.
Use Gemma 4 for generation, keep MiniLM for embeddings.
{% else %}
### ❌ Recommended: NO MIGRATION
Generation does not meet acceptance criteria. Pause feature and complete ONNX integration.
{% endif %}
{% else %}
### ⏳ PENDING
Complete all benchmarks (including real-device) before making the go/no-go decision.
{% endif %}

---

## 7. Notes

- Emulator benchmarks are **indicative only** — the decision gate requires real-device results
- Quality scoring uses concept-coverage heuristic — manual review recommended for edge cases
- Cross-platform embedding parity must be validated before F3 (content pack re-embedding)
"""


def main():
    config = load_config()
    results_dir = Path(__file__).parent.parent / "results"
    results = load_results(results_dir)

    template = Template(REPORT_TEMPLATE)
    report = template.render(
        timestamp=datetime.now().strftime("%Y-%m-%d %H:%M"),
        config=config,
        gen_results=results["generation-benchmarks"],
        gen_summary=results["generation-benchmarks"]["summary"] if results["generation-benchmarks"] else {},
        gen_accept=results["generation-benchmarks"]["acceptance"] if results["generation-benchmarks"] else {},
        embed_results=results["embedding-benchmarks"],
        embed_summary=results["embedding-benchmarks"]["summary"] if results["embedding-benchmarks"] else {},
        embed_accept=results["embedding-benchmarks"]["acceptance"] if results["embedding-benchmarks"] else {},
        platform_results=results["platform-audit"],
        device_results=results["device-benchmarks"],
    )

    report_path = Path(__file__).parent.parent / "reports" / "spike-report.md"
    report_path.parent.mkdir(parents=True, exist_ok=True)
    with open(report_path, "w") as f:
        f.write(report)

    print(f"Report generated: {report_path}")


if __name__ == "__main__":
    main()
