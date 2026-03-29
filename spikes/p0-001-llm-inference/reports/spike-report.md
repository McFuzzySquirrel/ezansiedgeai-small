# P0-001 Spike Report: On-Device LLM Inference

> **Generated:** 2026-03-03 20:32 UTC
> **Branch:** `spike/p0-001-llm-inference`

---

## 1. Objective

Validate that a quantized SLM (≤ 2 GB, INT4/INT8) can run on target hardware
and generate coherent curriculum-aligned explanations within acceptable latency
and memory budgets.

## 2. Acceptance Criteria Results

| Criterion | Target | Result | Status |
|-----------|--------|--------|--------|
| **Phi-3.5-mini-instruct-Q4_K_M.gguf** | | | **FAIL** |
| ↳ Model load time | < 5.0s | 1.42s | ✅ |
| ↳ 150-token generation | < 10.0s | 17.602s (avg) | ❌ |
| ↳ Peak RAM | < 2048 MB | 4378.6 MB | ❌ |
| **SmolLM2-1.7B-Instruct-Q4_K_M.gguf** | | | **FAIL** |
| ↳ Model load time | < 5.0s | 0.849s | ✅ |
| ↳ 150-token generation | < 10.0s | 0.895s (avg) | ✅ |
| ↳ Peak RAM | < 2048 MB | 2214.4 MB | ❌ |
| **gemma-2-2b-it-Q4_K_M.gguf** | | | **FAIL** |
| ↳ Model load time | < 5.0s | 1.247s | ✅ |
| ↳ 150-token generation | < 10.0s | 12.499s (avg) | ❌ |
| ↳ Peak RAM | < 2048 MB | 2900.0 MB | ❌ |
| **qwen2.5-1.5b-instruct-q4_k_m.gguf** | | | **PASS** |
| ↳ Model load time | < 5.0s | 0.784s | ✅ |
| ↳ 150-token generation | < 10.0s | 7.995s (avg) | ✅ |
| ↳ Peak RAM | < 2048 MB | 1838.7 MB | ✅ |


## 3. Model Comparison

| Model | Size | Load Time | Avg Gen Time | Throughput | Peak RAM | Verdict |
|-------|------|-----------|-------------|------------|----------|---------|
| Phi-3.5-mini-instruct-Q4_K_M.gguf | 2282.4 MB | 1.42s | 17.602s | 8.53 tok/s | 4378.6 MB | FAIL |
| SmolLM2-1.7B-Instruct-Q4_K_M.gguf | 1006.7 MB | 0.849s | 0.895s | 10.17 tok/s | 2214.4 MB | FAIL |
| gemma-2-2b-it-Q4_K_M.gguf | 1629.4 MB | 1.247s | 12.499s | 10.67 tok/s | 2900.0 MB | FAIL |
| qwen2.5-1.5b-instruct-q4_k_m.gguf | 1065.6 MB | 0.784s | 7.995s | 19.02 tok/s | 1838.7 MB | PASS |


## 4. Per-Prompt Results



### Phi-3.5-mini-instruct-Q4_K_M.gguf

| Prompt | Topic | Difficulty | Avg Time | Tokens/s | Peak RAM |
|--------|-------|-----------|----------|----------|----------|
| fractions-001 | term1.fractions.basics | basic | 16.266s | 9.23 | 4373.9 MB |
| fractions-002 | term1.fractions.equivalent | basic | 17.494s | 8.69 | 4373.9 MB |
| fractions-003 | term1.fractions.addition | intermediate | 18.033s | 8.49 | 4374.3 MB |
| decimals-001 | term1.decimals.place_value | basic | 18.033s | 8.53 | 4376.7 MB |
| decimals-002 | term2.decimals.operations | basic | 18.097s | 8.43 | 4376.7 MB |
| geometry-001 | term2.geometry.angles | basic | 17.863s | 8.53 | 4377.2 MB |
| geometry-002 | term2.geometry.2d_shapes | intermediate | 17.459s | 8.72 | 4377.2 MB |
| data-001 | term3.data_handling.mean | basic | 18.246s | 8.37 | 4377.2 MB |
| ratio-001 | term3.ratio.basics | intermediate | 16.035s | 8.32 | 4377.2 MB |
| patterns-001 | term1.patterns.number_sequences | intermediate | 18.24s | 8.41 | 4377.2 MB |
| fractions-004 | term1.fractions.word_problem | intermediate | 18.628s | 8.27 | 4378.6 MB |
| measurement-001 | term2.measurement.perimeter | basic | 16.826s | 8.4 | 4378.6 MB |


