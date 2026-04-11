# eZansiEdgeAI — Product Requirements Document (V1)

## 1. Overview

**Product Name:** eZansiEdgeAI  
**Summary:** An offline-first, phone-first AI learning assistant that provides CAPS-aligned Grade 6 Mathematics explanations to South African learners. The system runs entirely on low-end Android phones without internet, accounts, or subscriptions. An on-device language model explains curriculum content retrieved from verified content packs — it never invents answers. An optional school edge device provides speech services and content distribution but is never required.  
**Target Platform:** Android 10+ (API 29+), 4 GB RAM, ARMv8-A 64-bit phones; optional Linux edge device (Raspberry Pi 5 / refurbished laptop).  
**Key Constraints:** Fully offline after install, zero cost to learner/teacher/school, no accounts or tracking, all data stays on-device, ≤1.2 GB total first-launch storage (Gemma 4 1B ~600 MB + packs), ≤1.2 GB RAM working set (unified model), POPIA-compliant by design.

---

## 2. Version History

| Version | Date       | Author          | Changes                                    |
|---------|------------|-----------------|---------------------------------------------|
| 1.0     | 2026-03-14 | Copilot + Human | Initial PRD — consolidated from existing docs, ADRs, and spike results |
| 1.1     | 2026-04-11 | Copilot + Human | Phase 1 marked complete. AI stack updated: Gemma 4 1B replaces Qwen2.5+MiniLM (ADR-0012). Semantic search added. See feature-gemma4-semantic-search.md |

---

## 3. Goals and Non-Goals

### 3.1 Goals

- **Provide offline AI-powered maths explanations** grounded in verified CAPS curriculum content, accessible on low-end phones with no internet
- **Eliminate barriers to learning support:** zero cost, zero accounts, zero data usage, zero IT support required
- **Deliver retrieval-first explanations** that never hallucinate — the model explains what the content pack contains, nothing more
- **Support learner agency** through on-device preference-based personalisation (explanation style, reading level, example type) without surveillance or tracking
- **Enable teacher-driven distribution** via sideload (Bluetooth/USB/SD card) or optional school LAN — no app store required
- **Validate on-device AI feasibility** with real models, real content, and real device constraints (Phase 0 complete)
- **Ship a pilot-ready Android app** within 90 days covering Grade 6 Mathematics (all CAPS terms)

### 3.2 Non-Goals

- **Voice-first interface** — V1 is text-only; voice is a V2 edge enhancement
- **Cloud features** — no cloud sync, no remote analytics, no server-side processing
- **Teacher dashboards or analytics** — no performance tracking of any kind
- **Multi-subject support** — V1 is Grade 6 Mathematics only
- **Agent/reasoning chains** — no multi-step reasoning or autonomous agent behaviour
- **Gamification** — no badges, points, leaderboards, or streaks
- **Adaptive learning** — no algorithmic difficulty adjustment; learner chooses their level
- **Multi-language AI generation** — V1 English primary; Afrikaans stretch goal; content packs per language
- **App store distribution** — sideload is primary; Play Store is a future consideration

---

## 4. User Stories / Personas

### 4.1 Personas

| Persona | Description | Key Needs |
|---------|-------------|-----------|
| **Thandiwe** | 12 y/o Grade 6 learner, rural Eastern Cape. Mobicel Hero (4 GB RAM variant), cracked screen, no internet at home, walks 3 km to school. Class of 48, one teacher. | Understand fractions at her own pace using cooking examples. Zero data usage. Works offline. Low battery drain. |
| **Sipho** | 11 y/o Grade 6 learner, Khayelitsha township. Xiaomi Redmi 10C (4 GB RAM), shared with sibling. isiXhosa home language. Good at mental math, struggles with English word problems. | Switch to simple English explanations, visual examples. Separate profile on shared device. Practice data handling without data costs. |
| **Ms. Dlamini** | 34 y/o Grade 6 teacher, Soweto. Samsung Galaxy A14. 42 learners, no IT support. Sceptical of apps (past failures). | Install in <5 min, distribute via Bluetooth in a break, CAPS-aligned content she trusts, zero troubleshooting. |
| **Mr. Mokoena** | 51 y/o principal, rural Limpopo. Samsung Galaxy A04s. 380 learners, 12 teachers. No school internet, no IT budget. | Approve with confidence: no cost, no internet, no data collection, no IT dependency. Zero complaints. |
| **Dr. Ndaba** | 39 y/o curriculum specialist, PhD Mathematics Education. Laptop + iPhone. Strong pedagogy opinions. | Author content in Markdown, validate AI explanations for mathematical correctness, CAPS alignment, multiple representation pathways. |

### 4.2 User Stories

| ID | As a... | I want to... | So that... | Priority |
|----|---------|-------------|-----------|----------|
| US-01 | Learner (Thandiwe) | Ask a question about fractions and get an explanation immediately, offline | I can study at home without internet or data costs | Must |
| US-02 | Learner (Sipho) | Choose my explanation style (simple English, visual, step-by-step) | I can understand word problems in a way that works for me | Must |
| US-03 | Learner (Sipho) | Select my profile on a shared device without a password | I can pick up where I left off and my sibling has their own space | Must |
| US-04 | Learner (Thandiwe) | Browse topics by CAPS term and strand | I can find exactly the topic my teacher is covering in class | Should |
| US-05 | Teacher (Ms. Dlamini) | Install the app on my phone in under 5 minutes and transfer it to 5 learner phones via Bluetooth | I can distribute during a break without IT support | Must |
| US-06 | Teacher (Ms. Dlamini) | See that content matches CAPS structure exactly | I trust the app won't confuse my learners with wrong or off-curriculum content | Must |
| US-07 | Principal (Mr. Mokoena) | Verify the app needs no internet, no accounts, no cost, and collects no data | I can approve classroom use without budget or POPIA concerns | Must |
| US-08 | Content Creator (Dr. Ndaba) | Write curriculum content in Markdown, embed it, and produce a verified content pack | I can author and QA content without proprietary tools | Must |
| US-09 | Learner (Thandiwe) | Get explanations that use real-life examples (cooking, sharing, building) | I can connect maths to my daily experience | Should |
| US-10 | Teacher (Ms. Dlamini) | Update content packs over school WiFi when an edge node is available | Learners get updated content without me manually copying files | Should |
| US-11 | Learner (Sipho) | Tap "This helped" or "I'm confused" after an explanation | The app adjusts (locally) to give me step-by-step next time | Could |
| US-12 | Learner (Thandiwe) | Continue from where I was after my phone dies mid-session | I don't lose my place or chat history | Must |

