# Content Pack Builder

A build tool that transforms **curriculum source material into versioned, offline content packs** for distribution to learner devices and school edge nodes.

## Purpose

Content packs are the primary way educational material reaches learners. This tool automates the pipeline from raw curriculum content to a deployable, self-contained package that the learner mobile app can consume entirely offline.

A content pack is a **sealed, versioned unit** — once built, it is immutable and can be distributed, cached, and verified independently.

## What a Content Pack Contains

| Component             | Description                                               |
| --------------------- | --------------------------------------------------------- |
| Curriculum content    | Structured lessons, explanations, worked examples, practice problems (CAPS-aligned) |
| Embeddings            | Pre-computed vector embeddings for semantic search over the content |
| Metadata              | Subject, grade, curriculum version, language, pack version, checksums |
| Version info          | Semantic version, build timestamp, source material hash   |
| Index                 | Lookup structures for fast on-device retrieval            |

## Target Workflow

```
Source Material (CAPS curriculum PDFs, structured content)
        │
        ▼
   [1] Content Ingestion & Structuring
        │  Parse, chunk, and structure raw material
        ▼
   [2] Quality Validation
        │  Check curriculum alignment, completeness, accuracy
        ▼
   [3] Embedding Generation
        │  Generate vector embeddings for each content chunk
        ▼
   [4] Pack Packaging
        │  Bundle content + embeddings + metadata into a versioned pack
        ▼
   [5] Distribution
        │  Push to distribution channels (direct download, edge node, USB)
        ▼
   Learner Device / School Edge Node
```

## Design Principles

- **Reproducible builds** — the same source material and configuration must produce a bit-identical pack.
- **Delta updates** — support diffing between pack versions so learner devices download only changes.
- **Integrity verification** — every pack includes checksums; the mobile app verifies before loading.
- **Size budget** — a single subject/grade pack should target <500 MB to fit on constrained devices.

## Pack Format

Content packs are **SQLite databases** with a `.pack` extension (ADR 0008).

| Table | Contents |
|---|---|
| `manifest` | Key/value metadata: pack_id, version, subject, grade, language, curriculum, chunk_count, embedding_model, schema_version |
| `chunks` | One row per content chunk with SHA-256 integrity hash |
| `embeddings` | Float32 embedding vectors (all-MiniLM-L6-v2, 384 dims) |
| `faiss_indexes` | Serialised FAISS IndexFlatIP + JSON chunk_order array |

See `ejs-docs/adr/0008-content-pack-sqlite-format.md` for the full format rationale.

---

## Quick Start (P0-004 Prototype)

### 1. Set Up Environment

```bash
cd tools/content-pack-builder
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

Or reuse the P0-002 spike venv (same dependencies):

```bash
source ../../spikes/p0-002-embedding-retrieval/.venv/bin/activate
```

### 2. Build the Prototype Pack

```bash
# From tools/content-pack-builder/
python build_pack.py \
    --chunks content/maths-grade6-caps-fractions-v0.1/chunks.json \
    --output ../../content-packs/maths-grade6-caps-fractions-v0.1.pack \
    --models-dir ../../spikes/p0-002-embedding-retrieval/models
```

`--models-dir` points to the locally downloaded models from the P0-002 spike to avoid re-downloading.

### 3. Validate the Pack

```bash
python validate_pack.py \
    --pack ../../content-packs/maths-grade6-caps-fractions-v0.1.pack \
    --queries content/maths-grade6-caps-fractions-v0.1/test_queries.json \
    --models-dir ../../spikes/p0-002-embedding-retrieval/models
```

Expected output:
- SHA-256 integrity: all 10 chunks pass
- FAISS index: 10 vectors loaded
- Top-3 retrieval accuracy: ≥ 80% (PASS)
- Avg embed time: < 500ms
- Avg search time: < 0.5ms

### 4. Inspect the Pack

```bash
sqlite3 ../../content-packs/maths-grade6-caps-fractions-v0.1.pack ".tables"
sqlite3 ../../content-packs/maths-grade6-caps-fractions-v0.1.pack "SELECT key, value FROM manifest;"
sqlite3 ../../content-packs/maths-grade6-caps-fractions-v0.1.pack "SELECT COUNT(*) FROM chunks;"
```

---

## Content Structure

Source content lives under `content/<pack-id>/`:

```
content/
  maths-grade6-caps-fractions-v0.1/
    chunks.json        — 10 CAPS Term 1 fractions chunks
    test_queries.json  — 10 retrieval accuracy test queries
```

### chunks.json schema

```json
{
  "pack_id": "maths-grade6-caps-fractions-v0.1",
  "version": "0.1.0",
  "subject": "mathematics",
  "grade": "6",
  "language": "en",
  "curriculum": "CAPS",
  "chunks": [
    {
      "id": "fractions-basics-001",
      "topic_path": "term1.fractions.basics",
      "title": "What is a Fraction?",
      "content": "...",
      "difficulty": "basic",
      "term": 1
    }
  ]
}
```

Each chunk must be ≤ 2,000 tokens and tagged with a `topic_path` matching the CAPS pacing (`termN.topic.subtopic`).

---

## Scope

> **Phase 0 (P0-004)** — prototype pack builder is implemented and validated. A single 10-chunk fractions pack is produced and committed to `content-packs/`.

> **Phase 2** — full content pack tooling is developed after the core app shell is validated in Phase 1.

Phase 2 deliverables:
- Content ingestion pipeline from CAPS curriculum PDFs
- Quality validation (curriculum alignment, completeness checks)
- Delta update support between pack versions
- Pack signing / distribution pipeline
- Basic distribution via direct download

## Pack Format

> **To be defined.** The pack format will be specified as an ADR in `ejs-docs/adr/` during Phase 2 planning.
>
> Likely candidates: SQLite bundle, custom binary format, or a compressed directory structure (e.g., ZIP with convention).
