---
layout: default
title: "MATRIX — a deterministic neuro-symbolic research system"
description: "An open research system for deterministic neuro-symbolic inference: two serving applications that do not meet, a capability registry on four independent axes, and the negative results kept beside the positive ones."
---

**Status:** ACTIVE · **Updated:** 2026-11-06
**Repository:** `AlexanderNarbaev/agi`

# MATRIX

MATRIX is an open research system for **deterministic neuro-symbolic inference**. Knowledge is
represented as truth tables and their compilable forms, the learning path is separated from the
runtime, and a runtime decision is a verifiable boolean chain — with no LLM call and no
randomness on the decision path. That description is the project's own, and it is the one the
code implements: [`docs-v2/governance/PROJECT-STATE.md`](governance/PROJECT-STATE.md).

This page is the honest entrance. It states what runs, what has been measured, what is
blocked, and **where the evidence for each claim lives**, so that a reader can check any
sentence here against the artefact that produced it. Where a number could not be sourced, it
is not on this page.

## What this project is — and what it is not

MATRIX is:

- a **deterministic core** in which a decision is a boolean chain rather than a sampled
  continuation;
- a **research repository** in which a claim is a preregistered measurement, and a refuted
  hypothesis is kept as evidence with the same care as an accepted one;
- **two serving applications that do not meet** — an epistemic application and a brain
  runtime, separated by a gate that fails when they do
  ([`docs-v2/architecture/RUNTIME-TOPOLOGY.md`](architecture/RUNTIME-TOPOLOGY.md));
- a body of **implemented but unserved** subsystems, documented as such rather than hidden.

It is **not** a general LLM agent, **not** a single cognitive cycle, and **not** a system in
which every implemented class is reachable from a request. The distance between *implemented*
and *served* is the single most important fact about this repository, and it is measured
rather than asserted, in the capability registry
([`docs-v2/architecture/CAPABILITY-REGISTRY.yml`](architecture/CAPABILITY-REGISTRY.yml)).

## Vision and scope

**The vision.** Reasoning that can be checked. A system whose decisions are reproducible from
their inputs, whose evidence is attached to the claim rather than to the prose around it, and
whose failures are published with the same visibility as its successes.

**The scope, in the project's own terms.** The core is knowledge representation and
deterministic inference; around it sit research subsystems — consolidation, replay,
federation, digests, modulators — whose maturity differs sharply and is recorded per
subsystem. The repository's own governance carries the principles
([`CONSTITUTION.md`](../CONSTITUTION.md)) and the session rules
([`AGENTS.md`](../AGENTS.md)).

**Out of scope, stated plainly.** There is no hosted service and no hosted API: this
repository is the deliverable, and the two applications are run from source. There is no
release version — the tags that exist predate several waves of work
([`docs-v2/governance/PROJECT-STATE.md`](governance/PROJECT-STATE.md)).

## The architecture that actually runs

Two applications, measured by a constant-pool census of the compiled classes rather than
described from intent ([`docs-v2/architecture/RUNTIME-TOPOLOGY.md`](architecture/RUNTIME-TOPOLOGY.md),
whose evidence is `docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md`).

### Chain A — the epistemic application

`io.matrix.MatrixApplication` — one Quarkus process.

| | |
|---|---|
| binds | **9091** HTTP by default, on `127.0.0.1` (`matrix-core/src/main/resources/application.properties`) |
| also binds | **9094** TCP and **9093** multicast, bound by `P2PNetwork` and declared in no configuration — an open question of the owner (F-02), recorded and not fixed |
| routes | **210** — 57 under `/v1/workbench`, 11 under `/v1/cognitive`, 6 OpenAI-compatible chat routes, 136 across 36 further resources |
| owns | the epistemic store (claims, cases, evidence, revisions) and the cognitive store (episodes, predictions, errors) |

### Chain B — the brain runtime

`io.matrix.api.MinimalHttpServer` — a JDK `HttpServer`, not a Quarkus application.