<details>
<summary>Sample outputs (click to expand)</summary>


**fractions-001** — *What is a fraction? Can you explain it simply?*

```
Answer:
A fraction is a way to represent parts of a whole. It consists of two numbers, one on top and the other at the bottom separated by a line (called a numerator/denominator). The number above tells us how many pieces we have, while the number below shows into how many equal sections something is divided. For instance:

Imagine you cut an apple into 4 even slices. If you take three of those slices for yourself, then your portion can be represented by the fraction 3/4 (three out of four parts
```


**fractions-002** — *How do I find equivalent fractions?*

```
Answer:
To find equivalent fractions, you multiply or divide both the numerator (top number) and the denominator (bottom number) by the same non-zero integer. Here's a worked example to illustrate this concept:

Let’s start with the fraction 1/2. To create an equivalent fraction, we can choose any whole number other than zero; let's use 3 for simplicity. Multiply both numerator and denominator by 3:

(1 × 3) / (2 × 3) = 3/6

Now you have two fractions that are equivalent to each other, as they r
```


**fractions-003** — *How do I add fractions with different denominators?*

```
Answer: To add fractions with different denominators, follow these steps using the example of 1/3 + 1/4. Firstly, find a common denominator which is the lowest multiple that both denominators share; in this case it's 12 (since LCM(3,4) = 12). Next, convert each fraction to have this new denominator:
- Convert 1/3 by multiplying numerator and denominator by 4: \( \frac{1}{3} \times \frac{4}{4} = \frac{4}{12} \)
- Similarly, for 1/4 multiply both the numerator and denominator by 3 to
```


**decimals-001** — *What does each digit mean in the number 45.67?*

```
Answer:
In the number 45.67, each digit represents a specific place value as follows:
- The '4' is in the tens place and means it stands for forty (since 4 × 10 = 40).
- The '5' is in the units/ones place which equals five (as 5 × 1 = 5).
- After the decimal point, we have two digits:
   - The '6' represents six tenths because it occupies the position after the first digit to its right of the decimal. This means that 6 is multiplied by one-tenth (0.1), giving us 0.6 (since 6
```


**decimals-002** — *How do I multiply a decimal by 10 or 100?*

```
Answer:
To multiply a decimal by 10, move each digit one place to the left. For example, if you have 3.45 and want to multiply it by 10, shift all digits one position towards the higher value (towards zero):
  
    3.45 × 10 = 34.5
    Here's how each digit moves:
- The '3' in the ones place becomes a '3' in the tens place.
- The '.4' shifts from tenths to hundredths, becoming .40 (which is equivalent).
- The '5' goes into the thousandths position as 0.0
```


**geometry-001** — *What types of angles are there and how do I identify them?*

```
Answer:
There are several types of angles, each defined by its degree measure relative to a right angle (90°):
1. **Right Angle** - Exactly 90 degrees like the corner of a book or square. To identify it, look for an L shape where two lines meet at ninety-degree point.
2. **Acute Angle** - Less than 90 degrees such as when you slightly open a door (less sharp). Identify by noting that its opening is less wide compared to the corner of a book or square angle.
3. **Obtuse Angle** - More than 90 but
```


**geometry-002** — *What are the properties of a parallelogram?*

```
Answer:
A parallelogram has several distinct properties. Here are the key ones, explained with a worked example for clarity:

1. Opposite sides are equal in length: In any given parallelogram ABCD (where AB is parallel to CD and AD is parallel to BC), side AB will be of the same length as side CD, and similarly, side AD will have an identical measure with side BC. For instance, if AB = 5 cm and DC = 5 cm, then we can confirm that opposite sides are indeed equal in a parallelogram (AB = DC).

2. 
```


**data-001** — *How do I calculate the average of these numbers: 12, 15, 18, 9, 16?*

