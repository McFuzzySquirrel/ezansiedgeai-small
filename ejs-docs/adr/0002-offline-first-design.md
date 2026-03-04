---
ejs:
  type: journey-adr
  version: 1.1
  adr_id: "0002"
  title: Offline-first design
  date: 2026-03-03
  status: accepted
  session_id: ejs-session-2026-03-03-01
  session_journey: ejs-docs/journey/2026/ejs-session-2026-03-03-01.md

actors:
  humans:
    - id: Doug McCusker
      role: Project Lead
  agents:
    - id: GitHub Copilot
      role: AI coding assistant

context:
  repo: ezansiedgeai-small
  branch: main
---

# ADR 0002 — Offline-first design

## Context

Cloud/subscription AI assumes reliable internet, ongoing payments, modern hardware, and centralized infrastructure. In South Africa, many schools face expensive mobile data (ZAR 99/GB), inconsistent connectivity, load shedding (rolling power outages), and zero school IT support. Even free tech fails if it needs setup, drains battery, or requires accounts. The system must work without any network connection, without accounts, and without cloud dependency.

## Session Intent

Define the connectivity model for the platform.

## Decision Trigger

This decision changes the system boundary — every component must be designed for zero-network operation. It has pervasive, long-lived consequences for architecture, data flow, AI inference, and update mechanisms.

## Considered Options

### Option A: Cloud-first with offline fallback

- Primary features require internet
- Offline mode is degraded/limited
- Standard SaaS architecture
- Simpler development model
- Fails in target environment

### Option B: Offline-first, cloud-optional

- All core features work with zero network
- Network enhances but is never required
- More complex development (local inference, local storage, local retrieval)
- Matches real-world constraints

### Option C: Fully offline, no network capability

- No network support at all
- Simplest architecture
- No update mechanism
- No content distribution path

## Decision

Adopt offline-first design. The system must work fully offline after installation. Network connectivity (WiFi to school edge node) is an optional enhancement for content updates and capability boosting, never a requirement.

## Rationale

South African schools face expensive data (ZAR 99/GB), unreliable connectivity, and load shedding. Cloud-first fails in this environment. Fully-offline with no network at all would make updates impossible and content distribution impractical. Offline-first with optional network gives us the resilience of offline operation with a practical path for content pack updates via school WiFi or peer transfer. Zero network calls at runtime is a non-negotiable constraint. The app must never phone home, require login, or depend on cloud services.

## Consequences

### Positive

- Works during load shedding, connectivity outages, and in rural areas with no signal
- Zero data cost to learners
- No account/subscription barrier
- No privacy risk from cloud data transmission
- Builds trust with teachers and parents (no tracking)

### Negative / Trade-offs

- On-device AI inference is computationally constrained (smaller models, slower)
- Content updates require deliberate distribution (school WiFi, USB, Bluetooth)
- Cannot leverage cloud compute for better AI responses
- Local storage becomes a scarce resource
- Bug fixes and updates require physical distribution or occasional WiFi access

## Key Learnings

- "Offline fallback" architectures invariably have degraded offline experiences — true offline-first requires offline to be the primary design target
- Zero network calls at runtime is a testable, enforceable constraint
- Update distribution is a solved problem (WiFi sync, sideload) if designed for from day one

## Agent Guidance

- NEVER add a network call to any runtime code path
- All AI inference must run on-device (llama.cpp, ONNX Runtime)
- All content retrieval must use local vector DB (sqlite-vec)
- Test by disabling all network interfaces — the app must be fully functional
- Content pack updates happen via explicit user action (WiFi sync), never background
- No analytics SDKs, no telemetry, no crash reporting that requires network
- State must auto-persist and survive crashes (crash-safe local storage)
