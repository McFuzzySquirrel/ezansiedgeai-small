# Dataset Tools

Utilities for **preparing training datasets, fine-tuning data, and generating embeddings** for the eZansiEdgeAI platform.

## Purpose

These tools support the AI pipeline by:

- **Preparing fine-tuning datasets** — curate and format question/answer pairs, worked examples, and curriculum content for SLM fine-tuning.
- **Generating embeddings** — batch-produce vector embeddings from curriculum content for use in content packs and local vector databases.
- **Data validation** — verify dataset quality, curriculum alignment, and coverage against CAPS requirements.
- **Augmentation** — generate synthetic training examples to expand coverage of curriculum topics.

## Language Strategy

eZansiEdgeAI serves a multilingual learner population. The dataset tools must support a layered language approach:

### Phase 1: English Base
- All core curriculum content and Q&A pairs in English
- CAPS Grade 6 Mathematics terminology and notation

### Phase 2: Afrikaans Support
- Parallel Afrikaans translations of core content
- Bilingual Q&A pairs for code-switching support
- Afrikaans mathematical terminology alignment

### Future: African Language Phrase Layer
- Key mathematical terms and phrases in isiZulu, isiXhosa, Sesotho, and other SA languages
- Not full translation — a **phrase layer** that helps learners understand concepts using familiar terms
- Community-driven contribution model (see below)

> The goal is **not** perfect machine translation. It is ensuring that a learner who thinks in isiZulu can understand a maths explanation that uses familiar terms alongside English curriculum language.

## Community Contribution Workflow

> **Placeholder** — to be designed in Phase 2+.

The vision is to enable teachers and community members to contribute:

- Translations of key terms and phrases
- Locally relevant example problems (e.g., using South African contexts)
- Quality reviews of AI-generated content

A lightweight contribution and review pipeline will be designed to make this accessible to non-technical contributors.

## What Goes Here

This directory will contain:

- **Data processing scripts** — Python tools for ingestion, cleaning, formatting
- **Embedding generation pipelines** — batch embedding scripts using the selected model
- **Dataset schemas** — format specifications for training data, Q&A pairs, content chunks
- **Validation tools** — curriculum coverage checks, quality scoring

## Scope

> **Phase 2+** — dataset tooling is developed alongside the content pack builder.
>
> Phase 0 may include lightweight prototypes for generating test embeddings during the on-device inference spike.

## Prerequisites

> To be documented when tooling is implemented. Expected:
> - Python 3.10+
> - Sentence-transformers or similar embedding library
> - pandas / polars for data processing
