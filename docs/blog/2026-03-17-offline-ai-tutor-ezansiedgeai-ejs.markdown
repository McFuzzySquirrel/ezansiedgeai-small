---
layout: post
title: "Building an Offline AI Tutor for South African Learners, and the System I Built So the Learning Does Not Reset"
date: 2026-03-17 23:10:00 +0200
categories: personal update
tags: [edge AI, education, practical tools]
---

*Note: this is still a work in progress, but it is no longer only a spike story. The feasibility work is complete, the Android app foundation exists, the content-pack pipeline is real, and I have learned enough from implementation to revise parts of this post. Vision still matters, but only if it survives contact with devices, packaging, and actual build systems.*

I am testing a simple but high-stakes question: can a learner in South Africa get a useful AI maths tutor on a low-cost Android phone, fully offline, with no subscription and no internet dependency?

This post is about what I have measured so far, what changed once the work moved beyond vision and into implementation, and why I built a companion system to preserve decisions so progress compounds instead of resetting every session.

<!--more-->

This article covers two systems:

1. **eZansiEdgeAI Small**: the solution I am investigating and building to bring AI to the edge of access, with AI agents working from a comprehensive vision.
2. **Engineering Journey System (EJS)**: the system I built to track what AI agents are doing, in collaboration with me and with each other. I now use this in all my projects.

## The tutor I am trying to build

### What am I trying to solve?

South Africa has a learning crisis that predates load-shedding, predates COVID, and predates most technology conversations.

Too many learners sit in under-resourced classrooms, go home to households without reliable internet, and do not have access to a patient, knowledgeable tutor who can explain things in a way that makes sense to them, right now, for free.

Yes, there is a global EdTech boom. But it often bypasses these learners. Many solutions require internet. Cloud AI often requires subscriptions and data plans. Even many so-called offline apps are thin wrappers that still need to phone home when anything interesting happens.

So the question I am trying to answer is simple, but heavily constrained:

> Can a small language model, running entirely on a cheap Android phone, with no internet connection, tutor a learner through a South African CAPS curriculum?

Today. Not in the distant future. Not on expensive hardware most people cannot buy or support.

### Is it actually feasible?

The answer is still **maybe**, but it is a more informed maybe than it was when I started.

The spikes showed that the core technical bet is feasible. Since then, the project has moved beyond isolated experiments into a working Android codebase, documented emulator and sideload testing flows, and a usable content-pack build pipeline.

That does not mean the problem is solved. It means the most dangerous assumptions are no longer hypothetical.

Intent, vision, and specs are important, but feasibility matters more. If it is not feasible, you risk building an impressive but ineffective hobby project.

That is why I am using a spike-driven engineering approach: time-boxed research experiments to reduce risk, clarify requirements, and improve estimates before deeper implementation.

Spikes do not deliver the product directly. They deliver the knowledge needed to build the product honestly.

## Four bets behind the project

I deliberately picked a hard starting point: **Grade 6 CAPS maths**.

### 1. Small models might be good enough if the domain is bounded

A general-purpose LLM must know everything. A curriculum tutor only needs to know bounded material: fractions, decimals, basic geometry, patterns, measurement.

That is a tractable domain.

A smaller model running on-device, grounded by retrieval that feeds the right curriculum chunks, might be sufficient even if benchmark scores are not flashy.

After benchmarking several candidates, I selected **Qwen2.5-1.5B**.

Running Qwen2.5-1.5B locally for the first time, watching it generate coherent maths explanations in under 10 seconds, entirely offline, was a major moment.

The model works. RAM budget fits on 4 GB hardware. The bigger challenges are prompt design, curriculum grounding, and Android runtime integration.

You can review the model tests and results in the repository under `spikes/p0-001-llm-inference/reports/spike-report.md`.

### 2. Retrieval matters more than raw model cleverness

RAG is how I get closer to an accuracy guarantee.

