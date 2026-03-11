#!/usr/bin/env python3
"""Generate a spike report from P0-002 benchmark results.

Reads the JSON results from the benchmark run and produces a Markdown
spike report matching the P0-002 acceptance criteria.

Usage:
    python scripts/report_generator.py --results results/
    python scripts/report_generator.py --results results/ --output reports/spike-report.md
"""

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

from jinja2 import Template


REPORT_TEMPLATE = """\
# P0-002 Spike Report: Local Embedding + Retrieval

> **Generated:** {{ timestamp }}
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
| Embedding model load time | < {{ criteria.embedding_load_time_s }}s | {{ best_load_time }}s | {{ '✅' if best_passes_load else '❌' }} |
| Query embedding time | < {{ criteria.query_embedding_time_ms }}ms | {{ best_query_time }}ms | {{ '✅' if best_passes_query else '❌' }} |
| Vector search time | < {{ criteria.vector_search_time_ms }}ms | {{ best_search_time }}ms | {{ '✅' if best_passes_search else '❌' }} |
| Top-3 retrieval accuracy | ≥ {{ (criteria.retrieval_accuracy_top3 * 100) | int }}% | {{ (best_accuracy * 100) | round(1) }}% | {{ '✅' if best_passes_accuracy else '❌' }} |
| Peak RAM (embedding + store) | < {{ criteria.peak_ram_mb }} MB | {{ best_peak_ram }} MB | {{ '✅' if best_passes_ram else '❌' }} |
| Model size on disk | ≤ {{ criteria.model_size_mb }} MB | {{ best_model_size }} MB | {{ '✅' if best_passes_size else '❌' }} |

## 3. Model × Store Comparison

| Model | Store | Size | Load | Embed | Search | Accuracy | Peak RAM | Verdict |
|-------|-------|------|------|-------|--------|----------|----------|---------|
{% for r in results -%}
{% if r.status != 'FAILED' -%}
| {{ r.model_id }} | {{ r.store_id }} | {{ r.model_size_mb }} MB | {{ r.load_metrics.load_time_s }}s | {{ r.retrieval_metrics.avg_embedding_time_ms }}ms | {{ r.retrieval_metrics.avg_search_time_ms }}ms | {{ (r.retrieval_metrics.retrieval_accuracy * 100) | round(1) }}% | {{ r.peak_ram_mb }} MB | {{ r.status }} |
{% else -%}
| {{ r.model_id }} | {{ r.store_id }} | {{ r.model_size_mb }} MB | FAILED | — | — | — | — | FAIL |
{% endif -%}
{% endfor %}

## 4. Per-Query Retrieval Results (Best Model)

{% if best_result %}
**Model:** {{ best_result.model_id }} | **Store:** {{ best_result.store_id }}

| Query | Question | Hit? | Expected | Retrieved | Embed Time | Search Time |
|-------|----------|------|----------|-----------|------------|-------------|
{% for q in best_result.retrieval_metrics.query_results -%}
| {{ q.query_id }} | {{ q.question[:50] }}... | {{ '✅' if q.hit else '❌' }} | {{ q.expected_chunk_ids | join(', ') }} | {{ q.retrieved_chunk_ids | join(', ') }} | {{ q.avg_embedding_time_ms }}ms | {{ q.avg_search_time_ms }}ms |
{% endfor %}
{% endif %}

## 5. Storage Footprint

| Component | Size | Notes |
|-----------|------|-------|
| APK (estimated) | {{ storage.apk_estimated_mb }} MB | Without models |
| LLM (Qwen2.5-1.5B Q4_K_M) | {{ storage.llm_model_mb }} MB | From P0-001 |
{% for model_id, size in storage.embedding_models.items() -%}
| Embedding model ({{ model_id }}) | {{ size }} MB | Measured |
{% endfor -%}
| Content pack (estimated full Grade 6) | {{ storage_budget.content_pack_mb }} MB | Including embeddings + index |
| **Total (with smallest embedding model)** | **{{ total_footprint_min }} MB** | **Downloaded on first launch** |
| **Total (with largest embedding model)** | **{{ total_footprint_max }} MB** | **Downloaded on first launch** |

### Revised Storage Budget

| Component | Budget | Notes |
|-----------|--------|-------|
| APK | ≤ {{ storage_budget.apk_mb }} MB | App code only, no models |
| LLM model | ~{{ storage_budget.llm_model_mb }} MB | Downloaded on first launch |
| Embedding model | ≤ {{ storage_budget.embedding_model_mb }} MB | Downloaded on first launch |
| Content pack | ≤ {{ storage_budget.content_pack_mb }} MB | Per pack, including embeddings |
| **Total first-launch download** | **~{{ storage_budget.total_target_mb }} MB** | |

> **Note:** {{ storage_budget.notes }}

## 6. RAM Budget Analysis

| Component | RAM Usage | Notes |
|-----------|-----------|-------|
| Qwen2.5-1.5B LLM | {{ target_device.ram_llm_mb }} MB | From P0-001 (peak during inference) |
{% if best_result -%}
| Embedding model ({{ best_result.model_id }}) | {{ best_result.load_metrics.load_ram_delta_mb }} MB | Load-time delta |
| Peak during retrieval | {{ best_result.peak_ram_mb }} MB | Embedding + vector search |
{% endif -%}
| **Available headroom** | **{{ target_device.ram_budget_mb }} MB** | After LLM on 4 GB device |
| **Device RAM (usable)** | **{{ target_device.ram_usable_mb }} MB** | 4 GB marketed, ~3 GB usable |

> **RAM headroom relief:** Moving from 3 GB to 4 GB minimum device RAM increased
> headroom from ~209 MB to ~{{ target_device.ram_budget_mb }} MB — a
> {{ ((target_device.ram_budget_mb / 209) | round(1)) }}× improvement that
> removes RAM as a project-killer risk.

## 7. System Info

| Property | Value |
|----------|-------|
| Total System RAM | {{ system_info.total_ram_mb }} MB |
| CPU Cores (physical) | {{ system_info.cpu_count_physical }} |
| CPU Cores (logical) | {{ system_info.cpu_count }} |
| Content chunks | {{ content_stats.num_chunks }} |
| Test queries | {{ content_stats.num_queries }} |
| Runs per query | {{ bench_config.benchmark_runs }} |

## 8. Target Device Comparison

| Property | Target Device | Benchmark Host |
|----------|--------------|----------------|
| RAM (total) | {{ target_device.ram_total_mb }} MB | {{ system_info.total_ram_mb }} MB |
| RAM (usable) | {{ target_device.ram_usable_mb }} MB | — |
| RAM (after LLM) | {{ target_device.ram_budget_mb }} MB | — |
| CPU | {{ target_device.cpu }} | Host CPU |
| Android API | {{ target_device.android_api }} | N/A (host benchmark) |

> ⚠️ **Note:** These benchmarks ran on the development host, not on Android
> hardware. Absolute latency numbers will differ on target devices.
> Relative model rankings and RAM usage patterns are the primary signal.

## 9. Recommendation

{% if passing_results %}
### Passing Combinations ({{ passing_results | length }}/{{ results | length }})

{% for r in passing_results -%}
- **{{ r.model_id }}** + {{ r.store_id }} — {{ (r.retrieval_metrics.retrieval_accuracy * 100) | round(1) }}% accuracy, {{ r.peak_ram_mb }} MB peak RAM, {{ r.model_size_mb }} MB model
{% endfor %}

### Suggested Next Steps

1. Build sample content pack (P0-004) using the recommended embedding model.
2. Wire into end-to-end pipeline (P0-005) with Qwen2.5-1.5B.
3. Battery & thermal testing on real hardware (P0-003).
4. Create ADR 0007 documenting embedding model + vector store + storage budget decisions.
{% else %}
### ⚠️ No Combinations Passed All Acceptance Criteria

**Action required:** Review the results to determine:
1. Which criterion is the bottleneck?
2. Can we use a smaller/more efficient embedding model?
3. Should acceptance criteria be adjusted?
4. Is the RAM budget realistic?
{% endif %}

## 10. Verdict

**Overall: {{ 'GO ✅' if passing_results else 'CONDITIONAL ⚠️' if conditional else 'NO-GO ❌' }}**

{{ verdict_text }}

---

*Report generated by `scripts/report_generator.py` from benchmark results.*
"""