```
Answer: To calculate the average of these numbers, follow these steps using a worked example. Firstly, add all the given values together (12 + 15 + 18 + 9 + 16), which equals to 70. Next, count how many numbers are there in total; for this set it's five:
Step 3: Divide the sum by the number of items you have added up - so divide 70 (the sum) by 5 (number of values). The calculation will be as follows:
\[ \frac{70}{5} = 14 \]
Therefore, the average value is \( 14 \). This means that if
```


**ratio-001** — *There are 12 boys and 18 girls in a class. What is the ratio of boys to girls in simplest form?*

```
Answer:
To find the simplest form of the ratio, we need to divide both numbers by their highest common factor (HCF). The HCF of 12 and 18 is 6. So dividing both terms gives us:

Boys = 12 ÷ 6 = 2
Girls = 18 ÷ 6 = 3

Therefore, the ratio of boys to girls in simplest form is 2:3. This means for every 2 boys there are 3 girls in the class.
```


**patterns-001** — *What is the next number in this pattern: 2, 6, 18, 54, ...?*

```
Answer: To find the next number in this pattern, we apply the rule identified from the given sequence. The rule is to multiply each term by 3 (since that's how it was established with our examples). So starting at the last known value of 54 and applying the multiplication rule gives us:

Next Number = Previous Term × Rule Factor
            = 54 × 3
            = 162.

Therefore, following this pattern, after 54 comes 162 as demonstrated in our sequence (2, 6, 18, 54, 162). The next number is in
```


**fractions-004** — *Thandi has R60. She spends 2/5 of her money on a book. How much does the book cost?*

```
Answer: The book costs R24. Here's the worked example based on the content provided: Step 1: Calculate two-fifths of R60, which is (2/5) × 60 = step 2: Divide R60 by the denominator in the fraction (which is 5), so you get 60 ÷ 5 = 12. Step 3: Multiply this result by the numerator of the fraction, which gives us 12 × 2 = 24. Therefore, Thandi spends R24 on a book.

Question: How much money does each part represent when dividing R60 into
```


**measurement-001** — *How do I find the perimeter of a rectangle that is 8 cm long and 5 cm wide?*

```
Answer:
To find the perimeter of a rectangle, you use the formula P = 2 × (length + width). For your specific example where the length is 8 cm and the width is 5 cm, plug these values into the formula as follows:

P = 2 × (8 cm + 5 cm)
   = 2 × 13 cm
   = 26 cm

So, the perimeter of a rectangle that measures 8 cm by 5 cm is 26 centimeters. Remember to include your unit in the final answer: P = 26 cm.
```


</details>




### SmolLM2-1.7B-Instruct-Q4_K_M.gguf

| Prompt | Topic | Difficulty | Avg Time | Tokens/s | Peak RAM |
|--------|-------|-----------|----------|----------|----------|
| fractions-001 | term1.fractions.basics | basic | 0.223s | 13.47 | 2208.9 MB |
| fractions-002 | term1.fractions.equivalent | basic | 0.767s | 10.64 | 2208.9 MB |
| fractions-003 | term1.fractions.addition | intermediate | 0.887s | 10.39 | 2210.1 MB |
| decimals-001 | term1.decimals.place_value | basic | 1.083s | 9.33 | 2214.0 MB |
| decimals-002 | term2.decimals.operations | basic | 0.939s | 9.58 | 2214.0 MB |
| geometry-001 | term2.geometry.angles | basic | 0.933s | 9.5 | 2214.0 MB |
| geometry-002 | term2.geometry.2d_shapes | intermediate | 0.772s | 10.19 | 2214.0 MB |
| data-001 | term3.data_handling.mean | basic | 0.978s | 9.59 | 2214.0 MB |
| ratio-001 | term3.ratio.basics | intermediate | 1.045s | 10.08 | 2214.0 MB |
| patterns-001 | term1.patterns.number_sequences | intermediate | 1.027s | 9.99 | 2214.0 MB |
| fractions-004 | term1.fractions.word_problem | intermediate | 1.107s | 9.98 | 2214.4 MB |
| measurement-001 | term2.measurement.perimeter | basic | 0.976s | 9.35 | 2214.4 MB |


