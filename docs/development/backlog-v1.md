# V1 Backlog — eZansiEdgeAI

> Grade 6 Mathematics (CAPS), offline-first, phone-first.
> 90-day execution plan across 4 phases.

---

## Prioritisation Guidance

### Priority Levels

| Priority | Meaning | Rule |
|----------|---------|------|
| **P0 — Must Have** | V1 does not ship without this. | Cannot be descoped. If blocked, escalate immediately. |
| **P1 — Should Have** | Significantly improves the V1 experience. | Can be deferred to a fast-follow release if time-constrained. |
| **P2 — Nice to Have** | Polishes the experience but V1 is viable without it. | Only built if P0 and P1 are complete and tested. |

### Sequencing Principle

Each phase builds on the previous one. Do not start Phase N+1 work until Phase N's P0 items have acceptance criteria met and are verified on a low-spec device emulator (2 GB RAM, API 29).

---

## Phase 0 — Feasibility (Weeks 1–2)

> **Goal:** Prove that the core technical approach works on target hardware. Kill risks early.

### P0-001: On-Device LLM Inference Spike ✅

- **Description:** Run a quantized SLM (≤ 2 GB, INT4/INT8) on an Android emulator matching the target device profile. Measure latency, RAM usage, thermal behaviour, and output quality for curriculum-style prompts.
- **Acceptance Criteria:**
  - [x] Model loads in < 5 seconds on the target emulator. *(Qwen2.5-1.5B: 0.784s)*
  - [x] Generates a coherent 150-token response in < 10 seconds. *(Qwen2.5-1.5B: 8.0s avg)*
  - [x] Peak RAM usage stays below 2 GB. *(Qwen2.5-1.5B: 1,839 MB)*
  - [x] Document model candidates tested, latency numbers, and RAM profiles in a spike report.
- **Output:** Spike report + recommendation on model + runtime (llama.cpp vs ONNX Runtime vs other).
- **Result:** GO — Qwen2.5-1.5B-Instruct Q4_K_M via llama.cpp. 4 models benchmarked (12 prompts × 3 runs each). Only Qwen2.5-1.5B passes all criteria. SmolLM2-1.7B is backup (fastest but exceeds RAM by 8%). See [ADR 0006](../../ejs-docs/adr/0006-qwen25-1.5b-as-on-device-llm.md), [spike report](../../spikes/p0-001-llm-inference/reports/spike-report.md).
- **Completed:** 2026-03-03 | Branch: `spike/p0-001-llm-inference`

### P0-002: Local Embedding + Retrieval Spike

- **Description:** Run a tiny embedding model (~50 MB) on-device. Embed a sample set of 50 curriculum content chunks. Perform vector similarity search and measure retrieval quality and latency.
- **Acceptance Criteria:**
  - [ ] Embedding model loads and produces vectors in < 2 seconds for a single query.
  - [ ] Top-3 retrieval accuracy ≥ 80% on 20 hand-crafted test queries against the sample set.
  - [ ] Vector search completes in < 500 ms.
  - [ ] Document embedding model candidates, vector DB options, and retrieval quality results.
- **Output:** Spike report + recommendation on embedding model and local vector store.

### P0-003: Storage Footprint Validation

- **Description:** Calculate and validate the total installed footprint: APK + base model + embedding model + one content pack. Must fit within the 150 MB budget (excluding content packs beyond the base).
- **Acceptance Criteria:**
  - [ ] Total footprint documented with per-component breakdown.
  - [ ] If over budget: identify which component to compress, quantize further, or split.
  - [ ] Feasibility verdict: GO / NO-GO / CONDITIONAL with stated conditions.
- **Output:** Size budget spreadsheet + go/no-go decision.
- **⚠️ Note from P0-001:** Qwen2.5-1.5B model alone is 1,066 MB — far exceeds the 150 MB *installed* budget. The 150 MB target likely needs reinterpretation (APK excluding model, or model downloaded separately). RAM budget is also tight: 1,839 MB of 2,048 MB used by LLM alone, leaving ~209 MB for embedding model + vector DB + app.

### P0-004: Sample Content Pack Creation

- **Description:** Author 10 curriculum-aligned content chunks for Grade 6 fractions (CAPS Term 1). Pre-compute embeddings. Package as a prototype content pack matching the planned schema.
- **Acceptance Criteria:**
  - [ ] 10 chunks authored in Markdown, each ≤ 2 000 tokens.
  - [ ] Each chunk tagged with `topic_path` matching CAPS pacing.
  - [ ] Embeddings pre-computed and bundled.
  - [ ] Manifest JSON with version, checksums, and metadata.
