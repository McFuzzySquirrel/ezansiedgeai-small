---
name: create-content-chunk
description: >
  Author a CAPS-aligned Markdown content chunk for an eZansiEdgeAI content pack.
  Use this skill each time a new curriculum topic chunk is created — there will be
  3+ chunks and 2+ worked examples per Grade 6 Maths topic across Terms 1–4.
---

# Create Content Chunk

Creates a single Markdown content chunk file formatted for the eZansiEdgeAI content pack builder (`build_pack.py`). Each chunk covers one concept within a CAPS Grade 6 Mathematics topic.

## When to Use

Use this skill whenever authoring curriculum content for a content pack. Each Grade 6 Maths topic requires:
- ≥ 3 content chunks (conceptual explanation, procedure, common misconceptions)
- ≥ 2 worked examples (step-by-step solutions with real-world context)

This skill is used **many times** — once for every chunk across all CAPS Terms 1–4.

## Inputs

Before creating a chunk, you need:

1. **CAPS topic code** — e.g., `G6-T1-NS-FRACTIONS` (Grade 6, Term 1, Number-Sense, Fractions)
2. **Term** — 1, 2, 3, or 4
3. **Strand** — Numbers-Operations-Relationships, Patterns-Functions, Space-Shape, Measurement, Data-Handling
4. **Topic** — Specific topic name (e.g., "Common Fractions", "Decimal Fractions")
5. **Chunk type** — `concept`, `procedure`, `misconception`, `worked-example`
6. **Difficulty** — `basic` or `intermediate`
7. **Content** — The actual mathematical explanation or worked example

## Output Format

Each chunk is a single Markdown file with YAML frontmatter, placed in `tools/content-pack-builder/content/`:

### File naming convention

```
{term}-{strand-abbrev}-{topic-slug}-{type}-{seq}.md
```

Example: `t1-ns-common-fractions-concept-01.md`

### File template

```markdown
---
caps_code: "{CAPS topic code}"
term: {1-4}
strand: "{strand name}"
topic: "{topic name}"
chunk_type: "{concept|procedure|misconception|worked-example}"
difficulty: "{basic|intermediate}"
title: "{Short descriptive title}"
---

# {Title}

{Content body — clear, concise mathematical explanation.}

{For concepts: define the concept, explain what it means, connect to prior knowledge.}

{For procedures: step-by-step method, when to use it, how to check your answer.}

{For misconceptions: what learners commonly get wrong and why, with correction.}

{For worked examples: real-world context → mathematical setup → step-by-step solution → answer check.}
```

### Worked Example Template

```markdown
---
caps_code: "G6-T1-NS-FRACTIONS"
term: 1
strand: "Numbers, Operations and Relationships"
topic: "Common Fractions"
chunk_type: "worked-example"
difficulty: "basic"
title: "Sharing a pizza equally"
---

# Sharing a pizza equally

**Problem:** Thandiwe and 3 friends are sharing a pizza equally. What fraction of the pizza does each person get?

**Step 1:** Count the total number of people sharing.
Thandiwe + 3 friends = 4 people

**Step 2:** Write the fraction.
Each person gets 1 out of 4 equal parts = 1/4

**Step 3:** Check your answer.
4 × 1/4 = 4/4 = 1 whole pizza ✓

**Answer:** Each person gets **1/4** of the pizza.
```

## Content Guidelines

### Language
- Use **Grade 4 reading level English** — short sentences, simple words
- Define mathematical terms when first introduced
- Avoid jargon; prefer "shared equally" over "partitioned equitably"

### Examples
- Use **South African real-world contexts**: cooking, sharing food, building houses, walking to school, buying at the tuck shop, measuring cloth
- Reference personas: Thandiwe (cooking examples), Sipho (sports/sharing examples)
- Use Rand (R) for money examples, kilometres for distance, litres/ml for volume

### Mathematical Notation
- Use plain text fractions: `1/4`, `3/8`, `2/5`
- Use basic operators: `+`, `-`, `×`, `÷`
- Wrap complex notation in LaTeX-lite: `$\frac{3}{4}$`
- Always show the working, never just the answer

### Quality Checks
- [ ] Mathematically correct (no errors in calculations or definitions)
- [ ] CAPS-aligned (matches the official curriculum document)
- [ ] Age-appropriate language (Grade 4 reading level)
- [ ] South African context (local examples, ZAR currency, metric units)
- [ ] Self-contained (makes sense without reading other chunks)
- [ ] Frontmatter complete (all required fields present)

## CAPS Grade 6 Mathematics Strands

For reference, the CAPS strands and typical topics:

| Strand | Topics |
|--------|--------|
| Numbers, Operations & Relationships | Whole numbers, Common fractions, Decimal fractions, Percentages |
| Patterns, Functions & Algebra | Numeric patterns, Geometric patterns, Number sentences |
| Space & Shape | 2-D shapes, 3-D objects, Symmetry, Views |
| Measurement | Length, Mass, Capacity, Time, Temperature, Perimeter, Area |
| Data Handling | Collecting data, Representing data, Interpreting data, Probability |
