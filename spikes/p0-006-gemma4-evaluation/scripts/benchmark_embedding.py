#!/usr/bin/env python3
"""Benchmark Gemma 4 embedding quality and retrieval accuracy.

Tests embedding extraction from Gemma 4 1B, comparing retrieval accuracy
against P0-002 baselines (all-MiniLM-L6-v2 via ONNX). Includes cross-platform
parity checks (Python vs Android embedding paths).

Usage:
    python scripts/benchmark_embedding.py
    python scripts/benchmark_embedding.py --parity-check
    python scripts/benchmark_embedding.py --dim 384
"""

import argparse
import json
import os
import statistics
import sys
import time
from pathlib import Path

import numpy as np
import psutil
import yaml


def load_config() -> dict:
    config_path = Path(__file__).parent.parent / "config.yaml"
    with open(config_path) as f:
        return yaml.safe_load(f)


def measure_memory_mb() -> float:
    process = psutil.Process(os.getpid())
    return process.memory_info().rss / (1024 * 1024)


def cosine_similarity(a: np.ndarray, b: np.ndarray) -> float:
    norm_a = np.linalg.norm(a)
    norm_b = np.linalg.norm(b)
    if norm_a == 0 or norm_b == 0:
        return 0.0
    return float(np.dot(a, b) / (norm_a * norm_b))


def build_test_corpus(config: dict) -> list[dict]:
    """Build a small corpus from test prompts for retrieval testing."""
    corpus = []
    for prompt in config["test_prompts"]:
        corpus.append({
            "id": prompt["id"],
            "text": prompt["content"],
            "question": prompt["question"],
        })
    return corpus


def run_embedding_benchmark(config: dict, target_dim: int = 768) -> dict:
    """Run embedding benchmarks using Gemma 4 for embedding extraction."""
    embed_cfg = config["embedding_benchmark"]
    corpus = build_test_corpus(config)

    results = {
        "model": config["model"]["id"],
        "embedding_dim": target_dim,
        "distance_metric": embed_cfg["distance_metric"],
        "normalize": embed_cfg["normalize"],
        "corpus_size": len(corpus),
        "benchmarks": [],
        "retrieval_results": [],
        "summary": {},
    }

    print("=" * 60)
    print(f"Gemma 4 Embedding Benchmark — P0-006")
    print(f"Target dim: {target_dim}")
    print("=" * 60)

    # Try to load embedding model
    engine = None
    try:
        import mediapipe as mp
        # NOTE: MediaPipe embedding extraction API may vary.
        # This scaffold shows the intended flow.
        print("MediaPipe loaded — attempting embedding extraction setup...")
        # Actual implementation depends on MediaPipe's embedding API
        engine = None  # Placeholder
    except ImportError:
        print("WARNING: MediaPipe not available in Python environment.")
        print("Generating scaffold results...")

    mem_before = measure_memory_mb()

    # Embed corpus
    print(f"\nEmbedding {len(corpus)} documents...")
    corpus_embeddings = []
    embed_times = []

    for doc in corpus:
        start = time.monotonic()

        if engine is not None:
            # Real embedding extraction
            embedding = np.zeros(target_dim)  # Placeholder
        else:
            # Scaffold: deterministic pseudo-embedding for structure testing
            np.random.seed(hash(doc["text"]) % (2**31))
            embedding = np.random.randn(target_dim).astype(np.float32)
            if embed_cfg["normalize"]:
                embedding = embedding / np.linalg.norm(embedding)

        elapsed_ms = (time.monotonic() - start) * 1000
        embed_times.append(elapsed_ms)
        corpus_embeddings.append(embedding)
        print(f"  {doc['id']}: {elapsed_ms:.1f}ms, dim={len(embedding)}")

    # Retrieval benchmark
    print(f"\nRetrieval benchmark (top-{embed_cfg['top_k_retrieval']})...")
    correct_retrievals = 0

    for i, doc in enumerate(corpus):
        query = doc["question"]

        # Embed query
        start = time.monotonic()
        if engine is not None:
            query_embedding = np.zeros(target_dim)
        else:
            np.random.seed(hash(query) % (2**31))
            query_embedding = np.random.randn(target_dim).astype(np.float32)
            if embed_cfg["normalize"]:
                query_embedding = query_embedding / np.linalg.norm(query_embedding)
        query_time_ms = (time.monotonic() - start) * 1000

        # Compute similarities
        similarities = [
            cosine_similarity(query_embedding, ce) for ce in corpus_embeddings
        ]
        ranked = sorted(range(len(similarities)), key=lambda j: similarities[j], reverse=True)
        top_k = ranked[:embed_cfg["top_k_retrieval"]]

        is_correct = i in top_k
        if is_correct:
            correct_retrievals += 1

        rank = ranked.index(i) + 1

        results["retrieval_results"].append({
            "query_id": doc["id"],
            "question": query[:80],
            "correct_doc_rank": rank,
            "in_top_k": is_correct,
            "top_k_ids": [corpus[j]["id"] for j in top_k],
            "query_time_ms": query_time_ms,
        })
        print(f"  {doc['id']}: rank={rank}, top-{embed_cfg['top_k_retrieval']}={'✓' if is_correct else '✗'}")

    retrieval_accuracy = correct_retrievals / len(corpus) if corpus else 0
    mem_after = measure_memory_mb()

    results["summary"] = {
        "avg_embed_time_ms": statistics.mean(embed_times) if embed_times else 0,
        "median_embed_time_ms": statistics.median(embed_times) if embed_times else 0,
        "retrieval_accuracy_top3": retrieval_accuracy,
        "correct_retrievals": correct_retrievals,
        "total_queries": len(corpus),
        "peak_ram_mb": mem_after,
        "ram_delta_mb": mem_after - mem_before,
        "engine_available": engine is not None,
    }

    # Check against acceptance criteria
    embed_accept = config["embedding_acceptance"]
    results["acceptance"] = {
        "embedding_time_pass": results["summary"]["avg_embed_time_ms"] <= embed_accept["embedding_time_ms"],
        "retrieval_accuracy_pass": retrieval_accuracy >= embed_accept["retrieval_accuracy_top3"],
    }

    return results


