#!/usr/bin/env python3
"""Benchmark LLM inference for P0-001 feasibility spike.

Loads candidate GGUF models via llama-cpp-python, runs curriculum test
prompts, and records latency, RAM usage, token throughput, and output quality.

Usage:
    python scripts/benchmark.py --all
    python scripts/benchmark.py --model models/qwen2.5-1.5b-instruct-q4_k_m.gguf
    python scripts/benchmark.py --all --memory-limit 2048
"""

import argparse
import gc
import json
import os
import statistics
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

import psutil
import yaml
from llama_cpp import Llama


def load_config() -> dict:
    """Load benchmark config from config.yaml."""
    config_path = Path(__file__).parent.parent / "config.yaml"
    with open(config_path) as f:
        return yaml.safe_load(f)


def load_prompts() -> list[dict]:
    """Load curriculum test prompts."""
    prompts_path = Path(__file__).parent.parent / "prompts" / "curriculum_prompts.json"
    with open(prompts_path) as f:
        data = json.load(f)
    return data["prompts"]


def get_process_memory_mb() -> float:
    """Get current process RSS in MB."""
    return psutil.Process(os.getpid()).memory_info().rss / (1024 * 1024)


def build_prompt(template: str, prompt_data: dict) -> str:
    """Build a full prompt from template and prompt data."""
    return template.format(
        content=prompt_data["content"],
        question=prompt_data["question"],
    )


def benchmark_model_load(model_path: str, config: dict) -> tuple[Llama | None, dict]:
    """Benchmark model loading time and memory impact.

    Returns (model_instance, metrics_dict).
    """
    bench = config["benchmark"]
    mem_before = get_process_memory_mb()

    print(f"  Loading model: {Path(model_path).name}")
    start = time.perf_counter()

    try:
        model = Llama(
            model_path=model_path,
            n_ctx=bench["n_ctx"],
            n_threads=bench["n_threads"],
            verbose=False,
        )
    except Exception as e:
        return None, {
            "load_time_s": -1,
            "load_ram_delta_mb": -1,
            "error": str(e),
        }

    load_time = time.perf_counter() - start
    mem_after = get_process_memory_mb()

    metrics = {
        "load_time_s": round(load_time, 3),
        "load_ram_delta_mb": round(mem_after - mem_before, 1),
        "ram_after_load_mb": round(mem_after, 1),
    }

    print(f"    Load time:  {metrics['load_time_s']:.3f}s")
    print(f"    RAM delta:  +{metrics['load_ram_delta_mb']:.1f} MB")
    print(f"    RAM total:  {metrics['ram_after_load_mb']:.1f} MB")

    return model, metrics


def benchmark_single_prompt(
    model: Llama,
    prompt_text: str,
    prompt_data: dict,
    config: dict,
) -> dict:
    """Run a single prompt through the model and measure performance.

    Returns a dict with latency, token count, throughput, output text, and RAM.
    """
    bench = config["benchmark"]
    mem_before = get_process_memory_mb()

    start = time.perf_counter()
    response = model.create_completion(
        prompt=prompt_text,
        max_tokens=bench["max_tokens"],
        temperature=bench["temperature"],
        top_p=bench["top_p"],
        repeat_penalty=bench["repeat_penalty"],
        seed=bench["seed"],
    )
    elapsed = time.perf_counter() - start

    mem_after = get_process_memory_mb()

    output_text = response["choices"][0]["text"]
    usage = response.get("usage", {})
    completion_tokens = usage.get("completion_tokens", 0)
    prompt_tokens = usage.get("prompt_tokens", 0)
    tokens_per_sec = completion_tokens / elapsed if elapsed > 0 else 0

    return {
        "prompt_id": prompt_data["id"],
        "topic_path": prompt_data["topic_path"],
        "difficulty": prompt_data["difficulty"],
        "prompt_tokens": prompt_tokens,
        "completion_tokens": completion_tokens,
        "generation_time_s": round(elapsed, 3),
        "tokens_per_sec": round(tokens_per_sec, 2),
        "peak_ram_mb": round(max(mem_before, mem_after), 1),
        "output_text": output_text.strip(),
        "output_length_chars": len(output_text.strip()),
    }


