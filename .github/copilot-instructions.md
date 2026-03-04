# Copilot repository instructions (Engineering Journey System)

Follow the Engineering Journey System (EJS) contracts in this repo.

Full agent profile: `.github/agents/ejs-journey.agent.md`

---

## EJS Silent Recording Contract (Always-On)

> **This block is designed to be appended to any repo's existing copilot-instructions.md.**
> It injects recording behavior into whatever agent is currently active — no agent selection needed.
> It does not compete with or override existing agent instructions.

### What You Must Do (Silently, As You Work)

As the active agent in this session, you are responsible for recording your collaboration to the Session Journey file. Do this **automatically as part of your normal work** — do not prompt the user for permission to record.

#### 1. Session Journey File
- If a Session Journey file exists for today under `ejs-docs/journey/YYYY/ejs-session-YYYY-MM-DD-<seq>.md`, update it.
- If none exists, create one from the template at `ejs-docs/journey/_templates/journey-template.md`.
- Use session ID format: `ejs-session-YYYY-MM-DD-<seq>` (2-digit daily sequence).

#### 2. Record Interactions
As each interaction occurs, append to the **Interaction Summary** section:
```
- Human: <prompt / request>
  - Agent [your-name]: <response summary>
  - Outcome: <what changed / what was decided>
```
Attribute every entry by agent name. If you delegate to a sub-agent, record that too.

#### 3. Record Decisions Automatically
When you make or recommend a decision (approach choice, dependency selection, architecture shift, trade-off resolution):
- Add it to the **Decisions Made** section with: decision, reason, alternatives considered, impact
- Do this at the moment the decision occurs — do not wait until session end

#### 4. Record Experiments and Pivots
- When you try something and observe a result, add it to **Experiments / Evidence**
- When the approach changes, add it to **Iteration Log** with what triggered the pivot

#### 5. Record Sub-Agent Work
When you delegate to a sub-agent:
- Log the delegation in the Interaction Summary (what task, which sub-agent, what context)
- After the sub-agent completes, capture its contribution in the **Sub-Agent Contributions** section:
  - Task delegated
  - Decisions made (with rationale)
  - Alternatives considered
  - Outcome
  - Handoff to other agents
- Instruct sub-agents to use the EJS ADR template (`ejs-docs/adr/0000-adr-template.md`) and place ADRs under `ejs-docs/adr/` — do not let sub-agents create conflicting templates or instruction files

#### 6. Record Agent Influence
Track in the **Agent Influence** section:
- Which of your suggestions the human adopted
- Which the human rejected (and why, if stated)
- Human overrides or corrections

### What You Must NOT Do
- Do not prompt the user to "capture this decision" — capture it yourself silently
- Do not interrupt the implementation workflow to ask about recording
- Do not skip recording because the decision seems "obvious"
- Do not fabricate rationale — if the reason is unclear, record "rationale not stated"

### At Session End (wrap up / commit / push / ship)
When the user signals session end:
- Complete all sections with coherent summaries
- Populate all machine extracts (`INTERACTION_EXTRACT`, `DECISIONS_EXTRACT`, `LEARNING_EXTRACT`, `AGENT_GUIDANCE_EXTRACT`, `SUB_AGENT_EXTRACT`)
- Set `decision_detected` field appropriately
- Create an ADR at `ejs-docs/adr/NNNN-<kebab-title>.md` only when the decision rubric triggers (see `.github/agents/ejs-journey.agent.md` for the full rubric)

### Key Principle
Capture context **incrementally throughout the session**, not reconstructed at the end. This produces better documentation by preserving details when they're fresh.

### EJS Database (Optional)
- Run `python scripts/adr-db.py sync` at session start to refresh the decision index
- Use `python scripts/adr-db.py search <query>` to reference past decisions efficiently

Do not claim commands/tests ran unless you observed the output.