---

## 5. Research Findings

### 5.1 On-Device LLM Selection (Spike P0-001)

Four candidate models were benchmarked across 12 CAPS-aligned Grade 6 Maths prompts × 3 runs each:

| Model | Size (disk) | Load Time | 150-token Gen | Peak RAM | Throughput | Verdict |
|-------|-------------|-----------|----------------|----------|------------|---------|
| **Qwen2.5-1.5B Q4_K_M** | 1,066 MB | 0.78 s | 8.0 s | 1,839 MB | 19.0 tok/s | **SELECTED** — only model passing all 3 criteria |
| SmolLM2-1.7B Q4_K_M | 1,018 MB | 0.77 s | 7.3 s | 1,761 MB | 20.7 tok/s | Backup — faster but lower quality on maths |
| Gemma-2-2B Q4_K_M | 1,521 MB | 1.07 s | 14.4 s | 2,402 MB | 10.4 tok/s | REJECTED — exceeds RAM budget |
| Phi-3.5-mini Q4_K_M | 2,176 MB | 1.42 s | 23.3 s | 3,275 MB | 6.4 tok/s | REJECTED — far exceeds all budgets |

**Decision:** Qwen2.5-1.5B-Instruct (Q4_K_M GGUF) via llama.cpp. See [ADR-0006](../../ejs-docs/adr/0006-qwen25-1.5b-as-on-device-llm.md).

### 5.2 Embedding Model & Vector Store (Spike P0-002)

Three embedding models × four vector stores tested across 20 retrieval queries:

| Embedding Model | Size | Embed Time | Accuracy (Top-3) | Selected |
|-----------------|------|------------|-------------------|----------|
| **all-MiniLM-L6-v2** | 87 MB | 10 ms | 100% (20/20) | **YES** |
| bge-small-en-v1.5 | 128 MB | 18 ms | 95% (19/20) | No |
| gte-small | 67 MB | 30 ms | 95% (19/20) | No |

**Vector store:** FAISS Flat (IndexFlatIP) — exact search, zero configuration, consistent accuracy. See [ADR-0007](../../ejs-docs/adr/0007-embedding-model-vector-store-storage-budget.md).

### 5.3 Content Pack Format (Spike P0-004 + ADR-0008)

SQLite chosen over ZIP for content packs. Four tables: `manifest`, `chunks`, `embeddings`, `faiss_indexes`. Validated with a 10-chunk fractions pack (76 KB, 100% retrieval accuracy). See [ADR-0008](../../ejs-docs/adr/0008-content-pack-sqlite-format.md).

### 5.4 End-to-End Pipeline (Spike P0-005)

Full pipeline validated: question → embed → retrieve top-3 chunks → build grounded prompt (Jinja2 template) → LLM inference → display. 5/5 test questions passed. Average latency 18.4 s on dev CPU (target <15 s on device with ARM NEON). Sequential model loading confirmed viable (embedding model unloaded before LLM loads).

### 5.5 Strategic Context

- **Data costs:** ~USD 5.50/GB in South Africa — cloud AI is not viable for target users
- **Load shedding:** 2–8 hour daily power outages — battery efficiency and crash resilience are critical
- **Device landscape:** Learners typically have 4 GB RAM Android phones (Samsung A04s, A05, Xiaomi Redmi 10C, Mobicel Hero variants)
- **Trust deficit:** Past ed-tech initiatives failed due to account walls, data requirements, incompatible devices, and abandoned projects

---

## 6. Concept

### 6.1 Core Loop / Workflow

```
┌─────────────────────────────────────────────────────┐
│                    LEARNER FLOW                      │
│                                                      │
│  1. Launch app (< 3s cold start)                     │
│  2. Select or create profile (name only, ≤ 2 taps)  │
│  3. Browse topics (CAPS term → strand → topic)       │
│     OR type a question directly                      │
│  4. AI pipeline executes locally:                    │
│     a. Embed question (all-MiniLM-L6-v2, ~10 ms)    │
│     b. Retrieve top-3 chunks from pack (FAISS)       │
│     c. Build grounded prompt (template + prefs)      │
│     d. Generate explanation (Qwen2.5-1.5B, < 8s)    │
│  5. Display formatted explanation                    │
│     (Markdown + inline math, worked examples)        │
│  6. Learner optionally taps feedback (helped/confused)│
│  7. Continue or browse another topic                 │
│                                                      │
│  All steps happen on-device. Zero network. Zero data.│
└─────────────────────────────────────────────────────┘
```

### 6.2 Teacher Distribution Flow

```
  Ms. Dlamini receives APK at workshop
       │
       ▼
  Installs on her phone (< 5 min)
       │
       ▼
  Shares via Bluetooth/USB to 5 learner phones (< 15 min)
       │
       ▼
  Tells learners: "Open app → Term 2 → Fractions"
       │
       ▼
  No accounts, no Wi-Fi, no follow-up needed
```

### 6.3 Content Creator Flow

```
  Dr. Ndaba writes Markdown chunk
       │
       ▼
  Tags with CAPS code, term, difficulty
       │
       ▼
  Runs build_pack.py (embeds + indexes + packages)
       │
       ▼
  Runs validate_pack.py (integrity + retrieval accuracy)
       │
       ▼
  Previews on emulator → reviews AI explanations
       │
       ▼
  Ships .pack file for distribution
```

### 6.4 Success / Completion Criteria

The product is "done" when:
- A learner can launch the app on a 4 GB RAM Android phone in airplane mode, ask a Grade 6 maths question, and receive a correct, grounded explanation in under 10 seconds
- A teacher can install and distribute the app to 5 phones in a single break period
- A principal can verify zero cost, zero internet, and zero data collection
- All Grade 6 CAPS Mathematics topics (Terms 1–4) are covered with educator-reviewed content

---

## 7. Technical Architecture

### 7.1 Technology Stack