- **Output:** Prototype pack artifact (`maths-grade6-caps-prototype`).

### P0-005: End-to-End Pipeline Smoke Test

- **Description:** Wire P0-001 + P0-002 + P0-004 together. Input a question, embed it, retrieve relevant chunks, build a grounded prompt, generate an explanation. Validate the full pipeline on the target emulator.
- **Acceptance Criteria:**
  - [ ] Pipeline runs end-to-end without crashes.
  - [ ] Generated explanation references retrieved content (not hallucinated).
  - [ ] Total latency (embed + retrieve + generate) < 15 seconds.
  - [ ] Documented as a recorded demo or screenshot sequence.
- **Output:** Working prototype + demo recording.

---

## Phase 1 — Offline Learning Loop (Weeks 3–6)

> **Goal:** Deliver a minimal but usable Android app that lets a learner ask a maths question and get a grounded explanation, fully offline.

### P0-101: Android App Scaffold

- **Description:** Set up the Android project with the target configuration: min SDK 29, Kotlin, single-activity architecture, no Google Play Services dependency. Include build variants for debug and release.
- **Acceptance Criteria:**
  - [ ] Project builds and runs on target emulator.
  - [ ] APK is sideload-ready (single APK, not app bundle).
  - [ ] No internet permission in manifest.
  - [ ] CI pipeline runs unit tests on every push.

### P0-102: Chat Interface (Text Input/Output)

- **Description:** Build the primary learning interface: a text input field and a scrolling response area. Responses are rendered as formatted text with support for headings, bullet lists, bold, and inline math notation.
- **Acceptance Criteria:**
  - [ ] Learner can type a question and see a formatted response.
  - [ ] Math notation renders inline (LaTeX-lite, not WebView).
  - [ ] Chat history persists across app restarts.
  - [ ] UI is usable on a 720p, 5-inch screen.
  - [ ] Meets touch-target accessibility requirements (48×48 dp).

### P0-103: Content Pack Loader

- **Description:** Implement the pack installation pipeline: read a pack bundle from local storage, verify its manifest (SHA-256 checksums), extract and index content chunks, and register the pack as available.
- **Acceptance Criteria:**
  - [ ] Valid pack installs successfully and appears in the content library.
  - [ ] Corrupt pack (modified checksum) is rejected with a user-facing error.
  - [ ] Partially installed pack is cleaned up (no orphan files).
  - [ ] Pack metadata (topics, version) is queryable after installation.

### P0-104: Retrieval + Explanation Pipeline Integration

- **Description:** Integrate the embedding model, vector search, prompt template engine, and LLM into a unified `ExplanationEngine` service. The chat interface calls this service with the learner's question and receives a grounded explanation.
- **Acceptance Criteria:**
  - [ ] Question → embed → retrieve → prompt → generate → display works end-to-end in the app.
  - [ ] Explanation includes a worked example.
  - [ ] Response references retrieved curriculum content (verifiable by inspecting the prompt).
  - [ ] Latency < 10 seconds for the full pipeline on the target emulator.

### P0-105: Learner Profile — Basic

- **Description:** Implement local learner profiles. A learner selects their name from a simple list (no login, no password). Profile stores explanation preferences. Multiple profiles on one device (shared sibling use case).
- **Acceptance Criteria:**
  - [ ] Learner can create a profile (name only, no personal data).
  - [ ] Learner can switch profiles from a simple selector.
  - [ ] Each profile has independent chat history and preferences.
  - [ ] Profile data is encrypted at rest using Android Keystore.
  - [ ] Profile selection is ≤ 2 taps from app launch.

### P1-106: Topic Browser

- **Description:** Build a screen that displays available content topics organised by the CAPS structure (Term → Topic → Subtopic). Learner can browse topics and tap to ask a question about one.
- **Acceptance Criteria:**
  - [ ] Topics are loaded from the installed content pack metadata.
  - [ ] Navigation matches CAPS structure (e.g. Term 1 → Fractions → Addition).
  - [ ] Tapping a topic pre-fills the chat input with a contextual question.
  - [ ] Works with zero content packs installed (shows "No packs installed" state).

### P1-107: Prompt Template Engine

- **Description:** Build the prompt template system that constructs grounded prompts from: system instructions, retrieved content chunks, learner preferences (explanation style, reading level), and the question.
- **Acceptance Criteria:**
  - [ ] Templates are data-driven (loaded from configuration, not hardcoded).
  - [ ] Changing learner preferences changes the generated explanation style.
  - [ ] System prompt enforces curriculum grounding ("Use only the provided content").
  - [ ] Template output fits within the model's context window.

### P2-108: App Onboarding Flow

