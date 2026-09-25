---
layout: default
title: MindCycle — Cognitive Orchestration Layer
nav_order: 7
parent: Architecture
permalink: /ecosystem/architecture/mindcycle/
---

# MindCycle — 10-Stage Cognitive Orchestration Layer (MIND-W1)

The gateway now runs every `/v1/analyze` request through a 10-stage
cognitive conductor. Each stage either fires (with confidence and
evidence) or is skipped; the ordered result is the **BRC (Boolean
Reasoning Chain) trace** that the gateway returns for XAI.

## The pipeline

```
stimulus -> REFLEX -> SIGNAL -> SALIENCE -> ARITHMETIC -> ANALOGY ->
           BIR_RULES -> HDC_MEMORY -> TSETLIN -> MCTS -> MODULATORS
           -> MindResult(reply, confidence, accepted, modulators, BRC trace)
```

| # | Stage | What it does |
|---|-------|--------------|
| 1 | REFLEX | Fast-path gate: refuses empty / destructive input. |
| 2 | SIGNAL | Tokenises into a 256-bit hypervector (FNV-1a). |
| 3 | SALIENCE | Scores relevance; gates further work. |
| 4 | ARITHMETIC | Pure BigInteger composition: `2+3=5`, `10*5=50`. |
| 5 | ANALOGY | `A is to B as C is to ?` from seed relations. |
| 6 | BIR_RULES | Boolean Inference Rule lookup. |
| 7 | HDC_MEMORY | Bit-cosine similarity over the persistent HDC store. |
| 8 | TSETLIN | Clause-based classifier. |
| 9 | MCTS | Deliberation-budget annotation (W7 hardening). |
| 10 | MODULATORS | FROZEN safety gate (CONSTITUTION Article IV). |

## MindCycle vs BirBrainCycle

`BirBrainCycle` (matrix-core) is the engine that runs pure BIR/HDC/MCTS.
`MindCycle` (matrix-brain-runtime) is the orchestrator that decides
**which** stages to run for a given input, and produces a BRC trace that
explains the reasoning.

In production mode the gateway calls `MindCycle.think(input)` first;
only if MindCycle throws does it fall back to `BirBrainCycle.cycle()`.

## BRC trace example

```json
{
  "stage": "ARITHMETIC",
  "fired": true,
  "confidence": 0.99,
  "evidence": ["a=2", "op=+", "b=3", "result=5"]
}
```

## CONSTITUTION compliance

- **Article I** — no LLM in runtime; pure MATRIX-native.
- **Article III** — seeded `Random(42L)`.
- **Article IV** — FROZEN modulators gate every answer.
- **Article VI** — "cognitive stages" terminology, no consciousness claims.
- **Article VIII** — every answer carries a BRC trace.

---

# MIND-W2: Persistent Mind

The HDC knowledge store now persists across JVM restarts.

```
set MATRIX_MIND_DIR=/var/lib/matrix-mind
java -jar matrix-api-gateway.jar
# MIND-W2: PersistentHdcStore(/var/lib/matrix-mind/hdc_kb.ndjson)
# 47 vectors loaded from disk on startup
```

- **Atomic writes**: tmp + `ATOMIC_MOVE`.
- **Deterministic IDs**: `fnv1a64(input + "|" + response)` → idempotent teach.
- **Contradiction detection**:
  - cosine ≥ 0.65 → DUPLICATE
  - cosine ≥ 0.40 → POTENTIAL_CONFLICT
  - otherwise → NOVEL