| Component | Technology | Version / Format | Notes |
|-----------|-----------|------------------|-------|
| Mobile app | Kotlin + Android SDK | Min SDK 29 (Android 10) | No GMS dependency |
| LLM runtime | llama.cpp | GGUF format | CPU-only baseline, NPU optional |
| LLM model | Qwen2.5-1.5B-Instruct | Q4_K_M quantisation | 1,066 MB, 2048 context window |
| Embedding model | all-MiniLM-L6-v2 | Sentence Transformers / ONNX | 87 MB, 384-dim vectors |
| Vector search | FAISS Flat (IndexFlatIP) | Serialised in content pack | Exact cosine similarity |
| Content format | SQLite | `.pack` extension | 4 tables: manifest, chunks, embeddings, faiss_indexes |
| Prompt templates | Jinja2 | Data-driven, version-matched | Grounding enforcement, preference injection |
| Content builder | Python (build_pack.py) | CLI tool | Markdown → embedded → indexed → .pack |
| Content validator | Python (validate_pack.py) | CLI tool | SHA-256, retrieval accuracy, schema checks |
| Edge node (optional) | Linux (Ubuntu Server LTS / RPi OS) | Headless | mDNS, REST API, Whisper STT, Piper TTS |
| Edge discovery | mDNS | `_ezansi._tcp.local` | Passive, LAN-only |

### 7.2 Project Structure

```
ezansiedgeai-small/
├── apps/
│   ├── learner-mobile/          # Android Kotlin app (Phase 1)
│   └── school-edge-node/        # Edge server (Phase 3)
├── content-packs/               # Built .pack files
├── docs/
│   ├── architecture/            # System, phone, edge, deployment docs
│   ├── development/             # Backlog, coding principles, agent instructions
│   └── product/                 # Personas, constraints, metrics, this PRD
├── ejs-docs/
│   ├── adr/                     # Architecture Decision Records (0001–0008)
│   └── journey/                 # Session journey files
├── models/
│   ├── edge-models/             # Models for edge device
│   └── phone-models/            # Models for phone (downloaded at first launch)
├── scripts/                     # EJS tooling, git hooks
├── spikes/
│   ├── p0-001-llm-inference/    # LLM benchmark (COMPLETE)
│   ├── p0-002-embedding-retrieval/ # Embedding benchmark (COMPLETE)
│   └── p0-005-e2e-pipeline/     # E2E smoke test (COMPLETE)
└── tools/
    ├── content-pack-builder/    # build_pack.py, validate_pack.py
    └── dataset-tools/           # Language/content tooling (placeholder)
```

### 7.3 Phone Architecture (4 Layers)

```
┌──────────────────────────────────────────────────┐
│  APP LAYER                                        │
│  Learning UI · Chat Interface · Topic Browser     │
│  Content Library · Profile Manager · Voice (V2)   │
├──────────────────────────────────────────────────┤
│  AI LAYER                                         │
│  ExplanationEngine service (single entry point)   │
│  Embed → Retrieve → Prompt Build → Generate       │
├──────────────────────────────────────────────────┤
│  DATA LAYER                                       │
│  SQLite Content Packs · Learner Profile (AES-256) │
│  Vector DB (sqlite-vec) · Prompt Templates        │
├──────────────────────────────────────────────────┤
│  HARDWARE LAYER                                   │
│  CPU Inference (llama.cpp / ONNX + NEON)          │
│  NPU Delegate (NNAPI, optional) · mmap loading    │
│  Battery/Thermal Guards · Storage Abstraction      │
└──────────────────────────────────────────────────┘
```

**Boundary rules:**
- App Layer talks to AI Layer via `ExplanationEngine` only — no direct model/DB access
- AI Layer talks to Data Layer via repository interfaces — no raw file reads
- Data Layer owns all persistence, integrity verification, and encryption
- Hardware Layer manages CPU/NPU/thermal/battery abstractions

### 7.4 Edge Node Architecture (Optional, Phase 3)

```
┌──────────────────────────────────────────────────┐
│  WIFI SERVICE LAYER                               │
│  mDNS Advertiser · REST API (port 8080)           │
│  Content Distribution · Update Server             │
├──────────────────────────────────────────────────┤
│  AI SERVICES                                      │
│  Whisper STT (small/base) · Piper TTS             │
│  Optional Larger LLM (7B class, V2)               │
├──────────────────────────────────────────────────┤
│  DATA SERVICES                                    │
│  Content Pack Cache · Pack Version Management     │
├──────────────────────────────────────────────────┤
│  MANAGEMENT LAYER                                 │
│  Setup Script · Health Monitor · Watchdog         │
└──────────────────────────────────────────────────┘
```

**API endpoints:** `/capabilities`, `/content/packs`, `/stt`, `/tts`, `/infer` (V2), `/health`

### 7.5 Deployment Modes

| Capability | Mode A: Phone Only | Mode B: Phone + School Edge | Mode C: Community Hub |
|-----------|-------------------|----------------------------|----------------------|
| Text chat | ✅ | ✅ | ✅ |
| Content retrieval | ✅ | ✅ | ✅ |
| Worked examples | ✅ | ✅ | ✅ |
| Learner preferences | ✅ | ✅ | ✅ |
| Voice input (STT) | ❌ | ✅ (via edge) | ✅ (via hub) |
| Voice output (TTS) | ❌ | ✅ (via edge) | ✅ (via hub) |
| Content updates | ❌ (sideload only) | ✅ (LAN sync) | ✅ (LAN sync) |
| Network required | None | School WiFi | Hub creates WiFi AP |

**Degradation:** All modes silently degrade to Mode A if network/edge becomes unavailable. Mode A is the baseline that must always work.

---

## 8. Functional Requirements

### 8.1 Core AI Pipeline