Instead of relying on model memory alone (and risking hallucinations), the plan is to embed CAPS content locally, retrieve relevant chunks, and ground every generation call.

In that setup, the LLM is the reasoning and language engine. The content pack is the source of truth.

Feasibility target:

- Tiny embedding model + local vector store on-device alongside the LLM
- >= 80% retrieval accuracy on curriculum content
- Fit within RAM and storage budgets

After benchmarking candidates, I selected **all-MiniLM-L6-v2** with **FAISS Flat** as the V1 vector store.

You can review those benchmark results in the repository under `spikes/p0-002-embedding-retrieval/reports/spike-report.md`.

This result surprised me: all-MiniLM-L6-v2 achieved **100% top-3 retrieval accuracy** on 20 handcrafted Grade 6 CAPS maths queries.

Fractions, decimals, geometry, and measurement all returned correct chunks in top-3 every time.

That tells me the retrieval architecture is viable before writing Android integration code.

I still expect real-world issues later (for example, curriculum completeness), but feasibility is confirmed.

### 3. The hardware floor is low enough to matter

Original target was 3 GB devices. That left only **209 MB RAM headroom** after loading the LLM, effectively a project-killer constraint.

Embedding/retrieval spike measured actual embedding cost:

- all-MiniLM-L6-v2 peak RAM: **554 MB**

This made the right decision obvious: raise minimum device floor to **4 GB RAM**.

Headroom shifted from **209 MB** to **1,161 MB**. Risk profile changed dramatically.

Current measured feasibility snapshot:

- Qwen2.5-1.5B: **1,839 MB RAM**, ~8 second responses on x86 host
- all-MiniLM-L6-v2: **87 MB on disk**, **554 MB peak RAM**
- Total first-launch download: ~**1.15 GB**

That is feasible on affordable 4 GB devices in the South African market.

### 4. Storage constraints have to be honest

An early constraint was APK <= 150 MB installed.

That was written before Qwen2.5 measurements. Ambitious, but wrong.

Rather than architecting around a false target, I redefined packaging:

- APK <= 50 MB
- First-launch model downloads <= 1.2 GB
- Content packs <= 200 MB each

This mirrors how mobile games handle large asset packs. It is a practical compromise based on measured feasibility.

## What changed once the work got real

When I first wrote this, I was still mostly talking about what I hoped to build.

Now I can point to specific things that exist.

- The Android learner app builds successfully in Android Studio and from the command line
- Emulator and sideload testing runbooks exist for collaborators, because if setup is fragile, collaboration becomes fiction
- The content-pack builder now produces a Grade 6 all-terms seed pack artifact, not just a toy fractions example
- The builder and validator run locally with measurable results, including checksum verification and retrieval evaluation

One nuance matters here.

The app currently runs with **mock AI components by default** on this branch. That means I can validate app structure, content-pack loading paths, screens, flows, and packaging, but not yet claim that the full on-device Android runtime is using the real GGUF + ONNX stack end-to-end.

That distinction is important to me.

I do not want to present staged files as shipped capability, or a clean demo path as proof that the whole loop is production-ready.

## The second system: Engineering Journey System (EJS)

This part is a little harder to explain, but it is equally important.

EJS is a separate project focused on capturing human-to-agent and agent-to-agent collaboration.

I want to know what agents are doing, both for auditability and as a learning system for myself (and hopefully for the agents over time).

You can view current journey records and ADRs for eZansiEdgeAI Small in the repository under `ejs-docs/`.

### Why I built EJS

Decisions are where knowledge lives, not code.

Code is output. The reasoning that produced it, constraints considered, alternatives rejected, and experiments that changed direction can disappear quickly.

Traditionally, this disappears into chats, meetings, or memory. With multiple agents, this problem can grow as context windows and logs evolve.

I wanted to preserve:

- Prompt/response trail and the why behind changes
- Experiments attempted, evidence observed, and pivots made
- Trade-offs considered but not chosen
- Agent influence versus human overrides/corrections

