#!/usr/bin/env python3
"""
P0-004: Content pack validator.

Opens a .pack SQLite file, verifies structural integrity (SHA-256 per chunk),
loads the FAISS index, embeds test queries, and reports top-3 retrieval accuracy.

Exits 0 if top-3 accuracy >= 80%, exits 1 otherwise.

Usage:
  python validate_pack.py \\
      --pack ../../content-packs/maths-grade6-caps-fractions-v0.1.pack \\
      --queries content/maths-grade6-caps-fractions-v0.1/test_queries.json \\
      --models-dir ../../spikes/p0-002-embedding-retrieval/models
"""

import argparse
import hashlib
import json
import os
import sqlite3
import sys
import tempfile
import time
from pathlib import Path

import faiss
import numpy as np
from sentence_transformers import SentenceTransformer

PASS_THRESHOLD = 0.80
TOP_K = 3


def open_pack(pack_path: Path) -> sqlite3.Connection:
    if not pack_path.exists():
        print(f"ERROR: pack not found: {pack_path}", file=sys.stderr)
        sys.exit(1)
    con = sqlite3.connect(str(pack_path))
    con.row_factory = sqlite3.Row
    return con


def read_manifest(con: sqlite3.Connection) -> dict:
    rows = con.execute("SELECT key, value FROM manifest").fetchall()
    return {r["key"]: r["value"] for r in rows}


def read_chunks(con: sqlite3.Connection) -> list[dict]:
    rows = con.execute(
        "SELECT chunk_id, topic_path, title, difficulty, term FROM chunks ORDER BY id"
    ).fetchall()
    return [dict(r) for r in rows]


def verify_sha256(con: sqlite3.Connection) -> tuple[int, int]:
    rows = con.execute("SELECT chunk_id, content, sha256 FROM chunks").fetchall()
    passed, failed = 0, 0
    for row in rows:
        actual = hashlib.sha256(row["content"].encode("utf-8")).hexdigest()
        if actual == row["sha256"]:
            passed += 1
        else:
            failed += 1
            print(f"  CHECKSUM FAIL: {row['chunk_id']}")
    return passed, failed


def load_faiss_index(con: sqlite3.Connection) -> tuple[faiss.Index, list[str]]:
    row = con.execute(
        "SELECT index_data, chunk_order FROM faiss_indexes ORDER BY id LIMIT 1"
    ).fetchone()
    if row is None:
        print("ERROR: no FAISS index found in pack", file=sys.stderr)
        sys.exit(1)
    chunk_order = json.loads(row["chunk_order"])
    with tempfile.NamedTemporaryFile(suffix=".faiss", delete=False) as tmp:
        tmp.write(row["index_data"])
        tmp_path = tmp.name
    try:
        index = faiss.read_index(tmp_path)
    finally:
        os.unlink(tmp_path)
    return index, chunk_order


def resolve_model(manifest: dict, models_dir: Path | None) -> str:
    model_id = manifest.get("embedding_model", "sentence-transformers/all-MiniLM-L6-v2")
    if models_dir:
        local = models_dir / "all-minilm-l6-v2"
        if local.exists():
            return str(local)
    return model_id