| ID | Requirement | Priority |
|----|-------------|----------|
| AI-01 | Embed learner question using all-MiniLM-L6-v2 on-device (~10 ms) | Must |
| AI-02 | Retrieve top-3 relevant content chunks from local FAISS index via cosine similarity | Must |
| AI-03 | Construct grounded prompt using Jinja2 template: system prompt + retrieved chunks + learner preferences + question | Must |
| AI-04 | Generate explanation using Qwen2.5-1.5B via llama.cpp (≤150 tokens, temperature=0.3, 2048 context) | Must |
| AI-05 | Display formatted explanation with Markdown rendering and inline math notation | Must |
| AI-06 | Include worked example in explanation when relevant retrieved chunk contains one | Should |
| AI-07 | Reference source content chunk in explanation (attribution) | Should |
| AI-08 | Sequential model loading: unload embedding model before loading LLM to fit within RAM budget | Must |
| AI-09 | Enforce 30-second wall-time cap on inference; display graceful timeout message if exceeded | Must |
| AI-10 | LLM must never generate content beyond what is in the retrieved chunks (grounding enforcement) | Must |

### 8.2 Chat Interface

| ID | Requirement | Priority |
|----|-------------|----------|
| CH-01 | Text input field with send button; submit on keyboard "done" action | Must |
| CH-02 | Scrollable chat history displaying learner questions and AI explanations | Must |
| CH-03 | Markdown rendering in chat bubbles (headings, bold, lists, code blocks) | Must |
| CH-04 | Inline math rendering (LaTeX-lite, native — not WebView) | Must |
| CH-05 | Chat history persisted locally; survives app kill and phone restart | Must |
| CH-06 | Usable on 720p (5-inch) screens with cracked-screen-friendly 48×48 dp touch targets | Must |
| CH-07 | Loading indicator during inference with elapsed time | Should |
| CH-08 | Copy explanation text to clipboard | Could |

### 8.3 Content Pack Management

| ID | Requirement | Priority |
|----|-------------|----------|
| CP-01 | Load content pack (.pack SQLite file) from device storage | Must |
| CP-02 | Verify pack integrity via SHA-256 manifest checksums before loading | Must |
| CP-03 | Reject partial or corrupted packs — full or absent, no partial installs | Must |
| CP-04 | Query pack metadata (subject, grade, version, term coverage, chunk count) | Must |
| CP-05 | Support multiple installed packs (one active per subject/grade) | Must |
| CP-06 | Content library UI listing installed packs with version, size, and topic coverage | Should |
| CP-07 | Delete pack and reclaim storage | Should |
| CP-08 | Delta pack updates (<10 MB differential) with clean apply and rollback on failure | Should |
| CP-09 | Handle zero-pack state gracefully (prompt user to install a pack) | Must |

### 8.4 Learner Profile

| ID | Requirement | Priority |
|----|-------------|----------|
| LP-01 | Name-based profile selection (no password, no account) — ≤2 taps from launch | Must |
| LP-02 | Support multiple profiles on shared device (Sipho and sibling scenario) | Must |
| LP-03 | Profile stores preferences only: explanation style, example type, reading level, last topic | Must |
| LP-04 | Profile encrypted with AES-256-GCM via Android Keystore | Must |
| LP-05 | Learner can edit or delete their own profile | Must |
| LP-06 | No performance data, no usage tracking, no behaviour analytics stored | Must |
| LP-07 | Profile data never transmitted — stays on-device | Must |

### 8.5 Topic Browser

| ID | Requirement | Priority |
|----|-------------|----------|
| TB-01 | Browse topics following CAPS structure: Term → Strand → Topic | Should |
| TB-02 | Tap a topic to see related content and ask contextual questions | Should |
| TB-03 | Topics populated from installed content pack metadata | Should |
| TB-04 | Show coverage indicators (which topics have content) | Could |

### 8.6 Learner Preference Engine

| ID | Requirement | Priority |
|----|-------------|----------|
| PE-01 | Choose explanation style: step-by-step, conceptual, real-world examples | Must |
| PE-02 | Choose example type: everyday (cooking, sharing), visual (diagrams, number lines), procedural | Must |
| PE-03 | Choose reading level: basic (Grade 4 language) or intermediate | Must |
| PE-04 | Preferences injected into prompt template to personalise explanations | Must |
| PE-05 | Preferences accessible ≤2 taps from any screen | Must |
| PE-06 | Explanation quality feedback ("This helped" / "I'm confused"), stored locally | Could |
| PE-07 | After 3+ "confused" responses, auto-switch to step-by-step style | Could |

### 8.7 Prompt Template Engine

| ID | Requirement | Priority |
|----|-------------|----------|
| PT-01 | Data-driven prompt templates (Jinja2), version-matched to content packs | Should |
| PT-02 | Inject learner preferences (style, reading level, example type) into prompt | Should |
| PT-03 | Enforce grounding: system prompt instructs model to explain only retrieved content | Must |
| PT-04 | Fit within 2048-token context window (system + chunks + question + generation budget) | Must |

### 8.8 Edge Node Discovery & Sync (Phase 3)

| ID | Requirement | Priority |
|----|-------------|----------|
| ED-01 | Passive mDNS discovery of `_ezansi._tcp.local` edge nodes on LAN (5 s timeout) | Must |
| ED-02 | Query edge `/capabilities` endpoint to determine available services | Must |
| ED-03 | Download content pack updates from edge via REST with resumable transfer | Must |
| ED-04 | Verify downloaded pack integrity (SHA-256) before installing | Must |
| ED-05 | Silently degrade to Mode A if edge unreachable — no error dialogs | Must |
| ED-06 | Edge content distribution server: REST API, range requests, ≤500 MB RAM, mDNS advertise | Should |

### 8.9 Content Pack Builder (CLI Tool)

| ID | Requirement | Priority |
|----|-------------|----------|
| PB-01 | Accept Markdown chunks as input (one file per chunk, tagged with CAPS topic, term, difficulty) | Must |
| PB-02 | Compute embeddings using all-MiniLM-L6-v2 | Must |
| PB-03 | Build FAISS Flat index from embeddings | Must |
| PB-04 | Package into SQLite .pack file with manifest, chunks, embeddings, faiss_indexes tables | Must |
| PB-05 | Generate SHA-256 checksums for all chunks and include in manifest | Must |
| PB-06 | Validate output pack (schema, integrity, retrieval accuracy) via validate_pack.py | Must |
| PB-07 | Run entirely offline — no network calls during build | Must |

### 8.10 App Lifecycle & Resilience

