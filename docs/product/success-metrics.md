# eZansiEdgeAI — Success Metrics (V1)

> Defines what "working" means for V1: Grade 6 Mathematics, CAPS-aligned, offline-first, phone-first.

---

## Guiding Principle

Success is measured by **what the learner can do**, not by what the system can report. Because V1 collects no analytics and phones home to no server, every metric below must be **observable or testable without telemetry**.

---

## 1. Technical Success Metrics

### 1.1 Offline Reliability

| Metric | Target | How We Verify |
|---|---|---|
| App launches and is fully usable with airplane mode on | 100% of launches | Automated test suite; manual QA on 3 reference devices |
| Zero network calls at runtime | 0 DNS / TCP / UDP calls | Network traffic audit via `adb shell dumpsys netstats` and mitmproxy |
| Graceful survival of process kill (OOM, sudden power-off) | Zero data loss | Monkey-test with random `am force-stop` cycles; verify session state on relaunch |
| App functions identically whether SIM is present or not | Pass | Test matrix includes SIM-less devices |

### 1.2 Device Performance

| Metric | Target | How We Verify |
|---|---|---|
| Cold start to interactive home screen | **< 3 seconds** on Samsung A04 (baseline device) | Automated launch timing via `adb shell am start -W` |
| Response to any learner tap | **< 200 ms** perceived latency | Frame-timing instrumentation; manual feel-test |
| On-device inference (explanation generation) | **< 2 seconds** end-to-end | Benchmark harness on baseline device |
| Memory footprint (resident) | **< 180 MB RSS** during active session | `adb shell dumpsys meminfo` sampling |
| Battery drain per 30-minute session | **< 3%** on 4 000 mAh battery | Controlled discharge test (screen at 50%, Wi-Fi off) |
| Thermal | No thermal throttling during 30-minute session on baseline device | Continuous thermal-zone reads via adb |
| Installed size (APK + base content pack) | **≤ 150 MB** | CI build artifact size check |
| No ANR (Application Not Responding) | 0 ANRs in 100-hour soak test | `adb logcat` monitoring |

### 1.3 Content Integrity

| Metric | Target | How We Verify |
|---|---|---|
| All CAPS Grade 6 Maths topics covered | 100% of term 1–4 topics | Content checklist cross-referenced with DBE CAPS document |
| AI-generated explanations are mathematically correct | 100% (pre-validated, not live-generated) | Expert review of every explanation in the content pack |
| Content pack update applies without data loss | Pass | Automated upgrade test: install v1.0, populate state, apply v1.1 delta, verify state |

---

## 2. User Experience Success Metrics

### 2.1 Learner Experience

| Metric | Target | How We Verify |
|---|---|---|
| Taps from home screen to any learning content | **≤ 2** | UX walkthrough audit |
| Learner can start using the app without any instruction | Pass — no onboarding wizard, no tutorial overlay | Usability test with 5 learners who have never seen the app |
| Learner can choose explanation style and reading level | Available on every content screen | Feature checklist |
| Session recovery after interruption (call, power-off, app switch) | Learner returns to exact place | Automated interrupt/resume test |
| Text is readable at Grade 4 reading level | Flesch-Kincaid Grade Level ≤ 5.0 for all instructional text | Automated readability analysis on content corpus |
| Usable on cracked / low-brightness screens | High-contrast palette passes WCAG AA (4.5:1 minimum) | Automated contrast checker on all colour pairs |

### 2.2 Teacher Experience

| Metric | Target | How We Verify |
|---|---|---|
| Teacher can install the app (sideload APK) in under 5 minutes | Pass | Timed walkthrough with 3 teachers of varying tech comfort |
| Teacher can distribute a content-pack update to 5 learner phones in under 15 minutes | Pass | Timed walkthrough using Bluetooth/Wi-Fi Direct/SD card |
| In-app teacher guide covers all CAPS topics with suggested lesson hooks | 100% topic coverage | Content audit |
| Teacher never needs to "fix" the app for a learner | Zero support actions required during a 4-week pilot | Pilot observation log |

---

## 3. Deployment Success Metrics

| Metric | Target | How We Verify |
|---|---|---|
| APK installs on Android 10 through Android 15 | Pass on all major versions | CI device-farm matrix (real devices or emulators per version) |
| APK installs on Huawei (no Google Play Services) | Pass | Manual test on Huawei Y6p or equivalent |
| APK integrity survives Bluetooth transfer | SHA-256 matches after transfer | Transfer test across 3 phone-to-phone Bluetooth sessions |
| Sideload requires no "developer mode" or ADB | Standard "Install from unknown sources" prompt only | Walkthrough on fresh device |
| App does not request any dangerous permissions | Manifest declares zero dangerous permissions | Automated manifest audit in CI |
| Content-pack delta update size | **< 10 MB** for a single-term content refresh | Build-system delta measurement |

---

## 4. Adoption Success Metrics

> These are observed qualitatively during pilot; they are not in-app analytics.

| Metric | Target | How We Verify |
|---|---|---|
| Learner voluntarily opens the app outside of class | Observed in ≥ 50% of pilot learners | Teacher / facilitator observation journal |
| Teacher recommends the app to a colleague | ≥ 1 organic referral per pilot school | Post-pilot interview |
| No learner or parent raises a privacy concern | Zero complaints | Post-pilot interview |
| Learner can explain what the app does to a peer | Pass (indicates comprehension of value) | Brief learner interview |
| App is still installed 4 weeks after pilot starts | ≥ 80% retention on pilot devices | Manual device check |

---

## 5. What Success Is NOT

Clarity on anti-goals prevents scope creep and misaligned expectations.

| ❌ Not a success metric | Why |
|---|---|
| Number of daily active users | We collect no analytics. DAU is unmeasurable and irrelevant for V1 |
| Time spent in app | Longer ≠ better. A learner who gets a clear explanation in 3 minutes and leaves has succeeded |
| Test score improvement | V1 is a learning support tool, not an assessment platform. Attributing score changes to the app requires controlled studies beyond V1 scope |
| Content virality / social sharing | The app has no sharing features and no social layer |
| App store rating | Primary distribution is sideload; most users will never see a store listing |
| Revenue | V1 is zero-cost. Monetisation is a post-V1 concern |
| Number of schools reached | Distribution scale is a V2+ goal. V1 must work perfectly for 1 school before it works for 1 000 |
| Adaptive learning / personalisation depth | V1 offers learner-chosen preferences, not ML-driven personalisation. Sophistication is not the goal; usefulness is |

---

## 6. Measurement Integrity

Because we refuse to embed analytics, our verification methods are:

1. **Automated tests** — CI pipeline on reference devices / emulators.
2. **Manual QA** — Structured test protocols on physical low-end devices.
3. **Pilot observation** — Facilitator journals, teacher interviews, learner interviews. All opt-in, no recordings without consent.
4. **Build-system checks** — APK size, permission manifest, dependency licence audit, content readability analysis.

If a metric cannot be verified by one of these four methods, it is not a valid V1 metric.

---

*Last updated: 2026-03-03*
