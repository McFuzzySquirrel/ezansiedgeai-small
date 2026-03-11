# eZansiEdgeAI

[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)
[![Branch](https://img.shields.io/badge/status-Phase%200%20Complete-brightgreen?style=flat-square)]()
[![Platform](https://img.shields.io/badge/platform-Android%2010%2B-3ddc84?style=flat-square&logo=android&logoColor=white)]()
[![LLM](https://img.shields.io/badge/LLM-Qwen2.5--1.5B%20Q4__K__M-orange?style=flat-square)]()

> Offline, phone-first AI learning support for learners who cannot afford cloud AI.

eZansiEdgeAI delivers curriculum-grounded explanations to South African school learners on low-end Android phones — no internet connection, no subscription, no cloud account required. The system runs entirely on-device, using a quantized small language model, local embeddings, and pre-packaged curriculum content that can be sideloaded from a USB drive or transferred phone-to-phone.

The V1 target is **Grade 6 Mathematics, CAPS-aligned** (South African national curriculum).

> [!NOTE]
> This is open, enabling infrastructure for educational inclusion. It is not a commercial product. Every feature decision is tested against one question: *"Does this work for a learner on a cracked-screen hand-me-down phone with no data and load-shedding tonight?"*

---

## The Problem

Cloud AI assumes reliable internet, ongoing payments, and modern hardware. For millions of South African learners this means AI-powered tutoring is simply unavailable:

- Mobile data costs are among the highest on the continent.
- A typical Grade 6 class has 40+ learners and one teacher.
- Low-end phones (2–4 GB RAM) are the rule, not the exception.
- Load-shedding cuts power for 2–8 hours a day.

eZansiEdgeAI is designed for this reality.

---

## How It Works

The core insight is that **knowledge + retrieval + explanation** outperforms raw model capability at this scale. Rather than running a general-purpose AI model, the system:

1. Stores curriculum content locally as pre-chunked, pre-embedded content packs.
2. Embeds the learner's question using a tiny on-device embedding model.
3. Retrieves the most relevant content chunks via a local vector search.
4. Passes the retrieved content as grounding context to a quantized on-device LLM.
5. Displays a curriculum-aligned explanation — never a hallucination.

```
Learner asks a question
        │
        ▼
┌─ Phone ──────────────────────────────────────────┐
│  1. Embed query (all-MiniLM-L6-v2, ~10 ms)       │
│  2. Vector search over local content pack (FAISS) │
│  3. Build grounded prompt with retrieved chunks   │
│  4. Run Qwen2.5-1.5B-Instruct Q4_K_M via GGUF    │
│  5. Display explanation to learner                │
└──────────────────────────────────────────────────┘
```

If a **school edge node** (a Raspberry Pi or old laptop on the school's WiFi) is present, it can offload voice processing, serve content pack updates over LAN, and optionally host a larger model. But the phone always works without it.

---

## Features

- **Fully offline** — works with airplane mode permanently on. Zero network calls at runtime.
- **Phone-first** — targets Android 10+, 4 GB RAM devices (Samsung A04s, Xiaomi Redmi 10C class).
- **Retrieval-grounded** — the LLM explains content it retrieved; it never invents curriculum facts.
- **Zero cost to the learner** — no accounts, no sign-up, no Play Store dependency; sideloadable as a single APK.
- **Privacy by design** — all learner data stays on-device. No telemetry, no analytics SDK anywhere in the dependency tree.
- **Content packs as the unit of knowledge** — versioned, verifiable SQLite bundles (SHA-256 integrity). ≤ 200 MB per subject/grade.
- **School edge node (optional)** — LAN content sync, shared STT/TTS, and larger-model offload without making any of it a dependency.
- **Multi-profile, no passwords** — multiple learners can share a device; profiles are local and selected by name.

---

## AI Stack

Validated through Phase 0 spikes on the target hardware class:

| Component | Decision | Size | Key Metric |
|-----------|----------|------|------------|
| On-device LLM | Qwen2.5-1.5B-Instruct Q4_K_M (GGUF, llama.cpp) | ~1,066 MB | Load: 0.78s · 150-token response: 8s avg · RAM: 1,839 MB peak |
| Embedding model | all-MiniLM-L6-v2 (sentence-transformers) | ~87 MB | Load: 0.13s · Query embed: ~10 ms · Retrieval accuracy: 100% (Top-3) |
| Vector store | FAISS Flat (IndexFlatIP) | per-pack | Search: <0.1 ms at content-pack scale |
| Content pack format | SQLite (`.pack`) with embedded FAISS index BLOB | 76 KB (10 chunks prototype) | Validated end-to-end: all 5 smoke-test questions answered correctly |

See the ADRs for full decision rationale: [ADR 0006](ejs-docs/adr/0006-qwen25-1.5b-as-on-device-llm.md) · [ADR 0007](ejs-docs/adr/0007-embedding-model-vector-store-storage-budget.md) · [ADR 0008](ejs-docs/adr/0008-content-pack-sqlite-format.md).

---

## System Topology

```
┌─────────────────────────────────────────────────────────────────┐
│                        SCHOOL BUILDING                          │
│                                                                 │
│   ┌──────────────┐      WiFi (LAN only)     ┌──────────────┐   │
│   │  Learner     │◄────────────────────────►│  School Edge │   │
│   │  Phone       │   mDNS / REST             │  Node        │   │
│   └──────────────┘                           │  (optional)  │   │
│         ...                                  └──────┬───────┘   │
└─────────────────────────────────────────────────────┼───────────┘
                                                      │ (periodic,
                                                      │  opportunistic)
                                              ┌───────▼───────┐
                                              │  Internet      │
                                              │  (content pack │
                                              │   updates only)│
                                              └────────────────┘
```

The internet link is optional and used only for content pack and software updates. All three deployment modes — phone-only, phone + edge WiFi, and phone + edge WiFi + internet — are supported. Every mode degrades gracefully to phone-only.

---

## Repository Structure

```
.
├── apps/
│   ├── learner-mobile/      Android app (Phase 1 — stack TBD after Phase 0 spikes)
│   └── school-edge-node/    Optional LAN capability-booster (Phase 3)
├── content-packs/           Built .pack files (SQLite, FAISS-indexed)
├── docs/
│   ├── architecture/        System overview, deployment modes, component architecture
│   ├── development/         Coding principles, V1 backlog
│   └── product/             Constraints, personas, success metrics
├── ejs-docs/
│   ├── adr/                 Architecture Decision Records (ADR 0001–0008)
│   └── journey/             Engineering Journey System session logs
├── models/
│   ├── edge-models/         Model assets for the school edge node
│   └── phone-models/        Model assets for the learner mobile app
├── scripts/                 Bootstrap, git hooks, ADR database tooling
├── spikes/
│   ├── p0-001-llm-inference/       LLM benchmark spike (complete ✅)
│   ├── p0-002-embedding-retrieval/ Embedding + retrieval spike (complete ✅)
│   └── p0-005-e2e-pipeline/        End-to-end pipeline smoke test (complete ✅)
└── tools/
    └── content-pack-builder/       Build and validate .pack files
```

---

## Getting Started

### Prerequisites

- Python 3.10+ (for spikes and tooling)
- Git

### Run a Phase 0 Spike

Each spike is self-contained with its own virtual environment.

**LLM inference spike (P0-001):**
```bash
cd spikes/p0-001-llm-inference
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python scripts/download_models.py --model qwen2.5-1.5b
python scripts/benchmark.py --model models/qwen2.5-1.5b-instruct-q4_k_m.gguf
```

**Embedding + retrieval spike (P0-002):**
```bash
cd spikes/p0-002-embedding-retrieval
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python scripts/download_models.py --all
python scripts/benchmark.py --all
```

**End-to-end pipeline smoke test (P0-005):**
```bash
cd spikes/p0-005-e2e-pipeline
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python scripts/pipeline.py
```

### Build a Content Pack

```bash
cd tools/content-pack-builder
pip install -r requirements.txt
python build_pack.py --content content/ --output ../../content-packs/my-pack-v1.0.pack
python validate_pack.py ../../content-packs/my-pack-v1.0.pack
```

The builder ingests Markdown curriculum chunks, generates embeddings with all-MiniLM-L6-v2, builds a FAISS Flat index, and packages everything into a versioned SQLite `.pack` file with SHA-256 integrity hashes.

---

## Current Status

| Phase | Scope | Status |
|-------|-------|--------|
| **Phase 0** — Feasibility | LLM spike, embedding spike, storage budget, sample content pack, E2E pipeline | **Complete** |
| **Phase 1** — Core App | Android app skeleton, local inference integration, content pack loading, basic chat UI | In planning |
| **Phase 2** — Content & Quality | Full Grade 6 Maths CAPS content pack, explanation quality review, UX polish | Not started |
| **Phase 3** — Edge Node | School LAN node, content sync, STT/TTS services | Not started |

> [!IMPORTANT]
> Phase 0 has validated that the full pipeline — quantized LLM + local embedding + FAISS retrieval — is feasible on target hardware within all performance budgets. The architecture is confirmed. Phase 1 begins Android app development.

---

## Storage Budget (Post-Phase 0)

| Component | Budget |
|-----------|--------|
| APK | ≤ 50 MB |
| LLM (Qwen2.5-1.5B Q4_K_M) | ~1,066 MB (downloaded on first launch) |
| Embedding model (all-MiniLM-L6-v2) | ~87 MB (downloaded on first launch) |
| Content pack (per subject/grade) | ≤ 200 MB |
| **First-launch total download** | **~1,403 MB** |

Peak RAM (LLM + embedding loaded sequentially): ~554 MB active working set against a 1,161 MB budget on 4 GB devices. Models are never loaded simultaneously.

---

## Documentation

| Document | Description |
|----------|-------------|
| [Vision & Strategy](docs/vision.md) | Mission, hard truths, and strategic direction |
| [System Overview](docs/architecture/system-overview.md) | Component topology, data flow, design principles |
| [Deployment Modes](docs/architecture/deployment-modes.md) | Phone-only, phone + edge WiFi, phone + full stack |
| [V1 Backlog](docs/development/backlog-v1.md) | 90-day execution plan across 4 phases |
| [Coding Principles](docs/development/coding-principles.md) | Offline-first patterns, resource budgets, performance rules |
| [Constraints](docs/product/constraints.md) | Hardware, connectivity, power, and deployment realities |
| [User Personas](docs/product/user-personas.md) | Thandiwe, Sipho, Ms. Dlamini, and others |
| [ADR Index](ejs-docs/adr/) | All architecture decisions with rationale and evidence |