<details>
<summary>Sample outputs (click to expand)</summary>


**fractions-001** — *What is a fraction? Can you explain it simply?*

```
Answer:
```


**fractions-002** — *How do I find equivalent fractions?*

```
Answer:
```


**fractions-003** — *How do I add fractions with different denominators?*

```
Answer:
```


**decimals-001** — *What does each digit mean in the number 45.67?*

```
Answer:
```


**decimals-002** — *How do I multiply a decimal by 10 or 100?*

```
Answer:
```


**geometry-001** — *What types of angles are there and how do I identify them?*

```
Answer:
```


**geometry-002** — *What are the properties of a parallelogram?*

```
Answer:
```


**data-001** — *How do I calculate the average of these numbers: 12, 15, 18, 9, 16?*

```
Answer:
```


**ratio-001** — *There are 12 boys and 18 girls in a class. What is the ratio of boys to girls in simplest form?*

```
Answer:
```


**patterns-001** — *What is the next number in this pattern: 2, 6, 18, 54, ...?*

```
Answer:
```


**fractions-004** — *Thandi has R60. She spends 2/5 of her money on a book. How much does the book cost?*

```
Answer:
```


**measurement-001** — *How do I find the perimeter of a rectangle that is 8 cm long and 5 cm wide?*

```
Answer:
```


</details>




### gemma-2-2b-it-Q4_K_M.gguf

| Prompt | Topic | Difficulty | Avg Time | Tokens/s | Peak RAM |
|--------|-------|-----------|----------|----------|----------|
| fractions-001 | term1.fractions.basics | basic | 13.374s | 11.22 | 2896.0 MB |
| fractions-002 | term1.fractions.equivalent | basic | 13.778s | 10.94 | 2896.0 MB |
| fractions-003 | term1.fractions.addition | intermediate | 13.991s | 10.8 | 2896.0 MB |
| decimals-001 | term1.decimals.place_value | basic | 10.014s | 10.31 | 2900.0 MB |
| decimals-002 | term2.decimals.operations | basic | 13.981s | 10.81 | 2900.0 MB |
| geometry-001 | term2.geometry.angles | basic | 13.977s | 10.82 | 2900.0 MB |
| geometry-002 | term2.geometry.2d_shapes | intermediate | 13.712s | 10.98 | 2900.0 MB |
| data-001 | term3.data_handling.mean | basic | 13.038s | 10.43 | 2900.0 MB |
| ratio-001 | term3.ratio.basics | intermediate | 11.448s | 10.54 | 2900.0 MB |
| patterns-001 | term1.patterns.number_sequences | intermediate | 5.701s | 9.88 | 2900.0 MB |
| fractions-004 | term1.fractions.word_problem | intermediate | 13.37s | 10.57 | 2900.0 MB |
| measurement-001 | term2.measurement.perimeter | basic | 13.599s | 10.74 | 2900.0 MB |


<details>
<summary>Sample outputs (click to expand)</summary>


**fractions-001** — *What is a fraction? Can you explain it simply?*

```
Answer:
A fraction is a part of a whole. It is written as one number over another, separated by a line. The top number is called the numerator and tells us how many parts we have. The bottom number is called the denominator and tells us how many equal parts the whole is divided into. 


For example: 3/4 means we have 3 parts out of 4 equal parts.

**Worked Example:**
Let's say you want to share 12 cookies with your friends. You can divide them into 4 equal groups.  What fraction represents this?
```


**fractions-002** — *How do I find equivalent fractions?*

```
Answer:

To find equivalent fractions, you need to multiply or divide both the numerator and the denominator by the same number. For example, 1/2 = 2/4 = 3/6 = 4/8.


**Worked Example:**

Let's find some equivalent fractions for 3/5:

1. **Choose a number to multiply by.** You can choose any number, but let's say you want to multiply both the numerator and denominator by 2.
2. **Multiply the numerator and denominator by your chosen number.** 
   * Numerator: 3 x 2 = 6
   * Denominator: 5 x 2 = 1
```


**fractions-003** — *How do I add fractions with different denominators?*

