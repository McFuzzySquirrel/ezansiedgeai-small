#!/usr/bin/env python3
"""Benchmark embedding + retrieval for P0-002 feasibility spike.

Loads candidate embedding models, embeds curriculum content chunks, builds
vector indices, runs test queries, and measures retrieval accuracy, latency,
RAM usage, and storage footprint.

Usage:
    python scripts/benchmark.py --all
    python scripts/benchmark.py --model all-minilm-l6-v2
    python scripts/benchmark.py --all --memory-limit 1161
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

import numpy as np
import psutil
import yaml


def load_config() -> dict:
    """Load benchmark config from config.yaml."""
    config_path = Path(__file__).parent.parent / "config.yaml"
    with open(config_path) as f:
        return yaml.safe_load(f)


def load_chunks() -> list[dict]:
    """Load curriculum content chunks."""
    chunks_path = Path(__file__).parent.parent / "content" / "chunks.json"
    with open(chunks_path) as f:
        data = json.load(f)
    return data["chunks"]


def load_test_queries() -> list[dict]:
    """Load test queries with expected results."""
    queries_path = Path(__file__).parent.parent / "content" / "test_queries.json"
    with open(queries_path) as f:
        data = json.load(f)
    return data["queries"]


def get_process_memory_mb() -> float:
    """Get current process RSS in MB."""
    return psutil.Process(os.getpid()).memory_info().rss / (1024 * 1024)


def get_dir_size_mb(path: Path) -> float:
    """Get total size of a directory in MB."""
    if not path.exists():
        return 0.0
    total = sum(f.stat().st_size for f in path.rglob("*") if f.is_file())
    return total / (1024 * 1024)


# ---------------------------------------------------------------------------
# Vector store implementations
# ---------------------------------------------------------------------------

class NumpyCosineStore:
    """Baseline: brute-force cosine similarity with NumPy."""

    def __init__(self):
        self.embeddings = None
        self.ids = None

    def build(self, embeddings: np.ndarray, ids: list[str]) -> float:
        start = time.perf_counter()
        # Normalise for cosine similarity (dot product on unit vectors)
        norms = np.linalg.norm(embeddings, axis=1, keepdims=True)
        self.embeddings = embeddings / norms
        self.ids = ids
        return time.perf_counter() - start

    def search(self, query_embedding: np.ndarray, top_k: int) -> list[tuple[str, float]]:
        # Normalise query
        query_norm = query_embedding / np.linalg.norm(query_embedding)
        # Cosine similarity = dot product of normalised vectors
        similarities = self.embeddings @ query_norm
        top_indices = np.argsort(similarities)[::-1][:top_k]
        return [(self.ids[i], float(similarities[i])) for i in top_indices]

    def index_size_bytes(self) -> int:
        if self.embeddings is not None:
            return self.embeddings.nbytes
        return 0


class FaissFlatStore:
    """FAISS brute-force (exact) search."""

    def __init__(self):
        self.index = None
        self.ids = None

    def build(self, embeddings: np.ndarray, ids: list[str]) -> float:
        import faiss
        start = time.perf_counter()
        dim = embeddings.shape[1]
        self.index = faiss.IndexFlatIP(dim)  # Inner product (use normalised vectors for cosine)
        # Normalise
        faiss.normalize_L2(embeddings)
        self.index.add(embeddings)
        self.ids = ids
        return time.perf_counter() - start

    def search(self, query_embedding: np.ndarray, top_k: int) -> list[tuple[str, float]]:
        import faiss
        query = query_embedding.reshape(1, -1).copy()
        faiss.normalize_L2(query)
        distances, indices = self.index.search(query, top_k)
        return [(self.ids[i], float(distances[0][j])) for j, i in enumerate(indices[0]) if i >= 0]

    def index_size_bytes(self) -> int:
        if self.index is not None:
            return self.index.ntotal * self.index.d * 4  # float32
        return 0


class FaissIVFStore:
    """FAISS IVF-Flat (approximate) search."""

    def __init__(self, nlist: int = 8, nprobe: int = 4):
        self.nlist = nlist
        self.nprobe = nprobe
        self.index = None
        self.ids = None

    def build(self, embeddings: np.ndarray, ids: list[str]) -> float:
        import faiss
        start = time.perf_counter()
        dim = embeddings.shape[1]
        # Adjust nlist if we have fewer vectors
        nlist = min(self.nlist, len(ids))
        quantizer = faiss.IndexFlatIP(dim)
        self.index = faiss.IndexIVFFlat(quantizer, dim, nlist, faiss.METRIC_INNER_PRODUCT)
        # Normalise
        faiss.normalize_L2(embeddings)
        self.index.train(embeddings)
        self.index.add(embeddings)
        self.index.nprobe = self.nprobe
        self.ids = ids
        return time.perf_counter() - start

    def search(self, query_embedding: np.ndarray, top_k: int) -> list[tuple[str, float]]:
        import faiss
        query = query_embedding.reshape(1, -1).copy()
        faiss.normalize_L2(query)
        distances, indices = self.index.search(query, top_k)
        return [(self.ids[i], float(distances[0][j])) for j, i in enumerate(indices[0]) if i >= 0]

    def index_size_bytes(self) -> int:
        if self.index is not None:
            return self.index.ntotal * self.index.d * 4
        return 0


class HnswlibStore:
    """HNSWlib graph-based ANN search."""

    def __init__(self, ef_construction: int = 200, M: int = 16, ef_search: int = 50):
        self.ef_construction = ef_construction
        self.M = M
        self.ef_search = ef_search
        self.index = None
        self.ids = None

    def build(self, embeddings: np.ndarray, ids: list[str]) -> float:
        import hnswlib
        start = time.perf_counter()
        dim = embeddings.shape[1]
        self.index = hnswlib.Index(space="cosine", dim=dim)
        self.index.init_index(
            max_elements=len(ids),
            ef_construction=self.ef_construction,
            M=self.M,
        )
        self.index.add_items(embeddings, list(range(len(ids))))
        self.index.set_ef(self.ef_search)
        self.ids = ids
        return time.perf_counter() - start

    def search(self, query_embedding: np.ndarray, top_k: int) -> list[tuple[str, float]]:
        labels, distances = self.index.knn_query(query_embedding.reshape(1, -1), k=top_k)
        # hnswlib cosine distance = 1 - cosine_similarity
        return [(self.ids[i], 1.0 - float(d)) for i, d in zip(labels[0], distances[0])]

    def index_size_bytes(self) -> int:
        if self.index is not None:
            # Approximate: elements * dim * 4 + graph overhead
            return self.index.get_current_count() * self.index.dim * 4 * 2
        return 0


def create_vector_store(store_config: dict) -> object:
    """Create a vector store instance from config."""
    store_id = store_config["id"]
    if store_id == "numpy-cosine":
        return NumpyCosineStore()
    elif store_id == "faiss-flat":
        return FaissFlatStore()
    elif store_id == "faiss-ivf":
        return FaissIVFStore(
            nlist=store_config.get("nlist", 8),
            nprobe=store_config.get("nprobe", 4),
        )
    elif store_id == "hnswlib":
        return HnswlibStore(
            ef_construction=store_config.get("ef_construction", 200),
            M=store_config.get("M", 16),
            ef_search=store_config.get("ef_search", 50),
        )
    else:
        raise ValueError(f"Unknown vector store: {store_id}")


# ---------------------------------------------------------------------------
# Benchmark functions
# ---------------------------------------------------------------------------

def benchmark_model_load(model_path: str) -> tuple:
    """Load an embedding model and measure time + memory.

    Returns (model, metrics_dict).
    """
    from sentence_transformers import SentenceTransformer

    mem_before = get_process_memory_mb()

    print(f"  Loading model: {Path(model_path).name}")
    start = time.perf_counter()

    try:
        model = SentenceTransformer(str(model_path))
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


def benchmark_chunk_embedding(model, chunks: list[dict], config: dict) -> tuple[np.ndarray, dict]:
    """Embed all content chunks and measure performance.

    Returns (embeddings_array, metrics_dict).
    """
    texts = [c["content"] for c in chunks]
    batch_size = config["benchmark"]["embedding_batch_size"]

    mem_before = get_process_memory_mb()
    start = time.perf_counter()

    embeddings = model.encode(
        texts,
        batch_size=batch_size,
        show_progress_bar=False,
        convert_to_numpy=True,
    )

    elapsed = time.perf_counter() - start
    mem_after = get_process_memory_mb()

    metrics = {
        "num_chunks": len(chunks),
        "total_time_s": round(elapsed, 3),
        "avg_time_per_chunk_ms": round((elapsed / len(chunks)) * 1000, 2),
        "peak_ram_mb": round(max(mem_before, mem_after), 1),
        "embedding_dimensions": embeddings.shape[1],
    }

    print(f"    Embedded {len(chunks)} chunks in {elapsed:.3f}s ({metrics['avg_time_per_chunk_ms']:.2f} ms/chunk)")
    print(f"    Embedding dimensions: {embeddings.shape[1]}")

    return embeddings.astype(np.float32), metrics


def benchmark_query_retrieval(
    model,
    store,
    queries: list[dict],
    chunk_ids: list[str],
    config: dict,
) -> dict:
    """Run test queries and measure retrieval accuracy + latency.

    Returns metrics dict with per-query results.
    """
    top_k = config["benchmark"]["top_k"]
    benchmark_runs = config["benchmark"]["benchmark_runs"]

    query_results = []
    all_embedding_times = []
    all_search_times = []
    hits = 0
    total_expected = 0

    for query in queries:
        run_embedding_times = []
        run_search_times = []
        best_retrieved_ids = None

        for _ in range(benchmark_runs):
            # Embed the query
            mem_before = get_process_memory_mb()
            embed_start = time.perf_counter()
            query_embedding = model.encode(
                query["question"],
                show_progress_bar=False,
                convert_to_numpy=True,
            ).astype(np.float32)
            embed_time = time.perf_counter() - embed_start
            run_embedding_times.append(embed_time)

            # Search
            search_start = time.perf_counter()
            results = store.search(query_embedding, top_k)
            search_time = time.perf_counter() - search_start
            run_search_times.append(search_time)

            mem_after = get_process_memory_mb()

            if best_retrieved_ids is None:
                best_retrieved_ids = [r[0] for r in results]

        avg_embed_ms = statistics.mean(run_embedding_times) * 1000
        avg_search_ms = statistics.mean(run_search_times) * 1000
        all_embedding_times.append(avg_embed_ms)
        all_search_times.append(avg_search_ms)

        # Check accuracy: how many expected chunks are in top-k?
        retrieved_ids = best_retrieved_ids or []
        expected_ids = query["expected_chunk_ids"]
        query_hits = sum(1 for eid in expected_ids if eid in retrieved_ids)
        query_hit = query_hits > 0  # At least one expected chunk retrieved

        if query_hit:
            hits += 1
        total_expected += 1

        query_result = {
            "query_id": query["id"],
            "question": query["question"],
            "expected_chunk_ids": expected_ids,
            "retrieved_chunk_ids": retrieved_ids,
            "retrieved_scores": [r[1] for r in (store.search(
                model.encode(query["question"], show_progress_bar=False, convert_to_numpy=True).astype(np.float32),
                top_k,
            ))],
            "hit": query_hit,
            "chunks_found": query_hits,
            "chunks_expected": len(expected_ids),
            "avg_embedding_time_ms": round(avg_embed_ms, 2),
            "avg_search_time_ms": round(avg_search_ms, 2),
        }

        status_icon = "✓" if query_hit else "✗"
        print(
            f"    {status_icon} {query['id']:5s}  "
            f"embed: {avg_embed_ms:6.1f}ms  "
            f"search: {avg_search_ms:5.2f}ms  "
            f"hits: {query_hits}/{len(expected_ids)}  "
            f"retrieved: {retrieved_ids}"
        )

        query_results.append(query_result)

    accuracy = hits / total_expected if total_expected > 0 else 0

    return {
        "top_k": top_k,
        "total_queries": len(queries),
        "queries_with_hit": hits,
        "retrieval_accuracy": round(accuracy, 4),
        "avg_embedding_time_ms": round(statistics.mean(all_embedding_times), 2),
        "avg_search_time_ms": round(statistics.mean(all_search_times), 2),
        "max_embedding_time_ms": round(max(all_embedding_times), 2),
        "max_search_time_ms": round(max(all_search_times), 2),
        "query_results": query_results,
    }


def benchmark_model_with_store(
    model_config: dict,
    store_config: dict,
    config: dict,
    chunks: list[dict],
    queries: list[dict],
    models_dir: Path,
) -> dict:
    """Run full benchmark for one model + one vector store combination."""
    model_id = model_config["id"]
    store_id = store_config["id"]
    model_path = models_dir / model_id
    model_size_mb = get_dir_size_mb(model_path)

    print(f"\n{'='*70}")
    print(f"Benchmarking: {model_id} + {store_id}")
    print(f"Model size:   {model_size_mb:.1f} MB")
    print(f"{'='*70}")

    # Load model
    model, load_metrics = benchmark_model_load(str(model_path))
    if model is None:
        return {
            "model_id": model_id,
            "store_id": store_id,
            "model_size_mb": round(model_size_mb, 1),
            "status": "FAILED",
            "error": load_metrics.get("error", "Unknown error during load"),
            "load_metrics": load_metrics,
        }

    # Embed all chunks
    print(f"\n  Embedding {len(chunks)} content chunks...")
    chunk_ids = [c["id"] for c in chunks]
    embeddings, embed_metrics = benchmark_chunk_embedding(model, chunks, config)

    # Build vector store
    print(f"\n  Building vector store: {store_id}...")
    store = create_vector_store(store_config)
    embeddings_copy = embeddings.copy()  # Some stores modify in-place
    build_time = store.build(embeddings_copy, chunk_ids)
    index_size_bytes = store.index_size_bytes()
    print(f"    Build time:  {build_time:.3f}s")
    print(f"    Index size:  {index_size_bytes / 1024:.1f} KB")

    # Warmup
    bench_cfg = config["benchmark"]
    if bench_cfg["warmup_runs"] > 0:
        print(f"\n  Warmup ({bench_cfg['warmup_runs']} run(s))...")
        warmup_embedding = model.encode(
            "What is a fraction?",
            show_progress_bar=False,
            convert_to_numpy=True,
        ).astype(np.float32)
        store.search(warmup_embedding, bench_cfg["top_k"])

    # Run retrieval queries
    print(f"\n  Running {len(queries)} queries × {bench_cfg['benchmark_runs']} run(s)...")
    peak_ram_before = get_process_memory_mb()
    retrieval_metrics = benchmark_query_retrieval(model, store, queries, chunk_ids, config)
    peak_ram_after = get_process_memory_mb()

    peak_ram = max(peak_ram_before, peak_ram_after, load_metrics["ram_after_load_mb"])

    # Check acceptance criteria
    criteria = config["acceptance_criteria"]
    passes_load_time = load_metrics["load_time_s"] <= criteria["embedding_load_time_s"]
    passes_query_time = retrieval_metrics["avg_embedding_time_ms"] <= criteria["query_embedding_time_ms"]
    passes_search_time = retrieval_metrics["avg_search_time_ms"] <= criteria["vector_search_time_ms"]
    passes_accuracy = retrieval_metrics["retrieval_accuracy"] >= criteria["retrieval_accuracy_top3"]
    passes_ram = peak_ram <= criteria["peak_ram_mb"]
    passes_size = model_size_mb <= criteria["model_size_mb"]

    all_pass = all([passes_load_time, passes_query_time, passes_search_time,
                    passes_accuracy, passes_ram, passes_size])
    verdict = "PASS" if all_pass else "FAIL"

    result = {
        "model_id": model_id,
        "store_id": store_id,
        "model_size_mb": round(model_size_mb, 1),
        "status": verdict,
        "load_metrics": load_metrics,
        "embed_metrics": embed_metrics,
        "store_metrics": {
            "build_time_s": round(build_time, 3),
            "index_size_kb": round(index_size_bytes / 1024, 1),
        },
        "retrieval_metrics": retrieval_metrics,
        "peak_ram_mb": round(peak_ram, 1),
        "acceptance_criteria": {
            "load_time": {"limit_s": criteria["embedding_load_time_s"], "actual_s": load_metrics["load_time_s"], "pass": passes_load_time},
            "query_embedding_time": {"limit_ms": criteria["query_embedding_time_ms"], "actual_ms": retrieval_metrics["avg_embedding_time_ms"], "pass": passes_query_time},
            "vector_search_time": {"limit_ms": criteria["vector_search_time_ms"], "actual_ms": retrieval_metrics["avg_search_time_ms"], "pass": passes_search_time},
            "retrieval_accuracy": {"limit": criteria["retrieval_accuracy_top3"], "actual": retrieval_metrics["retrieval_accuracy"], "pass": passes_accuracy},
            "peak_ram": {"limit_mb": criteria["peak_ram_mb"], "actual_mb": round(peak_ram, 1), "pass": passes_ram},
            "model_size": {"limit_mb": criteria["model_size_mb"], "actual_mb": round(model_size_mb, 1), "pass": passes_size},
        },
    }

    # Summary
    print(f"\n  {'─'*60}")
    print(f"  Verdict:          {verdict}")
    print(f"  Load time:        {load_metrics['load_time_s']:.3f}s {'✓' if passes_load_time else '✗'} (limit: {criteria['embedding_load_time_s']}s)")
    print(f"  Avg query embed:  {retrieval_metrics['avg_embedding_time_ms']:.1f}ms {'✓' if passes_query_time else '✗'} (limit: {criteria['query_embedding_time_ms']}ms)")
    print(f"  Avg search:       {retrieval_metrics['avg_search_time_ms']:.2f}ms {'✓' if passes_search_time else '✗'} (limit: {criteria['vector_search_time_ms']}ms)")
    print(f"  Retrieval acc:    {retrieval_metrics['retrieval_accuracy']:.1%} {'✓' if passes_accuracy else '✗'} (limit: {criteria['retrieval_accuracy_top3']:.0%})")
    print(f"  Peak RAM:         {peak_ram:.1f} MB {'✓' if passes_ram else '✗'} (limit: {criteria['peak_ram_mb']} MB)")
    print(f"  Model size:       {model_size_mb:.1f} MB {'✓' if passes_size else '✗'} (limit: {criteria['model_size_mb']} MB)")

    # Cleanup
    del model
    del store
    gc.collect()

    return result


def find_models(models_dir: Path) -> list[str]:
    """Find all downloaded model directories."""
    if not models_dir.exists():
        return []
    return sorted([
        d.name for d in models_dir.iterdir()
        if d.is_dir() and not d.name.startswith(".")
    ])


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Benchmark embedding + retrieval for P0-002 feasibility spike."
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="Benchmark all downloaded models with all vector stores.",
    )
    parser.add_argument(
        "--model",
        action="append",
        dest="model_ids",
        metavar="MODEL_ID",
        help="Benchmark a specific model by ID (can be repeated).",
    )
    parser.add_argument(
        "--store",
        action="append",
        dest="store_ids",
        metavar="STORE_ID",
        help="Benchmark with a specific vector store (can be repeated).",
    )
    parser.add_argument(
        "--memory-limit",
        type=int,
        metavar="MB",
        help="Override the RAM budget (MB) for acceptance criteria.",
    )
    parser.add_argument(
        "--queries",
        type=int,
        metavar="N",
        help="Limit the number of queries to run (for quick tests).",
    )
    parser.add_argument(
        "--output-dir",
        type=str,
        default=None,
        help="Directory for results (default: results/).",
    )
    args = parser.parse_args()

    config = load_config()
    chunks = load_chunks()
    queries = load_test_queries()

    # Apply overrides
    if args.memory_limit:
        config["acceptance_criteria"]["peak_ram_mb"] = args.memory_limit
        print(f"RAM budget overridden to {args.memory_limit} MB")

    if args.queries:
        queries = queries[:args.queries]
        print(f"Using first {len(queries)} query(ies)")

    # Determine output directory
    spike_root = Path(__file__).parent.parent
    results_dir = Path(args.output_dir) if args.output_dir else spike_root / "results"
    results_dir.mkdir(parents=True, exist_ok=True)

    models_dir = spike_root / "models"
    model_map = {m["id"]: m for m in config["models"]}
    store_map = {s["id"]: s for s in config["vector_stores"]}

    # Determine which models to benchmark
    if args.all:
        available_models = find_models(models_dir)
        model_ids = [mid for mid in available_models if mid in model_map]
        store_ids = list(store_map.keys())
    else:
        model_ids = args.model_ids or []
        store_ids = args.store_ids or list(store_map.keys())

    if not model_ids:
        print("No models found. Run: python scripts/download_models.py --all")
        sys.exit(1)

    # Validate
    for mid in model_ids:
        if mid not in model_map:
            print(f"ERROR: Unknown model '{mid}'. Available: {', '.join(model_map.keys())}", file=sys.stderr)
            sys.exit(1)
        if not (models_dir / mid).exists():
            print(f"ERROR: Model not downloaded: '{mid}'. Run: python scripts/download_models.py --model {mid}", file=sys.stderr)
            sys.exit(1)

    for sid in store_ids:
        if sid not in store_map:
            print(f"ERROR: Unknown store '{sid}'. Available: {', '.join(store_map.keys())}", file=sys.stderr)
            sys.exit(1)

    total_combos = len(model_ids) * len(store_ids)

    print(f"\nP0-002: Embedding + Retrieval Benchmark")
    print(f"{'='*70}")
    print(f"Models to test:    {len(model_ids)} ({', '.join(model_ids)})")
    print(f"Vector stores:     {len(store_ids)} ({', '.join(store_ids)})")
    print(f"Total combinations: {total_combos}")
    print(f"Content chunks:    {len(chunks)}")
    print(f"Test queries:      {len(queries)}")
    print(f"Runs per query:    {config['benchmark']['benchmark_runs']}")
    print(f"RAM budget:        {config['acceptance_criteria']['peak_ram_mb']} MB")
    print(f"Results dir:       {results_dir}/")

    # Run benchmarks
    all_results = []
    for model_id in model_ids:
        for store_id in store_ids:
            result = benchmark_model_with_store(
                model_map[model_id],
                store_map[store_id],
                config,
                chunks,
                queries,
                models_dir,
            )
            all_results.append(result)

            # Save individual result
            result_file = results_dir / f"{model_id}__{store_id}.json"
            with open(result_file, "w") as f:
                json.dump(result, f, indent=2)
            print(f"  Saved: {result_file}")

    # Storage footprint analysis
    storage_footprint = {
        "apk_estimated_mb": config["storage_budget"]["apk_mb"],
        "llm_model_mb": config["storage_budget"]["llm_model_mb"],
        "embedding_models": {},
        "notes": config["storage_budget"]["notes"],
    }
    for model_id in model_ids:
        model_size = get_dir_size_mb(models_dir / model_id)
        storage_footprint["embedding_models"][model_id] = round(model_size, 1)

    # Save combined results
    combined = {
        "benchmark_run": {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "config": {
                "embedding_batch_size": config["benchmark"]["embedding_batch_size"],
                "top_k": config["benchmark"]["top_k"],
                "benchmark_runs": config["benchmark"]["benchmark_runs"],
                "similarity_metric": config["benchmark"]["similarity_metric"],
            },
            "acceptance_criteria": config["acceptance_criteria"],
            "target_device": config["target_device"],
            "storage_budget": config["storage_budget"],
            "content_stats": {
                "num_chunks": len(chunks),
                "num_queries": len(queries),
            },
            "system_info": {
                "total_ram_mb": round(psutil.virtual_memory().total / (1024 * 1024)),
                "cpu_count": psutil.cpu_count(),
                "cpu_count_physical": psutil.cpu_count(logical=False),
            },
        },
        "storage_footprint": storage_footprint,
        "results": all_results,
    }

    combined_file = results_dir / "benchmark_combined.json"
    with open(combined_file, "w") as f:
        json.dump(combined, f, indent=2)

    # Print final summary
    print(f"\n\n{'='*70}")
    print("BENCHMARK SUMMARY")
    print(f"{'='*70}")
    print(f"{'Model':<22} {'Store':<15} {'Size':>6} {'Load':>6} {'Embed':>8} {'Search':>8} {'Acc':>6} {'RAM':>7} {'Verdict':>8}")
    print(f"{'─'*22} {'─'*15} {'─'*6} {'─'*6} {'─'*8} {'─'*8} {'─'*6} {'─'*7} {'─'*8}")

    for r in all_results:
        if r["status"] == "FAILED":
            print(f"{r['model_id']:<22} {r['store_id']:<15} {r['model_size_mb']:>5.0f}M {'FAIL':>6} {'':>8} {'':>8} {'':>6} {'':>7} {'FAIL':>8}")
            continue

        print(
            f"{r['model_id']:<22} "
            f"{r['store_id']:<15} "
            f"{r['model_size_mb']:>5.0f}M "
            f"{r['load_metrics']['load_time_s']:>5.1f}s "
            f"{r['retrieval_metrics']['avg_embedding_time_ms']:>6.1f}ms "
            f"{r['retrieval_metrics']['avg_search_time_ms']:>6.2f}ms "
            f"{r['retrieval_metrics']['retrieval_accuracy']:>5.0%} "
            f"{r['peak_ram_mb']:>6.0f}M "
            f"{r['status']:>8}"
        )

    passing = [r for r in all_results if r["status"] == "PASS"]
    print(f"\nPassing combinations: {len(passing)}/{len(all_results)}")

    if passing:
        best = max(passing, key=lambda r: r["retrieval_metrics"]["retrieval_accuracy"])
        print(f"Best accuracy:  {best['model_id']} + {best['store_id']} ({best['retrieval_metrics']['retrieval_accuracy']:.0%})")
        smallest = min(passing, key=lambda r: r["model_size_mb"])
        print(f"Smallest model: {smallest['model_id']} ({smallest['model_size_mb']:.0f} MB)")

    # Storage summary
    print(f"\n{'='*70}")
    print("STORAGE FOOTPRINT")
    print(f"{'='*70}")
    print(f"  APK (estimated):       {storage_footprint['apk_estimated_mb']} MB")
    print(f"  LLM (Qwen2.5-1.5B):   {storage_footprint['llm_model_mb']} MB")
    for mid, size in storage_footprint["embedding_models"].items():
        print(f"  Embedding ({mid}): {size} MB")
    print(f"  Content pack (est):    {config['storage_budget']['content_pack_mb']} MB")

    print(f"\nResults saved to: {combined_file}")
    print(f"Next step: python scripts/report_generator.py --results {results_dir}")


if __name__ == "__main__":
    main()