- **Description:** Zero-step onboarding. On first launch, the app opens directly to the learning interface. An optional, dismissible tooltip explains how to ask a question. No tutorial screens, no terms acceptance, no account creation.
- **Acceptance Criteria:**
  - [ ] First launch → learning interface in < 2 taps.
  - [ ] Tooltip is dismissible and does not reappear.
  - [ ] No blocking modals, no required permissions, no setup wizards.

---

## Phase 2 — Content + Personalisation (Weeks 7–10)

> **Goal:** Make the learning experience adaptive to learner preferences and build the tooling to create real content packs.

### P0-201: Learner Preference Engine

- **Description:** Expand the profile system with preference controls: explanation style (`concise | detailed | step-by-step`), example type (`real-world | abstract | visual-description`), reading level (`basic | intermediate`).
- **Acceptance Criteria:**
  - [ ] Preferences are editable from a settings screen (≤ 2 taps from chat).
  - [ ] Changing a preference immediately affects the next explanation generated.
  - [ ] Preferences persist across app restarts.
  - [ ] Default preferences are sensible (e.g. `step-by-step`, `real-world`, `basic`).

### P0-202: Content Pack Builder — CLI Tool

- **Description:** Python CLI tool that takes authored Markdown content, validates it against the pack schema, computes embeddings, generates the manifest, and outputs a distributable pack bundle.
- **Acceptance Criteria:**
  - [ ] `python pack-builder.py build --input ./content/ --output ./dist/` produces a valid pack.
  - [ ] Validates chunk size (≤ 2 000 tokens), required metadata fields, and topic_path format.
  - [ ] Computes embeddings using the same model used on-device.
  - [ ] Generates SHA-256 checksums in the manifest.
  - [ ] Runs fully offline (no API calls to external services).

### P0-203: Grade 6 Mathematics Content Pack — Full

- **Description:** Author the complete Grade 6 CAPS Mathematics content pack covering all four terms. This is the curriculum content that makes V1 useful.
- **Acceptance Criteria:**
  - [ ] Covers all CAPS Grade 6 Mathematics topics across Terms 1–4.
  - [ ] Each topic has ≥ 3 content chunks and ≥ 2 worked examples.
  - [ ] Content reviewed by a qualified mathematics educator.
  - [ ] Pack passes all validation checks from the pack builder.
  - [ ] Compressed pack size ≤ 200 MB.

### P1-204: Content Pack Update (Delta)

- **Description:** Support installing a content pack update as a delta (only changed/added chunks) rather than a full re-download. Teacher transfers the delta file to the phone.
- **Acceptance Criteria:**
  - [ ] Delta file contains only new or modified chunks + updated manifest.
  - [ ] Delta applies cleanly over the existing installed pack.
  - [ ] Integrity verification runs after delta application.
  - [ ] If delta application fails, the previous pack version remains intact (rollback).
  - [ ] Delta file size is < 10 MB for a typical content update.

### P1-205: Content Library Management UI

- **Description:** Screen showing installed content packs with version, size, topic coverage, and options to update or delete.
- **Acceptance Criteria:**
  - [ ] Lists all installed packs with name, version, size, and install date.
  - [ ] Delete action removes pack files and frees storage.
  - [ ] Update action accepts a delta file from local storage.
  - [ ] Shows available storage remaining on device.

### P2-206: Explanation Quality Feedback

- **Description:** After each explanation, the learner can tap a simple "This helped" / "I'm still confused" indicator. This is stored locally and used to adjust prompt aggressiveness (e.g. more step-by-step if "confused" is frequent).
- **Acceptance Criteria:**
  - [ ] Two-button feedback after each explanation (not blocking).
  - [ ] Feedback stored locally in learner profile.
  - [ ] After 3+ "confused" signals on the same topic, the engine automatically switches to `step-by-step` + `basic` for that topic.
  - [ ] No data leaves the device.

---

## Phase 3 — School Node + Hardening (Weeks 11–13)

> **Goal:** Add optional LAN-based pack distribution and harden the app for real-world deployment.

### P0-301: LAN Edge Node Discovery

- **Description:** When connected to a WiFi network, the phone app discovers a school edge node via mDNS. Discovery is passive — it does not block any functionality.
- **Acceptance Criteria:**
  - [ ] Edge node advertises itself via mDNS on the local network.
  - [ ] Phone app discovers the node within 5 seconds of connecting to WiFi.
  - [ ] If no node is found, no error is shown — the app continues in standalone mode.
  - [ ] Discovery does not require internet access (LAN only).

### P0-302: Content Pack Sync Over LAN