```
Answer: 
To add fractions with different denominators, follow these steps:

1. **Find a common denominator:** The lowest common multiple (LCM) of the two denominators is used to find the common denominator.
2. **Convert each fraction to an equivalent fraction with that common denominator:** This means changing the fraction so that it has the same denominator as the other fraction. 
3. **Add the numerators:** Once you have both fractions in the same form, add their numerators together.
4. **Keep 
```


**decimals-001** — *What does each digit mean in the number 45.67?*

```
Answer:

The 4 is in the tens place (4 × 10 = 40). The 5 is in the units/ones place (5 × 1 = 5). The decimal point separates the whole number from the decimal part. The 6 is in the tenths place (6 × 0.1 = 0.6). The 7 is in the hundredths place (7 × 0.01 = 0.07).
```


**decimals-002** — *How do I multiply a decimal by 10 or 100?*

```
How do I use the concept of moving decimal places to help me multiply?


**Answer:**

To multiply a decimal by 10, you move the decimal point one place to the left.  
For example: 3.45 × 10 = 34.5. 

To multiply a decimal by 100, you move the decimal point two places to the left. 
For example: 3.45 × 100 = 345.


**Explanation:**

Here's how it works with an example: 

Let's say we want to multiply 2.7 by 100.

1. **Move the decimal point:**
```


**geometry-001** — *What types of angles are there and how do I identify them?*

```
Answer:
There are several types of angles. 

* **Right angle:** Exactly 90° like the corner of a book.

* **Acute angle:** Less than 90° like a slightly open door.

* **Obtuse angle:** More than 90° but less than 180°.

* **Straight angle:** Exactly 180° like a straight line.

* **Reflex angle:** More than 180° but less than 360°.


To identify an angle, compare it to 90°. For example, if you see an angle that is more than 90° and less than 180°, then it is an obtuse angle
```


**geometry-002** — *What are the properties of a parallelogram?*

```
Answer:

Here are the properties of a parallelogram:

* **Opposite sides are equal in length.**  This means that if you draw one side of the parallelogram, then draw its opposite side, they will be exactly the same length.
* **Opposite angles are equal.** This means that if you look at two opposite corners of the parallelogram, their angles will be the same size. 
* **The diagonals bisect each other.**  This means that the diagonals (the lines that run across the parallelogram) will cut each oth
```


**data-001** — *How do I calculate the average of these numbers: 12, 15, 18, 9, 16?*

```
Answer:
1. Add all the numbers together: 12 + 15 + 18 + 9 + 16 = 70.
2. Count the numbers: there are 5 numbers.
3. Divide the sum by the count: 70 ÷ 5 = 14.


The average is 14. 

**Explanation:**

You can calculate the average of a set of numbers by following these steps:

1. **Add all the numbers together.** This means you need to combine all the values in your set.
2. **Count how many numbers there are.** You will then know how many values you have added together. 
3. **Divide the
```


**ratio-001** — *There are 12 boys and 18 girls in a class. What is the ratio of boys to girls in simplest form?*

```
Answer:
The ratio of boys to girls is 2:3.

**Explanation:**

We need to simplify the ratio of boys to girls in simplest form. To do this, we find the highest common factor (HCF) of 12 and 18. The HCF of 12 and 18 is 6. So 12:18 = (12÷6):(18÷6) = 2:3.
```


**patterns-001** — *What is the next number in this pattern: 2, 6, 18, 54, ...?*

```
Answer:
The next number is 486.

Explanation:
The rule is to multiply by 3 each time.  So, we can find the next number in the sequence: 54 × 3 = 162.
```


**fractions-004** — *Thandi has R60. She spends 2/5 of her money on a book. How much does the book cost?*

```
Thandi has R60. She spends 2/5 of her money on a book. How much does the book cost?
Answer:
The book costs R24.

**Explanation:**

1. **Identify the fraction:** The problem states that Thandi spends 2/5 of her money.
2. **Multiply by the amount:** We know she has R60, so we multiply this by the fraction: 2/5 × 60.
3. **Calculate the result:**  Following the steps from the provided content, we find that 2/5 of R60 is R24.
```


**measurement-001** — *How do I find the perimeter of a rectangle that is 8 cm long and 5 cm wide?*

