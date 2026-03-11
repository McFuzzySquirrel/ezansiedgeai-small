---
ejs:
  type: journey-adr
  version: 1.1
  adr_id: "0008"
  title: SQLite as the content pack format for on-device curriculum delivery
  date: 2026-03-11
  status: accepted
  session_id: ejs-session-2026-03-11-01
  session_journey: ejs-docs/journey/2026/ejs-session-2026-03-11-01.md

actors:
  humans:
    - id: Doug McCusker
      role: Project Lead
  agents:
    - id: GitHub Copilot
      role: AI coding assistant

context:
  repo: ezansiedgeai-small
  branch: feature/p0-004-content-pack
---

# ADR 0008 — SQLite as the Content Pack Format

## Session Journey

[ejs-session-2026-03-11-01](../journey/2026/ejs-session-2026-03-11-01.md)

## Context

P0-004 requires a content pack format to bundle:

1. Curriculum text chunks (structured, queryable)
2. Pre-computed embeddings for each chunk (binary vectors)
3. A serialised FAISS index (binary blob)
4. Pack metadata / manifest (version, model reference, checksums)

Tools/content-pack-builder/README.md listed three candidate formats (prior to this ADR):

- **Compressed directory / ZIP** — simple to build and inspect, but requires full extraction before loading; no partial reads; Android apps need additional ZIP handling logic.
- **SQLite** — single file, queryable by any language without extraction; native Android support via `android.database.sqlite`; transactional writes; supports partial reads by table.
- **Custom binary** — minimum overhead but requires a custom parser on every platform; no read tooling; rejected early.

The tools README noted the format decision was deferred to an ADR. This is that ADR.

## Decision

Use **SQLite** as the content pack file format (`.pack` file extension).

Every pack is a single SQLite database with four tables:

| Table | Purpose |
|---|---|
| `manifest` | Key/value metadata (pack_id, version, subject, grade, language, curriculum, created_at, chunk_count, embedding_model, embedding_dim, schema_version) |
| `chunks` | One row per content chunk (chunk_id, topic_path, title, content, difficulty, term, sha256) |
| `embeddings` | One BLOB row per chunk (chunk_id, model_name, dim, vector as raw float32 bytes) |
| `faiss_indexes` | Serialised FAISS index BLOB(s) + chunk_order JSON array mapping index slots to chunk_ids |

Pack files use the `.pack` extension and WAL journal mode for safe concurrent reads.

## Rationale

**Why SQLite over ZIP:**

- No extraction step required — Android ships `android.database.sqlite` in every API level since API 1; no extra libraries needed to read a `.pack` file.
- Chunks and metadata are queryable directly without loading the entire file into memory.
- SHA-256 checksums are stored per-chunk in the `chunks` table and can be verified row by row — no need to re-read the whole archive.
- SQLite supports concurrent readers; ZIP extraction is destructive.
- Standard tooling (`sqlite3` CLI, SQLiteViewer, DB Browser for SQLite) makes inspection and debugging easy.

**Why SQLite over custom binary:**

- Zero custom parser needed on any platform.
- Schema versioning (`schema_version` in manifest) allows forward-compatible evolution.

**Trade-offs accepted:**

- SQLite has a ~1 page (4 KB) minimum overhead per database. For packs with 10–50 chunks this is negligible.
- FAISS index is stored as a single BLOB. Loading it requires writing to a temp file (FAISS's C++ API requires a file path). This is acceptable at load time (one-time per session) and is handled transparently by the app.
- Delta update support (diffing between pack versions) is deferred — SQLite `ATTACH DATABASE` makes this viable in a future iteration without a format change.

## Consequences

- The `build_pack.py` script produces `.pack` files (SQLite) from a `chunks.json` input + all-MiniLM-L6-v2 embeddings + FAISS Flat index.
- The `validate_pack.py` script can open any `.pack` file without extraction, verify SHA-256 per chunk, load the FAISS index, and run retrieval accuracy tests.
- P0-103 (Content Pack Loader for Android) will use Android's `SQLiteDatabase` API to read `.pack` files directly from the device filesystem — no ZIP extraction library needed.
- P0-104 (ExplanationEngine) loads the FAISS index from the `faiss_indexes` BLOB via a temp file at session start; does not hold the full file in memory.

## Schema Version

This ADR defines **schema_version = 1**. Future schema changes that are not backwards-compatible must increment `schema_version` and produce a new ADR.

## Agent Guidance

- Do not change the `.pack` file extension or SQLite schema without updating this ADR and bumping `schema_version`.
- When adding new metadata fields, add them to `manifest` as new key/value rows — do not alter the `manifest` table structure.
- The FAISS index BLOB requires a temp-file round-trip to load on any platform (FAISS `read_index` takes a file path). This is expected and not a bug.
- For packs with > 1,000 chunks, consider storing multiple FAISS index partitions in `faiss_indexes` (one per topic or term) to allow selective loading.
- SHA-256 in `chunks` is the hash of the raw `content` string (UTF-8 encoded). Verify this server-side at distribution time and on-device before first use.
