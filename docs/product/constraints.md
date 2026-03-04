# eZansiEdgeAI — Constraints Document (V1)

> Living document. Captures the real-world constraints that shape every architecture, UX, and deployment decision for V1 (Grade 6 Mathematics, CAPS-aligned).

---

## 1. Hardware Constraints

| Constraint | Detail |
|---|---|
| **Target device class** | Low-end to mid-range Android smartphones (Samsung A04s, Xiaomi Redmi 10C, Mobicel Hero 4 GB, etc.) |
| **Minimum OS** | Android 10 (API 29) |
| **RAM floor** | ~3 GB usable (devices marketed as "4 GB" typically have ~3 GB available after OS overhead). Raised from 3 GB marketed after P0-001 showed LLM alone uses 1,839 MB — see [ADR 0006](../../ejs-docs/adr/0006-qwen25-1.5b-as-on-device-llm.md) |
| **Storage budget** | **Component split** (not a single budget): APK ≤ 50 MB, AI models ≤ 1.2 GB (downloaded on first launch), content packs ≤ 200 MB each. Total first-launch download ~1.4 GB. Many target devices ship with 32 GB total; after OS, WhatsApp, and media the learner may have < 3 GB free |
| **CPU** | Quad-core ARM Cortex-A53 class or equivalent — no guaranteed NNAPI / GPU delegate support |
| **Screen** | 5"–6.5" LCD, 720p typical. Must be usable in direct sunlight and on cracked screens |
| **Battery** | 3 000–5 000 mAh. Must not drain noticeably during a 30-minute learning session |
| **Thermal** | Sustained on-device inference must stay below the thermal-throttle ceiling; no perceptible device heating |

### Implications
- On-device models must be aggressively quantised (INT8 / INT4).
- No background services, no wake-locks, no persistent notifications.
- UI rendering budget: < 16 ms per frame on lowest-tier GPU.

---

## 2. Connectivity Constraints

| Constraint | Detail |
|---|---|
| **Baseline assumption** | **No internet.** The app must be fully functional with zero connectivity |
| **Data cost** | South Africa has among the highest mobile data prices in Africa. 1 GB prepaid ≈ ZAR 99 (~ USD 5.50). Many learners share a family SIM with a capped monthly budget |
| **Network type** | Where available: 2G/EDGE in deep rural, 3G in peri-urban, spotty 4G in townships |
| **Wi-Fi** | Rare at target schools. When present, shared across 200+ learners on a single ADSL or fixed-wireless link |
| **Sync model** | **None for V1.** No cloud sync, no telemetry upload, no remote analytics. All data stays on-device |

