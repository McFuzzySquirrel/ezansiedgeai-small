---
name: ejs-story-builder
description: >
  Build a complete end-to-end project story from EJS journey and ADR files.
  Use when: "build story", "generate story", "tell the story", "project narrative",
  "end-to-end story", "full story", "what happened", "project history",
  "summarize all sessions". Reads journey sessions and ADRs, synthesizes a
  chronological narrative covering what was built, how it was built, and
  collaboration perspectives (human, human+agent, agent, agent-to-agent).
  Outputs to chat and saves to ejs-docs/narratives/.
---

# EJS Story Builder

Build a complete end-to-end project story from all (or filtered) EJS journey
and ADR files. The story covers what was built, how it was built, and weaves
in collaboration perspectives: human, human + agent, agent, and agent-to-agent.

> **Output**: The story is displayed in chat AND saved to
> `ejs-docs/narratives/story-YYYY-MM-DD.md`. Always regenerate the full
> narrative — do not attempt incremental updates to an existing story file.

## Steps

### 0. Identify the Human / Lead Perspective

Before generating the narrative, determine how to identify the primary decision-maker
or lead engineer throughout the story. Ask the user:

**"How would you like the human/lead perspective to be identified in this narrative?"**

Suggested options:
- **Personal name**: "Alice worked with the Copilot agent..." → Use actual first or full name
- **GitHub handle**: "@alice-dev identified the gap" → Use GitHub username in bold
- **Team name**: "The backend team" or "Team Alpha" → For group-led projects
- **Role descriptor**: "The lead engineer" or "The founder" → Generic but clear
- **Generic + Note**: "McFuzzySquirrel" with a customization note in the prologue

Default: If the user doesn't specify, check the earliest journey file's `author` field
and use that name. If no author is populated, use a generic descriptor: "The lead engineer".

Store this identifier as `HUMAN_IDENTIFIER` and use it throughout the narrative
in place of generic "the human" / "The human's" phrasing.

Optional: If the user provides their own identifier, ask if they want a customization
note added to the Prologue explaining that this story template can be adapted by others.

### 1. Determine Scope

Check if the user specified any filters. If not, include everything.

Supported filters (all optional):
- **Date range**: `from: YYYY-MM-DD` and/or `to: YYYY-MM-DD`
- **Tags**: match against journey frontmatter `tags` field
- **Session IDs**: explicit list (e.g., `ejs-session-2026-04-09-01`)
- **ADR IDs**: explicit ADR numbers to anchor the story around (e.g., `0013, 0016`)

If no filters are provided, scope is **all** journey and ADR files.

### 2. Gather the Index

Use whichever approach provides the best coverage:

**Option A — DB-first (preferred when available):**
```bash
python3 scripts/adr-db.py story
```
This returns a high-level narrative index: intent, key decision, key learning,
and ADR links per session. Use this as your roadmap, then read full files for
detail.

**Option B — File-first (fallback):**
Scan `ejs-docs/journey/` for all `ejs-session-*.md` files and `ejs-docs/adr/`
for all numbered ADR files (`0010-*.md`, `0011-*.md`, etc.). Skip the
`0000-adr-template.md` template and `_templates/` directory.

### 3. Read Source Files

For each session and ADR in scope, read the full markdown file. Extract:

**From journey files:**
| Section | What to extract |
|---------|----------------|
| YAML frontmatter | `session_id`, `date`, `agents_involved`, `tags`, `adr_links` |
| Problem / Intent | The purpose of the session |
| Interaction Summary | The collaboration trail — who said/did what |
| Agent Collaboration Summary | Which agents participated, key suggestions |
| Sub-Agent Contributions | Per-agent: task, decisions, alternatives, outcome, handoffs |
| Agent Influence | Suggestions adopted vs. rejected, human overrides |
| Experiments / Evidence | What was tried, what happened, what changed thinking |
| Iteration Log | Pivots, reversals, refinements |
| Decisions Made | Decision, reason, impact |
| Key Learnings | Technical, prompting, and tooling insights |
| Machine Extracts | `INTERACTION_EXTRACT`, `DECISIONS_EXTRACT`, `LEARNING_EXTRACT`, `AGENT_GUIDANCE_EXTRACT`, `SUB_AGENT_EXTRACT` |

