# Embedding Contract — Content Pack ↔ Android Embedding Model

> **Status:** Active  
> **Schema Version:** 2 (extends schema v1 from ADR-0008)  
> **Owners:** content-pack-engineer, ai-pipeline-engineer  
> **References:** [ADR-0008](../../ejs-docs/adr/0008-content-pack-sqlite-format.md), [Feature PRD §5–6](../../docs/product/feature-gemma4-semantic-search.md)

---

## 1. Purpose

This contract governs the embedding format shared between the **Python content
pack builder** (`build_pack.py`) and the **Android embedding model**
(`GemmaEmbeddingModel`). It ensures that:

- Content packs built by `build_pack.py` contain embeddings compatible with the
  on-device retrieval pipeline.
- The Android app can detect incompatible packs and prompt for updates.
- When real Gemma 4 embedding becomes available (MediaPipe API), only the
  embedding function changes — the pack format and schema stay the same.

---

## 2. Schema Version History

| Version | Embedding Model | Dimension | Status |
|---------|----------------|-----------|--------|
| 1 | `sentence-transformers/all-MiniLM-L6-v2` | 384 | **Deprecated** — packs still loadable but retrieval incompatible with v2 |
| 2 | `gemma4-1b` (hash-based deterministic fallback) | 768 | **Active** |

---

## 3. Schema Version 2 Specification

### 3.1 SQLite Schema

The SQLite table structure is **unchanged** from schema v1 (ADR-0008). All four
tables remain identical:

```sql
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
```

### 3.2 Manifest Fields (schema_version=2)

| Key | Value | Notes |
|-----|-------|-------|
| `schema_version` | `2` | Bumped from 1 |
| `embedding_model` | `gemma4-1b` | Logical model identifier |
| `embedding_model_version` | `gemma4-1b-hash-v1` | Tracks the actual embedding implementation (see §8) |
| `embedding_dim` | `768` | Gemma 4 native dimension |
| `pack_id` | *(varies)* | Unchanged from v1 |
| `version` | *(varies)* | Unchanged from v1 |
| `subject` | *(varies)* | Unchanged from v1 |
| `grade` | *(varies)* | Unchanged from v1 |
| `language` | *(varies)* | Unchanged from v1 |
| `curriculum` | *(varies)* | Unchanged from v1 |
| `created_at` | *(ISO 8601)* | Unchanged from v1 |
| `chunk_count` | *(integer as string)* | Unchanged from v1 |

### 3.3 Embeddings Table Changes

| Field | v1 Value | v2 Value |
|-------|----------|----------|
| `model_name` | `sentence-transformers/all-MiniLM-L6-v2` | `gemma4-1b` |
| `dim` | `384` | `768` |
| `vector` | 1,536 bytes (384 × float32) | 3,072 bytes (768 × float32) |

### 3.4 FAISS Index Changes

| Field | v1 Value | v2 Value |
|-------|----------|----------|
| `model_name` | `sentence-transformers/all-MiniLM-L6-v2` | `gemma4-1b` |
| FAISS index type | `IndexFlatIP` (dim=384) | `IndexFlatIP` (dim=768) |

---

## 4. Embedding Properties

```
Model:              gemma4-1b
Implementation:     SHA-256 hash-based deterministic (until real inference available)
Dimension:          768
Normalization:      L2 (‖v‖₂ = 1)
Distance metric:    Inner product (= cosine similarity on L2-normalized vectors)
Dtype:              float32 (IEEE 754, little-endian in BLOB storage)
FAISS index type:   IndexFlatIP
```

---

## 5. Deterministic Hash-Based Embedding Specification

Until the MediaPipe GenAI SDK exposes an embedding extraction API for Gemma 4,
both platforms use a **deterministic hash-based embedding** as a placeholder.
This embedding has no semantic meaning — it exists to validate the full
build → load → query → retrieve pipeline end-to-end.

### 5.1 Canonical Algorithm