| | |
|---|---|
| binds | **8765**, on `127.0.0.1` unless the security mode says otherwise |
| routes | **28** — `/v1/analyze`, `/v1/explain`, `/v1/learn`, `/v1/teach`, `/v1/sleep`, `/v1/goals`, `/v1/federate`, `/v1/audit*`, `/v1/gpu`, `/v1/distill`, `/v1/bir`, `/v1/conflicts`, `/v1/generalize`, `/v1/status`, `/v1/inbox/scan`, `/v1/transcode/*`, `/v1/auth/login`, plus `/health/live`, `/health/ready`, `/q/openapi`, `/metrics`, `/openapi.yaml` |
| default mode | **`stub`** — an 87-line `StubBrainCycle`. The real brain (`TrueMindCycle` → `BirBrainCycle`) runs only under `MATRIX_MODE=production`. This is the owner's open question F-01: a gateway whose default answers come from a stub is a gateway whose health check can mislead |
| owns | the brain cycle, the persistent mind (`data/mind/mind.sqlite`), the goal tracker, the hierarchical memory |

**They do not meet.** Chain B reaches zero classes of the epistemic and cognitive stores; the
isolation is proved by a gate that fails when the chains meet, and by a self-test that
deliberately breaks the isolation to show the gate can go red
(`scripts/w45-chain-isolation-check.sh`, `scripts/w45-chain-isolation-selftest.sh`).

**Six cycles, not five.** The served nine-stage loop, the consciousness loop and the
cognitive kernel are served by chain A; the true mind and the BIR brain by chain B in
production mode; the CLI mind is reachable from the command line only. Two classes named
`BrainLoopService` are not the same thing, and the earlier map named the wrong one.

## How to read a capability: four independent axes

A capability in this repository is described on **four axes that never collapse into one
green checkmark** ([`docs-v2/architecture/CAPABILITY-REGISTRY.yml`](architecture/CAPABILITY-REGISTRY.yml),
35 rows):

| the axis | the question | the values |
|---|---|---|
| **implementation** | does the source exist? | `PROPOSED` · `DESIGNED` · `IMPLEMENTED` · `INACTIVE` · `BLOCKED` · `UNKNOWN` |
| **integration** | is it wired into a production path? | `ISLAND` · `INTEGRATED` · `INTEGRATION_TESTED` · `UNKNOWN` |
| **verification** | what has been executed, and what does that execution prove? | `UNTESTED` · `TESTED` · `INTEGRATION_TESTED` · `MEASURED` · `FORMALLY_VERIFIED` · `REJECTED` · `UNKNOWN` |
| **operational** | what is it doing in production right now? | `DORMANT` · `EXPERIMENTAL` · `ACTIVE` · `SERVED` · `DEPRECATED` · `REMOVED` · `UNKNOWN` |

Three rules make those axes usable rather than decorative:

1. A capability may not be `SERVED` without a **named serving path** — an HTTP route, a CLI,
   a runtime wiring.
2. A capability may not be `MEASURED` without a **reproducible benchmark**.
3. A capability may not be `FORMALLY_VERIFIED` because a TLA+ file exists; it needs the
   model-check evidence.

Two further labels belong to the **wave reports** rather than to the registry, and they are
used the same way here: **BLOCKED** (the capability is real, and something outside the code
prevents it — for example a toolchain that is absent on the host) and **NOT VERIFIED** (the
check was run and produced no evidence, which is not a pass).

## The validation evidence

### The test baseline is a set of scopes, not one number

The full `matrix-core` suite is not run in this repository's practice — it needs the owner's
approval because of its memory cost. The baseline is therefore a set of **scopes**, each
verified on a named commit
([`docs-v2/governance/PROJECT-STATE.md`](governance/PROJECT-STATE.md) §3, and the per-scope
registry `docs-v2/engineering/TEST-CATALOGUE.yml`):

| the scope | the result | the commit |
|---|---|---|
| the cognitive kernel and the epistemic store | 128 classes, 910 tests, 0 failures, 0 skipped | `a6ded233` |
| the W43 regression scope | 98 classes, 1 036 tests, 0 failures | `a6ded233` |
| the brain runtime | 119 classes, 914 tests, 0 failures, 2 skipped | `a6ded233` |
| the API gateway | 43 classes, 240 tests, 0 failures | `a6ded233` |
| the chain-isolation gate and its negative control | 16 checks and 6 checks, 0 failed | `a6ded233` |
| the SIGKILL recovery journey | 37 checks, 0 failed | `a6ded233` |
| the quality gate | 271 literal candidates at the baseline of 271 | `a6ded233` |
| the security boundary | 0 violations | `a6ded233` |

**The total test population is unknown.** An older measurement of 9 816 executions with 24
failures is a floor from a past wave, not a total, and this page does not present it as one.

### The CI record