| ID | Requirement | Priority |
|----|-------------|----------|
| LC-01 | Auto-persist every interaction — no "save" button; survive abrupt kills | Must |
| LC-02 | Resume from last state after phone restart or app kill (mid-inference, mid-pack-install, mid-profile-write) | Must |
| LC-03 | No data loss on crash during LLM inference, pack installation, or profile write | Must |
| LC-04 | Doze-compatible, app-standby-compatible — no wake-locks or background services | Must |
| LC-05 | Cold start < 3 seconds | Must |

---

## 9. Non-Functional Requirements

| ID | Requirement | Priority |
|----|-------------|----------|
| NF-01 | End-to-end answer latency < 10 seconds on target device (< 8 s generation + retrieval overhead) | Must |
| NF-02 | RAM working set ≤ 2 GB (keep ≥ 1 GB free for OS on 4 GB device) | Must |
| NF-03 | Battery drain < 3% per 30-minute session | Must |
| NF-04 | UI frame rendering < 16 ms (60 fps) | Must |
| NF-05 | APK size ≤ 50 MB (models downloaded separately on first launch) | Must |
| NF-06 | Total first-launch download ≤ 1,403 MB (APK + LLM + embedding model + one content pack) | Must |
| NF-07 | Content pack size ≤ 200 MB per subject/grade | Must |
| NF-08 | No ANR (Application Not Responding) in 100-hour soak test | Must |
| NF-09 | Support adoptable storage (SD card) for model and pack storage | Should |
| NF-10 | Survive Bluetooth file transfer without corruption (APK integrity) | Must |
| NF-11 | Compatible with Android 10 through 15, including Huawei devices without GMS | Must |
| NF-12 | No thermal throttling during normal use (throttle token generation if temperature exceeds threshold) | Must |

---

## 10. Security and Privacy

| ID | Requirement | Priority |
|----|-------------|----------|
| SP-01 | **All learner data stays on-device.** Learner profile, chat history, preferences, and feedback never transmitted | Must |
| SP-02 | Zero runtime network calls — no DNS lookups, no TLS connections, no HTTP requests, no analytics, no crash reporting | Must |
| SP-03 | No user accounts, no authentication, no sign-up flows | Must |
| SP-04 | No dangerous Android permissions requested (no camera, microphone, location, contacts) | Must |
| SP-05 | Learner profile encrypted with AES-256-GCM via Android Keystore | Must |
| SP-06 | Content pack integrity verified via SHA-256 checksums before loading | Must |
| SP-07 | No analytics SDKs in dependency tree (not even transitive) | Must |
| SP-08 | Structured logging must never include learner questions, answers, or profile content | Must |
| SP-09 | POPIA-compliant by design — no PII collected, processed, or transmitted | Must |
| SP-10 | No parental consent required (no data collected that would trigger consent obligations) | Must |
| SP-11 | Content packs cryptographically signed; reject packs with invalid signatures | Should |
| SP-12 | Dependencies must use permissive licenses only (Apache 2.0, MIT, BSD) — no GPL | Must |

---

## 11. Accessibility

| ID | Requirement | Priority |
|----|-------------|----------|
| ACC-01 | WCAG 2.1 AA colour contrast (minimum 4.5:1 ratio) | Must |
| ACC-02 | Minimum 48×48 dp touch targets for all interactive elements (cracked-screen reliability) | Must |
| ACC-03 | System font-size scaling respected — no hardcoded sp overrides | Must |
| ACC-04 | Screen reader (TalkBack) support with logical navigation order and content descriptions | Must |
| ACC-05 | Sunlight-readable UI — high contrast palette usable in bright outdoor conditions | Should |
| ACC-06 | No purely decorative animations; functional animations only | Should |
| ACC-07 | Grade 4 reading level for all system UI text | Must |
| ACC-08 | Density-appropriate images and assets (no oversized resources for 720p screens) | Should |

---

## 12. User Interface / Interaction Design

### 12.1 Design Principles

- **Maximum 2 taps to any content** from launch
- **Instant perceived response** — UI responds < 200 ms, inference shows loading state
- **High contrast, sunlight-readable palette** — no dark patterns or decorative flourishes
- **Cracked-screen friendly** — large touch targets, generous spacing
- **No modals, no permission dialogs, no onboarding walls** — optional dismissible tooltips only

### 12.2 Key Screens

| Screen | Purpose | Key Elements |
|--------|---------|--------------|
| Profile Select | Choose or create learner profile | Name list, "Add" button, ≤2 taps |
| Home / Topic Browser | Browse CAPS topics by term/strand | Hierarchical list, content coverage indicators |
| Chat | Ask questions and receive explanations | Text input, scrollable history, Markdown+math rendering |
| Preferences | Set explanation style, reading level, examples | Simple toggles/radio buttons, ≤2 taps to access |
| Content Library | Manage installed packs | Pack list with version, size, delete option |

### 12.3 Content Rendering

- Native Android views for all content (no WebView)
- LaTeX-lite math rendering for inline mathematical notation
- RecyclerView for all scrollable lists (virtualised)
- Markdown rendering: headings, bold, lists, inline code, code blocks

---

## 13. System States / Lifecycle

```
                    ┌─────────────┐
                    │  FIRST RUN  │
                    │  (no packs) │
                    └──────┬──────┘
                           │ Install content pack
                           ▼
┌──────────┐    ┌─────────────────┐    ┌──────────────┐
│  LAUNCH  │───▶│  PROFILE SELECT │───▶│   HOME /     │
│  (< 3s)  │    │  (≤2 taps)      │    │  TOPIC BROWSE│
└──────────┘    └─────────────────┘    └──────┬───────┘
                                              │
                              ┌────────────────┼────────────────┐
                              ▼                ▼                ▼
                     ┌──────────────┐  ┌─────────────┐  ┌────────────┐
                     │    CHAT      │  │ PREFERENCES  │  │  CONTENT   │
                     │ (ask + wait) │  │ (edit style) │  │  LIBRARY   │
                     └──────┬───────┘  └─────────────┘  └────────────┘
                            │
                ┌───────────┼───────────┐
                ▼           ▼           ▼
         ┌───────────┐ ┌────────┐ ┌──────────┐
         │ EMBEDDING │ │RETRIEVE│ │ GENERATE │
         │ (~10 ms)  │ │(FAISS) │ │ (< 8s)   │
         └───────────┘ └────────┘ └──────┬───┘
                                         │
                                         ▼
                                  ┌─────────────┐
                                  │  DISPLAY     │
                                  │  EXPLANATION │
                                  └─────────────┘

         ┌──────────────────────────────────┐
         │  EDGE AVAILABLE (optional)       │
         │  mDNS discovered → capabilities  │
         │  → content sync / STT / TTS      │
         │  → degrades silently if lost     │
         └──────────────────────────────────┘
```

