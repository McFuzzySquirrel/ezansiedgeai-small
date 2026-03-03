# Copilot repository instructions (Engineering Journey System)

Follow the Engineering Journey System (EJS) contracts in this repo:

- Custom agent profile: `.github/agents/ejs-journey.agent.md`

## Session Lifecycle

### At Session Start
When the user begins work, initialize the Session Journey:
- Create Session Journey at `ejs-docs/journey/YYYY/ejs-session-YYYY-MM-DD-<seq>.md`
- Populate initial metadata and problem/intent
- Prepare structure for continuous updates
- Run `python scripts/adr-db.py sync` to ensure the SQLite index is fresh for referencing past decisions

### Throughout Session
Continuously update the Session Journey as work progresses:
- Add interactions to Interaction Summary as they occur
- Record experiments, outcomes, and learnings in real-time
- Document decisions with rationale when made
- Update iteration log with pivots and refinements
- When delegating to sub-agents, capture each sub-agent's decisions, alternatives considered, and handoffs in the Sub-Agent Contributions section
- Use `python scripts/adr-db.py search <query>` to reference past decisions efficiently

### At Session End (wrap up / commit / push / ship)
Finalize the Session Journey:
- Complete all sections with coherent summaries
- Populate all machine extracts
- Set `decision_detected` field appropriately
- Create an ADR at `ejs-docs/adr/NNNN-<kebab-title>.md` only when the decision rubric triggers

## Key Principle
Capture context **incrementally throughout the session**, not reconstructed at the end. This produces better documentation by preserving details when they're fresh.

Do not claim commands/tests ran unless you observed the output.
