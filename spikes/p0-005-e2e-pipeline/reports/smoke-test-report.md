# P0-005 Smoke Test Report

**Date:** 2026-03-11 20:14 UTC
**Pack:** `../../content-packs/maths-grade6-caps-fractions-v0.1.pack`
**LLM:** `qwen2.5-1.5b-instruct-q4_k_m.gguf`
**Embedding model:** `all-minilm-l6-v2`

## Summary

| Metric | Result | Target | Status |
|--------|--------|--------|--------|
| Questions run | 5 | 5 | ✓ |
| Passed | 5/5 | 5/5 | ✓ |
| Avg total latency | 18.39s | <60s | ✓ |

## Acceptance Criteria

| Criterion | Target | Result |
|-----------|--------|--------|
| [sq01] Pipeline (no crash) | ✓ | ✓ |
| [sq01] Total latency | <60s | 18.67s ✓ |
| [sq01] Embed time | <500ms | 12.7ms ✓ |
| [sq01] Search time | <500ms | 0.064ms ✓ |
| [sq01] Retrieved content | grounded | What is a Fraction? |
| [sq02] Pipeline (no crash) | ✓ | ✓ |
| [sq02] Total latency | <60s | 19.37s ✓ |
| [sq02] Embed time | <500ms | 9.9ms ✓ |
| [sq02] Search time | <500ms | 0.044ms ✓ |
| [sq02] Retrieved content | grounded | Adding Fractions with Different Denominators |
| [sq03] Pipeline (no crash) | ✓ | ✓ |
| [sq03] Total latency | <60s | 19.15s ✓ |
| [sq03] Embed time | <500ms | 8.8ms ✓ |
| [sq03] Search time | <500ms | 0.040ms ✓ |
| [sq03] Retrieved content | grounded | Simplifying Fractions Using HCF |
| [sq04] Pipeline (no crash) | ✓ | ✓ |
| [sq04] Total latency | <60s | 20.31s ✓ |
| [sq04] Embed time | <500ms | 9.5ms ✓ |
| [sq04] Search time | <500ms | 0.049ms ✓ |
| [sq04] Retrieved content | grounded | Fraction Word Problems — Sharing and Remaining |
| [sq05] Pipeline (no crash) | ✓ | ✓ |
| [sq05] Total latency | <60s | 14.45s ✓ |
| [sq05] Embed time | <500ms | 9.9ms ✓ |
| [sq05] Search time | <500ms | 0.052ms ✓ |
| [sq05] Retrieved content | grounded | Types of Fractions |

## Per-Question Results

### sq01: What is a fraction? Can you explain the numerator and denominator with an example?

**Status:** ✓ PASS  
**Total latency:** 18.67s  
**Peak RAM:** 2591 MB  

**Retrieved chunks:**
- [0.7607] `fractions-basics-001` — What is a Fraction?
- [0.6258] `fractions-basics-002` — Types of Fractions
- [0.6183] `fractions-addition-001` — Adding Fractions with the Same Denominator

**Phase breakdown:**

| Phase | Duration | RAM after |
|-------|----------|-----------|
| embed_model_load | 182.4ms | 762 MB |
| embed_query | 12.7ms | 811 MB |
| faiss_load | 0.9ms | 813 MB |
| faiss_search | 0.1ms | 813 MB |
| embed_model_unload | RAM freed: 48 MB | 764 MB |
| llm_load | 810.2ms | 2518 MB |
| llm_generate | 17355.9ms | 2591 MB |

**Generated explanation:**

```
A fraction represents a part of a whole. It is written as one number over another, separated by a line. The top number is called the numerator and tells us how many parts we have. The bottom number is called the denominator and tells us how many equal parts the whole is divided into.

Let's use an example to clarify:

**Example:**
Imagine you have 3 cookies and your friend has 5 cookies.
- **Numerator (numerator):** This represents the part of the whole that we are considering. In this case, it’s 3 because we’re talking about how many cookies I have.
- **Denominator (denominator):** This tells us into how many equal parts the whole is divided. Here, it's 5 because we're dividing the total number of cookies into 5 equal parts.

So, if you and your friend combined all your cookies:
- The fraction representing what you both have together would be \( \frac{3}{5} \
```

### sq02: How do I add 2/5 and 1/3 together? Show me the steps.

**Status:** ✓ PASS  
**Total latency:** 19.37s  
**Peak RAM:** 2593 MB  

**Retrieved chunks:**
- [0.6013] `fractions-addition-002` — Adding Fractions with Different Denominators
- [0.5901] `fractions-addition-001` — Adding Fractions with the Same Denominator
- [0.5812] `fractions-basics-002` — Types of Fractions

**Phase breakdown:**

| Phase | Duration | RAM after |
|-------|----------|-----------|
| embed_model_load | 114.4ms | 812 MB |
| embed_query | 9.9ms | 851 MB |
| faiss_load | 0.8ms | 851 MB |
| faiss_search | 0.0ms | 851 MB |
| embed_model_unload | RAM freed: 45 MB | 806 MB |
| llm_load | 832.1ms | 2523 MB |
| llm_generate | 18114.4ms | 2593 MB |

**Generated explanation:**

