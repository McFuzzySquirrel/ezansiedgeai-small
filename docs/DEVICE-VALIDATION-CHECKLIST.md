# Real-Device Validation Checklist (F5.7)

> **Purpose:** Verify Gemma 4 migration meets performance and UX targets on real hardware.
> All automated tests pass on emulator. This checklist covers what can only be validated on-device.

## Prerequisites

- [ ] ARM64 device with ≥ 4 GB RAM (representative target class)
- [ ] Gemma 4 1B INT4 model file (~600 MB) sideloaded to device
- [ ] Both content packs (schema v2, 768-dim) installed
- [ ] Debug APK installed: `adb install app/build/outputs/apk/debug/ezansi-v0.1.0-debug.apk`

## 1. Model Loading & Cold Start

| # | Test | Target | Pass? |
|---|------|--------|-------|
| 1.1 | App cold start (kill → launch) | < 3 seconds to interactive | ☐ |
| 1.2 | Gemma 4 model load time | < 5 seconds | ☐ |
| 1.3 | GPU delegate initializes | No crash, logs show GPU path | ☐ |
| 1.4 | CPU fallback (disable GPU in config) | Model loads, inference works | ☐ |

## 2. Generation Performance

| # | Test | Target | Pass? |
|---|------|--------|-------|
| 2.1 | 150-token response latency (GPU) | ≤ 5 seconds | ☐ |
| 2.2 | 150-token response latency (CPU fallback) | < 10 seconds | ☐ |
| 2.3 | Peak RAM during generation | ≤ 1,200 MB | ☐ |
| 2.4 | Quality: Run P0-001's 12 CAPS prompts | Coherent, curriculum-aligned | ☐ |

## 3. Semantic Search Performance

| # | Test | Target | Pass? |
|---|------|--------|-------|
| 3.1 | Embedding latency per query | < 100 ms | ☐ |
| 3.2 | End-to-end search (type → results) | < 500 ms perceived | ☐ |
| 3.3 | Top-3 retrieval accuracy (P0-002 20 queries) | ≥ 80% | ☐ |
| 3.4 | Cross-pack search merges results correctly | Results from both packs | ☐ |
| 3.5 | "No results" for unrelated queries | Clear empty state | ☐ |

## 4. "Ask AI" Flow

| # | Test | Target | Pass? |
|---|------|--------|-------|
| 4.1 | Tap search result → "Ask AI" button visible | Button present, ≥ 48×48 dp | ☐ |
| 4.2 | "Ask AI" navigates to ChatScreen | Pre-filled "Explain {title}" | ☐ |
| 4.3 | Chat generates response for search context | Coherent, on-topic | ☐ |

## 5. Accessibility (TalkBack)

| # | Test | Target | Pass? |
|---|------|--------|-------|
| 5.1 | Search bar has persistent label | TalkBack reads "Search topics" | ☐ |
| 5.2 | Search results announced on load | LiveRegion announces state | ☐ |
| 5.3 | "Ask AI" button has content description | Reads "Ask AI about {title}" | ☐ |
| 5.4 | All touch targets ≥ 48×48 dp | No undersized targets | ☐ |

## 6. Platform & Privacy

| # | Test | Target | Pass? |
|---|------|--------|-------|
| 6.1 | No network calls during search/generation | Airplane mode works | ☐ |
| 6.2 | No new permissions requested | Same as before migration | ☐ |
| 6.3 | No GMS dependency | Test on AOSP/Huawei if available | ☐ |
| 6.4 | APK size impact from MediaPipe SDK | ≤ 15 MB increase | ☐ |

## 7. Stability

| # | Test | Target | Pass? |
|---|------|--------|-------|
| 7.1 | 10 consecutive searches, no crash | Stable | ☐ |
| 7.2 | Search during generation, no crash | Handles concurrent access | ☐ |
| 7.3 | Rapid search typing (stress debounce) | No ANR, results update | ☐ |
| 7.4 | Background → foreground during search | State preserved | ☐ |
| 7.5 | Thermal after 5 min continuous use | No thermal throttling warning | ☐ |

## Decision After Validation

Based on results, choose:

- **✅ All pass** → Remove legacy deps (llama.cpp/ONNX), ship Gemma 4 as primary
- **⚠️ Gen passes, embed fails** → Hybrid: Gemma 4 for gen + MiniLM for embed
- **❌ Gemma 4 fails** → Revert to legacy stack, investigate further

## How to Run

```bash
# Build debug APK
cd apps/learner-mobile && ./gradlew assembleDebug

# Install on device
adb install -r app/build/outputs/apk/debug/ezansi-v0.1.0-debug.apk

# Push model file
adb push gemma-4-1b-it-int4.task /sdcard/Android/data/com.ezansi.app/files/models/

# Push content packs
adb push content-packs/*.pack /sdcard/Android/data/com.ezansi.app/files/packs/

# Monitor logs during testing
adb logcat -s EzansiAI:V MediaPipe:V
```
