---
ejs:
  type: journey-adr
  version: 1.1
  adr_id: "0004"
  title: Edge device as capability booster not brain
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

# Edge device as capability booster not brain

## Context

The original vision placed a Raspberry Pi or similar device at the center of each classroom running subject-specific SLMs. Research revealed problems: hardware availability fluctuates, maintenance is hard in rural deployments, physical theft/failure risk, scaling hardware per classroom is expensive, and load shedding (rolling power outages) can take the entire classroom system offline. With the phone-first decision (ADR 0001), the question becomes: what role does the school device play?

## Session Intent

Define the role and responsibilities of the school edge device in the architecture.

## Decision Trigger

This defines the runtime topology and system boundary between phone and school infrastructure. It determines what capabilities are available in each deployment mode and what happens when the edge device is unavailable.

## Considered Options

### Option A: Edge device as the brain (original vision)

- School device runs the primary AI models
- Phones are thin clients that send queries to the school device
- Requires reliable power and hardware at school
- Single point of failure for the entire classroom
- Load shedding = no learning

### Option B: Edge device as capability booster (new direction)

- Phone handles all core functions independently
- School device provides optional enhancements: content pack distribution, STT/TTS services, optional heavier inference
- If edge device is offline → learning still works on phone
- Edge device discovered via mDNS on school WiFi
- No dependency on edge device for any core function

### Option C: No edge device at all (phone only)

- Simplest architecture
- No content distribution mechanism beyond sideloading
- No speech services (STT/TTS) until phone hardware can handle them
- No upgrade path for enhanced capabilities

## Decision

The school edge device is an optional capability booster, not the brain. If the edge node is offline, down, or absent, learning must still work fully on the phone.

## Rationale

Making the edge device a capability booster rather than the brain eliminates single point of failure risk. Load shedding, theft, or hardware failure at school doesn't stop learning. The phone handles all core functions (question input, content retrieval, explanation generation). The edge device adds value when available: content pack updates over WiFi (avoiding expensive mobile data), shared STT/TTS services (too compute-heavy for phones currently), and optional heavier inference. This also means schools without ANY edge device can still deploy the system — phone-only mode works. Zero cost to school to run.

## Consequences

### Positive

- No single point of failure — learning continues during edge outages and load shedding
- Schools without edge devices can still use the system (Mode A: phone only)
- Reduces school IT burden — edge device is "nice to have" not "must have"
- Zero cost to run if no edge device
- Graceful capability degradation: edge down = phone still works, just without speech services and WiFi pack sync

### Negative/Trade-offs

- STT/TTS only available when edge device is online (V1)
- Content updates without edge device require manual sideloading (USB/Bluetooth)
- Cannot leverage edge compute for better AI responses when phone is standalone
- Edge device capabilities must be discoverable at runtime (mDNS complexity)
- Must design and test two modes: with-edge and without-edge

## Key Learnings

- "Capability booster" framing changes the entire design mindset — instead of "how does the edge serve phones?" it becomes "how does the phone optionally discover and use edge services?"
- The invariant "phone must work without edge" is a testable, enforceable architectural constraint
- Schools with no IT budget can still participate — this is critical for the mission

## Agent Guidance

- NEVER design a phone feature that requires the edge device to function
- Always implement edge features behind capability discovery (mDNS service lookup)
- Test every feature with edge device absent — it must work
- Edge services are: content pack distribution, STT (whisper.cpp), TTS (Piper), optional heavier inference
- Edge device discovery: mDNS (`_ezansi._tcp.local`)
- Design APIs as capability queries: "is STT available?" not "connect to STT server"
- Phase 3 scope: don't build edge features before Phase 3
