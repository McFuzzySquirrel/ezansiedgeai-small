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

1. **App Layer** — Learning UI, chat interface, voice UI stub, personal
   learning profile, local content library browser.
2. **AI Layer** — Quantized small LLM (GGUF/ONNX, ≤ 2 GB), tiny embedding
   model (≤ 100 MB), prompt templates tuned for curriculum explanation.
3. **Data Layer** — Local vector DB (e.g. SQLite + vector extension or
   FAISS-lite), encrypted learner profile, offline content packs.
4. **Hardware Layer** — CPU inference (NPU delegation when available),
   3–6 GB RAM budget, offline storage for packs.

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
┌─ Phone ──────────────────────────────────────────┐
│  1. Input Processing (text or STT if edge avail) │
│  2. Embed query with local embedding model       │
│  3. Vector search against local content pack     │
│  4. Build grounded prompt:                       │
│       [System Prompt]                            │
│       [Retrieved Content Chunks]                 │
│       [Learner Preferences]                      │
│       [Question]                                 │
│  5. Run inference on local quantized LLM         │
│  6. Post-process: format explanation + example   │
│  7. Display to learner                           │
└──────────────────────────────────────────────────┘
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
| Format | Versioned bundle: markdown/text chunks + pre-computed embeddings + metadata JSON |
| Size budget | ≤ 200 MB per pack (compressed) |
| Distribution | Sideloaded, LAN sync from edge, or downloaded when connectivity exists |
| Integrity | SHA-256 manifest; packs are signed to prevent tampering |

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
