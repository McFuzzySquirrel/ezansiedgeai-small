---
ejs:
  type: journey-adr
  version: 1.1
  adr_id: "0007"
  title: all-MiniLM-L6-v2 as embedding model, FAISS Flat as vector store, revised storage & RAM budgets
  date: 2026-03-04
  status: accepted
  session_id: ejs-session-2026-03-04-01
  session_journey: ejs-docs/journey/2026/ejs-session-2026-03-04-01.md

actors:
  humans:
    - id: Doug McCusker
      role: Project Lead
  agents:
    - id: GitHub Copilot
      role: AI coding assistant

context:
  repo: ezansiedgeai-small
  branch: spike/p0-002-embedding-retrieval
---

# ADR 0007 — all-MiniLM-L6-v2 as Embedding Model, FAISS Flat as Vector Store, Revised Storage & RAM Budgets

## Session Journey

[ejs-session-2026-03-04-01](../journey/2026/ejs-session-2026-03-04-01.md)

## Context

The eZansiEdgeAI retrieval-first architecture (ADR 0003) requires an embedding model and vector store to run on-device alongside the Qwen2.5-1.5B LLM (ADR 0006). The embedding pipeline must:

1. Convert curriculum content chunks into vector embeddings at build time
2. Convert learner queries into embeddings at runtime (< 500ms)
3. Search the vector index for relevant chunks at runtime (< 500ms)
4. Achieve ≥ 80% top-3 retrieval accuracy on CAPS Grade 6 Mathematics content
5. Fit within the RAM headroom left after the LLM

ADR 0006 established that Qwen2.5-1.5B uses 1,839 MB peak RAM. On the original 3 GB (marketed) target devices, this left only ~209 MB headroom — a project-killer risk for the embedding pipeline + app overhead.

Simultaneously, the original storage budget of ≤ 150 MB (constraints.md) was already known to be unrealistic: the LLM alone is 1,066 MB on disk. The storage budget needed redefinition.

This ADR covers three related decisions that emerged from the P0-002 spike and the preceding planning session:

1. **Raise the minimum device RAM** from 3 GB to 4 GB (marketed)
2. **Select the embedding model** from benchmarked candidates
3. **Select the vector store** and **redefine the storage budget**

## Session Intent

Execute P0-002 (Local Embedding + Retrieval Spike) with expanded scope: benchmark embedding model + vector store combinations against acceptance criteria, validate the full on-device storage footprint (absorbed from the original P0-003), and confirm that the revised 4 GB RAM floor provides sufficient headroom.

## Collaboration Summary

The human directed several scope decisions before implementation began:
- Raise RAM floor to 4 GB instead of pursuing a tiered device strategy
- Fold P0-003's storage footprint scope into P0-002
- Rename P0-003 to "Battery & Thermal Validation"
- Confirmed Mobicel Hero 4 GB variants exist in the SA market

