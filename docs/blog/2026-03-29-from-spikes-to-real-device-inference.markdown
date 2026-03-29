---
layout: post
title: "From Spikes to a Real Phone: Running an Offline AI Tutor End-to-End on ARM"
date: 2026-03-29 22:00:00 +0200
categories: personal update
tags: [edge AI, education, practical tools, android, llama.cpp]
---

*This is the second post in an ongoing series. The first post covered the feasibility spikes, the architecture bets, and why I built a companion system to stop decisions from resetting between sessions. This post covers what happened when the work moved from benchmarks on a development machine to a real phone in my hand.*

Two weeks ago, I had spike results, a working Android app scaffold, and a content-pack builder that produced a valid Grade 6 CAPS maths pack. But the AI pipeline was still running mock components. The spikes proved feasibility on a development host. The app proved the UI flows worked. Neither proved the thing that actually matters: does it work on the phone?

This post is about closing that gap.

<!--more-->

## Where I left off

The previous post ended with a set of honest questions:

- Can I load the seed pack reliably into a real device?
- Can I replace the mock embedding and LLM engines with the real ONNX + llama.cpp runtime?
- Can I validate that the full pipeline runs on target hardware without crash or OOM?

Those questions defined this phase.

## The emulator proved the plumbing

Before touching a real device, I needed the full pipeline running on an emulator. That sounds straightforward on paper but required sorting out several things simultaneously.

### Staging real assets is harder than building them

The content pack, the ONNX embedding model, and the GGUF language model all need to land in the app's private storage before anything interesting can happen.

On emulator, the obvious path (`adb push` to `/sdcard/Download`, then copy into app storage) failed immediately. Scoped storage restrictions under `run-as` blocked the copy. The workaround was staging via `/data/local/tmp` as a bridge, which is less elegant but entirely reliable.

Then the default emulator ran out of disk. The Qwen 2.5 1.5B GGUF file is 1.07 GB. The emulator had 813 MB free. A partial zero-byte file and a confused developer later, I provisioned a new AVD with a 16 GB data partition and started over.

This is the kind of friction that does not show up in architecture diagrams.

### Silent failures are worse than crashes

With assets staged, I launched the app, created a profile, typed a question, hit send, and nothing happened.

No crash. No error. Just silence.

The issue was a guard clause in the chat ViewModel that returned early when no active profile ID was set, without telling the user. I fixed that with an explicit error message. Then I created a profile on the profiles screen, came back to chat, and got the same silent failure again.

The ViewModel was holding stale state. The profile existed in storage, but the chat screen had initialised before it was created and never refreshed. A second fix added an on-demand profile reload in the send path.

Neither of these bugs would have surfaced without running the actual app flow. Unit tests covered the happy path. The unhappy path was a user clicking buttons in a different order than the test assumed.

### The pipeline ran, and the results were mixed (in a good way)

Once the profile issue was resolved, the pipeline worked. Logcat showed the full sequence: ONNX embedding model load, query embedding, FAISS retrieval, prompt construction, and then the LLM response.

Some queries retrieved relevant chunks and generated grounded answers. Others hit the retrieval-miss path and returned an honest "I couldn't find relevant content" message. Both are correct behaviors for different inputs.

The LLM native library was not yet integrated at this point, so the generation path returned a placeholder message. But the embedding and retrieval path was live and working with real models on the emulator.

## Then I built the native bridge

The emulator proved the plumbing. But the LLM was still a stub. To get real inference, I needed llama.cpp running natively on Android.

### Vendoring llama.cpp

I created a new `:core:llama` module with a C++ JNI bridge that compiles llama.cpp from vendored source for `arm64-v8a` and `x86_64`. The bridge exposes 14 native functions through a Kotlin wrapper: model load/unload, context management, tokenization, sampling, and the generation loop.

The `LlamaCppEngine` got a full rewrite. The previous stub returned a canned message. The new version runs a real inference loop: tokenize the prompt, batch decode, sample tokens, detokenize, and emit results through a Kotlin Flow.

On the emulator (x86_64), the native library loaded and the full RAG pipeline ran. But prompt evaluation was extremely slow on x86_64, as expected. The emulator is not ARM. Real performance would need a real phone.

### The `-O3` lesson

The first real-device run was painfully slow. Not "slower than host" slow, but "minutes per response" slow.

Debug builds on Android default to `-O0`, no compiler optimisation. On x86 host machines, this often does not matter much. On ARM, it is catastrophic. Adding `-O3 -DNDEBUG` to the CMake flags for debug builds brought performance from unusable to usable. That is roughly a 10x difference from a compiler flag.

This is the kind of thing you only learn on real hardware.

## The real phone

The test device was a Vivo V2434: ARM Cortex-A76, Android 15, 8 GB RAM. Connected via wireless ADB debug, sideloaded APK, assets staged through the same `/data/local/tmp` bridge.

### What I measured

The full pipeline on device for a single curriculum question:

