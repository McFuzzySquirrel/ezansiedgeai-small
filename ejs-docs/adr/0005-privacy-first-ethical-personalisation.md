---
ejs:
  type: journey-adr
  version: 1.1
  adr_id: "0005"
  title: Privacy-first ethical personalisation
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

# ADR-0005: Privacy-first ethical personalisation

## Context

Commercial EdTech platforms typically track learner performance, model behaviour, and sync data to the cloud for analytics. In the South African context this raises serious concerns: POPIA (Protection of Personal Information Act) compliance for minors, parental trust in rural communities, surveillance fears, and the practical reality that cloud sync requires connectivity the learners don't have. The platform needs to support personalisation (learners learn differently) without surveillance. The Personal Learning Profile should capture learning PREFERENCES, not performance data.

## Session Intent

Define the personalisation and data privacy architecture for learner profiles.

## Decision Trigger

This decision directly alters the security/privacy/compliance posture. It defines what data is collected, where it's stored, who can access it, and what is explicitly prohibited. POPIA compliance for minor learners is a legal requirement. This decision has long-lived consequences and is hard to reverse (adding tracking later would break trust).

## Considered Options

### Option A: Cloud-synced analytics (standard EdTech approach)

- Track learner performance, time-on-task, question accuracy
- Sync to cloud for teacher dashboards and analytics
- Enables adaptive learning algorithms
- Requires accounts, consent, connectivity, cloud infrastructure
- POPIA compliance complex for minors
- Surveillance risk — parents/communities may reject
- Data monetisation temptation

### Option B: Privacy-first local-only preferences (chosen approach)

- Capture learning PREFERENCES only: explanation style, reading level, example type, language, pace
- Stored locally only on the learner's device
- Editable and deletable by the learner at any time
- Never transmitted to cloud, school, or any external system
- No performance tracking, no behaviour modelling
- POPIA compliance simplified — minimal data, local only, no data controller

### Option C: Anonymised local analytics with optional sync

- Track performance locally, optionally sync anonymised data
- Moderate privacy — anonymisation is hard to guarantee
- Still requires consent infrastructure
- Scope creep risk toward full tracking

## Decision

Adopt privacy-first ethical personalisation. Learner profiles capture preferences only (not performance), stored locally only, editable by the learner, never transmitted externally.

## Rationale

Privacy-first is both an ethical choice and a practical one. POPIA compliance for minors requires special care — eliminating data collection and cloud transmission is the simplest path to compliance. Rural communities and parents are more likely to trust a system that explicitly does not track their children. Local-only storage eliminates connectivity requirements for profile sync. Preference-based personalisation (explanation style, reading level, language) provides meaningful adaptation without surveillance. The system supports learners by respecting their agency — they control their own profile. Performance tracking and behaviour modelling are explicitly excluded from V1 to prevent scope creep toward surveillance.

## What is captured (local only)

- Explanation style preference (visual, step-by-step, examples-first)
- Reading level preference
- Example type preference
- Language preference
- Pace preference (brief vs. detailed explanations)
- Confidence feedback signals (learner self-reports, not measured)

## What is NOT captured (by design)

- Question accuracy / scores
- Time-on-task
- Behaviour patterns
- Usage frequency
- Content consumption history
- Any performance metric

## Consequences

### Positive

- POPIA compliance simplified (minimal local-only data, no data controller role)
- Parental and community trust (explicit no-tracking commitment)
- No connectivity needed for profiles
- Learner agency — they control their data
- No data breach risk (nothing to breach)
- Eliminates entire categories of complexity (consent forms, data governance, cloud storage)

### Negative/Trade-offs

- Cannot build adaptive learning algorithms based on performance data
- No teacher visibility into learner progress (V1)
- No aggregate analytics for impact measurement
- Profile is lost if device is lost (no cloud backup)
- Cannot demonstrate learning outcomes through platform data (need external assessment)

## Key Learnings

- Privacy-by-design is easier to implement than privacy-by-retrofit
- "What we don't collect, we can't leak" is the strongest privacy guarantee
- Personalisation through preferences is meaningfully different from personalisation through surveillance — and learners notice
- POPIA compliance for minors is a significant legal burden that local-only data collection largely sidesteps

## Agent Guidance

- NEVER add any form of analytics, telemetry, or performance tracking
- NEVER transmit learner data off-device (no network calls for profile data)
- Learner profile must be readable/editable/deletable by the learner via the UI
- No dangerous Android permissions: no INTERNET for profile, no READ_CONTACTS, no ACCESS_FINE_LOCATION
- Profile stored in encrypted local storage (AES-256, device-bound key)
- Zero analytics SDKs: no Firebase Analytics, no Crashlytics, no ad SDKs
- If asked to add tracking features, refuse and reference this ADR
- Preference choices must use plain language (Grade 4 reading level)
- Default profile values must be sensible for Grade 6 CAPS Mathematics