EJS captures this reality with low friction and strong auditability:

- One Session Journey per session: initialized at start, updated throughout, finalized at end
- ADRs only for significant decisions: conditional and numbered to keep the ADR ledger curated
- Repo-portable and tool-agnostic structure: usable in GitHub web, VS Code, and team workflows
- Non-competing observer model: EJS records silently and does not override existing agent instructions

### What EJS captures

- **Architecture Decision Records (ADRs)**: significant decisions with context, alternatives, and consequences
- **Session Journey files**: running logs of what was tried, changed, learned, and suggested (including adoption/rejection)
- **Searchable decision database**: Python indexing of ADRs into a queryable store so new sessions can ask, have we already decided this?

## How this changed the way I work with AI

I use GitHub Copilot as my primary engineering collaborator.

EJS changes the collaboration dynamic in a specific way: Copilot does not just write code. It also documents reasoning during the session.

Pattern reference:
https://github.com/McFuzzySquirrel/Engineering-Journey-System/blob/main/ejs-docs/session-lifecycle-patterns.md

Every decision made in a session gets recorded. ADRs get created when decisions happen, not days later. Benchmark results and iterations are logged while fresh.

The result is auditable AI collaboration.

Example: I can inspect ADR-0007 and see exactly why all-MiniLM-L6-v2 was chosen over bge-small-en-v1.5, based on recorded evidence at decision time.

## What AI-assisted engineering is teaching me

### The quality of output depends on the quality of constraints

With vague briefs, Copilot gives vague plans.

With measurable constraints (RAM in MB, latency in ms, accuracy thresholds), it gives testable plans.

EJS reinforces this discipline. You cannot write a decision record without a reason, and you cannot state the reason without understanding the constraint.

### Human and agent both need memory

A single LLM session is effectively stateless.

It will not reliably remember earlier decisions, rejection rationales, or measured headroom.

EJS session files and ADRs provide persistent memory. Starting each session with that context immediately improves collaboration quality.

### Incremental recording beats end-of-session reconstruction

I tested both.

Reconstructed notes are thin, lose pivots, and miss near-turning points.

Capturing at decision-time is richer, more accurate, and more useful.

### Rejection is as valuable as adoption

The EJS Agent Influence section records both.

That has become some of the highest-value data in the system.

When Copilot proposed tiered device strategies for RAM fallback, I rejected that in favor of a simpler 4 GB floor raise. That rejection and rationale are now explicit, so future sessions do not re-propose it without context.

## The bigger picture

This project sits at the intersection of three priorities:

1. **Equitable access to learning**

The learners this is for do not have easy fallbacks. No tutor, no stable data budget, no guaranteed power.

2. **Honest engineering**

Edge AI in education often demos well on high-end hardware and fails on target devices.

Spike-first methodology (measure first, decide second, build third) is the only approach I trust for this work.

3. **Compounding knowledge**

EJS is a bet that project two benefits from project one, and project three benefits from project two.

Decisions made today should not need to be rediscovered next year.

## What comes next

Phase 0 is complete, and Phase 1 has produced something tangible: a real Android app foundation, a pack format, a builder, a validator, testing runbooks, and a seed Grade 6 all-terms content pack artifact.

So the next questions are narrower, and better.

- Can I load the seed pack reliably into the emulator and real devices and confirm the learner flows behave properly?
- Can I replace the current mock embedding and LLM engines in the Android app container with the real ONNX + llama.cpp-backed runtime?
- Can I expand the current seed content into a genuinely complete Grade 6 CAPS pack with enough density, worked examples, and review quality to be educationally trustworthy?
- Can I validate battery, thermal behavior, and response-time consistency on the kind of phone this project is actually for?

That is a better phase for a project to be in.

The questions are no longer "is any of this possible?"

They are now:

- where is the remaining implementation gap?
- what is still mock versus real?
- what evidence is strong enough to count?

That is slower than hype, but much more useful.