def benchmark_model(model_path: str, config: dict, prompts: list[dict]) -> dict:
    """Run full benchmark suite for a single model.

    Returns a comprehensive result dict.
    """
    bench = config["benchmark"]
    model_filename = Path(model_path).name
    model_size_mb = Path(model_path).stat().st_size / (1024 * 1024)

    print(f"\n{'='*60}")
    print(f"Benchmarking: {model_filename}")
    print(f"Model size:   {model_size_mb:.1f} MB")
    print(f"{'='*60}")

    # Load model
    model, load_metrics = benchmark_model_load(model_path, config)
    if model is None:
        return {
            "model_filename": model_filename,
            "model_size_mb": round(model_size_mb, 1),
            "status": "FAILED",
            "error": load_metrics.get("error", "Unknown error during load"),
            "load_metrics": load_metrics,
            "prompt_results": [],
        }

    # Warmup run (discard results)
    if bench["warmup_runs"] > 0 and prompts:
        print(f"\n  Warmup ({bench['warmup_runs']} run(s))...")
        warmup_prompt = build_prompt(config["prompt_template"], prompts[0])
        for _ in range(bench["warmup_runs"]):
            model.create_completion(
                prompt=warmup_prompt,
                max_tokens=20,  # Short warmup
                temperature=bench["temperature"],
                seed=bench["seed"],
            )

    # Benchmark each prompt
    prompt_results = []
    print(f"\n  Running {len(prompts)} prompts × {bench['benchmark_runs']} run(s)...")

    for prompt_data in prompts:
        prompt_text = build_prompt(config["prompt_template"], prompt_data)
        run_results = []

        for run_idx in range(bench["benchmark_runs"]):
            result = benchmark_single_prompt(model, prompt_text, prompt_data, config)
            run_results.append(result)

        # Average across runs
        avg_result = {
            "prompt_id": prompt_data["id"],
            "topic_path": prompt_data["topic_path"],
            "difficulty": prompt_data["difficulty"],
            "question": prompt_data["question"],
            "runs": bench["benchmark_runs"],
            "avg_generation_time_s": round(
                statistics.mean(r["generation_time_s"] for r in run_results), 3
            ),
            "min_generation_time_s": round(
                min(r["generation_time_s"] for r in run_results), 3
            ),
            "max_generation_time_s": round(
                max(r["generation_time_s"] for r in run_results), 3
            ),
            "avg_tokens_per_sec": round(
                statistics.mean(r["tokens_per_sec"] for r in run_results), 2
            ),
            "avg_completion_tokens": round(
                statistics.mean(r["completion_tokens"] for r in run_results), 1
            ),
            "peak_ram_mb": round(
                max(r["peak_ram_mb"] for r in run_results), 1
            ),
            "sample_output": run_results[-1]["output_text"],  # Last run's output
        }

        status_icon = (
            "✓" if avg_result["avg_generation_time_s"] <= config["acceptance_criteria"]["generation_time_s"]
            else "✗"
        )
        print(
            f"    {status_icon} {prompt_data['id']:25s}  "
            f"{avg_result['avg_generation_time_s']:6.2f}s  "
            f"{avg_result['avg_tokens_per_sec']:6.1f} tok/s  "
            f"{avg_result['peak_ram_mb']:7.1f} MB"
        )

        prompt_results.append(avg_result)

    # Aggregate metrics
    all_gen_times = [r["avg_generation_time_s"] for r in prompt_results]
    all_tok_per_sec = [r["avg_tokens_per_sec"] for r in prompt_results]
    peak_ram = max(r["peak_ram_mb"] for r in prompt_results) if prompt_results else 0

    # Check acceptance criteria
    criteria = config["acceptance_criteria"]
    passes_load_time = load_metrics["load_time_s"] <= criteria["model_load_time_s"]
    passes_generation = (
        statistics.mean(all_gen_times) <= criteria["generation_time_s"]
        if all_gen_times else False
    )
    passes_ram = peak_ram <= criteria["peak_ram_mb"]

    verdict = "PASS" if (passes_load_time and passes_generation and passes_ram) else "FAIL"

    result = {
        "model_filename": model_filename,
        "model_size_mb": round(model_size_mb, 1),
        "status": verdict,
        "load_metrics": load_metrics,
        "aggregate": {
            "avg_generation_time_s": round(statistics.mean(all_gen_times), 3) if all_gen_times else -1,
            "median_generation_time_s": round(statistics.median(all_gen_times), 3) if all_gen_times else -1,
            "avg_tokens_per_sec": round(statistics.mean(all_tok_per_sec), 2) if all_tok_per_sec else -1,
            "peak_ram_mb": peak_ram,
            "prompts_tested": len(prompt_results),
        },
        "acceptance_criteria": {
            "load_time": {"limit_s": criteria["model_load_time_s"], "actual_s": load_metrics["load_time_s"], "pass": passes_load_time},
            "generation_time": {"limit_s": criteria["generation_time_s"], "actual_s": round(statistics.mean(all_gen_times), 3) if all_gen_times else -1, "pass": passes_generation},
            "peak_ram": {"limit_mb": criteria["peak_ram_mb"], "actual_mb": peak_ram, "pass": passes_ram},
        },
        "prompt_results": prompt_results,
    }

    # Summary
    print(f"\n  {'─'*50}")
    print(f"  Verdict:        {verdict}")
    print(f"  Load time:      {load_metrics['load_time_s']:.3f}s {'✓' if passes_load_time else '✗'} (limit: {criteria['model_load_time_s']}s)")
    print(f"  Avg generation: {result['aggregate']['avg_generation_time_s']:.3f}s {'✓' if passes_generation else '✗'} (limit: {criteria['generation_time_s']}s)")
    print(f"  Peak RAM:       {peak_ram:.1f} MB {'✓' if passes_ram else '✗'} (limit: {criteria['peak_ram_mb']} MB)")
    print(f"  Avg throughput: {result['aggregate']['avg_tokens_per_sec']:.1f} tok/s")

    # Cleanup
    del model
    gc.collect()

    return result


