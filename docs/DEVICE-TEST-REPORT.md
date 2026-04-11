# Device Test Report — F5.7+ Real-Device Validation

> **Device:** vivo V2434 (ARM64-v8a, 7.4 GB RAM, Android 15 API 35, 720×1608 300dpi)
> **Branch:** `feature/gemma4`
> **Model:** Gemma 3 1B INT4 (529 MB `.task` file via MediaPipe GenAI SDK 0.10.33)
> **Content Packs:** 2× schema v2 (768-dim hash-based embeddings)

---

## Test Session 1: Core Pipeline (2026-04-11)

### Test 1.1: Topics Navigation ✅

**Steps:** Launch app → Tap Topics tab
**Expected:** Topic list loads with merged content from both packs
**Result:** ✅ Topics screen displays merged topic tree

**Bug Found & Fixed:**
- `IllegalArgumentException: Key "term1" was already used` — both packs had `term1` root node
- Fix: Added `mergeTopicTrees()` in `TopicsViewModel` to recursively merge children by path

📸 **Screenshot:** `01-topics-list.png`

---

### Test 1.2: Semantic Search ✅

**Steps:** Topics screen → Tap search bar → Type "fractions" → Wait for results
**Expected:** Search results appear with relevant content chunks
**Result:** ✅ Results appear instantly (hash-based embedding < 5ms)

**Bug Found & Fixed:**
- Initial: "Search failed — Search is not available yet. The embedding model is still loading."
- Root cause: `GemmaEmbeddingModel.isLoaded()` was gated on `GemmaModelProvider` but hash-based embedding doesn't need the model
- Fix: `isLoaded()` always returns `true`; search works immediately

📸 **Screenshot:** `02-search-failed.png` (before fix), `03-search-results.png` (after fix)

---

### Test 1.3: Ask AI Flow ✅

**Steps:** Search results → Tap "Ask AI" on a result → Wait for AI response
**Expected:** Chat screen opens, question auto-submitted, AI generates explanation
**Result:** ✅ Pipeline completes — 46-55s warm, 103s cold (includes model load)

**Bug Found & Fixed:**
- Navigation: Bottom bar didn't highlight Chat tab when navigating via Ask AI
- Root cause: Route matching used `==` (`"chat?question={question}" != "chat"`)
- Fix: Changed to `startsWith()` comparison in `EzansiBottomBar`

📸 **Screenshot:** `04-ask-ai-loading.png`

**Pipeline Metrics (warm cache):**
| Metric | Value |
|--------|-------|
| Embedding | <5ms (hash-based) |
| Retrieval | ~0.5s |
| Model load | 0.6s (warm) / 7.4s (first warm) / 16.6s (cold) |
| Generation | 46-55s |
| Response length | 302-605 chars |

---

### Test 1.4: Direct Chat ✅

**Steps:** Chat tab → Type "What are fractions?" → Send → Wait for response
**Expected:** AI generates coherent, age-appropriate explanation
**Result:** ✅ 605-char explanation, coherent, on-topic

---

### Test 1.5: Stability — Rapid Searches ✅

**Steps:** 3 consecutive searches: "numbers" → "shapes" → "fractions"
**Expected:** No crash, no ANR, results update between searches
**Result:** ✅ All searches completed, no crash or freeze

---

## Test Session 2: Extended Validation (2026-04-11)

### Test 2.1: Preferences Navigation ✅

**Steps:** Tap gear icon on home screen
**Expected:** Preferences screen shows with configurable options
**Result:** ✅ Preferences screen displays explanation style, reading level, example type options with Apply and Go Back buttons

📸 **Screenshot:** `05-preferences-screen.png`

---

### Test 2.2: Preference Change (Visual Style) ⚠️ Partial Pass

**Steps:** Set explanation style to "visual" → Ask "What are fractions?"
**Expected:** Response uses visual representations (diagrams, fraction bars)
**Result:** ⚠️ Partial Pass — preference pipeline works correctly (everyday SA examples appeared: sharing fruit equally), but 1B model doesn't reliably follow "visual" instruction to produce diagrams

