# Feature Progress: Gemma 4 Migration & Semantic Search

## Current State
**Feature PRD**: docs/product/feature-gemma4-semantic-search.md
**Original PRD**: docs/product/prd-v1.md (Phase 0 + Phase 1 complete)
**Phase**: F1 — Gemma 4 Validation Spike (P0-006)
**Status**: F1 Complete — Awaiting Decision Gate (F1.6)
**Last Updated**: 2026-04-10
**Branch**: feature/gemma4

## Completed Tasks
- [x] Feature PRD created and reviewed (docs/product/feature-gemma4-semantic-search.md)
- [x] Agent team updated for feature (6 agents modified)
- [x] F1.1: Add MediaPipe GenAI SDK dependency (da16ed1) — Apache 2.0, GMS-free ✓
- [x] F1.2: Spike scaffold at `spikes/p0-006-gemma4-evaluation/` — 5 scripts, config, README
- [x] F1.4: Cross-platform embedding parity validation (validate_parity.py)
- [x] F1.5: Device validation checklist + spike report template

## Current Task
- [ ] Phase F1, Task F1.3: Android spike benchmark harness (@ai-pipeline-engineer)
  - Status: In Progress
  - Notes: GemmaSpikeLlmEngine, GemmaSpikeEmbeddingModel, SpikeBenchmarkRunner + tests

## Remaining

### Phase F1: Gemma 4 Validation Spike (P0-006)
- [x] F1.1: Add MediaPipe SDK dependency (@project-architect)
- [x] F1.2: Create spike scaffold (@qa-test-engineer)
- [x] F1.3: Implement spike benchmark harness (@ai-pipeline-engineer)
- [x] F1.4: Cross-platform embedding parity validation (@ai-pipeline-engineer)
- [x] F1.5: Spike report + device-run checklist
- [ ] F1.6: Decision gate (user — unified/hybrid/no-migration)

### Phase F2: Gemma 4 Engine Integration
- [ ] F2.1: Finalize MediaPipe dep + audit (@project-architect)
- [ ] F2.2: Define model contract (@ai-pipeline-engineer)
- [ ] F2.3: Implement GemmaLiteRtEngine (@ai-pipeline-engineer)
- [ ] F2.4: Implement GemmaEmbeddingModel (@ai-pipeline-engineer)
- [ ] F2.5: Update prompt templates (@ai-pipeline-engineer)
- [ ] F2.6: Remove sequential loading (@ai-pipeline-engineer)
- [ ] F2.7: Update AppContainer (@ai-pipeline-engineer)
- [ ] F2.8: Engine unit + integration tests (@qa-test-engineer)

### Phase F3: Content Pack Re-embedding
- [ ] F3.1: Define embedding contract (@ai-pipeline-engineer + @content-pack-engineer)
- [ ] F3.2: Update build_pack.py (@content-pack-engineer)
- [ ] F3.3: Update validate_pack.py (@content-pack-engineer)
- [ ] F3.4: Pack version detection (@content-pack-engineer)
- [ ] F3.5: Re-embed content packs (@content-pack-engineer)
- [ ] F3.6: Validate re-embedded packs (@qa-test-engineer)

### Phase F4: Semantic Search
- [ ] F4.1: ContentSearchEngine API contract (@ai-pipeline-engineer)
- [ ] F4.2: Implement ContentSearchEngine (@ai-pipeline-engineer)
- [ ] F4.3: Search UI components (@android-ui-engineer)
- [ ] F4.4: Add search to TopicsScreen (@android-ui-engineer)
- [ ] F4.5: Ask AI navigation (@android-ui-engineer)
- [ ] F4.6: Search tests (@qa-test-engineer)

### Phase F5: Performance Validation & Hardening
- [ ] F5.1: Regression tests (@qa-test-engineer)
- [ ] F5.2: Privacy/compliance audit (@qa-test-engineer)
- [ ] F5.3: Accessibility audit (@qa-test-engineer)
- [ ] F5.4: Benchmark harness scaffold (@qa-test-engineer)
- [ ] F5.5: Update architecture docs (@ai-pipeline-engineer)
- [ ] F5.6: Deprecate old deps — only after validation (@project-architect)
- [ ] F5.7: Real-device validation (user handoff)

## Blockers
- None

## Notes
- F1 and F5 require real-device validation (Snapdragon 680-class, 4 GB RAM). Benchmark harness scaffolded here; user runs on hardware.
- Old dependencies (llama.cpp, ONNX Runtime) kept throughout until F5 passes — rollback safety.
- Rubber-duck critique adopted: added cross-platform embedding parity task, model contract task, parallel F2.3/F2.4 and F4.2/F4.3-F4.5.
