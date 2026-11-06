---
layout: default
title: MindCycle — Cognitive Orchestration Layer
nav_order: 7
parent: Architecture
permalink: /ecosystem/architecture/mindcycle/
---

**Status:** ACTIVE · **Updated:** 2026-11-06

# The mind cycle — the ten stages of chain B

In production mode the gateway runs every `/v1/analyze` request through
`io.matrix.brain.runtime.TrueMindCycle`. Each stage either **fires** — with a confidence
and its evidence — or is skipped, and the ordered result is the **BRC trace** (the boolean
reasoning chain) that the explanation shows.

> **What this page used to say.** It described a class called `MindCycle` as the
> orchestrator, and it described the contradiction check as two cosine thresholds
> (0.65 → DUPLICATE, 0.40 → POTENTIAL_CONFLICT). Both are stale: the gateway imports and
> instantiates **`TrueMindCycle`** (the legacy `MindCycle` is the CLI-only mind), and since
> W36.2 the **logical verdict decides the level** — the similarity is reported, not trusted.
> The thresholds are still in the class javadoc; they no longer decide.

## The pipeline

```
stimulus → REFLEX → SIGNAL → SALIENCE → ARITHMETIC → ANALOGY →
           BIR → HDC_MEMORY → TSETLIN → MCTS → MODULATORS
           → MindResult(reply, confidence, accepted, modulators, BRC trace)
```

| # | the stage | what it does |
|---|---|---|
| 1 | **REFLEX** | the fast-path gate: it refuses empty and destructive input |
| 1b | **HARMFUL_INTENT** | the harmful-intent classifier, run right after the reflex |
| 2 | **SIGNAL** | tokenises the input into a **256-bit** feature vector by per-token FNV-1a hashing (`SignalStage.DIM = 256`) |
| 3 | **SALIENCE** | scores relevance and gates the further work |
| 4 | **ARITHMETIC** | pure `BigInteger` composition — `2+3=5` |
| 5 | **ANALOGY** | `A is to B as C is to ?` from the seeded relation table |
| 6 | **BIR** | the boolean inference stage, over the compiled forms |
| 7 | **HDC_MEMORY** | similarity over the persistent HDC store |
| 8 | **TSETLIN** | the clause-based classifier |
| 9 | **MCTS** | the planning stage |
| 10 | **MODULATORS** | the four modulator stages — `ETHICAL_FILTER`, `SAFETY_MONITOR`, `LIE_DETECTOR`, `CONSISTENCY_CHECKER` |

**The seed is `Random(42L)`,** fixed in the constructor, so the cycle is reproducible. The
source comment cites "Article III" for it; in the constitution, **Article I** is the article
about stratified stochasticity and the deterministic seed, and Article III is the
frozen-zones article. The page states Article I; the comment in the source is wrong and is
noted in the W49 audit rather than silently corrected.

## The BRC trace

Each step is a `BrcStep`:

```java
public record BrcStep(
        String stage,          // the stage name
        boolean fired,         // whether this stage contributed to the answer
        double confidence,     // the stage-internal confidence in [0, 1]
        List<String> evidence  // document ids, rule ids, retrieved neighbours, plan steps
) { }
```

```json
{
  "stage": "ARITHMETIC",
  "fired": true,
  "confidence": 0.99,
  "evidence": ["a=2", "op=+", "b=3", "result=5"]
}
```

## The two engines, and the fallback

| | |
|---|---|
| **`TrueMindCycle`** (`matrix-brain-runtime`) | the ten-stage cycle above; what the gateway runs |
| **`BirBrainCycle`** (`matrix-core`) | the earlier engine — pure BIR/HDC/MCTS and the modulators. It **remains as a fallback** when the brain runtime is missing, and it is the engine that powers the native binary |
| **`MindCycle`** (`matrix-brain-runtime`) | the legacy hand-coded mind; it is the **CLI** mind, not the serving one |

If the `matrix-core` JAR is not reachable, the production client throws
`BrainUnavailableException` and the gateway answers **503** — and the stub mode is the
automatic fallback.

## The persistent mind

The HDC knowledge base survives a restart. It is an **NDJSON** file — `hdc_kb.ndjson` in
the mind directory (`MATRIX_MIND_DIR`, the default `data/mind`) — written by an atomic
temp-file-and-rename (`ATOMIC_MOVE`). The identifiers are deterministic, so a repeated
teach is idempotent.

**The contradiction check is logical, not numeric.** The store compares the sentences with
`FactTriples.compareSentences` and the **logical verdict decides the level** —
`CONTRADICTION`, `NEGATION`, `DUPLICATE`, `UNPARSEABLE` or `NOVEL` — while the similarity
is reported alongside it. The cosine thresholds that an earlier version of this page
quoted no longer decide anything; they are a leftover in the class javadoc.

## The constitution

| the article | what holds here |
|---|---|
| **I** — stratified stochasticity | no LLM and no wall-clock read on the decision path; the seed is `Random(42L)` and the numerical substrate is pure |
| **III** — the frozen zones | `matrix-core/src/main/java/io/matrix/ethics/frozen/**` and the workflows are not edited without an RFC |
| **IV** — the four prohibitions | the modulator stages gate every answer |
| **VI** — the forbidden claims | the stage names are engineering names; the trace explains a decision, it does not certify it |
| **VIII** — explicitness | every answer carries the BRC trace |

## Next

- [The XAI page](/ecosystem/xai/) — what the explanation shows.
- [The API reference](/ecosystem/api/) — the routes.
- [The runtime topology](../../architecture/RUNTIME-TOPOLOGY.html) — chain B and its default mode.
