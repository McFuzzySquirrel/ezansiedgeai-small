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

## Test Session 2: Extended Validation (Pending)

### Test 2.1: TalkBack Accessibility ☐

**Steps:**
1. Enable TalkBack in device Settings → Accessibility
2. Navigate to Topics tab — verify screen reader announces content
3. Tap search bar — verify "Search topics" label is announced
4. Search "fractions" — verify results are announced via LiveRegion
5. Tap "Ask AI" button — verify content description reads "Ask AI about {title}"
6. Navigate between tabs — verify all nav items are announced

**Expected:** All interactive elements have content descriptions, focus order is logical
**Result:** _Not yet tested_

---

### Test 2.2: User Preferences Applied ☐

**Steps:**
1. Go to Profiles → Select or create a profile
2. Go to Preferences → Change "Explanation Style" to "visual"
3. Ask a question (e.g., "What are fractions?")
4. Verify response uses visual representations (diagrams, fraction bars, etc.)
5. Change "Explanation Style" to "simple"
6. Ask the same question → Verify response uses simpler language
7. Change "Example Type" to "everyday" → Verify SA-relevant examples (Rand, sharing food)

**Expected:** Prompt template adapts to preference changes, AI responses reflect the selected style
**Result:** _Not yet tested_

---

### Test 2.3: Metric System Compliance ☐

**Steps:**
1. Ask AI about measurement-related topics (e.g., "How do I measure length?")
2. Ask "What is 1 metre in centimetres?"
3. Check responses for any imperial units (inches, feet, pounds, Fahrenheit)
4. Verify all measurements use metric system (metres, centimetres, kilograms, Celsius)

**Expected:** All responses use metric system — South African curriculum (CAPS) uses SI units exclusively
**Result:** _Not yet tested_

**Note:** If imperial units appear, the fix is in the system prompt template (`DefaultTemplates.SYSTEM_PROMPT`) — add explicit instruction to use metric/SI units only.

---

### Test 2.4: Preference Persistence ☐

**Steps:**
1. Set preferences → Kill app → Relaunch → Verify preferences are retained
2. Switch between profiles → Verify each profile has independent preferences
3. Delete a profile → Verify preferences file is cleaned up

**Expected:** Preferences survive app restart, are per-profile, and cleaned up on deletion
**Result:** _Not yet tested_

---

### Test 2.5: Search + Preference Interaction ☐

**Steps:**
1. Set "Explanation Style" to "step-by-step"
2. Search "fractions" → Tap "Ask AI"
3. Verify response is numbered steps
4. Change to "visual" → Repeat → Verify diagrams/visual descriptions

**Expected:** Ask AI responses respect the current preference setting
**Result:** _Not yet tested_

---

## Bugs Found

| # | Bug | Severity | Status | Fix |
|---|-----|----------|--------|-----|
| 1 | SDK API mismatch: `setTemperature`/`setTopK` moved to session-level | Critical | ✅ Fixed | `d0db253` |
| 2 | GPU native crash: `Backend.GPU` on device without GPU | Critical | ✅ Fixed | `d0db253` |
| 3 | Topics crash: duplicate LazyColumn keys | Critical | ✅ Fixed | `6f16c27` |
| 4 | Search blocked: `isLoaded()` gated on model provider | High | ✅ Fixed | `6f16c27` |
| 5 | Chat tab unselected: `==` vs `startsWith` route matching | Medium | ✅ Fixed | `6f16c27` |

## Known Limitations

| # | Limitation | Impact | Mitigation |
|---|-----------|--------|------------|
| 1 | Generation latency 46-55s (CPU) | UX — long wait | Expected for 1B model on CPU; "please wait" shown |
| 2 | Model cold load 16.6s | First query slow | XNNPack cache reduces to 7.4s on subsequent launches |
| 3 | Hash-based retrieval not semantic | Search quality | Will improve with real Gemma 4 embedding API |
| 4 | 1B model sometimes asks questions back | Response quality | Prompt tuning needed; not a pipeline bug |
| 5 | Imperial units in some responses | Curriculum compliance | System prompt needs metric-only instruction |

## Appendix: Screenshots

Screenshots captured during testing are stored in the session workspace and referenced above.
Commit-worthy screenshots should be added to `docs/screenshots/` if needed.
