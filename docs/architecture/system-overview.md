# System Overview — eZansiEdgeAI

## Purpose

eZansiEdgeAI is an offline-first, phone-first AI learning platform for
underserved South African schools. The system delivers curriculum-grounded
explanations to learners on low-end Android devices with zero cloud dependency.

This document describes the high-level topology, data flow, and design
principles that govern every component.

---

## Design Principles

| # | Principle | Implication |
|---|-----------|-------------|
| 1 | **Offline First** | Every feature must work without any network. Connectivity is a bonus, never a requirement. |
| 2 | **Phone First** | The learner's phone is the primary runtime. School infrastructure is an optional booster. |
| 3 | **Retrieval First** | Knowledge comes from local curriculum content packs, not model memory. |
| 4 | **Content Is Source of Truth** | The LLM explains content; it does not invent content. Hallucination is mitigated by grounding. |
| 5 | **Low Power by Design** | Target: Android 10+, 3–6 GB RAM, no sustained GPU requirement. |
| 6 | **Ethical Personalisation** | Preferences are local-only, learner-controlled, never centrally monetised. |
| 7 | **Zero Cost to School** | No subscriptions, no cloud accounts, no payment methods required. |

---

## System Topology

```
┌─────────────────────────────────────────────────────────────────┐
│                        SCHOOL BUILDING                          │
│                                                                 │
│   ┌──────────────┐      WiFi (LAN only)     ┌──────────────┐   │
│   │  Learner     │◄────────────────────────► │  School Edge │   │
│   │  Phone A     │   mDNS / REST / gRPC      │  Device      │   │
│   └──────────────┘                           │              │   │
│                                              │  - Content   │   │
│   ┌──────────────┐                           │    Cache     │   │
│   │  Learner     │◄────────────────────────► │  - STT / TTS │   │
│   │  Phone B     │                           │  - Update    │   │
│   └──────────────┘                           │    Server    │   │
│         ...                                  └──────┬───────┘   │
│                                                     │           │
└─────────────────────────────────────────────────────┼───────────┘
                                                      │ (periodic,
                                                      │  opportunistic)
                                              ┌───────▼───────┐
                                              │  Internet      │
                                              │  (content pack │
                                              │   updates only)│
                                              └────────────────┘
```

**Key:** Solid arrows are local WiFi. The internet link is optional and used
only for content pack and software updates by the edge device.

---

## Component Summary

### Learner Phone

The phone runs the full learning pipeline independently:

1. **App Layer** — Learning UI, chat interface, semantic search, voice UI stub,
   personal learning profile, local content library browser.
2. **AI Layer** — Gemma 4 1B INT4 unified model (~600 MB) for both generation
   and embedding via MediaPipe GenAI SDK / LiteRT. Legacy fallback: Qwen2.5-1.5B
   (llama.cpp) + all-MiniLM-L6-v2 (ONNX Runtime). Two public APIs:
   `ExplanationEngine` (RAG pipeline) and `ContentSearchEngine` (semantic search).
3. **Data Layer** — FAISS vector search (IndexFlatIP), encrypted learner profile,
   offline content packs (SQLite `.pack` with 768-dim pre-computed embeddings).
4. **Hardware Layer** — GPU-first inference via LiteRT GPU delegate with CPU
   fallback chain. 4–6 GB RAM budget. NPU delegation via NNAPI when available.

See [phone-architecture.md](phone-architecture.md) for full detail.

### School Edge Device

An optional low-cost device (old laptop, mini-PC, Raspberry Pi 5) on the
school's local WiFi network. It is a **capability booster, not the brain**.

1. **WiFi Service Layer** — Local API gateway, content distribution server,
   update server (mDNS-discoverable).
2. **AI Services** — Shared STT engine, shared TTS engine, optionally a
   larger model for complex queries the phone cannot handle.
3. **Data Services** — Shared content cache, school-level knowledge base.

See [edge-device-architecture.md](edge-device-architecture.md) for full detail.

---

## Data Flow — Learner Asks a Question

```
Learner types / speaks question
        │
        ▼
┌─ Phone ──────────────────────────────────────────────┐
│  1. Input Processing (text or STT if edge avail)     │
│  2. Embed query with Gemma 4 embedding mode          │
│  3. FAISS vector search against local content pack   │
│  4. Build grounded prompt:                           │
│       [System Prompt]                                │
│       [Retrieved Content Chunks]                     │
│       [Learner Preferences]                          │
│       [Question]                                     │
│  5. Generate with Gemma 4 1B (same loaded model)     │
│  6. Post-process: format explanation + example       │
│  7. Display to learner                               │
└──────────────────────────────────────────────────────┘
```

### Alternative Flow — Semantic Search

```
Learner types search query in Topics browser
        │
        ▼
┌─ Phone ──────────────────────────────────────────────┐
│  1. Embed query with Gemma 4 embedding mode          │
│  2. FAISS vector search (top-K, < 100 ms)            │
│  3. Rank and return results with metadata             │
│  4. Display results: chunk text, topic, CAPS info     │
│  5. (Optional) "Ask AI" → passes to ExplanationEngine│
└──────────────────────────────────────────────────────┘
```

If the edge device is reachable:
- STT/TTS can be offloaded (voice input/output).
- A query the phone LLM scores low-confidence on can be forwarded to the
  edge's larger model (future phase).
- Content packs can be synced/updated.

If the edge device is **not** reachable, nothing changes — the phone pipeline
runs identically. This is the critical invariant.

---

## Content Pack Architecture (Summary)

Content packs are the unit of knowledge distribution:

| Field | Detail |
|-------|--------|
| Scope | One subject, one grade (e.g. `maths-grade6-caps-v1.0`) |
| Format | SQLite `.pack` with pre-computed embeddings and FAISS index |
| Schema | v2 (768-dim Gemma 4 embeddings); v1 (384-dim MiniLM) supported via `PackVersionDetector` |
| Size budget | ≤ 200 MB per pack |
| Distribution | Sideloaded, LAN sync from edge, or downloaded when connectivity exists |
| Integrity | Double SHA-256 verification; packs are signed to prevent tampering |

---

## Security & Privacy Model

- **No user accounts.** The app does not require sign-up or login.
- **No telemetry.** Zero data leaves the phone unless the learner explicitly
  initiates a sync (future phase).
- **Local encryption.** Learner profile is encrypted at rest using Android
  Keystore-backed keys.
- **Content integrity.** Packs are verified against signed manifests before
  loading.

---

## V1 Boundaries

| In scope | Out of scope |
|----------|-------------|
| Grade 6 Mathematics (CAPS) | Other grades / subjects |
| Text chat interface | Voice-first interface |
| Offline operation | Cloud-dependent features |
| Learner preference profile (local) | Behaviour analytics / tracking |
| Content pack loading + retrieval | Teacher dashboards |
| LAN content sync from edge | Internet-dependent sync |

---

## Related Documents

- [Phone Architecture](phone-architecture.md)
- [Edge Device Architecture](edge-device-architecture.md)
- [Deployment Modes](deployment-modes.md)
- [Vision & Strategy](../vision.md)
