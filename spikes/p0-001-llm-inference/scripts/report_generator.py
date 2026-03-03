#!/usr/bin/env python3
"""Generate a spike report from benchmark results.

Reads the JSON results from the benchmark run and produces a Markdown
spike report matching the P0-001 acceptance criteria.

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
# P0-001 Spike Report: On-Device LLM Inference

> **Generated:** {{ timestamp }}
> **Branch:** `spike/p0-001-llm-inference`

---

## 1. Objective

Validate that a quantized SLM (≤ 2 GB, INT4/INT8) can run on target hardware
and generate coherent curriculum-aligned explanations within acceptable latency
and memory budgets.

## 2. Acceptance Criteria Results

| Criterion | Target | Result | Status |
|-----------|--------|--------|--------|
{% for r in results -%}
| **{{ r.model_filename }}** | | | **{{ r.status }}** |
{% if r.status != 'FAILED' -%}
| ↳ Model load time | < {{ criteria.model_load_time_s }}s | {{ r.load_metrics.load_time_s }}s | {{ '✅' if r.acceptance_criteria.load_time.pass else '❌' }} |
| ↳ 150-token generation | < {{ criteria.generation_time_s }}s | {{ r.aggregate.avg_generation_time_s }}s (avg) | {{ '✅' if r.acceptance_criteria.generation_time.pass else '❌' }} |
| ↳ Peak RAM | < {{ criteria.peak_ram_mb }} MB | {{ r.aggregate.peak_ram_mb }} MB | {{ '✅' if r.acceptance_criteria.peak_ram.pass else '❌' }} |
{% else -%}
| ↳ Error | — | {{ r.error | default("Load failed") }} | ❌ |
{% endif -%}
{% endfor %}

## 3. Model Comparison

| Model | Size | Load Time | Avg Gen Time | Throughput | Peak RAM | Verdict |
|-------|------|-----------|-------------|------------|----------|---------|
{% for r in results -%}
{% if r.status != 'FAILED' -%}
| {{ r.model_filename }} | {{ r.model_size_mb }} MB | {{ r.load_metrics.load_time_s }}s | {{ r.aggregate.avg_generation_time_s }}s | {{ r.aggregate.avg_tokens_per_sec }} tok/s | {{ r.aggregate.peak_ram_mb }} MB | {{ r.status }} |
{% else -%}
| {{ r.model_filename }} | {{ r.model_size_mb }} MB | FAILED | — | — | — | FAIL |
{% endif -%}
{% endfor %}

## 4. Per-Prompt Results

{% for r in results %}
{% if r.status != 'FAILED' %}
### {{ r.model_filename }}

| Prompt | Topic | Difficulty | Avg Time | Tokens/s | Peak RAM |
|--------|-------|-----------|----------|----------|----------|
{% for p in r.prompt_results -%}
| {{ p.prompt_id }} | {{ p.topic_path }} | {{ p.difficulty }} | {{ p.avg_generation_time_s }}s | {{ p.avg_tokens_per_sec }} | {{ p.peak_ram_mb }} MB |
{% endfor %}

<details>
<summary>Sample outputs (click to expand)</summary>

{% for p in r.prompt_results %}
**{{ p.prompt_id }}** — *{{ p.question }}*

```
{{ p.sample_output[:500] }}
```

{% endfor %}
</details>

{% endif %}
{% endfor %}

## 5. System Info

| Property | Value |
|----------|-------|
| Total System RAM | {{ system_info.total_ram_mb }} MB |
| CPU Cores (physical) | {{ system_info.cpu_count_physical }} |
| CPU Cores (logical) | {{ system_info.cpu_count }} |
| Benchmark threads | {{ bench_config.n_threads }} |
| Context window | {{ bench_config.n_ctx }} tokens |
| Max tokens | {{ bench_config.max_tokens }} |
| Temperature | {{ bench_config.temperature }} |
| Runs per prompt | {{ bench_config.benchmark_runs }} |

## 6. Target Device Comparison

| Property | Target Device | Benchmark Host |
|----------|--------------|----------------|
| RAM Budget | {{ target_device.ram_budget_mb }} MB | {{ system_info.total_ram_mb }} MB |
| CPU | {{ target_device.cpu }} | Host CPU |
| Android API | {{ target_device.android_api }} | N/A (host benchmark) |

> ⚠️ **Note:** These benchmarks ran on the development host, not on Android
> hardware or emulator. Absolute latency numbers will differ on target devices.
> Relative model rankings and RAM usage patterns are the primary signal.

## 7. Recommendation

{% if passing_models %}
### Passing Models ({{ passing_models | length }}/{{ results | length }})

{% for r in passing_models -%}
- **{{ r.model_filename }}** — {{ r.aggregate.avg_generation_time_s }}s avg, {{ r.aggregate.peak_ram_mb }} MB peak RAM, {{ r.aggregate.avg_tokens_per_sec }} tok/s
{% endfor %}

### Suggested Next Steps

1. Validate the top candidate(s) on an Android emulator (API 29, 2 GB RAM).
2. Assess output quality for curriculum alignment (manual review).
3. Measure thermal behaviour during sustained inference.
4. Proceed to P0-002 (embedding + retrieval spike) in parallel.
{% else %}
### ⚠️ No Models Passed All Acceptance Criteria

**Action required:** Review the results to determine:
1. Which criterion is the bottleneck (load time, generation speed, or RAM)?
2. Can a more aggressive quantisation (Q3_K_S, IQ4_XS) reduce size/RAM?
3. Should smaller models (< 1B parameters) be evaluated?
4. Is the acceptance criteria realistic for the target hardware?
{% endif %}

## 8. Verdict

**Overall: {{ 'GO ✅' if passing_models else 'CONDITIONAL ⚠️' }}**

{{ 'At least one model meets all acceptance criteria. Proceed with the recommended candidate to P0-005 (end-to-end pipeline).' if passing_models else 'No model meets all criteria on the host benchmark. Further investigation needed before GO/NO-GO.' }}

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
    passing_models = [r for r in results if r["status"] == "PASS"]

    template = Template(REPORT_TEMPLATE)
    return template.render(
        timestamp=datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC"),
        results=results,
        passing_models=passing_models,
        criteria=data["benchmark_run"]["acceptance_criteria"],
        system_info=data["benchmark_run"]["system_info"],
        bench_config=data["benchmark_run"]["config"],
        target_device=data["benchmark_run"]["target_device"],
    )


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generate P0-001 spike report from benchmark results."
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
    spike_root = Path(__file__).parent.parent
    if args.output:
        output_path = Path(args.output)
    else:
        output_path = spike_root / "reports" / "spike-report.md"

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w") as f:
        f.write(report)

    print(f"Spike report generated: {output_path}")
    print(f"Models tested:   {len(data['results'])}")
    passing = [r for r in data["results"] if r["status"] == "PASS"]
    print(f"Models passing:  {len(passing)}/{len(data['results'])}")


if __name__ == "__main__":
    main()
