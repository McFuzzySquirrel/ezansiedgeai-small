# Coding Principles & Standards — eZansiEdgeAI

> Every line of code must be justified by the question: *"Does this work for Thandiwe on a Mobicel Hero with no internet, a cracked screen, and 1% battery anxiety?"*

---

## 1. Offline-First Development Patterns

### No Network Assumptions — Ever

- **Zero runtime network calls.** No DNS lookups, no TLS handshakes, no HTTP requests, no WebSocket connections. The app binary must be fully functional with airplane mode enabled permanently.
- **No lazy-loaded resources.** Every asset, model weight, prompt template, and content chunk must be present on-device before it is referenced.
- **No feature flags or remote config.** Feature behaviour is determined at build time or by locally installed content packs — never by a remote service.
- **No analytics SDKs.** No Firebase, no Amplitude, no Sentry, no crash-reporting library that phones home. Not even dormant/disabled. These libraries must not exist in the dependency tree.

### Data Persistence

- **Auto-persist every interaction.** There is no "save" button. Learner state is written to local storage after every meaningful interaction (question asked, preference changed, pack installed).
- **Survive abrupt kills.** The Android low-memory killer, sudden power-off, or force-stop must never cause data loss. Use transactional writes (SQLite transactions, atomic file operations).
- **No in-memory-only state for anything the learner cares about.** Session context that is reconstructible (e.g. UI scroll position) may be ephemeral. Learner profile, installed packs, and chat history must not be.

### Content Pack Integrity

- **Verify before loading.** Every content pack must pass SHA-256 manifest verification before any of its data enters the runtime. A corrupt or tampered pack is rejected with a clear user-facing message.
- **No partial loads.** A pack is either fully installed and verified, or not present. There is no "partially downloaded" state.

---

## 2. Mobile Performance Principles

### Resource Budgets

| Resource | Budget | Rationale |
|----------|--------|-----------|
| APK size (base + model) | ≤ 150 MB installed | 32 GB devices with < 3 GB free |
| RAM ceiling | ≤ 2 GB working set | Leave ≥ 1 GB for OS on a 3 GB device |
| Battery drain | < 3% per 30-min session | 4 000 mAh reference device |
| UI frame time | < 16 ms | 60 fps on lowest-tier GPU |
| Cold start | < 3 seconds | Learner patience threshold |
| First answer | < 8 seconds | Model load + embed + retrieve + generate |

### Inference Discipline

- **CPU-only is the baseline.** NPU/GPU delegation is an optimisation, never a requirement. Code must have a CPU fallback path that meets performance budgets.
- **Model loading is lazy.** The LLM and embedding model are loaded into memory on first query, not at app startup. Unload aggressively when the app is backgrounded.
- **No sustained background inference.** No background services, no wake locks, no periodic inference tasks. The phone's CPU is shared with the learner's entire digital life.

### UI Performance

- **No heavy animations.** Transitions are functional (fade, slide), not decorative. Every animation must justify its frame budget.
- **Render math cheaply.** LaTeX-lite rendering for mathematical notation. Do not embed a full browser engine or WebView for math display.
- **Image assets are resolution-appropriate.** No 1080p images scaled down to fit a 720p screen. Provide density-appropriate drawables.
- **RecyclerView (or equivalent) for all lists.** Chat history, topic lists, and content browsers must virtualise their item rendering.

---

## 3. Content Pack Format Standards

### Pack Structure

```
maths-grade6-caps-v1.0/
├── manifest.json          # Version, subject, grade, SHA-256 checksums
├── metadata.json          # CAPS topic tree, display names, descriptions
├── chunks/                # Markdown/text content chunks (one file per topic)
│   ├── fractions-basics.md
│   ├── fractions-operations.md
│   └── ...
├── embeddings/            # Pre-computed embedding vectors (binary format)
│   ├── index.bin
│   └── metadata.json
└── examples/              # Worked example templates
    ├── fractions-basics-examples.json
    └── ...
```

### Manifest Requirements

- `pack_id`: Globally unique identifier (e.g. `maths-grade6-caps`).
- `version`: Semantic version string (e.g. `1.0.0`).
- `min_app_version`: Minimum app version required to load this pack.
- `subject`, `grade`, `curriculum`: Machine-readable classification.
- `checksums`: SHA-256 hash for every file in the bundle.
- `size_bytes`: Total uncompressed size.

### Content Chunk Rules

- Each chunk is self-contained — no cross-references that assume another chunk is loaded.
- Chunks are authored in Markdown with a defined subset: headings, lists, bold, inline math (`$...$`), and code blocks for worked examples.
- Maximum chunk size: 2 000 tokens (embedding model context window limit).
- Every chunk carries a `topic_path` aligned to the CAPS pacing schedule (e.g. `term1.week3.fractions.addition`).

### Embedding Requirements

- Embeddings are pre-computed at pack-build time using the same model that will run on-device.
- Embedding dimensions and model identifier are recorded in the embeddings metadata.
- If the on-device model changes, all packs must be re-embedded. This is enforced by a `model_id` field in the manifest.

---

## 4. Testing Requirements

### Test Pyramid