**Root Cause:** Model capability limitation — 1B parameter model lacks instruction-following fidelity for style modifiers. Pipeline code is correct (verified: ChatViewModel → ExplanationEngine → PromptBuilder preference flow).

📸 **Screenshot:** `06-chat-visual-pref.png`

---

### Test 2.3: Metric System Compliance ✅ (after fix)

**Steps:** Ask "How do I measure length?" → Check for imperial vs metric units
**Expected:** All metric, no imperial
**Result:**
- ❌ First attempt (weak prompt): Model defaulted to imperial ("inches, feet")
- ✅ Second attempt (strengthened prompt): Response used only metric units (m, cm, km)

**Fix Applied:** Strengthened metric instruction in both `SYSTEM_PROMPT` and `GROUNDING_INSTRUCTION`:
- Listed all allowed metric units explicitly (m, cm, mm, km, kg, g, L, mL, °C)
- Listed all forbidden imperial units explicitly
- Added SA context: "You are in South Africa"
- Added metric rule to `GROUNDING_INSTRUCTION` (always appended, closest to generation)

📸 **Screenshots:** `07-metric-test.png` (fail), `08-metric-pass.png` (pass)

---

### Test 2.4: Preference Persistence ✅

**Steps:** Set style to "visual" → Force-close app → Relaunch → Check preferences
**Expected:** Preference retained after app restart
**Result:** ✅ "Visual" style still selected after kill/restart

📸 **Screenshot:** `09-pref-persist.png`

---

### Test 2.5: TalkBack Accessibility ☐ Deferred

**Steps:** Enable TalkBack → Navigate tabs → Verify announcements
**Expected:** All interactive elements have content descriptions, focus order is logical
**Result:** _Deferred — user chose to skip TalkBack testing this session_

**Note:** Accessibility semantics are present in code (verified: `contentDescription` and `Modifier.semantics` in EzansiBottomBar, TopicsScreen, ChatScreen, SearchResultCard, PreferencesScreen, ProfilesScreen). Functional testing deferred to future session.

---

## Bugs Found

| # | Bug | Severity | Status | Fix |
|---|-----|----------|--------|-----|
| 1 | SDK API mismatch: `setTemperature`/`setTopK` moved to session-level | Critical | ✅ Fixed | `d0db253` |
| 2 | GPU native crash: `Backend.GPU` on device without GPU | Critical | ✅ Fixed | `d0db253` |
| 3 | Topics crash: duplicate LazyColumn keys | Critical | ✅ Fixed | `6f16c27` |
| 4 | Search blocked: `isLoaded()` gated on model provider | High | ✅ Fixed | `6f16c27` |
| 5 | Chat tab unselected: `==` vs `startsWith` route matching | Medium | ✅ Fixed | `6f16c27` |
| 6 | Imperial units in AI responses | High | ✅ Fixed | Strengthened metric prompt in SYSTEM_PROMPT + GROUNDING_INSTRUCTION |

## Known Limitations

| # | Limitation | Impact | Mitigation |
|---|-----------|--------|------------|
| 1 | Generation latency 46-55s (CPU) | UX — long wait | Expected for 1B model on CPU; "please wait" shown |
| 2 | Model cold load 16.6s | First query slow | XNNPack cache reduces to 7.4s on subsequent launches |
| 3 | Hash-based retrieval not semantic | Search quality | Will improve with real Gemma 4 embedding API |
| 4 | 1B model sometimes asks questions back | Response quality | Prompt tuning needed; not a pipeline bug |
| 5 | 1B model doesn't reliably follow style modifiers | "visual" style not visual | Model capability limitation; pipeline code is correct |

## Appendix: Screenshots

Screenshots captured during testing are stored in the session workspace and referenced above.
Commit-worthy screenshots should be added to `docs/screenshots/` if needed.
