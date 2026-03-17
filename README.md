<div align="center">

# eZansiEdgeAI

[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)
[![Phase 1](https://img.shields.io/badge/status-Phase%201%20Complete-brightgreen?style=flat-square)]()
[![Platform](https://img.shields.io/badge/platform-Android%2010%2B-3ddc84?style=flat-square&logo=android&logoColor=white)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7f52ff?style=flat-square&logo=kotlin&logoColor=white)]()
[![LLM](https://img.shields.io/badge/LLM-Qwen2.5--1.5B%20Q4__K__M-orange?style=flat-square)]()

**Offline, phone-first AI learning support for learners who cannot afford cloud AI.**

[Overview](#overview) · [How It Works](#how-it-works) · [Getting Started](#getting-started) · [Project Status](#project-status) · [Documentation](#documentation)

</div>

---

eZansiEdgeAI delivers curriculum-grounded maths explanations to South African school learners on low-end Android phones — no internet, no subscription, no cloud account. The system runs entirely on-device using a quantized small language model, local embeddings, and pre-packaged curriculum content that can be sideloaded via USB or transferred phone-to-phone.

**V1 target:** Grade 6 Mathematics, CAPS-aligned (South African national curriculum).

> [!NOTE]
> This is open infrastructure for educational inclusion, not a commercial product. Every feature decision is tested against one question: *"Does this work for a learner on a cracked-screen hand-me-down phone with no data and load-shedding tonight?"*

## Overview

Cloud AI assumes reliable internet, ongoing payments, and modern hardware. For millions of South African learners, AI-powered tutoring is simply unavailable:

- Mobile data costs are among the highest on the continent
- A typical Grade 6 class has 40+ learners and one teacher
- Low-end phones (3–4 GB RAM) are the rule, not the exception
- Load-shedding cuts power for 2–8 hours a day

eZansiEdgeAI is designed for this reality. The core insight is that **knowledge + retrieval + explanation** outperforms raw model capability at this scale.

### Features

- **Fully offline** — zero network calls at runtime; works with airplane mode permanently on
- **Phone-first** — targets Android 10+, 4 GB RAM devices (Samsung A04s, Xiaomi Redmi 10C class)
- **Retrieval-grounded** — the LLM explains retrieved curriculum content; it never invents facts
- **Zero cost** — no accounts, no sign-up, no Play Store dependency; sideloadable as a single APK
- **Privacy by design** — all data stays on-device, AES-256-GCM encrypted; no telemetry, no analytics SDK
- **Content packs** — versioned, SHA-256 verified SQLite bundles with pre-computed embeddings (≤ 200 MB each)
- **Multi-profile** — multiple learners share a device; profiles are local and selected by name
- **School edge node** *(optional)* — LAN content sync and larger-model offload, never a hard dependency

## How It Works

Rather than running a general-purpose AI, the system stores curriculum content locally as pre-chunked, pre-embedded content packs and uses retrieval-augmented generation to produce grounded explanations:

```
Learner asks a question
        │
        ▼
┌─ Phone ──────────────────────────────────────────────┐
│  1. Embed query    (all-MiniLM-L6-v2, ONNX, ~10 ms) │
│  2. Vector search  (FAISS over local content pack)   │
│  3. Build prompt   (Jinja2-style template engine)    │
│  4. Generate       (Qwen2.5-1.5B Q4_K_M, llama.cpp) │
│  5. Stream answer  (Markdown + LaTeX rendering)      │
└──────────────────────────────────────────────────────┘
```

If a **school edge node** (Raspberry Pi or old laptop on the school's WiFi) is present, it can serve content pack updates over LAN. But the phone always works without it.

### AI Stack

Validated through Phase 0 spikes on target hardware:

| Component | Choice | Size | Key Metric |
|-----------|--------|------|------------|
| On-device LLM | Qwen2.5-1.5B-Instruct Q4_K_M (GGUF, llama.cpp) | ~1,066 MB | 150-token response: ~8 s avg |
| Embedding model | all-MiniLM-L6-v2 (ONNX Runtime) | ~87 MB | Query embed: ~10 ms · Top-3 accuracy: 100% |
| Vector store | FAISS Flat (IndexFlatIP) | per-pack | Search: < 0.1 ms |
| Content packs | SQLite `.pack` with embedded FAISS index | ≤ 200 MB | SHA-256 verified, pre-computed 384-dim embeddings |

> [!TIP]
> See the ADRs for full decision rationale: [ADR 0006](ejs-docs/adr/0006-qwen25-1.5b-as-on-device-llm.md) · [ADR 0007](ejs-docs/adr/0007-embedding-model-vector-store-storage-budget.md) · [ADR 0008](ejs-docs/adr/0008-content-pack-sqlite-format.md)

### System Topology

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

All three deployment modes — phone-only, phone + edge WiFi, and phone + edge WiFi + internet — degrade gracefully to phone-only.

## Getting Started

### Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Android Studio | Ladybug+ | Build the Android app |
| JDK | 17 | Kotlin/Gradle compilation |
| Python | 3.10+ | Spikes and content pack tooling |
| Git | any | Source control |

### Build the Android App

```bash
git clone https://github.com/McFuzzySquirrel/ezansiedgeai-small.git
cd ezansiedgeai-small/apps/learner-mobile
./gradlew assembleDebug
```

> [!IMPORTANT]
> The app compiles and runs with mock AI implementations out of the box. Native model files (GGUF/ONNX) are not included in the repository — see [models/phone-models/](models/phone-models/) for download instructions.

### Run Tests

```bash
cd apps/learner-mobile
./gradlew test
```

The test suite covers the AI pipeline, template engine, data encryption, content pack loading, and UI view models (285 tests across 16 files).

### Build a Content Pack

```bash
cd tools/content-pack-builder
pip install -r requirements.txt
python build_pack.py --content content/ --output ../../content-packs/my-pack-v1.0.pack
python validate_pack.py ../../content-packs/my-pack-v1.0.pack
```

The builder ingests Markdown curriculum chunks, generates embeddings with all-MiniLM-L6-v2, builds a FAISS index, and packages everything into a versioned SQLite `.pack` file with SHA-256 integrity hashes.

### Run a Phase 0 Spike

Each spike is self-contained with its own virtual environment:

```bash
# LLM inference benchmark
cd spikes/p0-001-llm-inference
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python scripts/benchmark.py --model models/qwen2.5-1.5b-instruct-q4_k_m.gguf

# End-to-end pipeline smoke test
cd spikes/p0-005-e2e-pipeline
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python scripts/pipeline.py
```

## Project Status

| Phase | Scope | Status |
|-------|-------|--------|
| **Phase 0** — Feasibility | LLM spike, embedding spike, storage budget, sample content pack, E2E pipeline | **Complete** |
| **Phase 1** — Offline Learning Loop | Android app, AI pipeline, chat UI, topic browser, content pack loader, profiles, onboarding | **Complete** |
| **Phase 2** — Content & Personalisation | Full Grade 6 Maths CAPS pack, preference engine, delta updates, feedback system | Not started |
| **Phase 3** — School Edge Node | LAN discovery, content sync, edge server | Not started |

### Phase 1 Summary

The complete offline learning loop is implemented across 9 Gradle modules (74 Kotlin source files, 16 test files):

- **AI pipeline** — embed → retrieve → prompt → generate flow with 30 s timeout and sequential model loading
- **Chat interface** — Markdown + LaTeX rendering, streaming responses, message history
- **Topic browser** — CAPS curriculum structure with breadcrumb navigation and suggested questions
- **Content pack loader** — SQLite `.pack` reader with double SHA-256 verification
- **Learner profiles** — AES-256-GCM encrypted storage, multi-profile support
- **Preferences** — explanation style, reading level, example type (stored locally, injected into prompts)
- **Template engine** — Jinja2-style renderer (`{{ }}`, `{% if %}`, `{% for %}`, filters) with grounding enforcement
- **Onboarding** — zero-step entry with optional dismissible tooltips

> [!NOTE]
> The app runs with mock AI implementations (no native `.so` files required). Real on-device inference requires downloading the GGUF model and ONNX embedding model to the device.

### Storage Budget

| Component | Size |
|-----------|------|
| APK | ≤ 50 MB |
| LLM (Qwen2.5-1.5B Q4_K_M) | ~1,066 MB |
| Embedding model (all-MiniLM-L6-v2) | ~87 MB |
| Content pack (per subject/grade) | ≤ 200 MB |
| **Total first-launch footprint** | **~1,403 MB** |

Peak RAM: ~554 MB working set on 4 GB devices. Models are loaded sequentially, never simultaneously.

## Repository Structure

```
├── apps/
│   ├── learner-mobile/          Android app (Kotlin, Jetpack Compose, Material 3)
│   │   ├── app/                 Main application module (DI, navigation, theme)
│   │   ├── core/
│   │   │   ├── ai/              AI pipeline — ExplanationEngine, embeddings, LLM, templates
│   │   │   ├── common/          Shared utilities — Result type, dispatchers, storage
│   │   │   └── data/            Data layer — profiles, packs, chat history, encryption
│   │   └── feature/
│   │       ├── chat/            Chat screen, Markdown renderer, onboarding
│   │       ├── topics/          CAPS topic browser, suggested questions
│   │       ├── profiles/        Learner profile management
│   │       ├── preferences/     Learning preference settings
│   │       └── library/         Content pack library browser
│   └── school-edge-node/        Optional LAN capability-booster (Phase 3)
├── content-packs/               Built .pack files (SQLite, FAISS-indexed)
├── docs/
│   ├── architecture/            System overview, deployment modes, components
│   ├── development/             Coding principles, V1 backlog
│   └── product/                 PRD, constraints, personas, success metrics
├── ejs-docs/
│   ├── adr/                     Architecture Decision Records (ADR 0001–0008)
│   └── journey/                 Engineering Journey System session logs
├── models/
│   ├── edge-models/             Model assets for the school edge node
│   └── phone-models/            Model assets for the learner phone
├── scripts/                     Bootstrap, git hooks, ADR database tooling
├── spikes/                      Phase 0 feasibility spikes (all complete)
└── tools/
    └── content-pack-builder/    Python CLI to build and validate .pack files
```

## Architecture Decisions

Key decisions are documented as Architecture Decision Records:

| ADR | Decision |
|-----|----------|
| [0003](ejs-docs/adr/0003-retrieval-first-architecture.md) | Retrieval-first architecture over general-purpose generation |
| [0004](ejs-docs/adr/0004-content-pack-as-unit-of-knowledge.md) | Content packs as the unit of knowledge distribution |
| [0006](ejs-docs/adr/0006-qwen25-1.5b-as-on-device-llm.md) | Qwen2.5-1.5B as on-device LLM |
| [0007](ejs-docs/adr/0007-embedding-model-vector-store-storage-budget.md) | all-MiniLM-L6-v2 + FAISS for embedding and retrieval |
| [0008](ejs-docs/adr/0008-content-pack-sqlite-format.md) | SQLite `.pack` format with embedded FAISS index |

See the full [ADR index](ejs-docs/adr/) for all decisions.

## Documentation

| Document | Description |
|----------|-------------|
| [PRD v1](docs/product/prd-v1.md) | Full product requirements document |
| [Vision & Strategy](docs/vision.md) | Mission, hard truths, and strategic direction |
| [System Overview](docs/architecture/system-overview.md) | Component topology, data flow, design principles |
| [Deployment Modes](docs/architecture/deployment-modes.md) | Phone-only, phone + edge, phone + edge + internet |
| [Constraints](docs/product/constraints.md) | Hardware, connectivity, power, and deployment realities |
| [User Personas](docs/product/user-personas.md) | Thandiwe, Sipho, Ms. Dlamini, and others |
| [Coding Principles](docs/development/coding-principles.md) | Offline-first patterns, resource budgets, performance rules |
| [V1 Backlog](docs/development/backlog-v1.md) | Execution plan across 4 phases |