| Layer | Tool / Framework | Coverage Target | What It Validates |
|-------|-----------------|----------------|-------------------|
| Unit | JUnit 5 / KotlinTest | ≥ 80% line coverage on business logic | Prompt construction, retrieval ranking, pack parsing, preference logic |
| Integration | AndroidX Test / Robolectric | Key pipelines end-to-end | Embed → retrieve → prompt → generate (mocked model), pack install → verify → load |
| Device | Android Instrumentation (Espresso) | Critical user journeys | Ask question → see answer, install pack, switch profile |
| Performance | Android Benchmark (Microbenchmark + Macrobenchmark) | Every release | Cold start time, inference latency, memory high-water mark, battery drain |
| Content | Custom validation scripts | 100% of pack content | Chunk size limits, embedding alignment, manifest integrity, CAPS topic coverage |

### Non-Negotiable Test Rules

1. **No test may require network access.** Test environments must mirror the production invariant: offline-only.
2. **Device tests must run on a low-spec emulator profile** matching the Mobicel Hero (2 GB RAM, 720p, API 29).
3. **Performance regressions are blocking.** If a PR increases cold start time by > 500 ms or inference latency by > 2 s, it does not merge.
4. **Content pack tests run in CI.** Every pack build is validated against the schema and embedding model before it can be distributed.

---

## 5. Code Review Expectations

### Every PR Must

- [ ] State which user persona(s) it serves (Thandiwe, Sipho, Ms. Dlamini, etc.).
- [ ] Confirm it works fully offline (no new network dependencies introduced).
- [ ] Include tests at the appropriate pyramid level.
- [ ] Pass performance budgets on the low-spec emulator.
- [ ] Not introduce any new Android permission.
- [ ] Not add any dependency that phones home or has a restrictive license.

### Review Checklist (Reviewer)

1. **Offline integrity.** Does this change introduce any code path that could fail without network?
2. **Resource impact.** Does this increase APK size, RAM usage, or battery drain? By how much?
3. **Persona fit.** Would this work for a learner with a cracked screen, 2 GB RAM, and no data?
4. **Content safety.** If this touches the explanation pipeline, can the output include ungrounded (hallucinated) content?
5. **Privacy.** Does this change collect, store, or transmit any information the learner didn't explicitly request?

### Dependency Policy

- All dependencies must be permissively licensed: Apache 2.0, MIT, or BSD. No GPL in the APK.
- Every new dependency requires a brief justification in the PR description: why it's needed, what it adds to APK size, and whether it has transitive dependencies that phone home.
- Prefer the Android platform API over third-party libraries where the functionality is equivalent.

---

## 6. Accessibility & Internationalisation Principles

### Accessibility

- **System font-size scaling.** All text must respect the device's accessibility font-size setting. No hardcoded `sp` values that override system preferences.
- **High-contrast palette.** The default colour scheme must pass WCAG 2.1 AA contrast ratios. No colour-only information encoding — always pair colour with icon or text.
- **Touch target sizes.** Minimum 48×48 dp for all interactive elements. Learners with cracked screens or imprecise touch digitisers must be able to tap reliably.
- **Screen reader support.** All UI elements carry content descriptions. Navigation order is logical. This is a requirement from V1, not a future enhancement.
- **Sunlight readability.** UI design assumes outdoor or bright-window use. Avoid low-contrast greys and thin fonts.

### Internationalisation

- **V1 languages:** English (primary), Afrikaans (stretch). UI strings are externalised from day one using Android resource bundles.
- **Content language is pack-level.** The app does not translate content at runtime. Language variants are separate content packs (e.g. `maths-grade6-caps-en-v1.0`, `maths-grade6-caps-af-v1.0`).
- **Reading level adaptation.** The explanation engine supports `basic` and `intermediate` reading levels, controlled by learner preference. This is a prompt-template concern, not a translation concern.
- **No assumptions about text direction.** While V1 languages are LTR, the layout system must not hardcode direction. Future African language support may include Arabic-script languages.
- **Cultural context in examples.** Worked examples and real-world contexts must be locally relevant (South African currency, local food, familiar scenarios). This is a content-authoring standard, not a code standard — but the pack schema must support a `locale` field to enforce it.

---

## 7. Code Style & Conventions

- **Language:** Kotlin (Android app), Python (pack tooling and scripts).
- **Formatting:** Enforced by automated formatters — `ktlint` for Kotlin, `black` + `ruff` for Python. No style debates in reviews.
- **Naming:** Descriptive over concise. `buildGroundedPromptFromRetrievedChunks()` over `buildPrompt()`. The codebase is a teaching tool.
- **Comments:** Explain *why*, not *what*. Every public function has a KDoc/docstring. Internal functions have a one-line intent comment if the purpose isn't obvious from the name.
- **Error handling:** No silent swallows. Every `catch` block either recovers meaningfully or logs and re-throws. User-facing errors are in plain language ("Something went wrong loading your content pack" not "IOException in PackLoader").
- **Logging:** Use structured log levels (`DEBUG`, `INFO`, `WARN`, `ERROR`). No logging of learner content, questions, or preferences — even in debug builds.

---

*Last updated: 2026-03-03*
