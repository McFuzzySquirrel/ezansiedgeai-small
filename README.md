<div align="center">

# eZansiEdgeAI

[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)
[![Phase 1](https://img.shields.io/badge/status-Phase%201%20Complete-brightgreen?style=flat-square)]()
[![Platform](https://img.shields.io/badge/platform-Android%2010%2B-3ddc84?style=flat-square&logo=android&logoColor=white)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7f52ff?style=flat-square&logo=kotlin&logoColor=white)]()
[![LLM](https://img.shields.io/badge/LLM-Gemma%204%201B%20INT4-orange?style=flat-square)]()
[![Search](https://img.shields.io/badge/feature-Semantic%20Search-blue?style=flat-square)]()

**Offline, phone-first AI learning support for learners who cannot afford cloud AI.**

[Overview](#overview) · [How It Works](#how-it-works) · [Getting Started](#getting-started) · [Project Status](#project-status) · [Documentation](#documentation) · [Project Story](ejs-docs/narratives/story-2026-04-11.md)

</div>

---

eZansiEdgeAI delivers curriculum-grounded maths explanations to South African school learners on low-end Android phones — no internet, no subscription, no cloud account. The system runs entirely on-device using Gemma 4 1B as a unified model for both embedding and generation, with pre-packaged curriculum content that can be sideloaded via USB or transferred phone-to-phone.

For a full end-to-end narrative of how the project was built (sessions, ADR decisions, and milestones), see [Project Story (2026-04-11)](ejs-docs/narratives/story-2026-04-11.md).

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
┌─ Phone ──────────────────────────────────────────────────┐
│  1. Embed query    (Gemma 4 embedding mode, 768-dim)     │
│  2. Vector search  (FAISS over local content pack)       │
│  3. Build prompt   (Jinja2-style template engine)        │
│  4. Generate       (Gemma 4 1B INT4, MediaPipe/LiteRT)   │
│  5. Stream answer  (Markdown + LaTeX rendering)          │
└──────────────────────────────────────────────────────────┘
```

If a **school edge node** (Raspberry Pi or old laptop on the school's WiFi) is present, it can serve content pack updates over LAN. But the phone always works without it.

### AI Stack

Validated through Phase 0 spikes and Gemma 4 evaluation on target hardware:

| Component | Choice | Size | Key Metric |
|-----------|--------|------|------------|
| On-device model | Gemma 4 1B INT4 (MediaPipe GenAI SDK / LiteRT) | ~600 MB | Unified: generation + embedding from single model |
| GPU delegation | LiteRT GPU delegate → NNAPI → CPU fallback | — | ≤5 s end-to-end on Snapdragon 680-class GPU |
| Embedding | Gemma 4 embedding mode (768-dim) | Shared | Query embed: < 100 ms |
| Vector store | FAISS Flat (IndexFlatIP) | per-pack | Search: < 0.1 ms |
| Content packs | SQLite `.pack` v2 with embedded FAISS index | ≤ 200 MB | SHA-256 verified, pre-computed 768-dim embeddings |
| Semantic search | ContentSearchEngine (embed → retrieve → rank) | — | < 100 ms for top-10 results |

> [!NOTE]
> Legacy fallback engines (Qwen2.5-1.5B via llama.cpp + all-MiniLM-L6-v2 via ONNX Runtime) are retained behind a `useGemma` flag for rollback safety during device validation.

> [!TIP]
> See the ADRs for full decision rationale: [ADR 0006](ejs-docs/adr/0006-qwen25-1.5b-as-on-device-llm.md) · [ADR 0007](ejs-docs/adr/0007-embedding-model-vector-store-storage-budget.md) · [ADR 0008](ejs-docs/adr/0008-content-pack-sqlite-format.md) · [ADR 0009](ejs-docs/adr/0009-manual-dependency-injection.md) · [ADR 0010](ejs-docs/adr/0010-aes256gcm-profile-encryption.md) · [ADR 0011](ejs-docs/adr/0011-jinja2-style-template-engine.md)

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

### Install on a Phone

The debug APK is sideloadable — no Play Store needed:

```bash
# Via ADB (USB or wireless)
adb install app/build/outputs/apk/debug/ezansi-v0.1.0-debug.apk

# Or copy the APK to the phone and tap to install
# (enable "Install from unknown sources" in Android settings)
```

The debug APK (~128 MB) includes native libraries. The app launches with **mock AI** — all screens are functional (chat, topics with search, profiles, preferences, library, onboarding) but responses are placeholders until real model files are loaded on the device. See [models/phone-models/](models/phone-models/) for model download instructions.

### Run Tests

```bash
cd apps/learner-mobile
./gradlew test
```

The test suite covers the AI pipeline, template engine, data encryption, content pack loading, semantic search, and UI view models (445 tests across 9 modules).

### Build a Content Pack

```bash
cd tools/content-pack-builder
pip install -r requirements.txt
python build_pack.py --content content/ --output ../../content-packs/my-pack-v1.0.pack
python validate_pack.py ../../content-packs/my-pack-v1.0.pack
```

The builder ingests Markdown curriculum chunks, generates embeddings (Gemma 4 768-dim by default, MiniLM 384-dim with `--embedding-model minilm`), builds a FAISS index, and packages everything into a versioned SQLite `.pack` file with SHA-256 integrity hashes.

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
| **Feature** — Gemma 4 Migration | Unified model (gen + embed), GPU delegation, content pack v2, semantic search | **Complete** (device validation pending) |
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

### Gemma 4 Migration Summary

The AI pipeline has been upgraded from the Phase 1 dual-model stack (Qwen2.5-1.5B + all-MiniLM-L6-v2) to a unified Gemma 4 1B model:

- **Unified model** — single Gemma 4 1B INT4 model (~600 MB) handles both embedding and generation, halving peak RAM
- **GPU-first inference** — LiteRT GPU delegate with NNAPI and CPU fallback chain (was CPU-only)
- **Semantic search** — new `ContentSearchEngine` enables search in the Topics browser without triggering LLM generation
- **Content pack v2** — packs re-embedded at 768-dim (was 384-dim); `PackVersionDetector` handles both schema versions
- **Legacy fallback** — old engines retained behind `useGemma` flag for rollback safety during device validation

See the [Feature PRD](docs/product/feature-gemma4-semantic-search.md) and [Gemma 4 evaluation research](docs/research/gemma4-model-evaluation-and-semantic-search.md) for full rationale.

> [!NOTE]
> The app runs with mock AI implementations (no native `.so` files required). Real on-device inference requires downloading the Gemma 4 model to the device.

### Storage Budget

| Component | Gemma 4 (primary) | Legacy |
|-----------|-------------------|--------|
| APK | ≤ 50 MB | ≤ 50 MB |
| Model | ~600 MB (Gemma 4 1B INT4, unified) | ~1,153 MB (LLM + embedding) |
| Content pack (per subject/grade) | ≤ 200 MB | ≤ 200 MB |
| **Total first-launch footprint** | **~850 MB** | **~1,403 MB** |

Peak RAM: ≤1,200 MB model footprint on 4 GB devices (Gemma 4 unified model).
Legacy path: ~554 MB working set with sequential model loading.

## Repository Structure

```
├── apps/
│   ├── learner-mobile/          Android app (Kotlin, Jetpack Compose, Material 3)
│   │   ├── app/                 Main application module (DI, navigation, theme)
│   │   ├── core/
│   │   │   ├── ai/              AI pipeline — ExplanationEngine, ContentSearchEngine, embeddings, LLM, templates
│   │   │   ├── common/          Shared utilities — Result type, dispatchers, storage
│   │   │   └── data/            Data layer — profiles, packs, chat history, encryption
│   │   └── feature/
│   │       ├── chat/            Chat screen, Markdown renderer, onboarding
│   │       ├── topics/          CAPS topic browser, semantic search, suggested questions
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
│   ├── adr/                     Architecture Decision Records (ADR 0001–0011)
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
| [0006](ejs-docs/adr/0006-qwen25-1.5b-as-on-device-llm.md) | Qwen2.5-1.5B as on-device LLM *(superseded by ADR-0012)* |
| [0007](ejs-docs/adr/0007-embedding-model-vector-store-storage-budget.md) | all-MiniLM-L6-v2 + FAISS for embedding and retrieval *(superseded by ADR-0012)* |
| [0008](ejs-docs/adr/0008-content-pack-sqlite-format.md) | SQLite `.pack` format with embedded FAISS index |
| [0009](ejs-docs/adr/0009-manual-dependency-injection.md) | Manual DI via AppContainer over Hilt/Koin |
| [0010](ejs-docs/adr/0010-aes256gcm-profile-encryption.md) | AES-256-GCM with Android Keystore for learner data |
| [0011](ejs-docs/adr/0011-jinja2-style-template-engine.md) | Custom Jinja2-style prompt template engine |
| [0012](ejs-docs/adr/0012-gemma4-unified-on-device-model.md) | Gemma 4 1B as unified on-device model (supersedes 0006 + 0007) |

| [0012](ejs-docs/adr/0012-gemma4-unified-on-device-model.md) | Gemma 4 1B as unified on-device model (supersedes 0006 + 0007) |

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
| [Feature PRD: Gemma 4](docs/product/feature-gemma4-semantic-search.md) | Gemma 4 migration and semantic search feature requirements |
| [Device Validation Checklist](docs/DEVICE-VALIDATION-CHECKLIST.md) | Real-device test checklist for Gemma 4 migration (25 items) |
| [Gemma 4 Evaluation](docs/research/gemma4-model-evaluation-and-semantic-search.md) | Model comparison, migration path, benchmark results |
| [Coding Principles](docs/development/coding-principles.md) | Offline-first patterns, resource budgets, performance rules |
| [Emulator Testing Runbook](docs/development/emulator-testing-runbook.md) | Step-by-step setup, sanity checks, and troubleshooting for Android emulator testing |
| [Sideload Testing Runbook](docs/development/sideload-testing-runbook.md) | Real-device APK install and validation guide for collaborator testing |
| [V1 Backlog](docs/development/backlog-v1.md) | Execution plan across 4 phases |
