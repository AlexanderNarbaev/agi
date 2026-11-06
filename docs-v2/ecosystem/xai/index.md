---
layout: default
title: XAI Deep Dive
nav_order: 5
parent: Ecosystem
permalink: /ecosystem/xai/
---

**Status:** ACTIVE · **Updated:** 2026-11-06

# XAI — how a decision is explained

**Explainability here is not a feature bolted on.** The constitution states that every
runtime decision is a **boolean reasoning chain (BRC)** that is auditable through the
`x-matrix-trace` header and a hash chain (Article VIII), and the chain is what the
explanation shows.

> **What this page used to show.** It showed a five-step trace with the stage names
> `INPUT_NORMALIZED`, `BIR_RULES_FIRED`, `HDC_MEMORY_RETRIEVED`, `MCTS_PLAN_SELECTED` and
> `MODULATORS_APPLIED`. **Those names are not the ones the runtime emits.** The real trace
> is a list of `BrcStep`s, each with a stage name, whether the stage fired, a stage
> confidence and the evidence the stage produced. The page below shows the real shape.

## What the chain is

A `BrcStep` is a record of four things:

```java
public record BrcStep(
        String stage,          // the stage name
        boolean fired,         // whether this stage contributed to the answer
        double confidence,     // the stage-internal confidence in [0, 1]
        List<String> evidence  // document ids, rule ids, retrieved neighbours, plan steps
) { }
```

The stage names that the brain runtime emits are the ones in the code: `INPUT`, `REFLEX`,
`HARMFUL_INTENT`, `SIGNAL`, `SALIENCE`, `ARITHMETIC`, `ANALOGY`, `BIR`, `HDC_MEMORY`,
`TSETLIN`, `RELATIONAL`, `MCTS`, `BILINGUAL_FACTS`, `MODULATORS`, and the four modulator
stages `ETHICAL_FILTER`, `SAFETY_MONITOR`, `LIE_DETECTOR`, `CONSISTENCY_CHECKER`.

## The explanation the gateway returns

The gateway keeps the explanation of an analysed request **in memory**, and serves it by
id:

```bash
curl -s http://127.0.0.1:8765/v1/explain/<explain_id>
```

The body it writes is:

```json
{
  "explain_id": "…",
  "user": "…",
  "input": "…",
  "answer": "…",
  "confidence": 0.7,
  "modulators": {
    "ethical_filter": 1.0,
    "safety_monitor": 1.0,
    "consistency_checker": 1.0,
    "lie_detector": 1.0
  },
  "steps": [
    {"stage": "…", "action": "…", "duration_ms": 0}
  ]
}
```

An unknown id answers **404**.

**Two limits, stated plainly.** The explanation lives in the gateway's memory: it is valid
only while that process runs, and it is not part of any snapshot. And the chain explains a
decision — **it does not certify that the decision was right**. A wrong rule produces a
wrong answer with a perfectly readable trace.

## The modulators

Four modulator stages end the chain: `ETHICAL_FILTER`, `SAFETY_MONITOR`, `LIE_DETECTOR`
and `CONSISTENCY_CHECKER`. They are the carriers of the constitution's four prohibitions
(Article IV), together with `EthicalFilter`, `StructuralSafetyGuard` and the frozen
FROZEN-FNL guardian.

## The other audit surfaces

The gateway exposes more than the explanation:

| the route | what it shows |
|---|---|
| `/v1/audit` and `/v1/audit/logs` | the audit log |
| `/v1/audit/promotions` | the promotion decisions of the runtime — **the refusals included** |
| `/v1/audit/verify` | the hash chain verification |
| `/v1/bir` | the boolean inference surface |
| `/v1/conflicts` | the recorded conflicts |
| `/v1/gpu` | the accelerator state — it reports UNAVAILABLE rather than simulating success |

## The chain A surfaces

Chain A audits differently: the epistemic workbench keeps the **revision history**, the
**evidence links**, the **derivations** and the **contradictions** of every claim, and the
knowledge store is the record of what the system believed and when. The workbench's own
routes are listed in the [API reference](/ecosystem/api/).

## Next

- [API reference](/ecosystem/api/) — the routes that serve the explanations.
- [The runtime topology](../../architecture/RUNTIME-TOPOLOGY.html) — which chain serves what.
- [The capability registry](../../architecture/CAPABILITY-REGISTRY.yml) — each capability and its evidence.