The **five mandatory checks are green on a clean runner** — run `42206946030` at `f6e2be9f`:
Build & Test, the Web UI tests, the quality gates (the clean-code gate, the pinned formal
tool, the formal gate over **15 TLA+ specifications**, and the security boundary), the
security scan (the secret scan ran for the first time and found nothing) and the container
images (three smoke tests). The only red job is the **release path**, which needs the
owner's Docker Hub and Kubernetes credentials — the repository has no secrets configured, and
that is recorded as `R-W48-04`
([`docs-v2/engineering/W48-STATUS.md`](engineering/W48-STATUS.md) §8 and §12,
[`docs-v2/engineering/W48-CI-RECONCILIATION.md`](engineering/W48-CI-RECONCILIATION.md)).

### The formal method

The specification set is **15 TLA+ specifications**, model-checked in CI by a tool that is
**tracked in the repository and pinned by digest** (`tools/tla2tools.jar`, its provenance in
`tools/README.md`) rather than downloaded from a URL that can be replaced in place. The local
formal gate reports 15 passed, 0 failed.

### The GPU measurement

The GPU is an **optional accelerator** — the owner's decision OD-047 of 2026-11-06 — and not
part of the default serving path
([`docs-v2/engineering/W48-GPU-BENCHMARK.md`](engineering/W48-GPU-BENCHMARK.md)).

| the path | the factor |
|---|---|
| the MATRIX forward call | **12.06×** — four independent runs on one machine: 12.06 / 11.68 / 11.47 / 13.62 |
| the end-to-end path | **12.69×** (11.17× in the fourth run) |
| a single-point ONNX call | **0.24×** — the GPU is about **4× slower** here (0.25× in the fourth run) |

The historical 12.6× figure does not reproduce and is kept as history, bounded by the
measurement above. The benchmark refuses to publish a GPU number when the execution profile
does not name the CUDA provider.

## What is not working

The blockers are published with the same care as the results
([`docs-v2/governance/PROJECT-STATE.md`](governance/PROJECT-STATE.md) §4):

| the blocker | the state |
|---|---|
| the **native-image build** | **BLOCKED** — the GraalVM toolchain is absent on this host; the scope refuses to run rather than report a pass (8 classes, 25 discovered tests) |
| the **gateway's default mode** | serves an 87-line stub unless `MATRIX_MODE=production` is set — the owner's open question F-01 |
| chain A's **undeclared ports** | 9093 (multicast) and 9094 (TCP) bind without appearing in any serving record — F-02 |
| the **readiness probe** | permanently 503; the reactive PostgreSQL datasource gates it, and no production code reads it |
| **no whole-database backup** | the SQLite backup API appears nowhere in the repository and the runbook has no backup procedure |
| the **quality gates** | they run in a CI job named "Quality gates (can fail)", and the standards document does not say so |
| **two formal-spec mutants survive** | `Consensus-evaluate-without-quorum` and `KnowledgeAxes-conflict-forgets-what-it-recorded` — recorded findings, not new failures |
| the **corpus properties** | the two classes that need the owner's corpus **skip** on a clean runner, because the corpus is not in git. A skip is not a pass |
| the **static analysis** | the pinned SpotBugs engine cannot read Java 25 bytecode, so the check produces no evidence; the capability stays NOT VERIFIED |
| the **release path** | "Docker Build & Push" fails at its first step for want of the owner's credentials |

## For researchers

The research body is the part of this repository that is meant to be argued with.

- **[`docs-v2/research/RESEARCH-INDEX.md`](research/RESEARCH-INDEX.md)** — the index of the
  body: two hypothesis registers, 20 preregistration protocols, 62 experiment reports and 6
  syntheses.
- **[`docs-v2/research/NEGATIVE-RESULTS-INDEX.md`](research/NEGATIVE-RESULTS-INDEX.md)** — 37
  indexed negative results: refuted hypotheses, rejected capabilities, corrected claims,
  defects and non-reproducible experiments. **A negative result is evidence and is kept.**
- **[`docs-v2/research/HYPOTHESES-NEW.md`](research/HYPOTHESES-NEW.md)** — where a new
  hypothesis is registered, **before** the run, with its gate fixed in advance.
- **[`docs-v2/research/PROTOCOL.md`](research/PROTOCOL.md)** — the method, and the evidence
  levels that decide what a verdict may say.

The separation rule the body is built on: a research proposal must not become a normative
specification by the passage of time, and a research finding does not authorise a production
integration.

## For contributors

