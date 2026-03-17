---
ejs:
  type: journey-adr
  version: 1.1
  adr_id: "0009"
  title: Manual Dependency Injection via AppContainer
  date: 2026-03-17
  status: accepted
  session_id: ejs-session-2026-03-17-01
  session_journey: ejs-docs/journey/2026/ejs-session-2026-03-17-01.md

actors:
  humans:
    - id: McFuzzySquirrel
      role: lead developer
  agents:
    - id: project-architect
      role: scaffold and DI design
    - id: project-orchestrator
      role: task coordination

context:
  repo: ezansiedgeai-small
  branch: agent-forge/build-agent-team
---

# Session Journey

- Session Journey: `ejs-docs/journey/2026/ejs-session-2026-03-17-01.md`

# Context

eZansiEdgeAI targets low-end Android phones (3–4 GB RAM, Android 10+) with zero Google Play Services dependency. The app has ~15 injectable services across 9 Gradle modules. A dependency injection strategy was needed for wiring repositories, the AI pipeline, view models, and data stores.

Key constraints:
- No GMS dependency (Huawei AppGallery users, sideloading)
- Minimal APK size (≤ 50 MB budget)
- Fast cold-start (learners have short micro-sessions)
- Small service graph (~15 services, unlikely to exceed 30 in V1)

---

# Session Intent

Select and implement a dependency injection approach for the Phase 1 Android app that balances simplicity, performance, and the project's offline-first constraints.

# Collaboration Summary

The project-architect agent evaluated DI options during P0-101 (Android App Scaffold) and recommended manual DI. The human accepted without modification. All subsequent agents (learner-data-engineer, ai-pipeline-engineer, android-ui-engineer) consumed and extended the AppContainer pattern successfully.

---

# Decision Trigger / Significance

DI strategy is a foundational architecture decision that affects every module, every service, and every test. Changing it later requires modifying every ViewModel factory and service constructor. This must be decided once and followed consistently.

# Considered Options

## Option A: Hilt (Dagger with Android extensions)

Google's recommended DI for Android. Annotation-driven, compile-time code generation.

- **Pros:** Industry standard, compile-time safety, good tooling support
- **Cons:** Requires Google Play Services annotations library, significant code generation overhead, reflection at init, adds ~1.5 MB to APK, slower incremental builds, complex for a 15-service graph

## Option B: Koin

Kotlin-first service locator. Runtime resolution via DSL.

- **Pros:** Lightweight, Kotlin-idiomatic, no code generation
- **Cons:** Runtime errors instead of compile-time, performance overhead on resolution, dependency on third-party library, potential for subtle bugs in module declarations

## Option C: Manual DI with Kotlin lazy {} (Selected)

A single `AppContainer` class holds all services as `lazy {}` properties. ViewModels receive dependencies through constructor injection via `ViewModelProvider.Factory` implementations.

- **Pros:** Zero reflection, zero code generation, zero external dependencies, compile-time type safety, trivially debuggable, ~0 KB overhead
- **Cons:** Manual wiring required for each new service, no scope management (all singletons), doesn't scale well beyond ~50 services

---

# Decision

Use manual dependency injection via a single `AppContainer` class with Kotlin `lazy {}` delegates. All services are singletons initialised lazily on first access. ViewModels receive dependencies through explicit `ViewModelProvider.Factory` implementations.

**Key file:** `apps/learner-mobile/app/src/main/kotlin/com/ezansi/app/di/AppContainer.kt`

---

# Rationale

1. **Zero overhead:** No reflection, no annotation processing, no code generation. Cold start is as fast as possible.
2. **No GMS dependency:** Hilt requires `com.google.dagger:hilt-android` which pulls in GMS-related transitive dependencies. The app must be fully functional on Huawei devices and via sideloading.
3. **Appropriate scale:** With ~15 services, manual wiring is trivial. The entire dependency graph fits in one readable file.
4. **Compile-time safety:** Forgetting to wire a dependency is a compile error, not a runtime crash.
5. **Debuggability:** The entire DI graph is visible in one file. No generated code to trace through.

The team accepted that if the service count grows beyond ~50 (unlikely for V1), migration to Koin would be straightforward since constructor injection patterns are identical.

---

# Consequences

### Positive
- Zero APK size impact from DI framework
- Zero cold-start overhead
- Every dependency is visible in AppContainer.kt — no magic
- Tests can construct services directly without DI framework test utilities

### Negative / Trade-offs
- Every new service requires manual wiring in AppContainer.kt
- No scope management — all services are application-scoped singletons
- ViewModelFactory boilerplate for each ViewModel (could be reduced with a generic factory)
- Doesn't provide automatic lifecycle management

---

# Key Learnings

Manual DI works well when the service graph is small and the team prioritises transparency over convention. The key is using `lazy {}` for thread-safe lazy initialisation — this gives singleton behaviour without eager allocation.

---

# Agent Guidance

- **Always wire new services in AppContainer.kt** — this is the single source of truth for the dependency graph
- **Use `lazy {}` for all service properties** — ensures thread-safe lazy initialisation
- **ViewModels get dependencies via ViewModelProvider.Factory** — not by reaching into AppContainer directly from composables
- **Do not introduce Hilt, Koin, or any DI framework** without creating a new ADR superseding this one
- **Test services by direct construction** — no test-specific DI configuration needed

---

# Reuse Signals (Optional)

```yaml
reuse:
  patterns:
    - "Kotlin lazy {} singletons for small service graphs"
    - "ViewModelProvider.Factory for constructor injection into ViewModels"
  prompts:
    - "Wire the new service in AppContainer.kt with lazy {}"
  anti_patterns:
    - "Do not use @Inject annotations — there is no annotation processor"
    - "Do not access AppContainer from composables — pass through ViewModel"
  future_considerations:
    - "If service count exceeds 50, evaluate migration to Koin"
    - "Consider a generic ViewModelFactory to reduce per-ViewModel boilerplate"
```
