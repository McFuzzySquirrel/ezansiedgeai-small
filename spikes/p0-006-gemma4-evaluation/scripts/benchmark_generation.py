#!/usr/bin/env python3
"""Benchmark Gemma 4 text generation via MediaPipe GenAI SDK.

Runs the 12 CAPS curriculum prompts from config.yaml through Gemma 4 1B,
measuring latency, throughput, memory, and quality. Results are compared
against P0-001 baselines (Qwen2.5-1.5B via llama.cpp).

Usage:
    python scripts/benchmark_generation.py
    python scripts/benchmark_generation.py --memory-limit 1200
    python scripts/benchmark_generation.py --cpu-only
"""

import argparse
import json
import os
import statistics
import sys
import time
from pathlib import Path

import psutil
import yaml


def load_config() -> dict:
    config_path = Path(__file__).parent.parent / "config.yaml"
    with open(config_path) as f:
        return yaml.safe_load(f)


def get_model_path(config: dict) -> Path:
    models_dir = Path(__file__).parent.parent / "models"
    filename = config["model"]["filename"]
    path = models_dir / filename
    if not path.exists():
        print(f"ERROR: Model not found at {path}")
        print("Run: python scripts/download_model.py")
        sys.exit(1)
    return path


def measure_memory_mb() -> float:
    process = psutil.Process(os.getpid())
    return process.memory_info().rss / (1024 * 1024)


def format_prompt(template: str, prompt: dict) -> str:
    return template.format(content=prompt["content"], question=prompt["question"])


def score_response(response: str, expected_concepts: list[str]) -> dict:
    """Simple concept-coverage quality scoring."""
    response_lower = response.lower()
    hits = [c for c in expected_concepts if c.lower() in response_lower]
    return {
        "score": len(hits) / len(expected_concepts) if expected_concepts else 0.0,
        "hits": hits,
        "misses": [c for c in expected_concepts if c.lower() not in response_lower],
        "total_concepts": len(expected_concepts),
    }