**Kill/crash recovery:** All state auto-persisted. App relaunches to last screen. In-flight inference discarded (learner re-asks). Pack installs are atomic (full or rollback).

---

## 14. Implementation Phases

### Phase 0: Feasibility Validation (Weeks 1–2) ✅ COMPLETE

- [x] P0-001: LLM inference spike — Qwen2.5-1.5B selected ([report](../../spikes/p0-001-llm-inference/reports/spike-report.md))
- [x] P0-002: Embedding + retrieval spike — all-MiniLM-L6-v2 + FAISS Flat selected
- [x] P0-004: Sample content pack — 10 fractions chunks, CAPS Term 1, 76 KB, 100% retrieval accuracy
- [x] P0-005: E2E pipeline smoke test — 5/5 passed, 18.4 s avg on dev CPU
- [ ] P0-003: Battery & thermal validation — deferred to Phase 3 (requires real hardware)

### Phase 1: Offline Learning Loop (Weeks 3–6) ✅ COMPLETE

- [x] P0-101: Android app scaffold (Kotlin, min SDK 29, no GMS, sideload-ready APK)
- [x] P0-102: Chat interface (text I/O, Markdown+math rendering, history persistence, 720p, 48×48 dp)
- [x] P0-103: Content pack loader (verify SHA-256 manifest, error handling, query metadata)
- [x] P0-104: Retrieval + explanation pipeline integration (embed → retrieve → prompt → generate → display, < 10 s)
- [x] P0-105: Learner profile — basic (name selection, encrypted, multi-profile, ≤2 taps)
- [x] P1-106: Topic browser (CAPS structure navigation, contextual questions, zero-pack state)
- [x] P1-107: Prompt template engine (data-driven Jinja2, preference injection, grounding enforcement, context-window fit)
- [x] P2-108: Onboarding flow (zero-step, optional dismissible tooltip, no modals/permissions)

> **Gemma 4 Migration (post-Phase 1):** The AI model stack was migrated from Qwen2.5-1.5B + all-MiniLM-L6-v2 to Gemma 4 1B (unified model for generation + embedding) via MediaPipe GenAI SDK. User-facing semantic search was added. See [Feature PRD](feature-gemma4-semantic-search.md) and [ADR-0012](../../ejs-docs/adr/0012-gemma4-unified-on-device-model.md).

### Phase 2: Content + Personalisation (Weeks 7–10)

- [ ] P0-201: Learner preference engine (explanation style, example type, reading level, ≤2 taps)
- [ ] P0-202: Content pack builder CLI (Markdown → embed → index → .pack, offline, validated)
- [ ] P0-203: Full Grade 6 Maths content pack (all CAPS T1–T4, ≥3 chunks + ≥2 examples per topic, educator-reviewed, < 200 MB)
- [ ] P1-204: Delta pack updates (< 10 MB differential, clean apply, rollback on fail)
- [ ] P1-205: Content library management UI (list packs, delete, update, storage display)
- [ ] P2-206: Explanation quality feedback ("helped" / "confused", local storage, auto-adjust)

### Phase 3: School Node + Hardening (Weeks 11–13)

- [ ] P0-301: LAN edge node discovery (mDNS `_ezansi._tcp.local`, 5 s timeout, passive, LAN-only)
- [ ] P0-302: Content pack sync over LAN (query versions, download, verify, resumable, interrupt-safe)
- [ ] P0-303: Battery & thermal testing (real devices, < 3% drain, no throttling, documented results)
- [ ] P0-304: Crash recovery testing (kill during inference, mid-pack-install, mid-profile-write — no data loss)
- [ ] P1-305: Edge content distribution server (REST, range requests, < 500 MB RAM, mDNS, no internet required)
- [ ] P1-306: Sideload installation guide (in-app, step-by-step, Bluetooth/WiFi Direct/SD card, illustrated)
- [ ] P1-307: Release build hardening (ProGuard/R8, < 150 MB installed, license audit, zero dangerous permissions, signed, no debug code)
- [ ] P2-308: Teacher quick-start card (A4 PDF, printable B&W, one-pager, embedded in APK)

---

## 15. Testing Strategy

| Level | Scope | Tools / Approach | Key Scenarios |
|-------|-------|------------------|---------------|
| Unit Tests | Business logic: prompt construction, retrieval, pack parsing, profile encryption, preference injection | JUnit 5 / Kotlin Test | ≥80% coverage of business logic |
| Integration Tests | Embed → retrieve → prompt → generate (mocked LLM), pack install + load, profile CRUD | AndroidX Test / Robolectric | Full pipeline with mocked inference |
| Device Tests | Critical user journeys on target hardware | Espresso on low-spec emulator (2 GB RAM, 720p, API 29) | Ask question → get answer; install pack; switch profile; crash recovery |
| Performance | Latency, memory, battery, cold start | Android Benchmark, profiling tools | Cold start < 3 s, inference < 8 s, RAM < 2 GB, battery < 3% / 30 min |
| Content Validation | Pack integrity and coverage | validate_pack.py (custom) | Chunk size limits, embedding alignment, manifest SHA-256, CAPS coverage completeness, retrieval accuracy |
| Cross-Platform | Android 10–15 compatibility, Huawei (no GMS) | Manual + automated matrix | Sideload install, inference, pack loading, profile on each target API level |

### Key Test Scenarios

1. Ask fractions question in airplane mode → receive correct explanation < 10 s
2. Kill app during LLM inference → relaunch → no data loss, can re-ask
3. Install content pack → verify SHA-256 → load → query metadata → retrieve chunks
4. Create profile → set preferences → ask question → preferences reflected in explanation
5. Share APK via Bluetooth → install on second phone → full functionality
6. Run 100-hour soak test → zero ANR
7. Run on 4 GB RAM emulator with 720p screen → all UI elements usable
8. Install corrupt .pack file → graceful rejection with user-friendly message
9. Discover edge node → sync content pack → lose WiFi → silent degradation to Mode A
10. Multiple profiles on shared device → each profile maintains separate preferences and history

