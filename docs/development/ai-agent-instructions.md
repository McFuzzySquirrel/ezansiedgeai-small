# AI Agent Instructions — eZansiEdgeAI

> Instructions for AI coding agents (Copilot, Cursor, Cody, or any LLM-based coding assistant) working on this project. Read this before generating any code.

---

## 1. Project Context You Must Understand

### What This Project Is

eZansiEdgeAI is an offline-first, phone-first AI learning platform for underserved South African schools. V1 targets Grade 6 Mathematics aligned to the CAPS (Curriculum and Assessment Policy Statement) curriculum.

### Who It Serves

- **Thandiwe** — Rural learner, Mobicel Hero (2 GB RAM), cracked screen, no internet, shares a family SIM.
- **Sipho** — Township learner, Xiaomi Redmi 10C (4 GB RAM), isiXhosa home language, shares the phone with his sister.
- **Ms. Dlamini** — Grade 6 teacher, 42 learners, no IT support, needs the app to map to CAPS pacing.

Every code decision must pass the test: *"Does this work for these people?"*

### How It Works

1. Learner types a maths question.
2. The question is embedded locally using a small embedding model (~50 MB).
3. Relevant curriculum content chunks are retrieved from a local vector database.
4. A grounded prompt is constructed: system prompt + retrieved content + learner preferences + question.
5. A quantized local LLM (GGUF/ONNX, 1–2 GB) generates an explanation.
6. The explanation and a worked example are displayed.

All of this happens on-device with zero network access.

### Tech Stack

| Component | Technology |
|-----------|-----------|
| App | Android (Kotlin), minimum API 29 (Android 10) |
| LLM inference | llama.cpp / ONNX Runtime (quantized INT4/INT8 models) |
| Embeddings | Tiny embedding model (~50 MB), local vector search |
| Vector DB | SQLite + vector extension or FAISS-lite |
| Content format | Versioned packs: Markdown chunks + pre-computed embeddings + JSON metadata |
| Pack tooling | Python scripts |
| Edge node (optional) | Raspberry Pi / mini-PC, REST/gRPC over LAN |

---

## 2. Constraints You Must Respect

### Hard Constraints (Violating These Breaks the Product)

1. **No network calls at runtime.** Zero. No DNS, no HTTP, no WebSocket, no telemetry, no crash reporting. Not even behind a feature flag.
2. **No new Android permissions.** The app requests ZERO dangerous permissions. No camera, no microphone, no location, no contacts.
3. **No analytics or tracking libraries.** No Firebase, Amplitude, Sentry, or any SDK that transmits data. Not even as a transitive dependency.
4. **No user accounts or authentication.** No sign-up, no email, no phone number, no OAuth.
5. **≤ 150 MB installed footprint** for app + base content pack.
6. **≤ 2 GB RAM working set** — the app must leave ≥ 1 GB for the OS on a 3 GB device.
7. **All dependencies must be permissively licensed** (Apache 2.0, MIT, BSD). No GPL in the APK.
8. **All learner data stays on-device.** No data leaves the phone unless the learner explicitly initiates it.

### Performance Constraints

| Metric | Target |
|--------|--------|
| Cold start | < 3 seconds |
| First answer | < 8 seconds |
| Battery drain | < 3% per 30-min session |
| UI frame time | < 16 ms |

### Scope Constraints (V1 Will NOT Include)

Do not build, stub, or scaffold any of the following:
- Voice-first interface (a V2 stub exists in the UI layer — do not expand it)
- Cloud-dependent features
- Behaviour analytics or learner tracking
- Teacher dashboards
- Multi-subject support
- Advanced agent systems or multi-step reasoning chains
- Gamification (streaks, leaderboards, notifications)

---

## 3. Architectural Boundaries

### Layers You Must Respect

The phone app has four layers. Do not bypass layer boundaries.

```
App Layer  →  AI Layer  →  Data Layer  →  Hardware Layer
```

- **App Layer** talks to **AI Layer** through a defined service interface (e.g. `ExplanationEngine`). It never directly loads models or queries the vector DB.
- **AI Layer** talks to **Data Layer** through repository interfaces. It never reads raw files from disk.
- **Data Layer** owns all persistence: SQLite, content packs, learner profiles. It handles integrity verification and encryption.
- **Hardware Layer** abstractions manage CPU/NPU delegation and thermal monitoring.

### Content Pack Boundary

- The LLM **explains** content. It does not **invent** content. Every generated explanation must be grounded in retrieved content chunks.
- The prompt template engine is the only component that constructs prompts. Do not hardcode prompt strings in the AI layer or app layer.
- Content packs are immutable once installed. The app never modifies pack files.

### Privacy Boundary

- Learner preferences, chat history, and profile data are local-only.
- Logging must never include learner questions, answers, or preference values — even in debug builds.
- No code path may transmit learner data. This includes seemingly innocent patterns like crash reports that embed stack traces containing user input.

---

## 4. What You Should Do

### When Writing Code