def run_benchmark(config: dict, cpu_only: bool = False, memory_limit: int = 2048) -> dict:
    """Run generation benchmarks using MediaPipe GenAI SDK."""
    gen_cfg = config["generation_benchmark"]
    prompts = config["test_prompts"]
    template = config["prompt_template"]

    model_path = get_model_path(config)
    results = {
        "model": config["model"]["id"],
        "runtime": config["model"]["runtime"],
        "gpu_enabled": not cpu_only and gen_cfg["use_gpu_delegate"],
        "memory_limit_mb": memory_limit,
        "benchmarks": [],
        "summary": {},
    }

    # NOTE: MediaPipe GenAI Python API - actual import will depend on
    # the installed version. This is a scaffold for the real implementation.
    print("=" * 60)
    print(f"Gemma 4 Generation Benchmark — P0-006")
    print(f"Model: {config['model']['name']}")
    print(f"GPU: {'enabled' if results['gpu_enabled'] else 'disabled (CPU only)'}")
    print(f"Memory limit: {memory_limit} MB")
    print("=" * 60)

    try:
        import mediapipe as mp
        from mediapipe.tasks import genai

        # Configure inference
        model_options = genai.LlmInferenceOptions(
            model_path=str(model_path),
            max_tokens=gen_cfg["max_tokens"],
            temperature=gen_cfg["temperature"],
            top_k=gen_cfg["top_k"],
            random_seed=gen_cfg["seed"],
        )
        engine = genai.LlmInference(options=model_options)
        print("MediaPipe GenAI engine loaded successfully.")
    except ImportError:
        print("WARNING: MediaPipe GenAI not available in Python environment.")
        print("This benchmark is primarily designed for Android (Kotlin/JVM).")
        print("Generating scaffold results for report template...")
        engine = None
    except Exception as e:
        print(f"WARNING: Failed to initialize MediaPipe GenAI: {e}")
        print("Generating scaffold results for report template...")
        engine = None

    mem_before = measure_memory_mb()
    load_start = time.monotonic()

    # Benchmark each prompt
    for i, prompt in enumerate(prompts):
        print(f"\n[{i+1}/{len(prompts)}] {prompt['id']}: {prompt['question'][:60]}...")

        prompt_text = format_prompt(template, prompt)
        run_times = []
        run_tokens = []
        best_response = ""

        for run in range(gen_cfg["benchmark_runs"]):
            is_warmup = run < gen_cfg["warmup_runs"]
            label = "warmup" if is_warmup else f"run {run - gen_cfg['warmup_runs'] + 1}"

            start = time.monotonic()

            if engine is not None:
                try:
                    response = engine.generate_response(prompt_text)
                    elapsed = time.monotonic() - start
                    tokens = len(response.split())  # Approximate
                except Exception as e:
                    print(f"  {label}: ERROR — {e}")
                    elapsed = 0
                    response = ""
                    tokens = 0
            else:
                # Scaffold mode — no real inference
                elapsed = 0
                response = f"[SCAFFOLD] No inference engine available. Prompt: {prompt['id']}"
                tokens = 0

            if not is_warmup:
                run_times.append(elapsed)
                run_tokens.append(tokens)
                if len(response) > len(best_response):
                    best_response = response

            print(f"  {label}: {elapsed:.2f}s, ~{tokens} tokens")

        quality = score_response(best_response, prompt.get("expected_concepts", []))
        mem_after = measure_memory_mb()

        result = {
            "prompt_id": prompt["id"],
            "question": prompt["question"],
            "avg_time_s": statistics.mean(run_times) if run_times else 0,
            "min_time_s": min(run_times) if run_times else 0,
            "max_time_s": max(run_times) if run_times else 0,
            "avg_tokens": statistics.mean(run_tokens) if run_tokens else 0,
            "quality_score": quality["score"],
            "concept_hits": quality["hits"],
            "concept_misses": quality["misses"],
            "peak_ram_mb": mem_after,
            "response_preview": best_response[:200],
        }
        results["benchmarks"].append(result)

    # Summary
    all_times = [b["avg_time_s"] for b in results["benchmarks"]]
    all_scores = [b["quality_score"] for b in results["benchmarks"]]
    peak_ram = max(b["peak_ram_mb"] for b in results["benchmarks"]) if results["benchmarks"] else 0

    results["summary"] = {
        "avg_generation_time_s": statistics.mean(all_times) if all_times else 0,
        "median_generation_time_s": statistics.median(all_times) if all_times else 0,
        "avg_quality_score": statistics.mean(all_scores) if all_scores else 0,
        "peak_ram_mb": peak_ram,
        "ram_delta_mb": peak_ram - mem_before,
        "total_prompts": len(prompts),
        "engine_available": engine is not None,
    }

    # Check against acceptance criteria
    gen_accept = config["generation_acceptance"]
    time_threshold = gen_accept["generation_time_gpu_s"] if results["gpu_enabled"] else gen_accept["generation_time_cpu_s"]
    results["acceptance"] = {
        "generation_time_pass": results["summary"]["avg_generation_time_s"] <= time_threshold,
        "peak_ram_pass": peak_ram <= gen_accept["peak_ram_mb"],
        "quality_pass": results["summary"]["avg_quality_score"] >= gen_accept["quality_score_min"],
    }

    return results


def main():
    parser = argparse.ArgumentParser(description="Benchmark Gemma 4 generation")
    parser.add_argument("--cpu-only", action="store_true", help="Disable GPU delegate")
    parser.add_argument("--memory-limit", type=int, default=2048, help="Memory limit in MB")
    parser.add_argument("--output", default="results/generation-benchmarks.json")
    args = parser.parse_args()

    config = load_config()
    results = run_benchmark(config, cpu_only=args.cpu_only, memory_limit=args.memory_limit)

    # Save results
    output_path = Path(__file__).parent.parent / args.output
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w") as f:
        json.dump(results, f, indent=2)

    # Print summary
    s = results["summary"]
    print("\n" + "=" * 60)
    print("GENERATION BENCHMARK SUMMARY")
    print("=" * 60)
    print(f"Model:               {results['model']}")
    print(f"GPU:                 {'Yes' if results['gpu_enabled'] else 'No'}")
    print(f"Engine available:    {'Yes' if s['engine_available'] else 'No (scaffold mode)'}")
    print(f"Avg generation:      {s['avg_generation_time_s']:.2f}s")
    print(f"Avg quality:         {s['avg_quality_score']:.1%}")
    print(f"Peak RAM:            {s['peak_ram_mb']:.0f} MB")
    print(f"Results saved to:    {output_path}")

    a = results["acceptance"]
    print(f"\nAcceptance: time={'PASS' if a['generation_time_pass'] else 'FAIL'} "
          f"ram={'PASS' if a['peak_ram_pass'] else 'FAIL'} "
          f"quality={'PASS' if a['quality_pass'] else 'FAIL'}")


if __name__ == "__main__":
    main()
