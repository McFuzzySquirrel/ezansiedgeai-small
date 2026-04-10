#!/usr/bin/env python3
"""
P0-004 / F3.3: Content pack validator.

Opens a .pack SQLite file, verifies structural integrity (SHA-256 per chunk),
checks embedding dimension consistency, loads the FAISS index, embeds test
queries, and reports top-3 retrieval accuracy.

Supports both schema versions:
  - schema_version=1: all-MiniLM-L6-v2 (384-dim) — requires sentence-transformers
  - schema_version=2: gemma4-1b (768-dim) — uses gemma_embedding.py (no external model)

Exits 0 if all structural checks pass and top-3 accuracy >= 80% (when queries
are provided), exits 1 otherwise.

Usage:
  # Validate a schema v2 pack (gemma4):
  python validate_pack.py \\
      --pack ../../content-packs/my-pack-v2.pack

  # Validate a schema v1 pack (minilm):
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

PASS_THRESHOLD = 0.80
TOP_K = 3

# Schema version constants
SCHEMA_V1 = 1
SCHEMA_V2 = 2
MINILM_MODEL = "sentence-transformers/all-MiniLM-L6-v2"
GEMMA4_MODEL = "gemma4-1b"


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


def verify_embedding_dims(con: sqlite3.Connection, expected_dim: int) -> tuple[int, int]:
    """Verify all embedding vectors have the correct byte size.

    Each embedding vector is stored as a BLOB of float32 values, so the
    expected byte length is ``expected_dim * 4``.

    Returns:
        Tuple of (passed_count, failed_count).
    """
    rows = con.execute(
        "SELECT chunk_id, dim, length(vector) as vlen FROM embeddings"
    ).fetchall()
    passed, failed = 0, 0
    for row in rows:
        expected_bytes = expected_dim * 4  # float32
        if row["dim"] == expected_dim and row["vlen"] == expected_bytes:
            passed += 1
        else:
            failed += 1
            print(
                f"  DIM FAIL: {row['chunk_id']} dim={row['dim']} "
                f"bytes={row['vlen']} "
                f"(expected dim={expected_dim} bytes={expected_bytes})"
            )
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
    """Resolve embedding model path for schema v1 (MiniLM) packs.

    For schema v2 (Gemma 4) packs, this function is not used — embedding
    is handled by gemma_embedding.embed_text() instead.
    """
    model_id = manifest.get("embedding_model", MINILM_MODEL)
    if models_dir:
        local = models_dir / "all-minilm-l6-v2"
        if local.exists():
            return str(local)
    return model_id


def _make_embed_fn_gemma4(embedding_dim: int):
    """Create an embedding function using gemma_embedding for schema v2 packs.

    Returns a callable that takes a question string and returns a (1, dim)
    float32 numpy array suitable for FAISS search.
    """
    from gemma_embedding import embed_text

    def embed_fn(question: str) -> np.ndarray:
        vec = embed_text(question, dim=embedding_dim).astype(np.float32)
        vec = vec.reshape(1, -1)
        faiss.normalize_L2(vec)
        return vec

    return embed_fn


def _make_embed_fn_minilm(model_id: str):
    """Create an embedding function using SentenceTransformer for schema v1 packs.

    Lazily imports sentence-transformers — raises ImportError if not installed.

    Returns a callable that takes a question string and returns a (1, dim)
    float32 numpy array suitable for FAISS search.
    """
    from sentence_transformers import SentenceTransformer

    model = SentenceTransformer(model_id)

    def embed_fn(question: str) -> np.ndarray:
        q_vec = model.encode(
            [question],
            convert_to_numpy=True,
            normalize_embeddings=True,
            show_progress_bar=False,
        ).astype(np.float32)
        faiss.normalize_L2(q_vec)
        return q_vec

    return embed_fn


def run_queries(
    queries: list[dict],
    index: faiss.Index,
    chunk_order: list[str],
    embed_fn,
) -> dict:
    """Run test queries against the FAISS index and measure retrieval accuracy.

    Args:
        queries: List of query dicts with 'id', 'question', 'expected_chunk_ids'.
        index: Loaded FAISS index.
        chunk_order: List of chunk IDs matching FAISS index order.
        embed_fn: Callable that takes a question string and returns a (1, dim)
            float32 numpy array.

    Returns:
        Report dict with hits, total, accuracy, and per-query results.
    """
    hits, total = 0, 0
    results = []
    for q in queries:
        t0 = time.perf_counter()
        q_vec = embed_fn(q["question"])
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
        help="Optional path to local sentence-transformers models directory (schema v1 only)",
    )
    args = parser.parse_args()

    pack_path = Path(args.pack)
    con = open_pack(pack_path)

    print(f"\n=== Content Pack Validator (P0-004 / F3.3) ===")
    print(f"Pack: {pack_path}\n")

    # ── Manifest ─────────────────────────────────────────────────────
    manifest = read_manifest(con)
    schema_version = int(manifest.get("schema_version", "1"))
    embedding_model = manifest.get("embedding_model", MINILM_MODEL)
    embedding_dim = int(manifest.get("embedding_dim", "384"))

    print(f"Schema version:  {schema_version}")
    print(f"Embedding model: {embedding_model}")
    print(f"Embedding dim:   {embedding_dim}")
    if "embedding_model_version" in manifest:
        print(f"Model version:   {manifest['embedding_model_version']}")
    print("\nManifest:")
    for k, v in sorted(manifest.items()):
        print(f"  {k:<26} {v}")

    all_ok = True

    # ── Chunk count ──────────────────────────────────────────────────
    chunks = read_chunks(con)
    expected_count = int(manifest.get("chunk_count", 0))
    count_ok = len(chunks) == expected_count
    if not count_ok:
        all_ok = False
    print(
        f"\nChunk count: {len(chunks)} (manifest says {expected_count}) "
        f"{'✓' if count_ok else '✗ MISMATCH'}"
    )

    # ── SHA-256 integrity ────────────────────────────────────────────
    print("\nChecking SHA-256 integrity...")
    sha_passed, sha_failed = verify_sha256(con)
    sha_ok = sha_failed == 0
    if not sha_ok:
        all_ok = False
    print(
        f"  {sha_passed}/{sha_passed + sha_failed} chunks pass "
        f"({'✓' if sha_ok else f'✗ {sha_failed} FAILED'})"
    )

    # ── Embedding dimension consistency ──────────────────────────────
    print(f"\nChecking embedding dimensions (expected dim={embedding_dim})...")
    dim_passed, dim_failed = verify_embedding_dims(con, embedding_dim)
    dim_ok = dim_failed == 0
    if not dim_ok:
        all_ok = False
    print(
        f"  {dim_passed}/{dim_passed + dim_failed} embeddings pass "
        f"({'✓' if dim_ok else f'✗ {dim_failed} FAILED'})"
    )

    # ── FAISS index ──────────────────────────────────────────────────
    print("\nLoading FAISS index...")
    t0 = time.perf_counter()
    index, chunk_order = load_faiss_index(con)
    load_ms = (time.perf_counter() - t0) * 1000
    print(f"  Loaded in {load_ms:.1f}ms — {index.ntotal} vectors, dim={index.d}")

    # FAISS dimension match check
    faiss_dim_ok = index.d == embedding_dim
    if not faiss_dim_ok:
        all_ok = False
    print(
        f"  FAISS dim vs manifest: {index.d} vs {embedding_dim} "
        f"{'✓' if faiss_dim_ok else '✗ MISMATCH'}"
    )

    # ── Resolve test queries ─────────────────────────────────────────
    queries_path = Path(args.queries) if args.queries else None
    if not queries_path:
        # Auto-detect alongside the chunks content dir
        candidate = (
            Path(__file__).parent / "content" / pack_path.stem / "test_queries.json"
        )
        if candidate.exists():
            queries_path = candidate

    if not queries_path or not queries_path.exists():
        print(
            "\nNo test_queries.json found — skipping retrieval accuracy test."
            "\nProvide one with: --queries <path>"
        )
        print(f"\n{'=' * 76}")
        status = "PASS" if all_ok else "FAIL"
        print(f"Structural validation: [{status}]")
        con.close()
        sys.exit(0 if all_ok else 1)

    with open(queries_path, "r", encoding="utf-8") as f:
        queries_data = json.load(f)
    queries = queries_data["queries"]
    print(f"\nTest queries: {len(queries)} (from {queries_path.name})")

    # ── Load embedding function based on schema version ──────────────
    if schema_version >= SCHEMA_V2 and embedding_model == GEMMA4_MODEL:
        print(f"\nUsing gemma_embedding (dim={embedding_dim}) for query embedding")
        embed_fn = _make_embed_fn_gemma4(embedding_dim)
    else:
        models_dir = Path(args.models_dir) if args.models_dir else None
        model_id = resolve_model(manifest, models_dir)
        print(f"\nLoading embedding model: {model_id}")
        t0 = time.perf_counter()
        try:
            embed_fn = _make_embed_fn_minilm(model_id)
        except ImportError:
            print(
                "  WARNING: sentence-transformers not installed — "
                "skipping query accuracy test."
                "\n  Install with: pip install sentence-transformers"
            )
            print(f"\n{'=' * 76}")
            status = "PASS" if all_ok else "FAIL"
            print(f"Structural validation: [{status}]  (query test skipped)")
            con.close()
            sys.exit(0 if all_ok else 1)
        print(f"  Loaded in {time.perf_counter() - t0:.3f}s")

    # ── Run queries ──────────────────────────────────────────────────
    print(f"\nRunning {len(queries)} queries (top-{TOP_K} retrieval)...\n")
    report = run_queries(queries, index, chunk_order, embed_fn)

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
    accuracy_ok = report["accuracy"] >= PASS_THRESHOLD
    if not accuracy_ok:
        all_ok = False

    avg_embed = sum(r["embed_ms"] for r in report["results"]) / len(report["results"])
    avg_search = sum(r["search_ms"] for r in report["results"]) / len(
        report["results"]
    )

    status = "PASS" if all_ok else "FAIL"
    print(f"\n{'=' * 76}")
    print(
        f"Top-{TOP_K} accuracy : {accuracy_pct:.1f}%  "
        f"({report['hits']}/{report['total']})  "
        f"[{'PASS' if accuracy_ok else 'FAIL'}]"
    )
    print(
        f"Avg embed time : {avg_embed:.1f}ms   (target: <500ms)  "
        f"{'✓' if avg_embed < 500 else '✗'}"
    )
    print(
        f"Avg search time: {avg_search:.3f}ms  (target: <500ms)  "
        f"{'✓' if avg_search < 500 else '✗'}"
    )
    print(f"Overall        : [{status}]")

    con.close()
    sys.exit(0 if status == "PASS" else 1)


if __name__ == "__main__":
    main()
