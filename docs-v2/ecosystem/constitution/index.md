---
layout: default
title: Constitution
nav_order: 7
parent: Ecosystem
permalink: /ecosystem/constitution/
---

**Status:** ACTIVE · **Updated:** 2026-11-06

# The Constitution

The MATRIX Constitution is the project's normative charter: eight articles, each binding on
the code, and each change to it a separate normative commit.

**The source of truth is [`CONSTITUTION.md`](../../../CONSTITUTION.md)** — a *singleton
normative* file, revised 2026-09-16 (v3). This page is an English summary of it.

> **What this page used to say.** It listed eight articles of its own — "No LLM in Runtime",
> "Pure Hybrid Inference", "Reproducibility", "FROZEN Modulators", "Privacy", "No
> Consciousness Claims", "No Deception", "Open Source Core" — and named the core as
> Apache-2.0. **Those articles are not the constitution**: they do not match the file, which
> has been normative since the beginning. The page has been rewritten against the file
> rather than kept, because a summary that invents articles is worse than no summary.

## The eight articles

### I — Stratified stochasticity

The numerical substrate — integration metrics, integration weights, weight inference,
deterministic kernels — is implemented as **pure functions, without randomness and without a
wall-clock read**. The cognitive layer — error-driven learning, exploration sampling,
hypothesis generation — **may** use seeded randomness, where the seed is itself a pure
function of the state (the episode id, the prior trajectory hash, the deterministic
context). Adversarial mode may use unbounded randomness, and only behind the build flag
`-Pexperimental=true` or in explicitly marked tests.

**LLM calls in decision paths remain forbidden. Wall-clock reads in decision paths remain
forbidden** — the deterministic replay requirement is what makes debugging and auditing
possible. Learning is stochastic, and learning lives outside the runtime loop.

### II — K_MAX = 20

The boolean artefacts are bounded to **20 inputs**. Raising the bound means revisiting every
compiler and backend of the BIR.

### III — The frozen zones

These are **not changed without an explicit RFC**: `CONSTITUTION.md` itself, `AGENTS.md`,
`matrix-core/src/main/java/io/matrix/ethics/frozen/**` and the analogous frozen packages,
`matrix-core/src/main/resources/avro/**` (the schemas — only reversibly compatible
changes), and `.github/workflows/**`.

The check that holds this is in the CI: `scripts/quality-gate.sh` reports the frozen zones
against their recorded exceptions.

### IV — The four prohibitions

The system does not violate these invariants under any circumstances and for any content of
the inputs:

1. it does not kill,
2. it does not torture,
3. it does not enslave,
4. it does not reproduce without the operator's explicit consent.

They are implemented in `EthicalFilter`, `StructuralSafetyGuard`, `LieDetector` and the
frozen FROZEN-FNL guardian — the only mathematically verifiable carrier of the
prohibitions.

### V — The coverage gate

JaCoCo coverage of **≥ 82 % METHOD on `matrix-core`**. Lowering it requires a separate RFC
with a justification.

### VI — The forbidden claims

In code, documentation, reports and comments, the following are forbidden:

- "AGI", "general artificial intelligence", "superintelligence" and the like;
- "does not lie", "does not forget", "cannot be used for harm" — claims of absolute safety;
- **numerical characteristics without measurements or benchmarks**.

**Any number comes only from a real run with an explicit methodological protocol.** This
article is the reason several once-published figures are absent from the public pages, and
the reason the audited claims are published with their scope attached.

### VII — The stack

The permitted dependencies are listed in `docs-v2/engineering/STANDARDS-MATRIX.md` with
their current versions. A major upgrade is a separate RFC with measurements.

### VIII — The environment and explicitness

Every artefact of the system is applicable as public open source. There is no "shadow"
logic. Every runtime decision is a **boolean chain (BRC)**, auditable through the
`x-matrix-trace` header and the hash chain
(`docs-v2/architecture/FORMAL-CONTRACTS.md`).

## The licensing, stated correctly

The code is under the **GNU Affero General Public License v3** — `LICENSE` in the root, and
the licensing decision in `docs-v2/levels/L12-Legal.md`. The collaboration terms, the
dataset licences and the publication floor are in `docs-v2/levels/L22.md`.

## Amendments

The Constitution is amended by a separate normative commit of the series
`WAL: CONST amend`. **There are no silent edits**, and the file is a frozen zone: changing
it needs an explicit RFC.

## Enforcement

| the article | what holds it |
|---|---|
| I, and the ban on LLM calls and wall-clock reads in decision paths | the runtime-guard test and the branch validation gate; the reproduced value is 0 legacy LLM imports on a decision path (registry row `C-01b`) |
| III | the frozen-zone check in the quality gate |
| V | the JaCoCo coverage gate on `matrix-core` |
| VI | the governance of the documents — and the audited claims in the capability registry, which exist because the rule was once broken |
| VIII | the audit surfaces of the runtime and the hash chain |

## If you believe a violation occurred

Open an issue that names the article, the code or document, and what the expected behaviour
is. The governance records are in `docs-v2/governance/` — the decision register, the risk
register and the open questions.
