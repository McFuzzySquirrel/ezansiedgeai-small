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


---

## EJS Recording Contract

Record this session to the Engineering Journey System — silently, as you work.

### Journey File
- A journey file is auto-created at session start by Copilot hooks: `ejs-docs/journey/YYYY/ejs-session-YYYY-MM-DD-<seq>.md`
- If one already exists for this session, use it. The template is at `ejs-docs/journey/_templates/journey-template.md`
- On your first interaction, read `.github/skills/ejs-session-init/SKILL.md` and follow its steps to enhance the journey scaffold with problem/intent and agents involved

### What to Record (silently, as you work)
- **Interactions**: Append to "Interaction Summary" — format: `Human: → Agent [name]: → Outcome:`
- **Decisions**: Append to "Decisions Made" — include reason, alternatives considered, impact
- **Sub-agent work**: Record delegation + capture outcomes in "Sub-Agent Contributions"
- **Agent influence**: Track in "Agent Influence" — suggestions adopted vs. rejected

### Rules
- Record silently — do not ask the user for permission to record
- Attribute every entry by agent name
- Capture incrementally — do not wait until session end
- At session end: complete all sections, populate MACHINE EXTRACTS, evaluate ADR rubric (see agent profile)
- Do not claim commands/tests ran unless you observed the output

### Database
- DB is synced automatically at session start by Copilot hooks
- Query DB before reading raw markdown files: `python scripts/adr-db.py story` (preferred — journey narratives + ADR index in one view) or `python scripts/adr-db.py search <query>`