| Phase | Time |
|-------|------|
| Embed + retrieve + unload ONNX | ~1 second |
| LLM model load | ~2 seconds |
| Prompt evaluation (594 tokens) | 46,080 ms (~12.9 tok/s) |
| Token generation (139 tokens) | 21,233 ms (~6.5 tok/s) |
| **Total pipeline** | **68,508 ms** |

The output was 644 characters of coherent, curriculum-grounded maths explanation. No crash. No OOM. No thermal throttling on a single query.

### What that means

Generation throughput of 6.5 tokens per second is not fast. It is roughly 3x slower than the host benchmark. A ~70-second wait for an answer is not instant.

But it works. On a real phone. Completely offline. With no internet, no API key, no subscription, and no cloud dependency.

For a learner who has no other tutor available, 70 seconds is not a deal-breaker. It is an answer they would not otherwise get.

And there is room to optimise. Thread count tuning, `mmap` for model loading, and batch-size adjustments have not been explored yet. The current result is a baseline, not a ceiling.

## The UX details that matter

Performance is one dimension. Whether the app feels right to use is another.

### Retrieval misses should not look like errors

When the retrieval path finds no relevant chunks above the similarity threshold, the first version of the UI showed two things: a message-level response saying "I couldn't find relevant content" and a global error banner at the top of the screen.

That is confusing. Two signals for one expected condition. I lowered the retrieval threshold from 0.1 to 0.05 (to reduce false misses on valid queries) and suppressed the global banner for retrieval-miss cases. Now the user sees one clear assistant message.

### Preferences need a way out

The preferences screen let you set learning style and reading level, but had no save/apply button. You could change things and then had no obvious way to confirm and leave. I added an "Apply and go back" button that returns to the profiles screen.

The first version of that button was invisible. A `LazyColumn` with `fillMaxSize()` consumed all vertical space and pushed the button off-screen. A layout fix with `weight(1f)` on the content area and `fillMaxWidth()` on the list made the button permanently visible.

This is exactly the kind of bug that looks fine in Compose previews and breaks on a real screen. Live emulator verification caught it before it reached the phone.

## What the spike reports say now

All three spike reports originally carried the same disclaimer: "These benchmarks ran on the development host, not on Android hardware."

That disclaimer is now followed by a real-device addendum in each report:

- **P0-001 (LLM Inference)**: real device metrics — 12.9 tok/s prompt eval, 6.5 tok/s generation, 68.5s total pipeline
- **P0-002 (Embedding + Retrieval)**: ONNX loads on ARM, retrieval confirmed end-to-end, sequential model management works
- **P0-005 (E2E Pipeline)**: full RAG pipeline validated on target-class ARM hardware, coherent output, no OOM

The spike methodology worked as designed. Host benchmarks gave directional signal. Real hardware gave ground truth. The gap between them was significant (3x on generation speed) but did not change the verdict.

## What EJS captured along the way

This was a long session with a lot of pivots. Without the Engineering Journey System recording context continuously, I would have lost most of the detail.

Some of the things EJS preserved:

- The decision to use `/data/local/tmp` as a staging bridge, and why the obvious path failed
- The pivot from the default emulator to a 16 GB AVD when disk space was insufficient
- The silent profile-state bug and the two-step fix (explicit error, then stale-state recovery)
- The `-O3` discovery and its impact on ARM performance
- The layout regression caught during live verification

None of these are architectural decisions. They are implementation details. But they are the kind of details that save hours when someone (including future me) encounters the same situation.

## What I still do not know

- **Sustained inference**: one query ran without thermal issues. Ten queries in a row might tell a different story.
- **Target device performance**: the Vivo V2434 has 8 GB RAM and a Cortex-A76. The actual target audience has 4 GB phones with weaker cores. That is the real test.
- **Generation speed optimisation**: 70 seconds is a baseline. Thread tuning, memory mapping, and prompt-length management might bring this under 30 seconds.
- **Content completeness**: the seed pack covers Grade 6 CAPS topics at a seed level. Full curriculum density with worked examples and review quality is Phase 2 work.

## What changed in my thinking

When I wrote the first post, I was careful to say the answer to "can this work?" was "maybe, but a more informed maybe."

After today, I would say: it works, within constraints, on hardware that is close to the target.

The constraints matter. The response time is long. The content is not yet complete. The thermal and battery story is untested.

But the core bet, a small language model running entirely on a phone, grounded by local curriculum content, producing useful maths explanations offline, is no longer a hypothesis. I watched it happen on a phone connected to my laptop over Wi-Fi debug, with no internet involved in any part of the generation.

That shifts the project from "is this possible?" to "how do I make this good enough?"

Which is exactly the phase I want to be in.

## Why this matters

There is a learner somewhere in South Africa who does not have a tutor, does not have stable internet, and does not have money for a subscription.

If this project works, that learner gets a patient, curriculum-grounded maths tutor that runs entirely on the phone in their pocket.

Not eventually. Not when connectivity improves. Now.

That is still the reason for all of this engineering work. The spikes, the emulator bugs, the compiler flags, the layout regressions — they are all in service of that one outcome.

I am not there yet. But I am closer than I was two weeks ago, and I have the evidence to prove it.