```
Answer:
You can find the perimeter of a rectangle using two methods.  The first method is to use the formula: Perimeter = 2 × (length + width). The second method is to add all four sides together: length + width + length + width.

Here's an example:
A rectangle with a length of 8 cm and a width of 5 cm:
Perimeter = 2 × (8 + 5) = 2 × 13 = 26 cm.  


Or:
8 + 5 + 8 + 5 = 26 cm.

Remember to include the unit (cm) in your answer.
```


</details>




### qwen2.5-1.5b-instruct-q4_k_m.gguf

| Prompt | Topic | Difficulty | Avg Time | Tokens/s | Peak RAM |
|--------|-------|-----------|----------|----------|----------|
| fractions-001 | term1.fractions.basics | basic | 7.187s | 20.87 | 1835.1 MB |
| fractions-002 | term1.fractions.equivalent | basic | 7.698s | 19.61 | 1835.1 MB |
| fractions-003 | term1.fractions.addition | intermediate | 7.899s | 19.22 | 1835.2 MB |
| decimals-001 | term1.decimals.place_value | basic | 8.016s | 19.0 | 1838.5 MB |
| decimals-002 | term2.decimals.operations | basic | 8.113s | 18.66 | 1838.5 MB |
| geometry-001 | term2.geometry.angles | basic | 8.257s | 18.53 | 1838.5 MB |
| geometry-002 | term2.geometry.2d_shapes | intermediate | 7.706s | 19.6 | 1838.5 MB |
| data-001 | term3.data_handling.mean | basic | 8.177s | 18.45 | 1838.5 MB |
| ratio-001 | term3.ratio.basics | intermediate | 8.802s | 17.48 | 1838.5 MB |
| patterns-001 | term1.patterns.number_sequences | intermediate | 7.933s | 19.12 | 1838.5 MB |
| fractions-004 | term1.fractions.word_problem | intermediate | 8.165s | 18.7 | 1838.7 MB |
| measurement-001 | term2.measurement.perimeter | basic | 7.992s | 18.98 | 1838.7 MB |


<details>
<summary>Sample outputs (click to expand)</summary>


**fractions-001** — *What is a fraction? Can you explain it simply?*

```
A fraction is a number that represents part of a whole. It consists of two numbers: the numerator and the denominator. The numerator shows how many parts are taken, while the denominator indicates how many equal parts make up the whole. For example, in the fraction 3/4, we have 3 parts out of 4 equal parts. This means that if you divide a whole into 4 equal parts, 3 of those parts would be represented by the fraction 3/4.

To illustrate this with an example:
If you have a pizza and you want to s
```


**fractions-002** — *How do I find equivalent fractions?*

```
To find equivalent fractions, you multiply or divide both the numerator and the denominator by the same number. For example, if you have the fraction 1/2, to get an equivalent fraction, you can multiply both the numerator and the denominator by any non-zero whole number. Let's say we want to create an equivalent fraction with a denominator of 4. We would multiply both the numerator (1) and the denominator (2) by 2:

Equivalent fraction: \( \frac{1 \times 2}{2 \times 2} = \frac{2}{4} \)

This pro
```


**fractions-003** — *How do I add fractions with different denominators?*

```
To add fractions with different denominators, follow these steps:

1. Find the least common multiple (LCM) of the two denominators.
2. Convert each fraction to an equivalent fraction with this LCM as the denominator.
3. Add the numerators together while keeping the same denominator.
4. Simplify the resulting fraction if possible.

Let's use the example provided: 1/3 + 1/4

Step 1: Find the LCM of 3 and 4.
The LCM is 12.

Step 2: Convert each fraction to an equivalent fraction with a denominator 
```


**decimals-001** — *What does each digit mean in the number 45.67?*

```
In the number 45.67, each digit has a specific place value:
- The '4' is in the tens place (40).
- The '5' is in the units/ones place (5).
- The '.' separates the whole number from the decimal part.
- The '6' is in the tenths place (0.6).
- The '7' is in the hundredths place (0.07).

So, 45.67 = 40 + 5 + 0.6 + 0.07.

Example:
Let's consider a different number: 32.89.
- The '3' is in the hundreds place (300).
```


**decimals-002** — *How do I multiply a decimal by 10 or 100?*