The agent built the complete spike scaffold (mirroring P0-001's structure), authored 50 CAPS-aligned Grade 6 Maths content chunks and 20 test queries, implemented download/benchmark/report scripts, and executed the full benchmark. Results were unambiguous — all-MiniLM-L6-v2 dominated on accuracy (100%) while fitting all constraints.

## Decision Trigger / Significance

This ADR warrants formal recording because it:

- **Redefines a system constraint** — the minimum device RAM floor moves from 3 GB to 4 GB, which drops one device SKU (Samsung A04 2 GB) and changes the target market definition
- **Selects a core pipeline component** — the embedding model is used in every content retrieval query and in content pack builds. Changing it requires re-embedding all content.
- **Redefines the storage budget** — from a single 150 MB limit to a component-based model (APK + models + content packs), which affects distribution strategy, first-launch UX, and Play Store listing
- **Resolves the #1 project risk** — the 209 MB RAM headroom from ADR 0006 was the top risk item. This ADR demonstrates 1,161 MB headroom with the full embedding pipeline running.
- **Is backed by empirical evidence** — 12 combinations benchmarked (3 models × 4 vector stores) with quantified results

## Considered Options

### Embedding Model Options

#### Option A: all-MiniLM-L6-v2 (sentence-transformers)

- **Size:** 87.3 MB on disk
- **Dimensions:** 384
- **Query embedding time:** ~10 ms (host benchmark)
- **Retrieval accuracy:** 100% (20/20 queries hit at least one expected chunk in top-3)
- **Peak RAM (with vector store):** ~554 MB
- **RAM delta at load:** +29 MB
- **Strengths:** Highest accuracy. Fastest embedding. Well within size limit. Widely used, well-documented, MIT licensed.
- **Weakness:** Larger than gte-small (87 MB vs 64 MB), though both are well within the 100 MB limit.

#### Option B: bge-small-en-v1.5 (BAAI)

- **Size:** 128.1 MB on disk
- **Dimensions:** 384
- **Query embedding time:** ~18 ms
- **Retrieval accuracy:** 95% (19/20 queries)
- **Peak RAM (with vector store):** ~614 MB
- **Strengths:** Strong accuracy. Good multilingual potential.
- **Weakness:** **Exceeds the 100 MB model size limit.** Slower than all-MiniLM-L6-v2. Higher RAM. Failed q05 (word problems crossing fraction/decimal topics).

#### Option C: gte-small (Thenlper)

- **Size:** 64.4 MB on disk
- **Dimensions:** 384
- **Query embedding time:** ~30 ms
- **Retrieval accuracy:** 95% (19/20 queries)
- **Peak RAM (with vector store):** ~553 MB
- **Strengths:** Smallest model by far (64 MB). Lowest peak RAM.
- **Weakness:** 3× slower embedding than all-MiniLM-L6-v2. 5% lower accuracy. Failed same q05 query. Apache 2.0 license.

### Vector Store Options

#### Option D: FAISS Flat (exact search)

- **Index size:** 75 KB (50 chunks)
- **Search time:** 0.06 ms
- **Strengths:** Exact nearest-neighbour search. Zero-config. Simplest to integrate.
- **Weakness:** O(n) search — but irrelevant at content-pack scale (< 1,000 chunks).

#### Option E: FAISS IVF-Flat (approximate)

- **Index size:** 75 KB (50 chunks)
- **Search time:** 0.07 ms
- **Strengths:** Scales better for large datasets (> 10,000 vectors).
- **Weakness:** Requires training with sufficient data points (got clustering warning at 50 chunks). Marginal benefit at content-pack scale.

#### Option F: HNSWlib (graph-based ANN)

- **Index size:** 150 KB (50 chunks)
- **Search time:** 0.05 ms
- **Strengths:** Fastest search. Good scaling characteristics.
- **Weakness:** 2× index size vs FAISS. Compilation requires python3-dev. Additional native dependency on mobile.

#### Option G: NumPy cosine similarity (baseline)

- **Index size:** 75 KB (50 chunks)
- **Search time:** 0.07 ms
- **Strengths:** Zero additional dependencies beyond NumPy.
- **Weakness:** No optimised indexing. O(n) brute-force, same as FAISS Flat but without FAISS's future upgrade path.

---

# Decision

**1. Raise minimum device RAM to 4 GB (marketed) / ~3 GB usable.**

Drop Samsung A04 (2 GB) from target devices. Samsung A04s/A05 (4 GB), Xiaomi Redmi 10C (4 GB), and Mobicel Hero (4 GB) remain valid targets.

**2. Adopt all-MiniLM-L6-v2 as the V1 embedding model.**

Use `sentence-transformers/all-MiniLM-L6-v2` for all content embedding and query embedding operations.

**3. Adopt FAISS Flat as the V1 vector store.**

Use `faiss-cpu` IndexFlatIP for vector similarity search. Revisit if content scales beyond ~1,000 chunks per pack.

**4. Redefine the storage budget as a component-based model:**

| Component | Budget | Delivery |
|-----------|--------|----------|
| APK | ≤ 50 MB | Play Store / sideload |
| LLM model (Qwen2.5-1.5B Q4_K_M) | ~1,066 MB | Downloaded on first launch |
| Embedding model (all-MiniLM-L6-v2) | ~87 MB | Downloaded on first launch |
| Content pack (per pack) | ≤ 200 MB | Downloaded per grade/subject |
| **Total first-launch download** | **~1,403 MB** | |

---

# Rationale

### Embedding model: all-MiniLM-L6-v2

all-MiniLM-L6-v2 is the only model achieving **100% retrieval accuracy** across all 20 CAPS-aligned test queries — 5 percentage points ahead of both competitors. It is also the **fastest** (10ms vs 18ms BGE, 30ms GTE) and fits comfortably within the 100 MB size limit (87.3 MB).

| Criterion | Target | all-MiniLM-L6-v2 | bge-small-en-v1.5 | gte-small |
|-----------|--------|-------------------|-------------------|-----------|
| Model size | ≤ 100 MB | 87.3 MB ✅ | 128.1 MB ❌ | 64.4 MB ✅ |
| Query embed | < 500 ms | 10 ms ✅ | 18 ms ✅ | 30 ms ✅ |
| Accuracy (top-3) | ≥ 80% | 100% ✅ | 95% ✅ | 95% ✅ |
| Peak RAM | < 1,161 MB | 554 MB ✅ | 614 MB ✅ | 553 MB ✅ |

bge-small-en-v1.5 is eliminated by the size constraint (128 MB > 100 MB limit). gte-small passes all criteria but is 3× slower on embedding and 5% less accurate — there is no reason to prefer it over all-MiniLM-L6-v2 given that the 23 MB size difference is immaterial.

### Vector store: FAISS Flat

All four vector stores perform identically at content-pack scale (50 chunks, < 0.1ms search). The choice at this scale is about simplicity and future upgrade path:

- FAISS Flat provides exact search with zero configuration
- If content packs grow beyond ~1,000 chunks, FAISS IVF or HNSWlib can be swapped in as a drop-in replacement using the same FAISS API
- NumPy cosine is eliminated because it offers no upgrade path
- HNSWlib is eliminated because it adds a native compilation dependency (python3-dev / Android NDK complexity) with no benefit at current scale

### RAM floor: 4 GB

The 3 GB → 4 GB device floor change provides **5.6× RAM headroom improvement** (209 MB → 1,161 MB). This:

- **Removes RAM as a project-killer risk.** The full embedding pipeline (model load + vector search) peaks at ~554 MB, well within 1,161 MB headroom.
- **Leaves room for the Android app.** After LLM (1,839 MB) + embedding peak (554 MB) = 2,393 MB, there is still ~679 MB for the Android app runtime, system services, and UI.
- **Is market-viable.** The dropped device (Samsung A04 2 GB) is being replaced in the market by A04s/A05 (4 GB). Mobicel Hero 4 GB variants are confirmed available.
- **Is simpler than the alternative.** A tiered device strategy (different models for different RAM tiers) would add significant complexity to the app, content packs, and testing matrix.

### Storage budget: component model

The original 150 MB budget was set before P0-001 demonstrated that the LLM alone requires 1,066 MB. Redefining storage as components reflects reality:

- **APK ≤ 50 MB** — keeps Play Store listing viable (< 150 MB AAB limit) and sideload-friendly
- **Models downloaded on first launch** — standard pattern for AI apps (ChatGPT, Google Translate offline)
- **Content packs downloaded separately** — allows incremental download per grade/subject without re-downloading models
- **Total ~1.4 GB first-launch** — acceptable on Wi-Fi (school sync) or mobile data in South Africa (data bundles typically 1–5 GB)

---

# Consequences

### Positive

- **Unblocks P0-004** (sample content pack) — can now build with known embedding model + FAISS Flat
- **Unblocks P0-005** (E2E pipeline) — full stack is now: Qwen2.5-1.5B + all-MiniLM-L6-v2 + FAISS Flat
- **Retrieval accuracy exceeds target** — 100% vs 80% requirement provides confidence margin for harder queries
- **Massive RAM margin** — 554 MB embedding peak vs 1,161 MB budget leaves room for app complexity
- **Clear storage model** — component budgets give each team/feature a defined envelope
- **Embedding model is widely used** — all-MiniLM-L6-v2 is one of the most downloaded sentence-transformers models, ensuring good community support and documentation
- **MIT licensed** — no distribution concerns for sideload or Play Store

### Negative / Trade-offs

- **Drops Samsung A04 2 GB from targets** — some learners with the cheapest devices won't be served by V1. Mitigation: edge-device mode (ADR 0004) can serve these learners via school Wi-Fi.
- **~1.4 GB first-launch download** — significant on mobile data. Mitigation: design for school Wi-Fi sync; show clear progress/resume UI.
- **Host benchmarks only** — absolute latency numbers will differ on ARM Cortex-A53 class CPUs. Relative rankings and RAM patterns are the primary signal. Android validation moves to P0-104 (see P0-005 Validation below).
- **100% accuracy is on a 50-chunk test set** — accuracy may degrade with larger, more diverse content. Content pack testing (P0-004) must validate at scale.
- **FAISS Flat is O(n)** — acceptable for content packs (< 1,000 chunks) but will need revisiting if we move to cross-pack search at larger scale.

### P0-005 Validation (2026-03-11)

P0-005 (`spike/p0-005-e2e-pipeline`) ran the full sequential pipeline on a CPU-only dev machine and confirmed the core constraints predicted in this ADR:

| Constraint (this ADR) | P0-005 Measured Result | Status |
|---|---|---|
| Sequential loading — embedding unloads before LLM loads | Embed unloads 49 MB → LLM loads +1,754 MB; confirmed no overlap | ✅ |
| Query embedding < 500ms | ~10ms CPU | ✅ |
| FAISS search < 500ms | ~0.05ms | ✅ |
| Retrieval grounded (not hallucinated) | 5/5 questions: top chunk semantically correct, answers referenced chunk content | ✅ |
| Total pipeline latency < 15s | 14–20s CPU-only (Quadro M2200 sm_52 incompatible with PyTorch 2.10+); on-device target unvalidated | ⚠️ |

**Sequential loading note:** The `del model; gc.collect()` pattern between embedding and generation phases works as predicted. The RAM profile (embed peak ~864 MB, LLM peak ~2,591 MB, no concurrent overlap) is consistent with the 1,161 MB headroom model.

**Latency note:** The 15s on-device target is not yet validated — the dev machine runs CPU-only due to GPU incompatibility (sm_52). Android hardware validation (GGUF + NNAPI) remains a requirement for P0-104.

---

# Key Learnings

- **Model size ≠ quality** for education domain retrieval. all-MiniLM-L6-v2 (87 MB) outperformed both the larger bge-small (128 MB) and the smaller gte-small (64 MB) on accuracy.
- **Vector store choice is irrelevant at content-pack scale.** All four stores produced identical retrieval results with < 0.1ms search at 50 chunks. Don't over-engineer this.
- **Embedding model RAM overhead is tiny** (~29 MB load delta). The LLM dominates RAM; the embedding model is negligible in comparison.
- **Raise device floors early** when a single component consumes > 90% of the budget. The 3 GB → 4 GB change solved the #1 project risk with a single constraint update rather than architectural complexity.
- **Component-based storage budgets** are more honest than a single number when the system has clearly separable, independently downloadable components.

---

# Agent Guidance

- **Embedding model reference:** `sentence-transformers/all-MiniLM-L6-v2`, 384 dimensions, ~87 MB on disk
- **Vector store reference:** `faiss-cpu` IndexFlatIP, 75 KB per 50 chunks of 384-dim vectors
- **RAM budget:** ~554 MB peak for embedding + vector search. Combined with LLM (1,839 MB peak) = ~2,393 MB total on 3,072 MB usable (4 GB device).
- **Content pack format:** Each pack should include pre-computed embeddings (from all-MiniLM-L6-v2) + FAISS index. The app loads the index, does not re-embed content at runtime.
- **Query flow:** Learner question → all-MiniLM-L6-v2 embedding (~10ms) → FAISS Flat search (< 0.1ms) → top-3 chunks → Qwen2.5-1.5B generation with retrieved context
- **Do not** switch embedding models without re-embedding all content packs and re-running retrieval accuracy tests
- **Do not** re-embed content at runtime on-device — pre-compute at build time
- **Do not** use FAISS IVF with < 300 training points — it warns and provides no benefit
- **Revisit vector store** only if content exceeds ~1,000 chunks per searchable index
- **Validate on Android** — P0-005 confirmed sequential loading and sub-500ms embed/search on CPU. Absolute latency on Android ARMv8-A (GGUF + NNAPI) must be validated in P0-104.
- **LLM and embedding model do not run simultaneously** — the LLM unloads or the embedding model unloads. Peak RAM figures are per-component, not additive. Confirmed working: `del model; gc.collect()` between embedding and generation phases (P0-005).

---

# Reuse Signals

```yaml
reuse:
  patterns:
    - "Benchmark harness pattern from P0-001 reused successfully for P0-002: download → configure → benchmark → report"
    - "50 CAPS-aligned content chunks in spikes/p0-002-embedding-retrieval/content/chunks.json reusable for P0-004 content pack"
    - "20 test queries in content/test_queries.json reusable for retrieval regression testing"
    - "Component-based storage budget pattern applicable to any on-device AI app"
  prompts:
    - "Grade 6 Maths chunks covering: fractions, decimals, geometry, measurement, data handling, patterns/algebra, ratio/rate"
  anti_patterns:
    - "Do not compare embedding models by size alone — accuracy and speed vary independently of model size"
    - "Do not over-engineer vector store choice at small scale — all stores are equivalent under ~1,000 chunks"
    - "Do not use a single storage budget number when components are independently downloadable"
    - "Do not pursue tiered device strategies when raising the floor is simpler and market-viable"
  future_considerations:
    - "Revisit FAISS Flat → IVF/HNSW if cross-pack search or larger content sets exceed ~1,000 chunks"
    - "Test multilingual embedding (all-MiniLM supports it) when Afrikaans content is authored"
    - "Monitor ONNX Runtime Mobile as alternative to sentence-transformers for Android inference"
    - "Consider quantising the embedding model (INT8) if 87 MB becomes a concern on smaller storage"
    - "q05 (word problems crossing fraction/decimal topics) failed on 2 of 3 models — consider enriching cross-topic chunks"
```