## Why it is called Small

eZansiEdgeAI Small is named intentionally.

eZansi means down below or from the south in Zulu, grounding where this is built and who it is for.

Small means:

- It runs on the phone in a learner's pocket
- It does not require reliable internet, data budgets, or stable power
- It is curriculum-bounded, not trying to know everything, but trying to explain the right thing well enough for a learner right now

In this project, small is the most ambitious thing I can build.

## Why I wrote a real PRD

Up to this point, I had been operating from a detailed project vision that functioned like a PRD.

For the next phase, I need broader collaboration, and that requires a structured source of truth. So I created a consolidated PRD that:

- Unifies vision, architecture decisions (ADR-0001 to ADR-0011), Phase 0 spike results, engineering principles, and product documentation
- Is self-contained enough that developers, AI agents, and stakeholders can understand V1 scope from this document alone

Note: You can view the full PRD in the repository.

## From spike results to a working app foundation

The agent team is no longer theoretical. It has already helped translate the feasibility work into a real Android project structure and a functioning offline learning loop foundation.

Highlights so far:

- **Android app foundation**: multi-module Kotlin project, Compose UI, onboarding, profiles, preferences, topic browser, chat surface, and library flows
- **Content Pack Builder**: SQLite-based chunks -> embeddings -> FAISS -> `.pack` pipeline, now exercised against a Grade 6 all-terms seed pack
- **Validation discipline**: checksum verification, retrieval evaluation, explicit evidence logged into backlog and journey records
- **Testing support**: collaborator runbooks for emulator and sideload testing, including content-pack and model-file staging instructions

The specialized agent team still matters, because this is exactly the kind of project where vague ownership turns into drift.

These are the roles I brought in with clearly scoped ownership:

- Project orchestrator
- Project architect
- Learner data engineer
- Content pack engineer
- AI pipeline engineer
- Android UI engineer
- QA test engineer

As mentioned earlier, this team is highly specialized by design.

That structure has been useful not just for code generation, but for sequencing. Build system work had to land before feature work. Content-pack packaging had to become concrete before testing documentation could be honest. QA thinking had to happen before I pretended anything was finished.

## Working with Copilot as a multi-agent system

Most earlier work happened in VS Code.

For the implementation phase, I started leaning more deliberately on GitHub Copilot's multi-agent workflow patterns so that orchestration, specialization, and traceability could coexist instead of competing.

The important change was not just the interface. It was the operating model.

The Project Orchestrator handled sequencing by dependency and phase objective. Specialist agents handled narrowly scoped implementation work. EJS kept the session memory and decision trail intact while that happened.

That combination turned out to be more useful than I expected. It reduced one of the biggest risks in AI-assisted engineering: apparent momentum without durable context.

If you want to learn how this team was created, see McFuzzy Agent Forge:
https://github.com/McFuzzySquirrel/mcfuzzy-agent-forge

It transforms a PRD into a specialized team of custom agents and reusable skills with clear ownership and collaboration patterns.

There is more in progress, and I will post updates as the work evolves.

## Notes

This article reflects the state of the project as of March 2026.

Repositories:

- eZansiEdgeAI Small (spike results, ADRs, session journals):
  https://github.com/McFuzzySquirrel/ezansiedgeai-small
- Engineering Journey System (EJS):
  https://github.com/McFuzzySquirrel/Engineering-Journey-System
- McFuzzy Agent Forge:
  https://github.com/McFuzzySquirrel/mcfuzzy-agent-forge

Special thanks to Nabeel Prior for encouragement, thoughtful input, and support throughout this journey.

All of my projects exist for one core reason: learning through experimentation.

Each repository starts with questions like: Is this possible? I wonder if...?

Sometimes these projects aim to solve practical problems. Sometimes they are curiosity-driven rabbit holes. All are honest reflections of learning in progress.

Expect some projects to be messy, others structured, all of them real.

Feel free to reuse, modify, and build on anything here in your own repositories.