**From ADR files:**
| Section | What to extract |
|---------|----------------|
| YAML frontmatter | `adr_id`, `title`, `date`, `status`, `session_id`, actors |
| Context | Problem/opportunity that triggered this |
| Session Intent | What the human intended |
| Collaboration Summary | How human and agent collaborated |
| Considered Options | Options A, B, C with descriptions |
| Decision | The decision made |
| Rationale | Why, trade-offs, rejected alternatives |
| Consequences | Positive and negative |
| Key Learnings | Transferable knowledge |

### 4. Build Chronological Timeline

1. Order all sessions by date (from YAML frontmatter `date` field)
2. Map ADRs to their originating sessions via `session_id` / `session_journey` cross-references
3. **Group related sessions** into narrative arcs:
   - Sessions on the same date working toward the same goal → group as one arc
   - Sessions across dates on the same feature/theme → group as one arc
   - Use Problem/Intent similarity and `tags` overlap to identify groupings
   - Solo sessions that stand alone are their own arc

### 5. Extract Perspectives

As you read each session, tag content by perspective. These perspectives are
woven into the chronological narrative — they are NOT separate sections.

| Perspective | Where to find it | What it reveals |
|-------------|-----------------|-----------------|
| **Human** | Problem/Intent, human-initiated pivots in Iteration Log, rejected suggestions in Agent Influence | Why this work mattered, what the human prioritized, where the human overrode the agent |
| **Human + Agent** | Interaction Summary (the back-and-forth), adopted suggestions in Agent Influence, collaborative experiments | How the partnership worked — ideas proposed, refined, accepted |
| **Agent** | Agent Collaboration Summary, agent-initiated decisions, tool usage, experiment execution | What the agent contributed independently — analysis, code generation, research |
| **Agent-to-Agent** | Sub-Agent Contributions, handoff chains (SA1→SA2→SA3), inter-agent disagreements | How agents coordinated — delegation, dependency chains, conflict resolution |

### 6. Generate the Narrative

Write the story using the [narrative template](./assets/narrative-template.md)
as the output structure. Follow these guidelines:

#### Voice and Style
- **Third-person narrative voice** — not first-person ("The team..." not "I...")
- **Factual and evidence-based** — every claim must be traceable to a specific journey session or ADR
- **Perspectives woven naturally** into the chronological flow:
  - "The human set out to solve X. Agent [name] suggested Y, which was adopted because Z."
  - "A sub-agent was delegated the research task and surfaced finding W, which shifted the approach."
  - "The human rejected the agent's proposal for A, preferring B because..."
- **Decisions as story backbone** — decisions are the turning points of the narrative, not incidental details
- **Pivots as plot points** — when direction changed, highlight what triggered the change and the before/after

#### Narrative Arc Structure
For each arc (grouped sessions), write:

1. **What was happening** — the intent/problem (from Problem/Intent sections)
2. **How it unfolded** — key interactions, experiments, pivots, weaving in all relevant perspectives
3. **What was decided** — decisions with rationale (link to ADR if one was created)
4. **What was learned** — key insights that carried forward

#### Grouping Rules
- **Same-day sessions** on related work → combine into one arc with a unifying title
- **Multi-day arcs** for features that span sessions → group under a theme title
- **Standalone sessions** → their own arc
- Within each arc, maintain chronological order of individual sessions
- Reference individual session IDs in parentheses so readers can trace back

#### Connecting the Arcs
- Between arcs, add a brief transition that shows how one arc's outcome led to the next
- Track how decisions compound — show how early decisions constrained or enabled later ones
- Surface recurring themes (e.g., "the simplification pattern appeared again...")

### 7. Write the Output

1. **Display the full narrative in chat** for the user to review
2. **Save to file**: `ejs-docs/narratives/story-YYYY-MM-DD.md` (using today's date)
   - Create the `ejs-docs/narratives/` directory if it doesn't exist
3. **Confirm** with a summary:
   - Filename and path
   - Number of sessions covered
   - Number of ADRs referenced
   - Date range of the story
   - Approximate word count

## Contextual References

- Journey template: `ejs-docs/journey/_templates/journey-template.md`
- ADR template: `ejs-docs/adr/0000-adr-template.md`
- Database tool: `scripts/adr-db.py` (`story` command for index, `search` for queries)
- Narrative output template: [narrative-template.md](./assets/narrative-template.md)
- Session lifecycle patterns: `ejs-docs/session-lifecycle-patterns.md`

## Key Principle

The story makes the implicit explicit. Every session captured a moment of
engineering collaboration. The story connects those moments into a coherent
narrative — showing not just what was built, but how humans and agents
built it together, what they learned, and where it stands now.