```
function embed(text: string, dim: int = 768) → float[dim]:
    1. digest     ← SHA-256(UTF-8(text))          // 32-byte hash
    2. hex        ← hex_encode(digest)             // 64-char hex string
    3. seed_hex   ← hex[0:16]                      // first 16 hex chars
    4. seed       ← parse_unsigned_int64(seed_hex)  // big-endian, unsigned
    5. rng        ← MersenneTwister(seed)           // MT19937, seeded with seed
    6. raw[i]     ← rng.uniform(-1.0, +1.0)  for i in 0..dim-1
    7. magnitude  ← sqrt(Σ raw[i]²)
    8. return     raw[i] / magnitude   for i in 0..dim-1   // L2-normalized
```

### 5.2 Step-by-Step Detail

| Step | Operation | Detail |
|------|-----------|--------|
| 1 | SHA-256 hash | Hash the UTF-8 byte encoding of the input text. Standard SHA-256 as per FIPS 180-4. |
| 2 | Hex encoding | Convert the 32-byte digest to a 64-character lowercase hex string. |
| 3 | Seed extraction | Take the first 16 hex characters (= 8 bytes = 64 bits). |
| 4 | Seed parsing | Parse the 16-char hex string as an unsigned 64-bit integer (big-endian). |
| 5 | PRNG seeding | Seed a Mersenne Twister (MT19937) PRNG with the 64-bit seed. |
| 6 | Vector generation | Draw `dim` floating-point values, each uniformly distributed in [-1.0, +1.0). |
| 7–8 | L2 normalization | Divide each element by the L2 norm of the vector so ‖v‖₂ = 1. |

### 5.3 Why SHA-256 Seed (Not Platform Hash)

| Approach | Problem |
|----------|---------|
| Python `hash(text)` | Randomised per process (PYTHONHASHSEED); not reproducible. |
| Kotlin `text.hashCode()` | 32-bit, JVM-specific algorithm; different from Python's hash. |
| **SHA-256 → first 16 hex → int64** | Deterministic, platform-independent, no collision issues at 64-bit range. |

### 5.4 PRNG Specification

The canonical PRNG is the **Mersenne Twister (MT19937)**. This is the default
PRNG in Python's `random.Random` class.

**Python reference implementation:** `random.Random(seed)` where `seed` is the
unsigned 64-bit integer from step 4. The `random.uniform(-1.0, 1.0)` method
generates each vector element.

**Reference implementation:** See [`gemma_embedding.py`](./gemma_embedding.py)
in this directory.

### 5.5 Cross-Platform Parity Status

> ⚠️ **Known incompatibility:** The Android `GemmaEmbeddingModel` currently uses
> `text.hashCode().toLong()` as the PRNG seed and `java.util.Random` (a Linear
> Congruential Generator) as the PRNG. Both differ from this contract:
>
> | Aspect | Contract (canonical) | Android (current) |
> |--------|---------------------|-------------------|
> | Seed | SHA-256 → first 16 hex → uint64 | `text.hashCode().toLong()` (JVM-specific) |
> | PRNG | Mersenne Twister (MT19937) | Java LCG (`java.util.Random`) |
>
> **Impact during hash-based fallback:** Content packs are built with the Python
> implementation (canonical), and the pack's pre-computed embeddings are stored in
> the `embeddings` table and FAISS index. At query time on Android, the
> `GemmaEmbeddingModel` computes query embeddings using its own (non-canonical)
> algorithm. Since hash-based embeddings have **no semantic meaning**, retrieval
> quality is effectively random regardless of which PRNG is used. The pipeline
> works end-to-end but returns arbitrary results.
>
> **Resolution path:** When real Gemma 4 embedding becomes available (§8), both
> platforms will use the same model for inference, achieving true semantic parity.
> Updating Android's hash-based fallback to match this contract is low priority
> but desirable for integration testing. The required Android changes are:
>
> 1. Replace `text.hashCode().toLong()` with SHA-256 → first 16 hex → uint64
> 2. Replace `java.util.Random` with a Mersenne Twister implementation
>    (e.g., Apache Commons Math `MersenneTwister` or a bundled MT19937)
>
> **Tracking:** This will be addressed as part of task F3.4 (Android pack loader
> version detection) or a dedicated follow-up task.