**Non-negotiable test rules:**
- No test requires network access
- Device tests must pass on low-spec emulator (2 GB RAM, 720p, API 29)
- Performance regressions > 500 ms cold start or > 2 s inference block merge
- Pack validation tests run in CI

---

## 16. Analytics / Success Metrics

**No telemetry is collected.** Success is measured through automated tests, manual QA on low-spec devices, and pilot observation.

### Technical Success

| Metric | Target | Measurement Method |
|--------|--------|--------------------|
| Offline functionality | 100% features work in airplane mode | Automated test: airplane mode soak |
| Cold start time | < 3 seconds | Android Benchmark on target emulator |
| End-to-end answer latency | < 10 seconds on target device | Benchmark suite on low-spec emulator |
| Memory usage (RSS) | < 180 MB (app only, excluding model mmap) | Android profiler during test |
| Battery drain | < 3% per 30 min session | Manual test on real device |
| No ANR | Zero in 100-hour soak | Android vitals monitoring (local) |
| CAPS coverage | 100% of Grade 6 Maths topics T1–T4 | Content pack validation script |
| Content correctness | 100% explanations mathematically correct | Educator review (Dr. Ndaba persona) |

### User Experience Success

| Metric | Target | Measurement Method |
|--------|--------|--------------------|
| Taps to content | ≤ 2 from launch | Manual walkthrough on all user paths |
| Install time (teacher) | < 5 min on first phone | Timed manual test |
| Distribution time | < 15 min to 5 phones via Bluetooth | Timed manual test |
| Grade-level readability | Grade 4 reading level for UI text | Readability analysis of all strings |
| Accessibility | WCAG 2.1 AA | Automated contrast checks + TalkBack walkthrough |

### Pilot Success (Post-Launch)

| Metric | Target | Measurement Method |
|--------|--------|--------------------|
| Voluntary usage | ≥ 50% of pilot learners use outside class | Teacher observation journals |
| Teacher referral | ≥ 1 teacher refers to another teacher | Pilot feedback |
| Privacy complaints | Zero | Pilot feedback |
| Learner comprehension | Learners can explain what the app does | Informal learner interviews |
| Retention (4 weeks) | ≥ 80% still using at 4 weeks | Teacher observation |

### What Success Is NOT

These are explicitly anti-metrics — optimising for them would harm the product:
- DAU / time spent in app (engagement ≠ learning)
- Test score improvement (correlation ≠ causation without control group)
- Social sharing / viral coefficient
- App store ratings
- Revenue / monetisation
- Number of schools reached (in V1)

---

## 17. Acceptance Criteria

1. App launches and is fully functional on a 4 GB RAM Android 10 phone in airplane mode
2. Learner can ask a Grade 6 maths question and receive a grounded, mathematically correct explanation in < 10 seconds
3. All Grade 6 CAPS Mathematics topics (Terms 1–4) are covered in the content pack with educator-reviewed content
4. Explanations are personalised based on learner-chosen preferences (style, reading level, examples)
5. Multiple learner profiles work on a single shared device with encrypted, isolated data
6. Teacher can install APK and distribute to 5 phones via Bluetooth in < 15 minutes without IT support
7. App requests zero dangerous Android permissions
8. No network calls made at runtime — verified by test
9. Learner data (profile, chat history, preferences) is encrypted on-device and never transmitted
10. App survives abrupt kill during any operation (inference, pack install, profile write) without data loss
11. Content packs are SHA-256 verified before loading; corrupted packs are rejected with clear message
12. UI meets WCAG 2.1 AA contrast requirements with 48×48 dp minimum touch targets
13. Cold start < 3 seconds, no ANR in 100-hour soak test
14. Battery drain < 3% per 30-minute session
15. APK size ≤ 50 MB; total first-launch storage ≤ 1.4 GB
16. All dependencies use permissive licenses (Apache 2.0 / MIT / BSD)
17. Content pack builder produces valid .pack files from Markdown input, fully offline

---

## 18. Dependencies and Risks

### 18.1 Dependencies

| Dependency | Type | Purpose | Risk if Unavailable | Mitigation |
|------------|------|---------|---------------------|------------|
| llama.cpp | C++ library (MIT) | LLM inference runtime | No on-device inference | Core dependency — no alternative at this weight class; pre-validate per release |
| Qwen2.5-1.5B-Instruct (Q4_K_M) | Model weights (Apache 2.0) | On-device LLM | No explanations | SmolLM2-1.7B as validated backup (ADR-0006) |
| all-MiniLM-L6-v2 | Model (Apache 2.0) | Embedding for retrieval | No vector search | bge-small-en-v1.5 as tested alternative (ADR-0007) |
| FAISS | C++ library (MIT) | Vector similarity search | No retrieval | sqlite-vec as simpler fallback |
| sentence-transformers | Python (Apache 2.0) | Embedding computation in pack builder | Can't build content packs | ONNX Runtime as alternative inference path |
| Jinja2 | Python (BSD) | Prompt template rendering | No dynamic prompts | Simple string formatting fallback |
| Android SDK (API 29+) | Platform | Target runtime | — | Core platform — non-negotiable |
| Kotlin | Language (Apache 2.0) | App development language | — | Core language — non-negotiable |
| SQLite | Library (public domain) | Content pack format + local storage | — | Built into Android — always available |

### 18.2 Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| LLM generates incorrect maths explanations | Medium | High — learner trusts wrong content | Retrieval-first grounding, educator content review, system prompt constraints, pre-validated test suite |
| 4 GB RAM phones can't run model reliably | Low | High — core functionality breaks | Validated in P0-001 spike (1,839 MB peak); sequential model loading; SmolLM2 backup uses less RAM |
| Content pack quality insufficient for CAPS coverage | Medium | High — incomplete curriculum | Structured authoring templates, content specialist review (Dr. Ndaba persona), validation tooling |
| Sideload distribution friction too high | Medium | Medium — adoption barrier | Step-by-step guide, teacher training card, Bluetooth/USB/SD card options |
| Android fragmentation causes device-specific issues | Medium | Medium — broken on some phones | Test matrix across API 29–35, Huawei (no GMS), low-RAM emulators |
| llama.cpp ARM NEON performance insufficient on low-end CPUs | Low | High — latency exceeds budget | Validated in spike; fallback: reduce max_tokens, simplify prompts |
| Load shedding kills phone during inference | High | Low — inference discarded, no data loss | Auto-persist all state, atomic transactions, crash recovery tested |
| Teacher scepticism prevents adoption | Medium | Medium — no pilot uptake | Zero-friction install, CAPS alignment visible, no cost/accounts, teacher reference card |
| Model weights download (1.1 GB) inaccessible to learners | High | High — app non-functional | Pre-load models before distribution; sideload models via USB/SD; teacher pre-loads at workshop |

