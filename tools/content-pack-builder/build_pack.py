#!/usr/bin/env python3
"""
P0-004: Content pack builder.

Reads a chunks.json, embeds content, builds a FAISS Flat index, and writes a
single SQLite .pack file.

Embedding models:
  gemma4 (default) — Gemma 4 1B hash-based deterministic embedding (768-dim).
                      Uses gemma_embedding.py; no ML model download needed.
                      Produces schema_version=2 packs.
  minilm  (legacy) — all-MiniLM-L6-v2 via sentence-transformers (384-dim).
                      Requires sentence-transformers installed.
                      Produces schema_version=1 packs.

SQLite schema (4 tables — see EMBEDDING_CONTRACT.md):
  manifest     — key/value pack metadata
  chunks       — content records with SHA-256 per chunk
  embeddings   — per-chunk embedding BLOBs (float32, L2-normalised)
  faiss_indexes — serialised FAISS IndexFlatIP + chunk_order JSON

Usage:
  # Gemma 4 mode (default — no ML model download):
  python build_pack.py \\
      --chunks content/maths-grade6-caps-fractions-v0.1/chunks.json \\
      --output ../../content-packs/maths-grade6-caps-fractions-v0.1.pack

  # MiniLM legacy mode (requires sentence-transformers):
  python build_pack.py \\
      --chunks content/maths-grade6-caps-fractions-v0.1/chunks.json \\
      --output ../../content-packs/maths-grade6-caps-fractions-v0.1.pack \\
      --embedding-model minilm \\
      --models-dir ../../spikes/p0-002-embedding-retrieval/models

  # Gemma 4 with custom dimension:
  python build_pack.py \\
      --chunks content/maths-grade6-caps-fractions-v0.1/chunks.json \\
      --output ../../content-packs/maths-grade6-caps-fractions-v0.1.pack \\
      --embedding-dim 384
"""

import argparse
import hashlib
import json
import os
import sqlite3
import sys
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path

import faiss
import numpy as np

# ── Embedding model constants ────────────────────────────────────────
# MiniLM (legacy, schema v1)
MINILM_MODEL_ID = "sentence-transformers/all-MiniLM-L6-v2"
MINILM_DIM = 384

# Gemma 4 (default, schema v2) — constants imported from gemma_embedding
# at embed time to keep this file's top-level imports light.

SUPPORTED_DIMS = (256, 384, 512, 768)
DEFAULT_DIMS = {"gemma4": 768, "minilm": 384}
BATCH_SIZE = 8