def find_models(models_dir: Path) -> list[Path]:
    """Find all GGUF model files in the models directory."""
    return sorted(models_dir.glob("*.gguf"))


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Benchmark LLM inference for P0-001 feasibility spike."
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="Benchmark all downloaded models in models/.",
    )
    parser.add_argument(
        "--model",
        type=str,
        action="append",
        dest="model_paths",
        metavar="PATH",
        help="Path to a specific GGUF model to benchmark.",
    )
    parser.add_argument(
        "--memory-limit",
        type=int,
        metavar="MB",
        help="Override the RAM budget (MB) for acceptance criteria.",
    )
    parser.add_argument(
        "--prompts",
        type=int,
        metavar="N",
        help="Limit the number of prompts to run (for quick tests).",
    )
    parser.add_argument(
        "--output-dir",
        type=str,
        default=None,
        help="Directory for results (default: results/).",
    )
    args = parser.parse_args()

    config = load_config()
    prompts = load_prompts()

    # Apply overrides
    if args.memory_limit:
        config["acceptance_criteria"]["peak_ram_mb"] = args.memory_limit
        print(f"RAM budget overridden to {args.memory_limit} MB")

    if args.prompts:
        prompts = prompts[: args.prompts]
        print(f"Using first {len(prompts)} prompt(s)")

    # Determine output directory
    spike_root = Path(__file__).parent.parent
    results_dir = Path(args.output_dir) if args.output_dir else spike_root / "results"
    results_dir.mkdir(parents=True, exist_ok=True)

    # Determine which models to benchmark
    if args.all:
        models_dir = spike_root / "models"
        model_paths = find_models(models_dir)
        if not model_paths:
            print(f"No GGUF models found in {models_dir}/")
            print("Run: python scripts/download_models.py --all")
            sys.exit(1)
    elif args.model_paths:
        model_paths = [Path(p) for p in args.model_paths]
        for p in model_paths:
            if not p.exists():
                print(f"ERROR: Model not found: {p}", file=sys.stderr)
                sys.exit(1)
    else:
        parser.print_help()
        return

    print(f"\nP0-001: LLM Inference Benchmark")
    print(f"{'='*60}")
    print(f"Models to test:  {len(model_paths)}")
    print(f"Prompts per model: {len(prompts)}")
    print(f"Runs per prompt: {config['benchmark']['benchmark_runs']}")
    print(f"Max tokens:      {config['benchmark']['max_tokens']}")
    print(f"RAM budget:      {config['acceptance_criteria']['peak_ram_mb']} MB")
    print(f"Results dir:     {results_dir}/")

    # Run benchmarks
    all_results = []
    for model_path in model_paths:
        result = benchmark_model(str(model_path), config, prompts)
        all_results.append(result)

        # Save individual result
        result_file = results_dir / f"{model_path.stem}.json"
        with open(result_file, "w") as f:
            json.dump(result, f, indent=2)
        print(f"  Saved: {result_file}")

    # Save combined results
    combined = {
        "benchmark_run": {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "config": {
                "n_ctx": config["benchmark"]["n_ctx"],
                "max_tokens": config["benchmark"]["max_tokens"],
                "temperature": config["benchmark"]["temperature"],
                "n_threads": config["benchmark"]["n_threads"],
                "benchmark_runs": config["benchmark"]["benchmark_runs"],
            },
            "acceptance_criteria": config["acceptance_criteria"],
            "target_device": config["target_device"],
            "prompts_used": len(prompts),
            "system_info": {
                "total_ram_mb": round(psutil.virtual_memory().total / (1024 * 1024)),
                "cpu_count": psutil.cpu_count(),
                "cpu_count_physical": psutil.cpu_count(logical=False),
            },
        },
        "results": all_results,
    }

    combined_file = results_dir / "benchmark_combined.json"
    with open(combined_file, "w") as f:
        json.dump(combined, f, indent=2)

    # Print final summary
    print(f"\n\n{'='*60}")
    print("BENCHMARK SUMMARY")
    print(f"{'='*60}")
    print(f"{'Model':<45} {'Size':>7} {'Load':>7} {'Gen':>7} {'RAM':>7} {'tok/s':>7} {'Verdict':>8}")
    print(f"{'─'*45} {'─'*7} {'─'*7} {'─'*7} {'─'*7} {'─'*7} {'─'*8}")

    for r in all_results:
        if r["status"] == "FAILED":
            print(f"{r['model_filename']:<45} {r['model_size_mb']:>6.0f}M {'FAILED':>7} {'':>7} {'':>7} {'':>7} {'FAIL':>8}")
            continue

        print(
            f"{r['model_filename']:<45} "
            f"{r['model_size_mb']:>6.0f}M "
            f"{r['load_metrics']['load_time_s']:>6.1f}s "
            f"{r['aggregate']['avg_generation_time_s']:>6.1f}s "
            f"{r['aggregate']['peak_ram_mb']:>6.0f}M "
            f"{r['aggregate']['avg_tokens_per_sec']:>6.1f} "
            f"{r['status']:>8}"
        )

    passing = [r for r in all_results if r["status"] == "PASS"]
    print(f"\nPassing models: {len(passing)}/{len(all_results)}")

    if passing:
        best = min(passing, key=lambda r: r["aggregate"]["avg_generation_time_s"])
        print(f"Fastest passing: {best['model_filename']} ({best['aggregate']['avg_generation_time_s']:.1f}s avg)")

    print(f"\nResults saved to: {combined_file}")
    print(f"Next step: python scripts/report_generator.py --results {results_dir}")


if __name__ == "__main__":
    main()