---

## 19. Future Considerations

| Item | Description | Potential Version |
|------|-------------|-------------------|
| Voice interface | STT input + TTS output via edge device (Whisper + Piper) | V2 |
| Additional subjects | Natural Sciences, English FAL — new content packs per subject | V2 |
| Additional grades | Grade 4–5, Grade 7 — new content packs per grade | V2 |
| African language support | isiZulu, isiXhosa, Sesotho content packs + multilingual prompts | V2 |
| Larger edge LLM | 7B-class model offload to edge device for complex questions | V2 |
| Adaptive difficulty | Automatic difficulty adjustment based on learner feedback patterns | V2+ |
| Teacher dashboard | Read-only aggregate view (opt-in, privacy-preserving) | V3 |
| Community Hub mode | Battery-backed devices at libraries/NGOs creating WiFi AP | V2 |
| Play Store distribution | Play Store listing + in-app update mechanism | V2 |
| NPU acceleration | NNAPI delegate for supported devices (faster inference) | V2 |
| Multi-modal content | Diagrams, SVG illustrations, interactive visual examples | V2 |
| Peer study groups | Local WiFi-based collaborative features (no internet) | V3 |

---

## 20. Open Questions

| # | Question | Default Assumption |
|---|----------|--------------------|
| 1 | How will models (1.1 GB) be distributed to learners' phones? Sideload only, or pre-loaded by teacher? | Teacher pre-loads at distribution workshop; sideload via USB/SD card |
| 2 | What is the minimum number of CAPS topic chunks needed for a viable pilot? | ≥3 chunks + ≥2 worked examples per topic across all 4 terms |
| 3 | Should Afrikaans be included in V1 or deferred to V2? | Deferred to V2 (separate content pack); V1 is English only |
| 4 | What is the target pilot size (number of learners, schools, duration)? | 1 school, 20–40 learners, 1 teacher, 4-week duration |
| 5 | How will content quality be assured at scale (beyond Dr. Ndaba persona)? | V1: single reviewer; V2: review workflow with multiple curriculum specialists |
| 6 | Should the app support Android Go edition (ultra-low-end devices)? | Not in V1 — Android Go typically has <3 GB RAM, below minimum |
| 7 | What happens when models update (new Qwen version)? Re-benchmark required? | Yes — ADR-0006 states no model switch without full benchmark re-run. **Update:** Model switched to Gemma 4 1B (ADR-0012 supersedes ADR-0006). Full spike + benchmark was run (P0-006). |
| 8 | Is llama.cpp the right runtime for Android, or should ONNX Runtime be primary? | llama.cpp for V1 (validated); re-evaluate for V2 if ONNX performance improves on ARM. **Update:** Both replaced by MediaPipe GenAI SDK / LiteRT (ADR-0012). Legacy deps deprecated. |
| 9 | How will the project handle content for topics where explanations require visual diagrams? | V1: text + inline math only; V2: SVG/image support in content packs |
| 10 | What is the sustainability model post-V1 (funding, maintenance, content updates)? | Deferred — V1 validates feasibility; sustainability plan required before V2 |

---

## 21. Glossary

| Term | Definition |
|------|------------|
| **CAPS** | Curriculum and Assessment Policy Statement — South Africa's national curriculum framework |
| **Content Pack** | A versioned SQLite file (.pack) containing curriculum chunks, pre-computed embeddings, and a FAISS index for a specific subject, grade, and language |
| **Chunk** | A self-contained piece of curriculum content (≤2,000 tokens) covering a specific topic, tagged with CAPS metadata |
| **Edge Device / Edge Node** | An optional school-side device (Raspberry Pi or laptop) providing content distribution, STT/TTS, and optional heavier inference over local WiFi |
| **Grounded Prompt** | A prompt constructed from retrieved content chunks + learner preferences + the learner's question, ensuring the LLM only explains verified material |
| **FAISS** | Facebook AI Similarity Search — library for efficient vector similarity search, used for content retrieval |
| **GGUF** | A model file format used by llama.cpp for quantised LLM inference |
| **llama.cpp** | An open-source C++ library for running quantised LLMs on CPU (and optionally GPU/NPU) |
| **Load Shedding** | Scheduled power outages in South Africa due to electricity supply constraints, lasting 2–8 hours |
| **mDNS** | Multicast DNS — a zero-configuration service discovery protocol for local networks |
| **Mode A** | Phone-only deployment — no network, all features work standalone |
| **Mode B** | Phone + school edge WiFi — adds voice services and content sync |
| **Mode C** | Community hub — edge device creates its own WiFi access point |
| **POPIA** | Protection of Personal Information Act — South Africa's data protection legislation |
| **Q4_K_M** | A GGUF quantisation level that reduces model size by ~4x with minimal quality loss |
| **Retrieval-First** | Architecture where the LLM explains information retrieved from a curated knowledge base rather than generating from its own weights |
| **Sideload** | Installing an APK without using an app store — via Bluetooth, USB, SD card, or WiFi Direct |
| **sqlite-vec** | A SQLite extension providing vector similarity search capabilities |
| **STT** | Speech-to-Text — converting spoken audio to text (e.g., via Whisper) |
| **TTS** | Text-to-Speech — converting text to spoken audio (e.g., via Piper) |

---

*This PRD consolidates the project's vision, architecture decisions (ADR-0001 through ADR-0008), Phase 0 spike results, coding principles, and product documentation into a single authoritative reference. It is designed to be self-contained: a developer, AI agent, or stakeholder should understand the full scope of V1 from this document alone.*