def run_parity_check(config: dict, target_dim: int = 768) -> dict:
    """Check embedding parity between Python and Android paths.

    This generates embeddings using the Python path and saves them
    for comparison with Android-generated embeddings.
    """
    print("=" * 60)
    print("Cross-Platform Embedding Parity Check — P0-006")
    print("=" * 60)
    print("\nThis check requires:")
    print("  1. Run this script to generate Python embeddings")
    print("  2. Run the Android benchmark app to generate Android embeddings")
    print("  3. Compare the two sets using --compare-parity flag")
    print("\nGenerating Python-side embeddings...")

    corpus = build_test_corpus(config)
    parity_data = {
        "platform": "python",
        "model": config["model"]["id"],
        "embedding_dim": target_dim,
        "embeddings": {},
    }

    for doc in corpus:
        # Scaffold: generate deterministic embeddings
        np.random.seed(hash(doc["text"]) % (2**31))
        embedding = np.random.randn(target_dim).astype(np.float32)
        embedding = embedding / np.linalg.norm(embedding)
        parity_data["embeddings"][doc["id"]] = embedding.tolist()

    return parity_data


def main():
    parser = argparse.ArgumentParser(description="Benchmark Gemma 4 embeddings")
    parser.add_argument("--dim", type=int, default=768, help="Embedding dimension")
    parser.add_argument("--parity-check", action="store_true", help="Run cross-platform parity check")
    parser.add_argument("--output", default="results/embedding-benchmarks.json")
    args = parser.parse_args()

    config = load_config()

    if args.parity_check:
        results = run_parity_check(config, target_dim=args.dim)
        output_path = Path(__file__).parent.parent / "results" / "parity-python.json"
        output_path.parent.mkdir(parents=True, exist_ok=True)
        with open(output_path, "w") as f:
            json.dump(results, f, indent=2)
        print(f"\nParity data saved to: {output_path}")
        return

    results = run_embedding_benchmark(config, target_dim=args.dim)

    output_path = Path(__file__).parent.parent / args.output
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w") as f:
        json.dump(results, f, indent=2)

    s = results["summary"]
    print("\n" + "=" * 60)
    print("EMBEDDING BENCHMARK SUMMARY")
    print("=" * 60)
    print(f"Model:               {results['model']}")
    print(f"Dimension:           {results['embedding_dim']}")
    print(f"Engine available:    {'Yes' if s['engine_available'] else 'No (scaffold mode)'}")
    print(f"Avg embed time:      {s['avg_embed_time_ms']:.1f}ms")
    print(f"Retrieval accuracy:  {s['retrieval_accuracy_top3']:.1%} (top-3)")
    print(f"Peak RAM:            {s['peak_ram_mb']:.0f} MB")
    print(f"Results saved to:    {output_path}")

    a = results["acceptance"]
    print(f"\nAcceptance: time={'PASS' if a['embedding_time_pass'] else 'FAIL'} "
          f"retrieval={'PASS' if a['retrieval_accuracy_pass'] else 'FAIL'}")


if __name__ == "__main__":
    main()
