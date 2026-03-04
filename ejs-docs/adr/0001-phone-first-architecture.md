---
ejs:
  type: journey-adr
  version: 1.1
  adr_id: "0001"
  title: Phone-first architecture
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

# ADR 0001 — Phone-first architecture

## Session Journey

[ejs-session-2026-03-03-01](../journey/2026/ejs-session-2026-03-03-01.md)

## Context

The original vision for eZansiEdgeAI included Raspberry Pi classroom devices as compute nodes running local small language models (SLMs). Research into the realities of South African underserved school environments revealed significant challenges with this approach: hardware availability fluctuates due to supply chain issues and budget constraints, maintenance is extremely difficult in rural deployments where there is no on-site IT support, theft and hardware failure risk is high, and scaling dedicated hardware per classroom is expensive. Meanwhile, learners already own phones — devices that are already charged, already trusted, and already familiar. The project needed to decide: should the primary compute surface be dedicated classroom hardware or the learner's own phone?

## Session Intent

Establish the primary compute surface for the eZansiEdgeAI platform.

## Decision Trigger

This is a fundamental system boundary and runtime topology decision. Choosing the primary compute surface determines the entire deployment model, development targets, and cost structure. It affects every downstream architecture choice — from model quantisation targets to UI design to offline sync strategy. It is long-lived and hard to reverse once engineering effort is committed.

## Considered Options

### Option A: Classroom device-first (Raspberry Pi / dedicated hardware)

- Dedicated hardware per classroom (Raspberry Pi 4/5 or equivalent)
- School controls the device — easier to manage content and updates
- More compute available (8GB RAM, faster CPU, potential NPU add-ons)
- Requires procurement, installation, and ongoing maintenance
- **Risk:** theft, hardware failure, power dependency (South Africa's load shedding makes reliable power a real concern)
- **Risk:** scaling cost per classroom — every new classroom needs a new device, cables, case, SD card, and setup time

### Option B: Phone-first (learner's own device)

- Learners already own phones — no procurement cost to schools
- Already charged, already trusted, already familiar
- More constrained compute (3–6GB RAM, limited storage, no guaranteed NPU)
- Works at home, not just at school — extends learning beyond classroom hours
- No maintenance burden on schools
- Scales naturally with learner population

### Option C: Hybrid equal-weight (phone and classroom device equally important)

- Both surfaces treated as primary compute targets
- More complex architecture — every feature must work on both surfaces
- Higher development cost and testing burden
- Splits engineering focus across two very different runtime environments
- Risk of "lowest common denominator" design that doesn't play to either platform's strengths

## Decision

**Adopt phone-first architecture.** The learner's phone is the primary compute surface. School infrastructure (Raspberry Pi edge nodes, classroom WiFi) is an optional enhancement — an accelerator — not a dependency.

## Rationale

Phones are already owned, already charged, already trusted, and already familiar. They require zero cost to the school. They work at home, not just at school — extending learning beyond classroom hours and making the platform resilient to school closures, transport disruptions, and other interruptions common in underserved communities.

The constrained compute environment (3–6GB RAM, Android 10+, limited storage) is a design challenge we accept because it eliminates the deployment barrier entirely. There is no procurement cycle, no installation visit, no maintenance contract, no theft risk to the school, and no dependency on reliable power infrastructure.

Classroom devices (Raspberry Pi edge nodes) would require procurement, installation, maintenance, and are vulnerable to theft, power outages (load shedding is a daily reality in much of South Africa), and hardware failure — all problems that kill "good intention tech" in underserved environments. The graveyard of well-meaning ed-tech projects that shipped hardware to schools and then failed when it broke, was stolen, or couldn't be maintained is a strong signal against this approach.

The hybrid equal-weight approach was rejected because it splits engineering focus and increases complexity without proportional benefit. A small team cannot build excellent experiences for two fundamentally different compute surfaces simultaneously. Phone-first with optional edge enhancement is a better use of limited engineering capacity.

## Consequences

### Positive

- Zero hardware cost to schools — removes the biggest adoption barrier
- Learning continues at home — not gated by school hours or classroom access
- No deployment or maintenance burden on schools or teachers
- Leverages devices learners already own and trust
- Scales with learner population, not infrastructure budget
- Resilient to load shedding, school closures, and transport disruptions

### Negative / Trade-offs

- Constrained to low-end Android specs (3–6GB RAM, limited storage)
- Must optimise aggressively for battery, thermal throttling, and storage usage
- Cannot assume any particular device capability (NPU, high-resolution display, etc.)
- UI must work on small, sometimes cracked screens with inconsistent touch response
- Model size limited to what fits on phone storage alongside other apps
- Some learners may share phones or have very old devices — must degrade gracefully

## Key Learnings

- The best deployment strategy eliminates deployment barriers rather than managing them
- Phone ownership in South African schools is higher than reliable school IT infrastructure availability
- Designing for constraints (low RAM, offline-only, limited storage) is more achievable than solving procurement, logistics, and maintenance at scale
- "Good intention tech" fails when it depends on infrastructure the target environment cannot sustain

## Agent Guidance

- Every feature **must** work on a phone with 3GB RAM, Android 10+, no internet connection
- **Never** design a feature that requires classroom hardware to function
- School edge device is an **accelerator**, not a dependency
- Test on the lowest-spec target device profile (Samsung A04 / Mobicel Hero class)
- Performance budgets:
  - APK ≤ 150MB
  - RAM ≤ 2GB active usage
  - Battery < 3% per 30-minute learning session
- Prefer on-device inference with quantised models over any approach requiring connectivity
- UI must be usable on a 5-inch screen at 720p resolution
