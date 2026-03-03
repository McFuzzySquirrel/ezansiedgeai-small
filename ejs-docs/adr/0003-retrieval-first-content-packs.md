---
ejs:
  type: journey-adr
  version: 1.1
  adr_id: "0003"
  title: Retrieval-first content packs as source of truth
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

# ADR 0003: Retrieval-first content packs as source of truth

## Context

The original vision imagined an "offline AI assistant" using a general-purpose local LLM. Research revealed that running large general models on phones with 2-4GB RAM leads to poor performance, overheating, battery drain, and user abandonment. The real breakthrough is not model size — it's Knowledge + Retrieval + Explanation. The model should explain curriculum content, not invent content. This shifts from "Offline LLM" to "Offline Curriculum Knowledge Packs + Local Explanation Engine." This scales knowledge distribution without scaling compute cost.

## Session Intent

Define the knowledge architecture — where does the AI's knowledge come from?

## Decision Trigger

This defines a public contract (the content pack format), introduces a system boundary (content packs as a distinct, versioned data layer), and has long-lived consequences for the entire pipeline (content authoring → pack building → distribution → retrieval → explanation).

## Considered Options

### Option A: Model-memory-first (fine-tuned LLM knows the curriculum)

- Fine-tune model on curriculum content
- Knowledge embedded in model weights
- No retrieval needed at runtime
- Requires fine-tuning infrastructure (expensive, complex)
- Knowledge hard to update (retrain model)
- Hallucination risk — model may generate plausible but wrong content
- Model size increases with knowledge scope

### Option B: Retrieval-first (content packs + explanation engine)

- Curriculum knowledge stored in versioned content packs
- Embeddings enable semantic retrieval from local vector DB
- Small LLM explains retrieved content (doesn't need to "know" it)
- Content is the source of truth — model explains, doesn't invent
- Content updates = new pack version, not model retrain
- Much smaller model required (explanation vs. knowledge)
- Hallucination reduced — grounded in retrieved content

### Option C: Hybrid (fine-tuned model + retrieval augmentation)

- Fine-tune for base knowledge, RAG for specifics
- Most complex approach
- Still requires fine-tuning infrastructure
- Unclear when model uses memory vs. retrieval

## Decision

Adopt retrieval-first architecture with versioned content packs as the source of truth. The model explains content retrieved from local curriculum packs — it does not invent or generate content from its own weights.

## Rationale

Retrieval-first eliminates the need for expensive fine-tuning, reduces hallucination risk by grounding responses in verified curriculum content, allows content updates without model retraining (just ship a new pack), and requires a much smaller model (explanation capability vs. full knowledge). This scales through content, not infrastructure. A new subject or grade is a new content pack, not a new model. Teachers can trust the system because the content comes from verified CAPS-aligned material, not model imagination.

## Consequences

### Positive

- Smaller models work (explanation only, not knowledge storage)
- Content updates are pack version updates, not model retrains
- Hallucination risk reduced — grounded in retrieved content
- Teacher trust increased (content is verifiable curriculum material)
- Scales via content (new pack per subject/grade) not compute
- Content can be community-authored and reviewed

### Negative/Trade-offs

- Content pack creation requires a build pipeline (Phase 2)
- Local vector DB adds storage footprint
- Retrieval quality depends on embedding model and chunk strategy
- Explanations are limited to what's in the pack — no general knowledge
- Pack format becomes a public contract that must be maintained

## Key Learnings

- The real product innovation is "Offline Curriculum Knowledge Packs + Local Explanation Engine" — not an "Offline LLM"
- Separating content (packs) from capability (model) enables independent scaling of each
- Teacher trust is a first-class design goal — grounded content builds trust, hallucinated content destroys it

## Agent Guidance

- The model MUST NOT generate answers from its own knowledge — always retrieve from content packs first
- Prompt templates must include retrieved context and instruct the model to explain ONLY the provided content
- If retrieval returns no relevant content, say "I don't have information on that topic" — never fabricate
- Content packs are versioned and immutable once distributed (append-only updates via delta packs)
- Pack format is a contract: changes require an ADR
- Embedding model must be small enough for phone (all-MiniLM-L6-v2, INT8 quantized, ~25MB)
- Vector DB: sqlite-vec (SQLite extension, no external dependencies)
