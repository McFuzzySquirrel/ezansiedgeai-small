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

## Scope

> **Phase 2** — content pack tooling is developed after the core on-device inference and app shell are validated in Phase 0–1.

Phase 2 deliverables:

- Content ingestion pipeline for Grade 6 Mathematics (CAPS)
- Embedding generation using the selected embedding model
- Pack format specification and packaging tool
- Basic distribution via direct download

## Pack Format

> **To be defined.** The pack format will be specified as an ADR in `ejs-docs/adr/` during Phase 2 planning.
>
> Likely candidates: SQLite bundle, custom binary format, or a compressed directory structure (e.g., ZIP with convention).