def sha256_of(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def load_chunks(chunks_path: Path) -> tuple[dict, list[dict]]:
    with open(chunks_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    chunks = data["chunks"]
    print(f"Loaded {len(chunks)} chunks from {chunks_path.name}")
    return data, chunks


def resolve_minilm_model(models_dir: Path | None) -> str:
    """Return local MiniLM model path if available, else Hugging Face model ID."""
    if models_dir:
        local = models_dir / "all-minilm-l6-v2"
        if local.exists():
            print(f"  Using local model: {local}")
            return str(local)
        print(f"  Local model not found at {local} — will download from Hugging Face")
    return MINILM_MODEL_ID


def embed_chunks_gemma4(chunks: list[dict], dim: int) -> np.ndarray:
    """Embed chunks using the Gemma 4 deterministic embedding (no ML model needed)."""
    from gemma_embedding import embed_batch, EMBEDDING_MODEL_ID as GEMMA_MODEL_ID

    texts = [c["content"] for c in chunks]
    print(f"  Embedding model: {GEMMA_MODEL_ID} (hash-based deterministic)")
    print(f"  Embedding {len(texts)} chunks (dim={dim})...")
    t0 = time.perf_counter()
    embeddings = embed_batch(texts, dim=dim)
    elapsed = time.perf_counter() - t0
    print(f"  Embedded in {elapsed:.3f}s ({elapsed / len(texts) * 1000:.1f}ms per chunk)")
    return embeddings  # shape (N, dim), L2-normalised, float32


def embed_chunks_minilm(chunks: list[dict], models_dir: Path | None) -> np.ndarray:
    """Embed chunks using all-MiniLM-L6-v2 (legacy — requires sentence-transformers)."""
    try:
        from sentence_transformers import SentenceTransformer
    except ImportError:
        print(
            "ERROR: sentence-transformers is required for --embedding-model minilm.\n"
            "Install with: pip install sentence-transformers",
            file=sys.stderr,
        )
        sys.exit(1)

    model_id = resolve_minilm_model(models_dir)
    print(f"  Loading embedding model: {model_id}")
    t0 = time.perf_counter()
    model = SentenceTransformer(model_id)
    print(f"  Model loaded in {time.perf_counter() - t0:.3f}s")

    texts = [c["content"] for c in chunks]
    print(f"  Embedding {len(texts)} chunks (batch_size={BATCH_SIZE})...")
    t0 = time.perf_counter()
    embeddings = model.encode(
        texts,
        batch_size=BATCH_SIZE,
        show_progress_bar=True,
        convert_to_numpy=True,
        normalize_embeddings=True,
    )
    elapsed = time.perf_counter() - t0
    print(f"  Embedded in {elapsed:.3f}s ({elapsed / len(texts) * 1000:.1f}ms per chunk)")
    return embeddings.astype(np.float32)  # shape (N, 384), L2-normalised


def embed_chunks(
    chunks: list[dict],
    embedding_model: str,
    embedding_dim: int,
    models_dir: Path | None,
) -> np.ndarray:
    """Route embedding to the selected model backend."""
    if embedding_model == "gemma4":
        return embed_chunks_gemma4(chunks, dim=embedding_dim)
    else:
        return embed_chunks_minilm(chunks, models_dir=models_dir)


def build_faiss_index(embeddings: np.ndarray) -> bytes:
    """Build a FAISS IndexFlatIP and serialise it to bytes.

    The index dimension is inferred from the embedding shape.
    """
    dim = embeddings.shape[1]
    vecs = embeddings.copy().astype(np.float32)
    faiss.normalize_L2(vecs)
    index = faiss.IndexFlatIP(dim)
    index.add(vecs)
    with tempfile.NamedTemporaryFile(suffix=".faiss", delete=False) as tmp:
        tmp_path = tmp.name
    try:
        faiss.write_index(index, tmp_path)
        with open(tmp_path, "rb") as f:
            return f.read()
    finally:
        os.unlink(tmp_path)


def write_pack(
    output_path: Path,
    pack_metadata: dict,
    chunks: list[dict],
    embeddings: np.ndarray,
    faiss_bytes: bytes,
    embedding_model: str,
    embedding_dim: int,
) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    if output_path.exists():
        output_path.unlink()

    con = sqlite3.connect(str(output_path))
    con.execute("PRAGMA journal_mode=WAL")
    con.execute("PRAGMA foreign_keys=ON")

    con.executescript("""
        CREATE TABLE manifest (
            key   TEXT PRIMARY KEY,
            value TEXT NOT NULL
        );

        CREATE TABLE chunks (
            id         INTEGER PRIMARY KEY AUTOINCREMENT,
            chunk_id   TEXT NOT NULL UNIQUE,
            topic_path TEXT NOT NULL,
            title      TEXT NOT NULL,
            content    TEXT NOT NULL,
            difficulty TEXT NOT NULL,
            term       INTEGER NOT NULL,
            sha256     TEXT NOT NULL
        );

        CREATE TABLE embeddings (
            id         INTEGER PRIMARY KEY AUTOINCREMENT,
            chunk_id   TEXT NOT NULL REFERENCES chunks(chunk_id),
            model_name TEXT NOT NULL,
            dim        INTEGER NOT NULL,
            vector     BLOB NOT NULL
        );

        CREATE TABLE faiss_indexes (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            store_type  TEXT NOT NULL,
            model_name  TEXT NOT NULL,
            index_data  BLOB NOT NULL,
            chunk_order TEXT NOT NULL
        );
    """)

    # Compute model-specific manifest fields
    if embedding_model == "gemma4":
        from gemma_embedding import (
            EMBEDDING_MODEL_ID as GEMMA_MODEL_ID,
            EMBEDDING_MODEL_VERSION as GEMMA_MODEL_VERSION,
            SCHEMA_VERSION as GEMMA_SCHEMA_VERSION,
        )
        schema_version = str(GEMMA_SCHEMA_VERSION)
        model_name = GEMMA_MODEL_ID
        model_version = GEMMA_MODEL_VERSION
    else:
        schema_version = "1"
        model_name = MINILM_MODEL_ID
        model_version = MINILM_MODEL_ID  # no separate version field for v1

    manifest_rows = [
        ("pack_id", pack_metadata["pack_id"]),
        ("version", pack_metadata["version"]),
        ("subject", pack_metadata["subject"]),
        ("grade", pack_metadata["grade"]),
        ("language", pack_metadata["language"]),
        ("curriculum", pack_metadata["curriculum"]),
        ("created_at", pack_metadata["created_at"]),
        ("chunk_count", str(len(chunks))),
        ("embedding_model", model_name),
        ("embedding_dim", str(embedding_dim)),
        ("schema_version", schema_version),
    ]
    # Add embedding_model_version for schema v2+
    if embedding_model == "gemma4":
        manifest_rows.append(("embedding_model_version", model_version))

    con.executemany("INSERT INTO manifest VALUES (?, ?)", manifest_rows)

    for chunk in chunks:
        con.execute(
            "INSERT INTO chunks (chunk_id, topic_path, title, content, difficulty, term, sha256) "
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            (
                chunk["id"],
                chunk["topic_path"],
                chunk["title"],
                chunk["content"],
                chunk["difficulty"],
                chunk["term"],
                sha256_of(chunk["content"]),
            ),
        )

    for i, chunk in enumerate(chunks):
        vec = embeddings[i].astype(np.float32)
        con.execute(
            "INSERT INTO embeddings (chunk_id, model_name, dim, vector) VALUES (?, ?, ?, ?)",
            (chunk["id"], model_name, embedding_dim, vec.tobytes()),
        )

    chunk_order = json.dumps([c["id"] for c in chunks])
    con.execute(
        "INSERT INTO faiss_indexes (store_type, model_name, index_data, chunk_order) "
        "VALUES (?, ?, ?, ?)",
        ("faiss-flat", model_name, faiss_bytes, chunk_order),
    )

    con.commit()
    con.close()


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Build a SQLite content pack from chunks.json.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument("--chunks", required=True, help="Path to chunks.json")
    parser.add_argument("--output", required=True, help="Output .pack file path")
    parser.add_argument(
        "--embedding-model",
        choices=["gemma4", "minilm"],
        default="gemma4",
        help="Embedding model to use (default: gemma4)",
    )
    parser.add_argument(
        "--embedding-dim",
        type=int,
        choices=list(SUPPORTED_DIMS),
        default=None,
        help="Embedding dimension override (default: 768 for gemma4, 384 for minilm)",
    )
    parser.add_argument(
        "--models-dir",
        default=None,
        help="Optional path to local sentence-transformers models directory (minilm mode only)",
    )
    args = parser.parse_args()

    chunks_path = Path(args.chunks)
    output_path = Path(args.output)
    models_dir = Path(args.models_dir) if args.models_dir else None
    embedding_model = args.embedding_model
    embedding_dim = args.embedding_dim if args.embedding_dim else DEFAULT_DIMS[embedding_model]

    if not chunks_path.exists():
        print(f"ERROR: chunks file not found: {chunks_path}", file=sys.stderr)
        sys.exit(1)

    schema_version = "2" if embedding_model == "gemma4" else "1"

    print(f"\n=== Content Pack Builder (P0-004) ===")
    print(f"Input:           {chunks_path}")
    print(f"Output:          {output_path}")
    print(f"Embedding model: {embedding_model}")
    print(f"Embedding dim:   {embedding_dim}")
    print(f"Schema version:  {schema_version}\n")

    data, chunks = load_chunks(chunks_path)

    pack_metadata = {
        "pack_id": data.get("pack_id", chunks_path.parent.name),
        "version": data.get("version", "0.1.0"),
        "subject": data.get("subject", "mathematics"),
        "grade": str(data.get("grade", "6")),
        "language": data.get("language", "en"),
        "curriculum": data.get("curriculum", "CAPS"),
        "created_at": datetime.now(timezone.utc).isoformat(),
    }

    print("\n--- Embedding ---")
    embeddings = embed_chunks(chunks, embedding_model, embedding_dim, models_dir)

    print("\n--- Building FAISS Flat index ---")
    t0 = time.perf_counter()
    faiss_bytes = build_faiss_index(embeddings)
    print(f"  Built in {(time.perf_counter() - t0) * 1000:.1f}ms ({len(faiss_bytes):,} bytes)")

    print("\n--- Writing pack ---")
    write_pack(
        output_path,
        pack_metadata,
        chunks,
        embeddings,
        faiss_bytes,
        embedding_model=embedding_model,
        embedding_dim=embedding_dim,
    )

    size_bytes = output_path.stat().st_size
    size_kb = size_bytes / 1024
    print(f"\n=== Pack built successfully ===")
    print(f"  File:           {output_path}")
    print(f"  Size:           {size_kb:.1f} KB ({size_kb / 1024:.3f} MB)")
    print(f"  Chunks:         {len(chunks)}")
    print(f"  pack_id:        {pack_metadata['pack_id']}")
    print(f"  schema_version: {schema_version}")
    print(f"  embedding:      {embedding_model} ({embedding_dim}-dim)")
    print(f"\nValidate with:")
    print(
        f"  python validate_pack.py"
        f" --pack {output_path}"
        f" --queries {chunks_path.parent / 'test_queries.json'}"
    )


if __name__ == "__main__":
    main()