---

## 6. Backward Compatibility

### 6.1 Pack Compatibility Matrix

| Pack schema_version | Pack embedding_dim | App embedding_dim | Compatible? |
|--------------------|--------------------|-------------------|-------------|
| 1 | 384 | 768 | ❌ Incompatible — dimension mismatch |
| 2 | 768 | 768 | ✅ Compatible |
| 2 | 768 | 384 | ❌ Incompatible — dimension mismatch |

### 6.2 Detection Rules

The Android content pack loader **MUST** check these manifest fields at load time:

```kotlin
fun isPackCompatible(manifest: Map<String, String>): PackCompatibility {
    val schemaVersion = manifest["schema_version"]?.toIntOrNull() ?: 1
    val embeddingDim = manifest["embedding_dim"]?.toIntOrNull() ?: 384
    val embeddingModel = manifest["embedding_model"] ?: "sentence-transformers/all-MiniLM-L6-v2"

    return when {
        schemaVersion < 2 ->
            PackCompatibility.INCOMPATIBLE_SCHEMA("Pack uses schema v$schemaVersion; v2 required")
        embeddingDim != 768 ->
            PackCompatibility.INCOMPATIBLE_DIMENSION("Pack uses ${embeddingDim}-dim; 768 required")
        embeddingModel != "gemma4-1b" ->
            PackCompatibility.INCOMPATIBLE_MODEL("Pack uses $embeddingModel; gemma4-1b required")
        else ->
            PackCompatibility.COMPATIBLE
    }
}
```

### 6.3 User-Facing Behaviour

When an incompatible (v1) pack is detected:

1. **Do NOT load** the pack into the retrieval pipeline.
2. **Display a clear message:** "This content pack needs to be updated for the
   new AI engine. Please download the updated version."
3. **Do NOT auto-delete** the old pack — the user may need it until the update
   is available.
4. **No automatic re-embedding** at runtime — re-embedding 100+ chunks on-device
   would take too long and drain battery.

---

## 7. Migration Path

### 7.1 Rebuild Packs (F3.2)

Update `build_pack.py` to:

1. Import `gemma_embedding.embed_text` / `embed_batch` instead of
   `SentenceTransformer`.
2. Set manifest `schema_version=2`, `embedding_model=gemma4-1b`,
   `embedding_model_version=gemma4-1b-hash-v1`, `embedding_dim=768`.
3. Build FAISS `IndexFlatIP` with dimension 768.
4. Support `--embedding-model` flag: `gemma4` (default) or `minilm` (legacy).
5. Support `--embedding-dim` flag: default 768 for gemma4, 384 for minilm.

### 7.2 Validate Packs (F3.3)

Update `validate_pack.py` to:

1. Read `schema_version` and `embedding_model` from manifest.
2. Select the correct embedding function based on manifest model.
3. Verify `embedding_dim` matches FAISS index dimension.
4. Verify all vectors in `embeddings` table have the declared dimension.
5. Report schema version in validation output.

### 7.3 Android Pack Loader (F3.4)

Update the content pack loader to:

1. Read `schema_version` from manifest at load time.
2. Apply compatibility check (§6.2).
3. Surface upgrade prompt for incompatible packs (§6.3).
4. Accept only `schema_version >= 2` packs for retrieval.

---

## 8. Embedding Model Versioning

The `embedding_model_version` manifest field tracks the **actual implementation**
used to compute embeddings. This allows distinguishing between hash-based
fallback and real inference, even though both use the `gemma4-1b` model
identifier.

### 8.1 Version Progression

| `embedding_model_version` | Implementation | Semantic? | Notes |
|--------------------------|----------------|-----------|-------|
| `gemma4-1b-hash-v1` | SHA-256 hash → MT19937 → L2-normalized | No | Current — deterministic placeholder |
| `gemma4-1b-real-v1` | MediaPipe Gemma 4 1B embedding extraction | Yes | Future — when MediaPipe API is available |
| `gemma4-1b-real-v2` | *(future improvements)* | Yes | If model fine-tuning or pooling strategy changes |