- **Description:** When an edge node is discovered, the phone can check for content pack updates and download them over the local WiFi network.
- **Acceptance Criteria:**
  - [ ] Phone queries the edge node for available pack versions.
  - [ ] If a newer version exists, the learner sees an "Update available" indicator.
  - [ ] Download happens over LAN (HTTP, not internet).
  - [ ] Downloaded pack passes integrity verification before installation.
  - [ ] Sync is interruptible and resumable (WiFi may drop).

### P0-303: Battery & Thermal Testing

- **Description:** Structured testing of battery drain and device temperature during typical learning sessions on real low-end devices (or representative emulators).
- **Acceptance Criteria:**
  - [ ] Battery drain < 3% per 30-minute session on a 4 000 mAh device.
  - [ ] No thermal throttling triggered during a 30-minute session.
  - [ ] Results documented for at least 3 device profiles (low, mid, target).
  - [ ] Any performance regressions from Phase 1/2 identified and fixed.

### P0-304: Crash Recovery & Data Integrity Testing

- **Description:** Systematically test abrupt process kills, force-stops, and power-offs during every critical operation (mid-inference, mid-pack-install, mid-profile-write).
- **Acceptance Criteria:**
  - [ ] No data loss on abrupt kill during any operation.
  - [ ] App restarts cleanly after force-stop with state intact.
  - [ ] Partially installed packs are cleaned up on next launch.
  - [ ] Learner profile is never corrupted.

### P1-305: Edge Node — Content Distribution Server

- **Description:** Minimal REST server running on the edge device that serves content packs and delta updates to phones on the LAN.
- **Acceptance Criteria:**
  - [ ] Serves pack manifest and pack files over HTTP.
  - [ ] Supports range requests for resumable downloads.
  - [ ] Advertises via mDNS (`_ezansi._tcp.local`).
  - [ ] Runs on Raspberry Pi 5 (or equivalent) with < 500 MB RAM usage.
  - [ ] No internet connectivity required to operate.

### P1-306: Sideload Installation Guide (In-App)

- **Description:** Embedded guide within the app explaining how a teacher can distribute the APK and content packs to learners via Bluetooth, WiFi Direct, or SD card.
- **Acceptance Criteria:**
  - [ ] Guide is accessible from the app's help section.
  - [ ] Includes step-by-step instructions with illustrations.
  - [ ] Covers: APK transfer, content pack transfer, first-launch setup.
  - [ ] Written at a non-technical reading level.
  - [ ] Available offline (bundled in the APK).

### P1-307: Release Build Hardening

- **Description:** Prepare the release build: ProGuard/R8 minification, APK signing, license audit of all dependencies, final permissions audit.
- **Acceptance Criteria:**
  - [ ] Release APK is minified and < 150 MB installed.
  - [ ] All dependencies pass license audit (Apache 2.0 / MIT / BSD only).
  - [ ] Permissions manifest contains ZERO dangerous permissions.
  - [ ] APK is signed with a release key.
  - [ ] No debug logging or test code in the release build.

### P2-308: Teacher Quick-Start Card

- **Description:** A printable single-page PDF (A4) that a teacher can use to introduce the app to learners. Embedded in the APK as a viewable/shareable asset.
- **Acceptance Criteria:**
  - [ ] One-page A4 format, printable in black and white.
  - [ ] Covers: what the app does, how to install, how to ask a question, how to switch profiles.
  - [ ] Written in simple English with visual steps.
  - [ ] Accessible from the app's help section.

---

## Definition of Done

A work item is **done** when all of the following are true:

### Code Quality
- [ ] All acceptance criteria are met.
- [ ] Code compiles without warnings.
- [ ] Unit tests pass with ≥ 80% coverage on new business logic.
- [ ] Integration tests pass for affected pipelines.
- [ ] No new lint warnings introduced.

### Performance
- [ ] Tested on the low-spec emulator (2 GB RAM, API 29, 720p).
- [ ] No performance budget regressions (cold start, inference latency, memory, battery).
- [ ] No new ANR (Application Not Responding) risk paths.

### Offline Integrity
- [ ] Works with airplane mode enabled.
- [ ] No new network-dependent code paths introduced.
- [ ] No new permissions added.

### Privacy
- [ ] No learner data logged, transmitted, or exposed.
- [ ] No new third-party libraries that phone home.

### Documentation
- [ ] Session journey updated with work done.
- [ ] ADR created if the decision rubric triggers.
- [ ] PR description states which persona(s) the work serves.

### Review
- [ ] Peer-reviewed (or AI-agent review with human approval).
- [ ] Reviewer confirms offline integrity, performance impact, and persona fit.

---

*Last updated: 2026-03-03*