Start with **[`CONTRIBUTING.md`](../CONTRIBUTING.md)** — it carries the two contribution paths
(engineering and research), the prerequisites, the known-good scopes and the gates.

```bash
git clone https://github.com/AlexanderNarbaev/agi.git
cd agi

# chain A — the epistemic application, then http://localhost:9091
./gradlew :matrix-core:quarkusDev

# chain B — the brain runtime, then http://localhost:8765
./gradlew :matrix-api-gateway:writeRuntimeClasspath --no-daemon --console=plain
bash scripts/start-mind.sh

# a targeted scope, and the gates
./gradlew :matrix-core:test --tests "io.matrix.knowledge.*"
bash scripts/quality-gate.sh
bash scripts/security-boundary-check.sh
```

The repository's own guides: [Getting started](guide/getting-started.html) ·
[Architecture](architecture/RUNTIME-TOPOLOGY.html) ·
[Contributing](../CONTRIBUTING.md) ·
[Project state](governance/PROJECT-STATE.html) ·
[Decision register](governance/DECISION-REGISTER.html) ·
[Roadmap](governance/ROADMAP.html) ·
[Open questions](governance/OPEN-QUESTIONS.html).

## Publication and collaboration

The code is licensed under the **GNU Affero General Public License v3** (`LICENSE`; the
licensing decision is `docs-v2/levels/L12-Legal.md`). The collaboration rules are recorded in
`docs-v2/levels/L22.md`:

- **academic freedom** — confirming and refuting results are equally welcome;
- **open access** — code under AGPLv3 plus the ethical terms, datasets under CC0 or CC BY,
  papers at minimum as arXiv preprints;
- **no dual-use** — partnerships with military departments, weapons laboratories and
  mass-surveillance vendors are excluded, and partners undergo the L7 ethical audit;
- **attribution** — co-authorship follows substantial intellectual contribution; technical
  help goes in the acknowledgements.

**How to start a collaboration.** Open an issue that names the problem and the scope — a bug
report, a feature request, or an RFC for an architectural change. For research, register a
hypothesis card in `docs-v2/research/HYPOTHESES-NEW.md` with its gate fixed in advance, and
report the result **whether it is accepted or refuted**.

## Project status

**2026-11-06 — commit `ed3725d5`, branch `develop`.** The wave that produced this page is
**W49**; the wave that preceded it, **W48**, is closed, and its report is
[`docs-v2/engineering/W48-STATUS.md`](engineering/W48-STATUS.md). The canonical snapshot of
the project state is
[`docs-v2/governance/PROJECT-STATE.md`](governance/PROJECT-STATE.md) — it is replaced, not
appended to, as the state moves, and it names the commit it was written at.

| the record | the document |
|---|---|
| what the project is, right now | [`docs-v2/governance/PROJECT-STATE.md`](governance/PROJECT-STATE.md) |
| what actually runs | [`docs-v2/architecture/RUNTIME-TOPOLOGY.md`](architecture/RUNTIME-TOPOLOGY.md) |
| what each capability is, on four axes | [`docs-v2/architecture/CAPABILITY-REGISTRY.yml`](architecture/CAPABILITY-REGISTRY.yml) |
| the test scopes and their commits | [`docs-v2/engineering/TEST-CATALOGUE.yml`](engineering/TEST-CATALOGUE.yml) |
| the last completed wave | [`docs-v2/engineering/W48-STATUS.md`](engineering/W48-STATUS.md) |
| the CI record | [`docs-v2/engineering/W48-CI-RECONCILIATION.md`](engineering/W48-CI-RECONCILIATION.md) |
| the GPU measurement | [`docs-v2/engineering/W48-GPU-BENCHMARK.md`](engineering/W48-GPU-BENCHMARK.md) |
| the decisions and their dates | [`docs-v2/governance/DECISION-REGISTER.md`](governance/DECISION-REGISTER.md) |
| the open questions | [`docs-v2/governance/OPEN-QUESTIONS.md`](governance/OPEN-QUESTIONS.md) |
| the research and the negative results | [`docs-v2/research/RESEARCH-INDEX.md`](research/RESEARCH-INDEX.md) · [`docs-v2/research/NEGATIVE-RESULTS-INDEX.md`](research/NEGATIVE-RESULTS-INDEX.md) |

---

*The published site is <https://alexandernarbaev.github.io/agi/>. The source of this page is
`docs-v2/index.md`; the commit above is the one it was written at, and the wave moves the
branch while it runs.*