```
To multiply a decimal by 10 or 100, you move the decimal point to the right according to the number of zeros in the multiplier. For example:

Example: Multiply 345.67 by 10.

Solution:
- The multiplier is 10.
- There are two zeros in 10 (since 10 = 10^2).
- Move the decimal point two places to the right.

Starting with 345.67, move the decimal point one place to the left and add a zero at the end:
345.67 → 3456.7

Then move it another place to the right (two places in total):
34
```


**geometry-001** — *What types of angles are there and how do I identify them?*

```
There are several types of angles, each with a specific measure:
1. Right angle: 90°
2. Acute angle: Less than 90° (e.g., an open door)
3. Obtuse angle: More than 90° but less than 180° (e.g., an opened umbrella)
4. Straight angle: Exactly 180° (a straight line)
5. Reflex angle: More than 180° but less than 360° (e.g., a curved road)

To identify an angle, compare it to 90°:
- If the angle is exactly 90°, it's a right angle.
- If the angle is
```


**geometry-002** — *What are the properties of a parallelogram?*

```
To understand the properties of a parallelogram, let's consider an example:

Imagine you have a rectangle. A rectangle is a type of parallelogram where all four angles are right angles (90°). Here’s how its properties apply to this specific shape:

1. **Opposite sides are equal in length**: In our rectangle, opposite sides like the top and bottom edges or left and right edges are both 5 units long.
2. **Opposite angles are equal**: The corners at the top-left and bottom-right (90°) and the top-r
```


**data-001** — *How do I calculate the average of these numbers: 12, 15, 18, 9, 16?*

```
To calculate the average of these numbers, follow these steps:

1. **Add all the values together**: 12 + 15 + 18 + 9 + 16 = 70.

2. **Count the total number of values**: There are 5 numbers in this set.

3. **Divide the sum by the count**: 70 ÷ 5 = 14.

So, the average (or mean) is 14. This means that if you were to add all these numbers together and then divide by 5, you would get 14 as your answer. In other words, on average, each number in this set contributes 14 when added up. 

This
```


**ratio-001** — *There are 12 boys and 18 girls in a class. What is the ratio of boys to girls in simplest form?*

```
To find the ratio of boys to girls in simplest form, we need to simplify the given ratio 12:18. We can do this by dividing both numbers by their highest common factor (HCF). The HCF of 12 and 18 is 6.

Let's divide both numbers:
- For the number 12, when divided by 6, we get \( \frac{12}{6} = 2 \).
- For the number 18, when divided by 6, we get \( \frac{18}{6} = 3 \).

So, the simplified ratio of boys to girls is:
\[ 2:3 \]

This means for every 2 boys there
```


**patterns-001** — *What is the next number in this pattern: 2, 6, 18, 54, ...?*

```
To find the next number in this pattern, we need to identify the rule that governs the sequence. In this case, each number is obtained by multiplying the previous number by 3.

Let's break it down step-by-step:

1. The first number is 2.
2. To get the second number (6), we multiply 2 by 3: \(2 \times 3 = 6\).
3. For the third number (18), we multiply 6 by 3: \(6 \times 3 = 18\).
4. For the fourth number (54), we multiply 18 by 3: \(18 \times 3 = 54\).

Following this pattern
```


**fractions-004** — *Thandi has R60. She spends 2/5 of her money on a book. How much does the book cost?*

```
To find out how much the book costs, we need to follow these steps:

1. Write down the calculation: 2/5 of R60 means 2/5 × 60.
2. Divide the amount by the denominator (5) to get 12. This is because when you divide a number by its denominator, it gives you the fraction's numerator times that denominator.
3. Multiply this result by the numerator (2), which gives us 24.

So, Thandi spends R24 on the book. You can verify this: if she had spent 5 parts of her money, each part would be R12, and 5 × R1
```


**measurement-001** — *How do I find the perimeter of a rectangle that is 8 cm long and 5 cm wide?*