- **Follow the coding principles** in `docs/development/coding-principles.md`. Read it.
- **Name things descriptively.** `buildGroundedPromptFromRetrievedChunks()` over `buildPrompt()`. This codebase is also a teaching tool.
- **Write tests.** Every non-trivial function gets a unit test. Every pipeline change gets an integration test. Tests must run offline.
- **Respect the resource budgets.** If your change loads a new asset, quantify its memory and storage impact.
- **Use the existing patterns.** Before creating a new abstraction, check if an equivalent pattern already exists in the codebase.
- **Keep PRs focused.** One concern per PR. Do not mix refactoring with feature work.

### When Making Decisions

- **Check existing ADRs** in `ejs-docs/adr/` before making architectural decisions. A past decision may already cover your case.
- **If a decision is significant** (changes an interface, introduces a dependency, alters the data model), flag it. The human will create an ADR.
- **Prefer simplicity.** The simplest solution that meets the constraint envelope is the best solution. Premature abstraction is a tax on a small team.
- **Prefer platform APIs.** Use Android SDK capabilities over third-party libraries wherever the functionality is equivalent.

### When Writing Content Pack Tooling

- Pack tooling is Python. Use `black` + `ruff` for formatting and linting.
- All pack scripts must be runnable offline (no API calls to external embedding services).
- Pack validation scripts must enforce the schema defined in `docs/development/coding-principles.md`.

---

## 5. What You Should NOT Do

1. **Do not add dependencies without justification.** Every new library increases APK size, attack surface, and maintenance burden.
2. **Do not generate ungrounded content.** Never write code that lets the LLM answer from parametric memory alone. Every response must include retrieved content in the prompt.
3. **Do not introduce background services.** No wake locks, no alarms, no foreground services, no periodic sync tasks.
4. **Do not expand V1 scope.** If a feature is in the "Will NOT Include" list, do not build it, even if it seems easy.
5. **Do not hardcode English strings in the UI.** All user-facing text goes through Android resource bundles (`strings.xml`).
6. **Do not store secrets in code.** Content pack signing keys, if used, are build-time secrets — never embedded in the APK.
7. **Do not use WebView for content rendering.** It is too heavy for the target device class. Use native Android views.
8. **Do not assume Google Play Services are available.** Many target devices (Huawei, sideloaded installs) do not have them.

---

## 6. How to Handle Uncertainty

### When You're Unsure About Architecture

- Stop and state what you're unsure about. Do not guess.
- Reference the relevant architecture document (`docs/architecture/`) and explain what the ambiguity is.
- Propose two options with trade-offs. Let the human decide.

### When You're Unsure About Scope

- Default to **not building it.** V1 scope is deliberately narrow. If a feature isn't in the backlog, it's out of scope until explicitly added.
- If you think something is missing from the backlog, flag it as a suggestion — do not implement it.

### When You're Unsure About Performance Impact

- Measure, don't guess. Write a benchmark or profile the change on the low-spec emulator (2 GB RAM, API 29).
- If you cannot measure, state your assumptions and flag the risk.

### When You Encounter Conflicting Requirements

- The priority order is: **Privacy > Offline capability > Performance > Features > Code elegance**.
- If two requirements conflict, the one higher in this list wins.

---

## 7. EJS Documentation Integration

This project uses the Engineering Journey System (EJS) to capture development context.

### What You Must Know

- **Session Journeys** are created at `ejs-docs/journey/YYYY/ejs-session-YYYY-MM-DD-<seq>.md` to record what happened during a work session.
- **ADRs** (Architecture Decision Records) live at `ejs-docs/adr/NNNN-<kebab-title>.md` and capture significant technical decisions with context and rationale.
- **The ADR database** can be searched with `python scripts/adr-db.py search <query>` to find past decisions.

### Your Responsibilities

- When a session starts, create the session journey file with initial metadata.
- Throughout the session, update the journey file incrementally — don't reconstruct at the end.
- Record experiments, outcomes, decisions, and rationale as they happen.
- When a decision is significant enough to warrant an ADR (new dependency, interface change, architectural shift), flag it and help draft the ADR.
- Run `python scripts/adr-db.py sync` after creating or modifying ADRs to keep the index current.
- Do not claim commands or tests ran successfully unless you observed the output.

### Decision Rubric for ADRs

Create an ADR when a decision:
- Changes a public interface or data format
- Introduces or removes a dependency
- Alters the content pack schema
- Modifies the privacy or security model
- Changes a performance budget
- Affects deployment or distribution strategy

Do NOT create an ADR for:
- Routine refactoring within an established pattern
- Bug fixes that don't change behaviour
- Style or formatting changes

---

## 8. Quick Reference — The Five Questions

Before submitting any code, answer these five questions:

1. **Does it work offline?** — If this code path could fail without network, it must not ship.
2. **Does it fit the device?** — Will this run on a 2 GB RAM phone with 3 GB free storage?
3. **Is the learner's data safe?** — Does any data leave the device? Is anything logged that shouldn't be?
4. **Is the content grounded?** — If this touches the explanation pipeline, is the LLM constrained to retrieved content?
5. **Is it in scope?** — Is this feature in the V1 backlog? If not, stop.

---

*Last updated: 2026-03-03*