### Implications
- Initial install must work via sideloading (APK + content pack transferred phone-to-phone via Bluetooth/Wi-Fi Direct, or from a teacher's SD card).
- Content-pack updates delivered as small differential bundles (< 10 MB).
- Zero network calls at runtime — no DNS, no TLS handshake, nothing.

---

## 3. Power / Load-Shedding Constraints

| Constraint | Detail |
|---|---|
| **Load shedding** | Eskom schedules rotate 2–6 hour outage blocks. Rural areas often experience unscheduled outages lasting 8+ hours |
| **Charging access** | Many learners charge at a neighbour's house or a spaza shop. Charging time is limited and unpredictable |
| **Battery anxiety** | Learners will not use an app that visibly eats battery — they need the phone for communication and safety |

### Implications
- App must survive abrupt process kills (Android low-memory killer, sudden power-off) with zero data loss.
- Session state auto-persisted every interaction; no "save" concept.
- Doze-mode and App-Standby compatible — no alarms, no foreground services.
- Power consumption target: < 3% battery per 30-minute session on a 4 000 mAh device.

---

## 4. Deployment Constraints

| Constraint | Detail |
|---|---|
| **No IT support** | Target schools have zero dedicated IT staff. The teacher is the installer, the troubleshooter, and the administrator |
| **Play Store access** | Not guaranteed. Many devices have restricted or absent Google Play. Huawei devices (common in SA) use AppGallery |
| **Sideloading** | Primary distribution channel for V1. Must be a single APK (no split APKs, no app bundles for sideload path) |
| **Updates** | Must not require uninstall/reinstall. Teacher downloads a content-pack delta file and taps "Update" inside the app |
| **Onboarding** | Zero-step onboarding. No account creation, no email, no phone-number verification. Learner opens the app and starts learning |
| **Multi-user** | A single device may be shared among siblings. Learner profiles are local, optional, and PIN-free (select name from list) |

### Implications
- APK must be a self-contained artifact — no runtime downloads, no feature flags, no remote config.
- In-app content updater with integrity verification (hash check, not signature — avoids Play dependency).
- Teacher guide embedded inside the app, not hosted externally.

---

## 5. Adoption & Usability Constraints

| Constraint | Detail |
|---|---|
| **Digital literacy** | Many Grade 6 learners have limited app experience beyond WhatsApp and YouTube |
| **Language** | Home languages include isiZulu, isiXhosa, Sesotho, Setswana, Sepedi, Afrikaans, English, and more. V1 UI in English with bilingual scaffolding where feasible |
| **Reading level** | Grade 4–6 reading level for all instructional text. Avoid jargon. Prefer visual + short text |
| **Accessibility** | Support system font-size scaling. High-contrast colour palette. No colour-only information encoding |
| **Attention span** | Design for 10–15 minute micro-sessions, not 45-minute class periods |
| **Trust** | Learners (and parents) must never feel surveilled. No camera, no microphone, no location access |
| **Teacher buy-in** | Teachers will only recommend the app if it clearly maps to CAPS topics and reduces their workload rather than adding to it |

### Implications
- Navigation: max 2 taps to reach any learning content from the home screen.
- All interactions must feel instant (< 200 ms perceived response).
- No gamification dark patterns (streaks, leaderboards, FOMO notifications).

---

## 6. Privacy & Ethics Constraints

| Constraint | Detail |
|---|---|
| **Data residency** | All learner data stays on-device. Period. No exceptions for V1 |
| **POPIA compliance** | South Africa's Protection of Personal Information Act applies. Since no data leaves the device, the compliance surface is minimal — but we must not collect what we don't need |
| **No profiling** | No learner behaviour modelling, no performance tracking for external parties, no adaptive algorithms that build a profile |
| **Learner agency** | Learner chooses explanation style, reading level, and example types. The system does not decide "what's best" for them |
| **Parental consent** | Not required for V1 because no personal data is collected, stored remotely, or shared |
| **Content safety** | AI-generated explanations must be deterministic and pre-validated. No live LLM generation on user input in V1 |

### Implications
- No analytics SDK (Firebase, Amplitude, etc.) — not even disabled/dormant.
- No advertising SDK, no tracking pixels, no third-party libraries that phone home.
- Permissions manifest: ZERO dangerous permissions requested.

---

## 7. Cost Constraints

| Constraint | Detail |
|---|---|
| **Learner cost** | **Zero.** No subscription, no in-app purchase, no "premium" tier |
| **Teacher cost** | **Zero.** No paid training, no paid materials, no paid support |
| **School cost** | **Zero.** No server, no router, no IT contract |
| **Infrastructure cost** | V1 requires no backend infrastructure. No servers to run, no databases to manage, no cloud bills |
| **Content creation cost** | Content packs authored using open-source tooling. No proprietary curriculum-authoring licenses |

### Implications
- Sustainability model for post-V1 is out of scope but must not be precluded by V1 architecture choices.
- All dependencies must be permissively licensed (Apache 2.0, MIT, BSD). No GPL-contamination in the APK.

---

## Constraint Interaction Map

Some constraints amplify each other:

- **Low RAM + no connectivity** → models and content must be bundled, yet fit in tight memory.
- **No IT support + sideloading** → installation must be foolproof; a corrupt APK is a showstopper.
- **Battery anxiety + load shedding** → every CPU cycle spent on inference is a cycle the learner notices.
- **Privacy + no connectivity** → simplifies compliance but eliminates any future "opt-in analytics" shortcut.

These intersections are where the hardest design trade-offs live and where architectural decisions (see `ejs-docs/adr/`) will be recorded.

---

*Last updated: 2026-03-04*