def run_queries(
    queries: list[dict],
    index: faiss.Index,
    chunk_order: list[str],
    model: SentenceTransformer,
) -> dict:
    hits, total = 0, 0
    results = []
    for q in queries:
        t0 = time.perf_counter()
        q_vec = model.encode(
            [q["question"]],
            convert_to_numpy=True,
            normalize_embeddings=True,
            show_progress_bar=False,
        ).astype(np.float32)
        faiss.normalize_L2(q_vec)
        embed_ms = (time.perf_counter() - t0) * 1000

        t1 = time.perf_counter()
        distances, indices = index.search(q_vec, TOP_K)
        search_ms = (time.perf_counter() - t1) * 1000

        retrieved = [chunk_order[i] for i in indices[0] if i >= 0]
        expected = set(q["expected_chunk_ids"])
        hit = bool(expected & set(retrieved))
        hits += int(hit)
        total += 1
        results.append(
            {
                "id": q["id"],
                "question": q["question"],
                "expected": list(expected),
                "retrieved": retrieved,
                "hit": hit,
                "embed_ms": embed_ms,
                "search_ms": search_ms,
            }
        )
    return {
        "hits": hits,
        "total": total,
        "accuracy": hits / total if total else 0.0,
        "results": results,
    }


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Validate a SQLite content pack.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument("--pack", required=True, help="Path to .pack file")
    parser.add_argument("--queries", default=None, help="Path to test_queries.json")
    parser.add_argument(
        "--models-dir",
        default=None,
        help="Optional path to local sentence-transformers models directory",
    )
    args = parser.parse_args()

    pack_path = Path(args.pack)
    con = open_pack(pack_path)

    print(f"\n=== Content Pack Validator (P0-004) ===")
    print(f"Pack: {pack_path}\n")

    # Manifest
    manifest = read_manifest(con)
    print("Manifest:")
    for k, v in sorted(manifest.items()):
        print(f"  {k:<22} {v}")

    # Structural checks
    chunks = read_chunks(con)
    expected_count = int(manifest.get("chunk_count", 0))
    count_ok = len(chunks) == expected_count
    print(f"\nChunk count: {len(chunks)} (manifest says {expected_count}) {'✓' if count_ok else '✗ MISMATCH'}")

    # SHA-256 integrity
    print("\nChecking SHA-256 integrity...")
    passed, failed = verify_sha256(con)
    print(f"  {passed}/{passed + failed} chunks pass ({'✓' if failed == 0 else f'✗ {failed} FAILED'})")

    # FAISS index
    print("\nLoading FAISS index...")
    t0 = time.perf_counter()
    index, chunk_order = load_faiss_index(con)
    load_ms = (time.perf_counter() - t0) * 1000
    print(f"  Loaded in {load_ms:.1f}ms — {index.ntotal} vectors, dim={index.d}")

    # Resolve test queries
    queries_path = Path(args.queries) if args.queries else None
    if not queries_path:
        # Auto-detect alongside the chunks content dir
        candidate = Path(__file__).parent / "content" / pack_path.stem / "test_queries.json"
        if candidate.exists():
            queries_path = candidate

    if not queries_path or not queries_path.exists():
        print(
            "\nNo test_queries.json found — skipping retrieval accuracy test."
            "\nProvide one with: --queries <path>"
        )
        con.close()
        sys.exit(0)

    with open(queries_path, "r", encoding="utf-8") as f:
        queries_data = json.load(f)
    queries = queries_data["queries"]
    print(f"\nTest queries: {len(queries)} (from {queries_path.name})")

    # Load embedding model
    models_dir = Path(args.models_dir) if args.models_dir else None
    model_id = resolve_model(manifest, models_dir)
    print(f"\nLoading embedding model: {model_id}")
    t0 = time.perf_counter()
    model = SentenceTransformer(model_id)
    print(f"  Loaded in {time.perf_counter() - t0:.3f}s")

    # Run queries
    print(f"\nRunning {len(queries)} queries (top-{TOP_K} retrieval)...\n")
    report = run_queries(queries, index, chunk_order, model)

    print(f"{'ID':<6} {'Hit':<5} {'Embed':>9} {'Search':>8}  Question")
    print("-" * 76)
    for r in report["results"]:
        mark = "✓" if r["hit"] else "✗"
        print(
            f"{r['id']:<6} {mark:<5} {r['embed_ms']:>7.1f}ms {r['search_ms']:>6.3f}ms  "
            f"{r['question'][:52]}"
        )
        if not r["hit"]:
            print(f"       Expected : {r['expected']}")
            print(f"       Retrieved: {r['retrieved']}")

    accuracy_pct = report["accuracy"] * 100
    status = "PASS" if report["accuracy"] >= PASS_THRESHOLD else "FAIL"
    avg_embed = sum(r["embed_ms"] for r in report["results"]) / len(report["results"])
    avg_search = sum(r["search_ms"] for r in report["results"]) / len(report["results"])

    print(f"\n{'=' * 76}")
    print(
        f"Top-{TOP_K} accuracy : {accuracy_pct:.1f}%  "
        f"({report['hits']}/{report['total']})  [{status}]"
    )
    print(f"Avg embed time : {avg_embed:.1f}ms   (target: <500ms)  {'✓' if avg_embed < 500 else '✗'}")
    print(f"Avg search time: {avg_search:.3f}ms  (target: <500ms)  {'✓' if avg_search < 500 else '✗'}")

    con.close()
    sys.exit(0 if status == "PASS" else 1)


if __name__ == "__main__":
    main()