def load_combined_results(results_dir: Path) -> dict:
    """Load the combined benchmark results JSON."""
    combined_path = results_dir / "benchmark_combined.json"
    if not combined_path.exists():
        print(f"ERROR: Combined results not found: {combined_path}", file=sys.stderr)
        print("Run benchmarks first: python scripts/benchmark.py --all")
        sys.exit(1)
    with open(combined_path) as f:
        return json.load(f)


def generate_report(data: dict) -> str:
    """Render the spike report from benchmark data."""
    results = data["results"]
    passing_results = [r for r in results if r["status"] == "PASS"]
    non_failed = [r for r in results if r["status"] != "FAILED"]

    # Find best result (highest accuracy among passing, or best overall)
    if passing_results:
        best_result = max(passing_results, key=lambda r: r["retrieval_metrics"]["retrieval_accuracy"])
    elif non_failed:
        best_result = max(non_failed, key=lambda r: r["retrieval_metrics"]["retrieval_accuracy"])
    else:
        best_result = None

    # Extract best metrics for summary
    if best_result:
        best_load_time = best_result["load_metrics"]["load_time_s"]
        best_query_time = best_result["retrieval_metrics"]["avg_embedding_time_ms"]
        best_search_time = best_result["retrieval_metrics"]["avg_search_time_ms"]
        best_accuracy = best_result["retrieval_metrics"]["retrieval_accuracy"]
        best_peak_ram = best_result["peak_ram_mb"]
        best_model_size = best_result["model_size_mb"]
    else:
        best_load_time = best_query_time = best_search_time = -1
        best_accuracy = 0
        best_peak_ram = best_model_size = -1

    criteria = data["benchmark_run"]["acceptance_criteria"]
    storage = data.get("storage_footprint", {})
    storage_budget = data["benchmark_run"]["storage_budget"]

    # Calculate total footprints
    embedding_sizes = list(storage.get("embedding_models", {}).values())
    min_emb = min(embedding_sizes) if embedding_sizes else 0
    max_emb = max(embedding_sizes) if embedding_sizes else 0
    total_footprint_min = storage.get("apk_estimated_mb", 50) + storage.get("llm_model_mb", 1066) + min_emb + storage_budget.get("content_pack_mb", 200)
    total_footprint_max = storage.get("apk_estimated_mb", 50) + storage.get("llm_model_mb", 1066) + max_emb + storage_budget.get("content_pack_mb", 200)

    # Verdict text
    if passing_results:
        verdict_text = (
            f"At least one embedding model + vector store combination meets all acceptance criteria. "
            f"Proceed with the recommended combination to P0-004 (sample content pack) and P0-005 (end-to-end pipeline)."
        )
        conditional = False
    elif non_failed:
        # Check which criteria are failing
        failing_criteria = set()
        for r in non_failed:
            for crit_name, crit_data in r["acceptance_criteria"].items():
                if not crit_data["pass"]:
                    failing_criteria.add(crit_name)
        verdict_text = (
            f"No combination passes all criteria. Failing criteria: {', '.join(failing_criteria)}. "
            f"Review whether criteria can be relaxed or a different approach is needed."
        )
        conditional = True
    else:
        verdict_text = "All combinations failed to load. Check model downloads and dependencies."
        conditional = False

    template = Template(REPORT_TEMPLATE)
    return template.render(
        timestamp=datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC"),
        results=results,
        passing_results=passing_results,
        best_result=best_result,
        criteria=criteria,
        best_load_time=best_load_time,
        best_query_time=best_query_time,
        best_search_time=best_search_time,
        best_accuracy=best_accuracy,
        best_peak_ram=best_peak_ram,
        best_model_size=best_model_size,
        best_passes_load=best_load_time <= criteria["embedding_load_time_s"] if best_result else False,
        best_passes_query=best_query_time <= criteria["query_embedding_time_ms"] if best_result else False,
        best_passes_search=best_search_time <= criteria["vector_search_time_ms"] if best_result else False,
        best_passes_accuracy=best_accuracy >= criteria["retrieval_accuracy_top3"] if best_result else False,
        best_passes_ram=best_peak_ram <= criteria["peak_ram_mb"] if best_result else False,
        best_passes_size=best_model_size <= criteria["model_size_mb"] if best_result else False,
        storage=storage,
        storage_budget=storage_budget,
        total_footprint_min=round(total_footprint_min, 1),
        total_footprint_max=round(total_footprint_max, 1),
        target_device=data["benchmark_run"]["target_device"],
        system_info=data["benchmark_run"]["system_info"],
        bench_config=data["benchmark_run"]["config"],
        content_stats=data["benchmark_run"]["content_stats"],
        verdict_text=verdict_text,
        conditional=conditional if not passing_results else False,
    )


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generate P0-002 spike report from benchmark results."
    )
    parser.add_argument(
        "--results",
        type=str,
        required=True,
        help="Path to the results directory containing benchmark_combined.json.",
    )
    parser.add_argument(
        "--output",
        type=str,
        default=None,
        help="Output path for the report (default: reports/spike-report.md).",
    )
    args = parser.parse_args()

    results_dir = Path(args.results)
    data = load_combined_results(results_dir)

    report = generate_report(data)

    # Determine output path
    if args.output:
        output_path = Path(args.output)
    else:
        output_path = Path(__file__).parent.parent / "reports" / "spike-report.md"

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w") as f:
        f.write(report)

    print(f"Report written to: {output_path}")
    print(f"Report length: {len(report)} characters")


if __name__ == "__main__":
    main()