```
To add 2/5 and 1/3, follow these steps:

Step 1: Find a common denominator.
The denominators are 5 and 3. The least common multiple (LCM) of 5 and 3 is 15.

Step 2: Convert each fraction to an equivalent fraction with the common denominator.
- For 2/5, multiply both the numerator and the denominator by 3:
  \[
  \frac{2}{5} = \frac{2 \times 3}{5 \times 3} = \frac{6}{15}
  \]
- For 1/3, multiply both the numerator and the denominator by 5:
  \[
  \frac{1}{3} = \frac{1 \times 5}{3 \times 5} = \frac{5}{15}
  \]

Step 3: Add the numerators.
\[
6 + 5 =
```

### sq03: How do I simplify 18/24 to its simplest form?

**Status:** ✓ PASS  
**Total latency:** 19.15s  
**Peak RAM:** 2594 MB  

**Retrieved chunks:**
- [0.6945] `fractions-simplifying-001` — Simplifying Fractions Using HCF
- [0.5207] `fractions-equivalent-001` — Equivalent Fractions
- [0.3925] `fractions-word-problems-002` — Fraction Word Problems — Sharing and Remaining

**Phase breakdown:**

| Phase | Duration | RAM after |
|-------|----------|-----------|
| embed_model_load | 116.2ms | 824 MB |
| embed_query | 8.8ms | 863 MB |
| faiss_load | 0.8ms | 863 MB |
| faiss_search | 0.0ms | 863 MB |
| embed_model_unload | RAM freed: 44 MB | 820 MB |
| llm_load | 819.1ms | 2532 MB |
| llm_generate | 17915.0ms | 2594 MB |

**Generated explanation:**

```
To simplify 18/24 to its simplest form, you need to find the Highest Common Factor (HCF) of both numbers. The factors of 18 are 1, 2, 3, 6, 9, 18, and the factors of 24 are 1, 2, 3, 4, 6, 8, 12, 24. The HCF is 6.

Next, divide both numbers by their HCF:

\[ \frac{18}{24} = \frac{18 ÷ 6}{24 ÷ 6} = \frac{3}{4} \]

So, the simplified form of 18/24 is \( \frac{3}{4} \). This fraction cannot be further simplified because 3 and 4 have no common factors other than 1. Therefore, this is also in its lowest terms or simplest form
```

### sq04: Thandi has R80 and spends 3/4 of it. How much money did she spend?

**Status:** ✓ PASS  
**Total latency:** 20.31s  
**Peak RAM:** 2594 MB  

**Retrieved chunks:**
- [0.1945] `fractions-word-problems-002` — Fraction Word Problems — Sharing and Remaining
- [0.1931] `fractions-word-problems-001` — Finding a Fraction of an Amount
- [0.0967] `fractions-basics-002` — Types of Fractions

**Phase breakdown:**

| Phase | Duration | RAM after |
|-------|----------|-----------|
| embed_model_load | 113.9ms | 825 MB |
| embed_query | 9.5ms | 864 MB |
| faiss_load | 0.8ms | 864 MB |
| faiss_search | 0.0ms | 864 MB |
| embed_model_unload | RAM freed: 44 MB | 821 MB |
| llm_load | 761.2ms | 2532 MB |
| llm_generate | 19137.3ms | 2594 MB |

**Generated explanation:**

```
To find out how much money Thandi spent, we need to calculate 3/4 of R80. Here's the step-by-step process:

1. **Find 1/4 of R80**: 
   - We know that 1/4 means one part out of four equal parts.
   - To find this, divide R80 by 4: \( \frac{R80}{4} = R20 \).

2. **Multiply the result by 3**:
   - Since Thandi spent 3/4, we need to multiply the amount she had (R80) by 3.
   - \( 3 \times R20 = R60 \).

So, Thandi spent R60.

To check our answer: If Thandi had R80 and spent R60, then she has \( R80 - R60 = R20 \) left. This confirms that our calculation is
```

### sq05: What is the difference between a proper fraction and an improper fraction?

**Status:** ✓ PASS  
**Total latency:** 14.45s  
**Peak RAM:** 2595 MB  

**Retrieved chunks:**
- [0.6239] `fractions-basics-002` — Types of Fractions
- [0.5519] `fractions-basics-001` — What is a Fraction?
- [0.4824] `fractions-addition-001` — Adding Fractions with the Same Denominator

**Phase breakdown:**

| Phase | Duration | RAM after |
|-------|----------|-----------|
| embed_model_load | 115.7ms | 825 MB |
| embed_query | 9.9ms | 864 MB |
| faiss_load | 6.9ms | 864 MB |
| faiss_search | 0.1ms | 864 MB |
| embed_model_unload | RAM freed: 44 MB | 821 MB |
| llm_load | 759.5ms | 2533 MB |
| llm_generate | 13270.7ms | 2595 MB |

**Generated explanation:**

```
A proper fraction has a numerator smaller than its denominator, while an improper fraction has a numerator equal to or larger than its denominator. Proper fractions represent less than one whole, whereas improper fractions can represent one whole or more. For example, 2/5 is a proper fraction because the numerator (2) is smaller than the denominator (5), but 7/4 is an improper fraction because the numerator (7) is equal to and larger than the denominator (4). Understanding these differences helps in converting between fractions and mixed numbers. Proper fractions are typically used for parts of a whole, while improper fractions can represent wholes or more.
```
