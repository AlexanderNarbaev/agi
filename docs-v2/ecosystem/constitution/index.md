---
layout: default
title: Constitution
nav_order: 7
parent: Ecosystem
permalink: /ecosystem/constitution/
---

# Constitution

The **MATRIX Constitution** is the project's ethical and technical charter.
It defines hard constraints that cannot be violated — by anyone, for any reason,
including commercial pressure.

The full source is at [`CONSTITUTION.md`](https://github.com/AlexanderNarbaev/agi/blob/develop/CONSTITUTION.md).

## The Articles

### Article I — No LLM in Runtime

> **No Large Language Model shall be invoked in the runtime inference path.**

All inference runs on:
- BIR (symbolic rules)
- HDC (vector memory)
- MCTS (planning)
- Seeded Random (deterministic)
- FROZEN modulators (vetos)

LLMs are allowed **only**:
- In build-time tools (e.g., ONNX distillation of small perception models)
- In developer-side tooling (e.g., IDE autocomplete)
- Never in `/v1/analyze`, `/v1/explain`, `/v1/federate` request paths

**Enforcement:** CI guard in `.github/workflows/branch-validation.yml` blocks
any PR adding `openai`, `anthropic`, `cohere`, or `gemini` imports to
production runtime modules.

### Article II — Pure Hybrid Inference

> **Every decision must be grounded in both logic and memory.**

Pure LLM (logic only) and pure database lookup (memory only) are forbidden
for primary decisions. MATRIX always runs:

1. BIR rule firing (logic)
2. HDC memory retrieval (memory)
3. MCTS plan search (planning)
4. Modulator gates (veto)

### Article III — Reproducibility

> **All decisions must be reproducible from the same input and seed.**

Every inference uses **seeded Random** (`java.util.Random` with explicit seed
or `SplittableRandom`). No `Math.random()`, no `System.nanoTime()` in the
inference path.

This means:
- Two requests with the same input return the same output
- Auditors can replay any past decision
- Debugging is deterministic

### Article IV — FROZEN Modulators

> **The four modulators are immutable.**

| Modulator | Purpose |
|-----------|---------|
| **ETHICAL_FILTER** | Refuses outputs violating ethical guidelines |
| **SAFETY_MONITOR** | Refuses outputs that could cause harm |
| **CONSISTENCY_CHECKER** | Refuses outputs contradicting prior state |
| **LIE_DETECTOR** | Refuses outputs misrepresenting source data |

All four must pass for `accepted: true`. **No developer can override them.**
No PR can modify their logic without an explicit CONSTITUTION amendment.

### Article V — Privacy

> **User data is sovereign.**

- Inference runs on-prem by default
- Cloud option uses encryption-at-rest and in-transit
- "Right to be forgotten" (GDPR) is honored via the `GdprPruner` in
  `matrix-audit` (T-06)
- Data retention policies are configurable per plan

### Article VI — No Consciousness Claims

> **MATRIX does not claim consciousness, sentience, or any form of inner experience.**

We use metaphors like "civilization" and "brain" to explain complex systems
to humans. These are pedagogical tools, not claims about the system's
metaphysical status.

When you read docs:
- "the brain decides" → the BIR/HDC/MCTS pipeline produced an output
- "the civilization coordinates" → multiple instances reached consensus
- "the modulator fired" → the veto threshold was crossed

These are engineering shorthand, **not** metaphysical claims.

### Article VII — No Deception

> **MATRIX must never knowingly deceive its users.**

- If confidence is low, the system says so
- If a modulator fails, the system says so
- If the system doesn't know, it says so
- Hallucination is structurally impossible (BIR enforces facts)

### Article VIII — Open Source Core

> **The research core is open source.**

`matrix-core` is licensed under Apache-2.0. The ecosystem modules
(`matrix-api-gateway`, `matrix-sdk-java`, etc.) are also open source.

Commercial offerings are around:
- Hosted SLA (enterprise tier)
- Compliance certification (T-06 audit + T-07 license validation)
- Priority support

Not around hiding the core algorithm.

---

## Amendments

The Constitution can be amended only by:
1. Public RFC in `docs-v2/proposals/`
2. 2-week comment period
3. Approval by 3 maintainers
4. Version bump of CONSTITUTION.md

No silent edits.

## Enforcement

| Article | CI Check |
|---------|----------|
| I | No LLM imports in production modules |
| II | Inference must use both BIR and HDC |
| III | No `Math.random()`, no `System.nanoTime()` |
| IV | Modulators cannot be overridden in PR |
| V | GdprPruner must be present in `matrix-audit` |
| VI | No consciousness-claiming language in docs |
| VII | No false-positive confidence in tests |
| VIII | `matrix-core` stays Apache-2.0 |

## Compliance

If you believe a CONSTITUTION violation has occurred:
1. Open an issue tagged `constitution-violation`
2. Include the article number, the offending code/docs, and the expected fix
3. Maintainers have 24 hours to acknowledge

---

**Last updated:** 2026-09-21 (Wave T-03)