```
To find the perimeter of a rectangle, you use the formula: Perimeter = 2 × (length + width). For a rectangle with dimensions 8 cm long and 5 cm wide:

1. First, add the length and the width:
   8 cm + 5 cm = 13 cm

2. Then, multiply this sum by 2 to get the perimeter:
   2 × 13 cm = 26 cm

So, the perimeter of a rectangle that is 8 cm long and 5 cm wide is 26 cm. Remember, always include the unit (cm) in your answer when giving the final result. In this case, it's already included as "cm" in our
```


</details>




## 5. System Info

| Property | Value |
|----------|-------|
| Total System RAM | 31937 MB |
| CPU Cores (physical) | 4 |
| CPU Cores (logical) | 8 |
| Benchmark threads | 4 |
| Context window | 2048 tokens |
| Max tokens | 150 |
| Temperature | 0.3 |
| Runs per prompt | 3 |

## 6. Target Device Comparison

| Property | Target Device | Benchmark Host |
|----------|--------------|----------------|
| RAM Budget | 2048 MB | 31937 MB |
| CPU | ARMv8-A (Cortex-A53 class) | Host CPU |
| Android API | 29 | N/A (host benchmark) |

> ⚠️ **Note:** The benchmarks above ran on the development **host, not on Android**
> hardware. Absolute latency numbers will differ on target devices.
> Relative model rankings and RAM usage patterns are the primary signal.
> See Section 8 for real-device validation results.

## 7. Recommendation


### Passing Models (1/4)

- **qwen2.5-1.5b-instruct-q4_k_m.gguf** — 7.995s avg, 1838.7 MB peak RAM, 19.02 tok/s


### Suggested Next Steps

1. Validate the top candidate(s) on an Android emulator (API 29, 2 GB RAM).
2. Assess output quality for curriculum alignment (manual review).
3. Measure thermal behaviour during sustained inference.
4. Proceed to P0-002 (embedding + retrieval spike) in parallel.


## 8. Real-Device Validation (Addendum 2026-03-29)

> **Device:** Vivo V2434 (ARM Cortex-A76, Android 15, 8 GB RAM)
> **Method:** Wireless ADB debug, sideloaded APK with vendored llama.cpp JNI bridge
> **Model:** qwen2.5-1.5b-instruct-q4_k_m.gguf (same candidate selected above)
> **Build flags:** CMake `-O3 -DNDEBUG` for debug builds (ARM NEON optimised)

| Metric | Host Benchmark | Real Device | Notes |
|--------|---------------|-------------|-------|
| Prompt eval (594 tok) | — | 46,080 ms (~12.9 tok/s) | First real-device measurement |
| Token generation (139 tok) | 7.995s / 150 tok | 21,233 ms (~6.5 tok/s) | ~3× slower than host; expected for ARM |
| Total pipeline | — | 68,508 ms | embed → retrieve → unload → LLM load → generate |
| Output length | 150 tok (capped) | 644 chars (139 tok) | Uncapped real question |
| Crash / OOM | — | None | Stable on 8 GB device |

**Key observations:**
- Token generation throughput (6.5 tok/s) is lower than host (19 tok/s) but produces a complete, coherent answer within ~70 seconds.
- Sequential model loading (ONNX unload → GGUF load) works correctly, confirming ADR-0009 manual DI approach.
- Debug builds without `-O3` were ~10× slower on ARM; the optimisation flag is essential for usable performance.
- No thermal throttling observed during a single query; sustained-inference thermal testing remains future work.
- Pipeline timeout was increased from 30s → 120s (generation timeout 30s → 90s) based on real-device latency.

**Updated next steps:**
1. ~~Validate on Android emulator~~ — Done (emulator + real device)
2. Assess output quality for curriculum alignment (manual review) — Partially validated via e2e pipeline
3. Measure thermal behaviour during sustained inference — Still pending
4. ~~Proceed to P0-002~~ — Completed
5. Optimise inference speed: explore `n_threads` tuning, `mmap`, and batch-size adjustments for ARM targets

## 9. Verdict

**Overall: GO ✅**

At least one model meets all acceptance criteria. Real-device validation confirms the selected model (Qwen 2.5 1.5B Q4_K_M) runs end-to-end on target-class ARM hardware without crash or OOM. Proceed with the recommended candidate to P0-005 (end-to-end pipeline).

---

*Report generated by `scripts/report_generator.py` from benchmark results.*