### 8.2 Version Compatibility Rules

- `gemma4-1b-hash-v1` ↔ `gemma4-1b-hash-v1` → Compatible (same algorithm)
- `gemma4-1b-hash-v1` ↔ `gemma4-1b-real-v1` → **Incompatible** (different vectors)
- `gemma4-1b-real-v1` ↔ `gemma4-1b-real-v1` → Compatible

When the app detects a pack with `embedding_model_version` ≠ the app's active
embedding version, it should prompt the user to update the pack. The check is
a simple string comparison.

### 8.3 Transition to Real Embedding

When the MediaPipe GenAI SDK adds an embedding extraction API:

1. Implement real embedding in both Python (`gemma_embedding.py`) and Android
   (`GemmaEmbeddingModel`).
2. Bump `embedding_model_version` to `gemma4-1b-real-v1`.
3. **Rebuild ALL content packs** with the real embedding. The pack format is
   unchanged — only the vectors in `embeddings` and `faiss_indexes` change.
4. Android version detection (§6.2) triggers pack update prompt.
5. Hash-based code is retained as a fallback for CI/testing (no real model needed).

---

## 9. Embedding Dimension Contract (FT-FR-05)

This is the shared contract between the content-pack-engineer and the
ai-pipeline-engineer. Both parties must agree before changing dimensions.

```
┌────────────────────────┐         ┌─────────────────────────┐
│   build_pack.py        │         │  GemmaEmbeddingModel    │
│   (Python)             │         │  (Android/Kotlin)       │
│                        │         │                         │
│  embed_text(text, 768) │ ──────► │  embed(text) → [768]    │
│                        │  MUST   │                         │
│  Output: float32[768]  │  MATCH  │  Output: float32[768]   │
│  Norm: L2 (‖v‖₂ = 1)  │  DIM    │  Norm: L2 (‖v‖₂ = 1)   │
│  Metric: inner product │         │  Metric: inner product  │
└────────────────────────┘         └─────────────────────────┘
         │                                    │
         ▼                                    ▼
┌────────────────────────┐         ┌─────────────────────────┐
│  Content Pack (.pack)  │         │  FAISS IndexFlatIP      │
│  embeddings table      │ ──────► │  search(query_vec, k)   │
│  faiss_indexes table   │  LOAD   │                         │
│  dim=768 everywhere    │         │  dim=768                │
└────────────────────────┘         └─────────────────────────┘
```

**Rules:**

1. The dimension is declared in the pack manifest (`embedding_dim`).
2. Every vector in the `embeddings` table must have exactly `dim × 4` bytes.
3. The FAISS index must have the same dimension.
4. The Android embedding model must output vectors of the same dimension.
5. Dimension changes require rebuilding all packs AND updating the Android model
   config — coordinate via this contract.

**Supported dimensions:** 256, 384, 512, 768. Default for Gemma 4: **768**.

---

## 10. File Reference

| File | Role | Location |
|------|------|----------|
| `EMBEDDING_CONTRACT.md` | This document — canonical spec | `tools/content-pack-builder/` |
| `gemma_embedding.py` | Python reference implementation | `tools/content-pack-builder/` |
| `build_pack.py` | Pack builder (to be updated in F3.2) | `tools/content-pack-builder/` |
| `validate_pack.py` | Pack validator (to be updated in F3.3) | `tools/content-pack-builder/` |
| `GemmaEmbeddingModel.kt` | Android embedding (to be updated) | `apps/learner-mobile/core/ai/` |
| `ADR-0008` | Pack format rationale | `ejs-docs/adr/` |
| `feature-gemma4-semantic-search.md` | Feature PRD | `docs/product/` |

---

## Changelog

| Date | Change | Author |
|------|--------|--------|
| 2026-04-10 | Initial contract (schema v2, hash-based fallback) | content-pack-engineer |
