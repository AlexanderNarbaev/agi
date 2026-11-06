# MATRIX

**Language:** [English](README.md) · [Русский](README.ru.md)

> A deterministic neuro-symbolic core: knowledge is stored as truth tables and their
> compilable forms, learning is separated from inference, and the serving path executes
> compiled artefacts only — no LLM calls, no wall-clock, no unseeded randomness.

**The project in one line:** MATRIX is an open research project on *inspectable inference* —
how to build a system whose answer carries the claim it rests on, the evidence behind that
claim, and the boundary of what it does not know — and it is published with the measurements
that support each statement, including the ones that came out negative.

**Current state:** the canonical snapshot is
[docs-v2/governance/PROJECT-STATE.md](docs-v2/governance/PROJECT-STATE.md) — read it first;
this README is a summary of it, not a substitute for it.

---

## 1. What MATRIX is

MATRIX is a **deterministic neuro-symbolic core**. Three properties define it:

1. **Knowledge is compiled, not prompted.** A proposition is a claim in an epistemic model
   (`Source` → `Claim` → `Evidence` → `Derivation`); the executable form is a truth table
   and its compilable forms (TT / CLAUSESET / BDD). The runtime evaluates compiled
   artefacts. ([PROJECT-STATE.md §1](docs-v2/governance/PROJECT-STATE.md),
   [SPEC-002 — the boolean compute layer](docs-v2/specifications/SPEC-002-boolean-compute-layer.md))
2. **Learning is separated from inference.** The path that acquires and consolidates
   knowledge is a different path from the one that answers. ([SPEC-000 — the developmental
   loop](docs-v2/specifications/SPEC-000-developmental-loop.md))
3. **Determinism is a constraint, not an aspiration.** No LLM call and no unseeded
   randomness on a decision path; a CI gate enforces the first
   ([C-01b](docs-v2/architecture/CAPABILITY-REGISTRY.yml), `.github/workflows/ci.yml`).

**What MATRIX is not.** It is not a general LLM agent, it is not one cognitive cycle, and
it is not a system in which every implemented class is served. The distance between
*implemented* and *served* is the central fact about this repository, and it is measured
on four independent axes in the
[capability registry](docs-v2/architecture/CAPABILITY-REGISTRY.yml) — 36 rows, of which
**17 are `SERVED`** with a named serving path.

**Shape of the tree.** Fourteen Gradle modules, two serving applications, and a large body
of implemented-but-unserved research subsystems
([W46-REPOSITORY-TOPOLOGY.md](docs-v2/engineering/W46-REPOSITORY-TOPOLOGY.md)).

---

## 2. The problem, and the research questions

### The problem

A language model's answer is a token sequence. There is no durable object behind it that
says *this proposition, this evidence, this inference* — and when the answer is wrong, the
user cannot inspect what was believed, why, or where the belief stopped. Three consequences
follow, and they are the ones MATRIX is built against:

- **Provenance is reconstructed after the fact.** The explanation is generated, not recorded.
- **Contradictions are resolved by averaging.** A claim supported by one source and refuted
  by another collapses into a confidence number, and the uncertainty is lost.
- **Abstention is indistinguishable from failure.** A system that does not know and a system
  that guesses produce the same shape of output.

### The research questions

The project states its questions as hypotheses with preregistered gates rather than as
goals — 38 cards in the register, and 20 preregistration protocols under
[`docs-v2/research/protocols/`](docs-v2/research/protocols/), in
[docs-v2/research/HYPOTHESES.md](docs-v2/research/HYPOTHESES.md). A selection, with the
verdict each one carries today:

| the question | the verdict on record |
|---|---|
| `H-002` — does CLAUSESET (Tsetlin) beat MPDT-GA on quality×bytes at ≥10× the speed? | **refuted** (toy scope): GA was faster ×5.5, more accurate by up to 8.75 pp, and 7 500× more compact |
| `H-003` — is no "living" learner (GA, three-factor) justified against Tsetlin? | **refuted** (toy scope) |
| `H-010` — WiSARD WNN vs. TsetlinTrainer within 2 pp of accuracy at ≥10× the speed | **accepted** on the synthetic scope: median 242×, 9/9 on accuracy |
| `H-007` — does the knowledge stack reach Recall@5 ≥ 0.85? | running |
| `H-009` — BIR-classifier parity within ±3 pp of an LLM ≤ 3B at ≥10⁴× less energy | running; preliminary latency ×149 vs. ONNX CPU, fidelity 0.999; the energy gate is pending |
| `H-006` — does the BIR-composition guardrail hold FPR ≤ 5 % at TPR ≥ 95 %, within 50 ms p99 on the JVM? | running: FPR 0 %, TPR 100 %, p99 0 ms verified on the implementation and its unit tests; the full production traffic is pending |
| `H-011` — does the SDM M1 read beat a flat top-K Hamming read on precision@5? | running (the SDM reader is implemented) |

**Not every card has a protocol document of its own, and eleven hypothesis identifiers are
cited across the designs, the specifications and the experiment reports as if they were
declared while no register declares them** — the project records that as an open question
rather than resolving it silently
([OPEN-QUESTIONS.md, Q-W46-19](docs-v2/governance/OPEN-QUESTIONS.md)). The table above uses
only cards that have a protocol document of their own.

Two rules govern the register, and they are the reason the table above contains "toy scope"
and "pending": **a research proposal never silently becomes a specification**, and **a
research finding does not authorise a production integration**
([RESEARCH-INDEX.md](docs-v2/research/RESEARCH-INDEX.md)).

The negative evidence is indexed rather than buried:
[NEGATIVE-RESULTS-INDEX.md](docs-v2/research/NEGATIVE-RESULTS-INDEX.md).

---

## 3. Long-term vision vs. current implementation

**The vision** is a capability ladder — from a reflexive substrate to compositional
reasoning — written down in
[DESIGN-58 — the capability-levels roadmap](docs-v2/designs/DESIGN-58-capability-levels-roadmap.md)
(Levels 0–6, each with a stated behavioural criterion). The project's own rule is that a
measurement of "the brain can do X" is bound to one of those levels
(`AGENTS.md`, META-R5).

**The current implementation** is much smaller than the vision, and the gap is on the
record. The honest summary:

| | the state |
|---|---|
| the serving surface | two HTTP applications, one of them gated behind an explicit mode flag (§4) |
| the epistemic model | implemented, served and exercised: claims, evidence, sources, derivations, revisions, with a non-destructive revision history |
| the cognitive kernel | implemented and served (`POST /v1/cognitive/observe`, `POST /v1/cognitive/solve`) |
| the research subsystems | a large body of implemented, measured and **unserved** code: hyperdimensional memory, MCTS planning, federation, a Tsetlin machine, a connectome model, a biochemical modulator island |
| the autonomy layer | a scheduler that accepts an impulse under a budget and a FROZEN gate, and — in this wave — a consumer that records the accepted impulse as a bounded internal proposal and **executes nothing** (§7) |

The vision is a research programme, not a product plan. It is written down so that a
future measurement can be bound to a level, not so that the present can be described in
its vocabulary.

---

## 4. Current architecture: two applications that do not meet

MATRIX serves **two applications that deliberately do not call each other**. The
separation is enforced and measured, not asserted.

| | **Chain A — the epistemic application** | **Chain B — the brain runtime** |
|---|---|---|
| process | Quarkus, `io.matrix.MatrixApplication` | JDK `HttpServer`, `io.matrix.api.MinimalHttpServer` |
| address | `127.0.0.1:9091` | `127.0.0.1:8765` |
| surface | `POST /v1/workbench/**` (57 routes), `POST /v1/cognitive/**`, `POST /v1/chat/completions` | 28 routes: `/v1/analyze`, `/v1/explain`, `/v1/learn`, `/v1/teach`, `/v1/sleep`, `/v1/goals`, health, metrics |
| the persistent store | one SQLite file, the epistemic and cognitive schemas over it | its own SQLite file, its own NDJSON knowledge base |
| registry rows | `W44-EPISTEMIC`, `W44-COG-KERNEL`, `W44-BRAIN-LOOP`, `W46-CYCLE-CONSCIOUSNESS`, `W44-GOALMGR`, `W45-ATOMIC`, `W45-BUDGET`, `W45-FAILCLOSED`, `W45-SNAPSHOT`, `W46-GPU`, `W44-ALGO-PORTFOLIO` | `W46-CYCLE-TRUEMIND`, `W46-CYCLE-BIR`, `W44-STUB`, `W49-AUTONOMY-PROPOSALS` |

**The isolation is proved over the compiled classes.** The reachability analyser resolves
the call edges from the constant pool of every class on chain B's classpath and finds that
no class reachable from a chain-B entry point reaches the cognitive store, the epistemic
store, or chain A's HTTP surface. The shared **179 classes** are a library
(`io.matrix.bir`, `io.matrix.neuron`, `io.matrix.tsetlin`, `io.matrix.compression`), not a
path to the data. The gate is
[`scripts/w45-chain-isolation-check.sh`](scripts/w45-chain-isolation-check.sh); its negative
control is [`scripts/w45-chain-isolation-selftest.sh`](scripts/w45-chain-isolation-selftest.sh),
which breaks the isolation on purpose and requires the gate to notice.
([W46-ARCHITECTURE-TRUTH-MAP.md](docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md),
[W45.2-CHAIN-BOUNDARY.md](docs-v2/engineering/W45.2-CHAIN-BOUNDARY.md))

**The module graph has no cycles** — at the module level. The measurement covers module
edges only: two class-level cycles exist inside `matrix-core`, and a 42-package strongly
connected component is documented rather than hidden
([`W46-MODULE-GRAPH`](docs-v2/architecture/CAPABILITY-REGISTRY.yml)).

**One mode flag decides what chain B serves.** The gateway's default mode is `stub`: the
`POST /v1/analyze` route answers from a small keyword stub, and `/health/live` reports
`"brain_available": false` while it is serving. The real cycle
(`TrueMindCycle` → `BirBrainCycle`) runs only in production mode, which
[`scripts/start-mind.sh`](scripts/start-mind.sh) selects. The registry row is
[`W44-STUB`](docs-v2/architecture/CAPABILITY-REGISTRY.yml).

---

## 5. What works today, and what is experimental

**Served** — seventeen registry rows, each with a named serving path and its own evidence:
the epistemic workbench, the cognitive kernel, the nine-stage brain loop behind
`POST /v1/chat/completions`, the goal manager, the transactional epistemic write, the cycle
budget, the fail-closed destructive reload, the workspace snapshot, the algorithm selector,
the chain isolation, the single-writer guard, the two chain-B cycles, and the bounded
autonomy proposal record (§7).

**Implemented, measured, and not served** — the capability registry keeps these on a
separate axis. Two examples from the registry's own `DORMANT` rows: the biochemical
modulator island (`W44-BIOCHEM`) and the connectome model (`W44-CONNECTOME`), both with
`serving_path: none`.

**The registry's four axes**, and why they are four:

| axis | the question |
|---|---|
| implementation | does the source exist? |
| integration | is it wired into a production path? |
| verification | what has been executed, and what does that execution prove? |
| operational | what is it doing right now? |

A subsystem can be `IMPLEMENTED` and `DORMANT` at the same time. A gate can be `MEASURED`
and `DORMANT` at the same time. Collapsing them is the error the registry exists to
prevent.

---

## 6. Evidence

### 6.1 Tests

The full `matrix-core` suite is **not** run routinely here — it needs the owner's
approval because of the memory cost. What exists is a set of **scopes**, each verified at a
named commit, in
[docs-v2/engineering/TEST-CATALOGUE.yml](docs-v2/engineering/TEST-CATALOGUE.yml):

| the scope | the classes | the methods | the failures | the skips | measured at |
|---|---|---|---|---|---|
| `io.matrix.cognitive.*` + `io.matrix.knowledge.*` | 134 | 969 | **0** | 0 | `f48427b7`, 2026-11-06 |
| `io.matrix.memory.*` + `io.matrix.brain.*` + `io.matrix.api.*` + `io.matrix.rag.*` | 98 | 1 036 | **0** | 0 | `a6ded233`, 2026-11-04 |
| `io.matrix.brain.runtime.*` | 120 | 919 | **0** | **2** | `af9ba688`, 2026-11-06 |
| the API gateway module | 43 | 240 | **0** | 0 | `a6ded233`, 2026-11-04 |
| the native build configuration | 1 | 2 | **0** | 0 | `31d8f832`, 2026-11-06 |
| **the native binary** | 8 discovered | **0 executed** | — | — | **25 methods not executed — BLOCKED** |

**Two skipped tests are not two passing tests**, and the catalogue says so: the brain-runtime
scope records 2 skips, and the corpus-dependent classes in the other scopes skip where the
owner's corpus is absent, with the reason recorded.

**The one full-suite run on record** was authorised once, on 2026-11-05: **1 226 classes,
9 051 executions, 23 skipped, 25 failures**. Two of those 25 were fixed afterwards; the
remaining 23 are the native-binary classes, which the default JVM task now excludes and
reports as a scope of their own. **That run's JUnit XML was not retained**, so those four
denominators rest on the retained report rather than on a re-readable artefact, and the
failure breakdown was re-derived from XML separately
([W48-TEST-RECONCILIATION.md §1](docs-v2/engineering/W48-TEST-RECONCILIATION.md)).

**The evidence is the JUnit XML** under `<module>/build/test-results/test/*.xml`, not the
Gradle console summary (`AGENTS.md`, §Тестирование).

### 6.2 Gates

Each of these is a script that **exits non-zero when it fails**, and each has a negative
control proving it can still go red.

| the gate | the command | the recorded result |
|---|---|---|
| the quality gate | `bash scripts/quality-gate.sh` | 271 literal candidates at a baseline of 271, exit 0 |
| the security boundary | `bash scripts/security-boundary-check.sh` | 0 violations, exit 0 |
| the chain isolation | `bash scripts/w45-chain-isolation-check.sh` | 16 checks, 0 failed, exit 0 |
| its negative control | `bash scripts/w45-chain-isolation-selftest.sh` | 6 checks, 0 failed, exit 0 |
| the crash-recovery journey | `bash scripts/w45-cognitive-recovery-journey.sh` | 37 checks, 0 failed, exit 0 — real `SIGKILL`s against a live writer |

### 6.3 Continuous integration

**The five mandatory CI jobs are GREEN on a clean GitHub runner** — run `42206946030` at
`f6e2be9f`:

| the job | the verdict |
|---|---|
| Build & Test (Java) | success |
| Web UI Tests | success |
| Quality gates (can fail) | success — the clean-code gate, the pinned formal tool, the formal gate over **15 specifications**, the build, and the security boundary |
| Security Scan | success — the secret scan ran for the first time and found nothing; the vulnerable-dependency check ran |
| Container Images (build + smoke) | success — all three smoke tests |

**The one red job is the release path**, and its cause is external: "Docker Build & Push"
fails at its first step with "Username and password required", because the repository has no
secrets configured. The credentials are the owner's to set; no code change substitutes for
them. ([W48-CI-RECONCILIATION.md §4.11](docs-v2/engineering/W48-CI-RECONCILIATION.md))

### 6.4 Formal verification

Fifteen TLA+ specifications live under [`formal/`](formal/), and
[`scripts/formal-gate.sh`](scripts/formal-gate.sh) model-checks every one of them with the
pinned tool. The gate is part of the "Quality gates" CI job.

**What the gate does not prove.** The fifteenth specification's deadlock check is off, its
`WeightMonotonic` property is vacuous by construction, and it says nothing about a
federation with more than three voters. **Two mutants of the mutation harness survived** —
`Consensus-evaluate-without-quorum` and
`KnowledgeAxes-conflict-forgets-what-it-recorded` — recorded holes in the gate's own
coverage, not new failures. ([C-01c](docs-v2/architecture/CAPABILITY-REGISTRY.yml),
[W48-CI-RECONCILIATION.md §4.5](docs-v2/engineering/W48-CI-RECONCILIATION.md))

### 6.5 The GPU measurement

The GPU is an **optional accelerator**, not part of the default serving path — the owner's
decision [OD-047](docs-v2/governance/DECISION-REGISTER.md). With the launcher's
`LD_LIBRARY_PATH` set, the CUDA provider registers and the execution profile names it;
without it, the adapter records the fallback and its cause.

The measurement is reproducible by a committed script
([`scripts/gpu-benchmark.sh`](scripts/gpu-benchmark.sh)) and separates three quantities that
are often merged into one:

| the quantity | the CPU median | the CUDA median | the ratio |
|---|---|---|---|
| the transformer forward pass — MATRIX's own call | 160.995 ms | 13.345 ms | **12.06×** |
| the same, over two further independent runs | — | — | **11.68×**, **11.47×** |
| a fourth independent run, measured in W49 on the same machine | 189.406 ms | 13.908 ms | **13.62×** — the ratio moves with the machine's state; the CPU median differs by 17 % from the run above |
| the raw `session.run`, without the logits readback | 173.846 ms | 7.325 ms | **23.73×** |
| the end-to-end path (tokenise, generate, detokenise) | 2 220.734 ms | 174.952 ms | **12.69×** (11.17× in the W49 run) |
| **a single ONNX point call** | 0.008 ms | 0.034 ms | **0.24× — the GPU is four times slower** (0.25× in the W49 run) |
| the BIR point call | 0.075 µs | 0.080 µs | 0.94× — there is no GPU path for it |

**The historical 12.6× stays historical.** It was one ad-hoc probe with no preserved
artefact; all three re-measurements land 4.3–9.0 % below it. The figure is *bounded* by the
current measurement, not replaced by it. The graph is also **not** fully offloaded: 1 170 of
2 600 nodes still ran on the CPU in the measured session.
([W48-GPU-BENCHMARK.md §1, §5, §6](docs-v2/engineering/W48-GPU-BENCHMARK.md),
[`W46-GPU`](docs-v2/architecture/CAPABILITY-REGISTRY.yml))

---

## 7. Known limitations and open research problems

This section is the one the project asks a reader to read twice. Each item names the
artefact that records it.

1. **The native image is BLOCKED.** The build *configuration* is verified; the *artefact*
   is not. No native executable exists on the development host, and the most recent
   native-image CI run installed its toolchain successfully and then died with an
   `OutOfMemoryError`. The 25 native-binary test methods are **not executed** — they are
   excluded from the default JVM task and reported as a scope that refuses to pretend.
   ([`W48-NATIVE-IMAGE`](docs-v2/architecture/CAPABILITY-REGISTRY.yml),
   [OD-046](docs-v2/governance/DECISION-REGISTER.md))
2. **The autonomy layer records intentions and executes nothing.**
   `ImpulseScheduler.fire(...)` returns `FIRED`, which means the impulse passed the FROZEN
   ethical gate and the budget was allocated — **not** that any work was dispatched or
   completed. The consumer added in this wave (`io.matrix.autonomy.ImpulseProposalConsumer`)
   turns an accepted impulse into a **bounded internal proposal** with a stable id, its
   provenance, its accounted budget, an expiry and an observable terminal outcome
   ([registry row `W49-AUTONOMY-PROPOSALS`](docs-v2/architecture/CAPABILITY-REGISTRY.yml);
   the scope is [ADR-W48-AUTONOMY-IMPULSE-CONSUMER.md §5](docs-v2/engineering/ADR-W48-AUTONOMY-IMPULSE-CONSUMER.md),
   approved as [OD-045](docs-v2/governance/DECISION-REGISTER.md)). **It is not an executor:**
   no message is sent, no command is run, no external service is touched and Chain A is
   never written; the terminal outcome `ACCEPTED_FOR_REVIEW` means *recorded and waiting*,
   not *done*. Two limits are on the record: the store is **in memory**, so an open
   proposal does not survive a restart, and the durable store is a later change with its
   own mandate. Its twelve acceptance and contract tests were executed with 0 failures,
   0 errors and 0 skipped.
   ([SPEC-015](docs-v2/specifications/SPEC-015-autonomy-impulses.md),
   [ADR-W48-AUTONOMY-IMPULSE-CONSUMER.md](docs-v2/engineering/ADR-W48-AUTONOMY-IMPULSE-CONSUMER.md))
3. **The static-analysis capability is NOT VERIFIED.** The pinned SpotBugs engine cannot
   read Java 25 bytecode. An engine that can exists and reports 1 414 findings locally —
   981 of them `EI_EXPOSE_REP*`. The decision about those findings is the owner's.
   ([W48-STATUS.md §8](docs-v2/engineering/W48-STATUS.md))
4. **Two formal-spec mutants survived**, so the formal gate has known holes
   (§6.4).
5. **The release path is red** for want of the owner's credentials (§6.3).
6. **Two container smoke tests have never reached their verdict.** The api-gateway step
   failed first and the job stopped there; the matrix-core and minikube steps have had
   their configuration corrected but remain **NOT VERIFIED**.
7. **The Helm chart's port does not match the application.** The chart declares
   `containerPort: 8080` and probes `/q/health/live`, while the application serves on
   `QUARKUS_HTTP_PORT` (default 9091) and binds `QUARKUS_HTTP_HOST` (default
   `127.0.0.1`). Which port the platform serves is an owner-level decision.
8. **Chain B serves a stub by default** (§4), and the GPU falls back to the CPU silently
   unless `LD_LIBRARY_PATH` is set (§6.5).
9. **Two class-level cycles exist inside `matrix-core`**, one of them in the FROZEN
   `ethics/frozen/` zone, plus a 42-package strongly connected component (§4).
10. **A single full-suite run exists**, and its XML was not retained (§6.1). There is no
    standing total for the whole repository, and the published figures that claim one
    correspond to no current run ([`C-01`](docs-v2/architecture/CAPABILITY-REGISTRY.yml)).
11. **The open owner decisions are named and tracked** — the GPU's place in the serving
    path, the shape of the destructive reload's consent parameter, a preview mode for it,
    what a replacement may erase, and the retention policy for the append-only histories
    ([OPEN-QUESTIONS.md](docs-v2/governance/OPEN-QUESTIONS.md)).

---

## 8. Building and running a supported path

**Requirements.** JDK 25, and nothing else for the epistemic application. Check before
you build: `java -version` and `javac -version` — on many systems the `java` on `PATH` is a
JRE with no `javac` beside it, and some JDKs are older than 25. Set `JAVA_HOME` to a JDK 25
installation and put it on `PATH`.

### The epistemic application (chain A)

```bash
./gradlew :matrix-core:quarkusBuild --no-daemon --console=plain
java -jar matrix-core/build/quarkus-app/quarkus-run.jar
```

It binds `127.0.0.1:9091` by default (`quarkus.http.host` / `quarkus.http.port` in
`matrix-core/src/main/resources/application.properties`). The two calls that show what it
does:

```bash
# load the bundled knowledge package into a workspace
curl -s -X POST http://127.0.0.1:9091/v1/workbench/workspaces/photosynthesis/load

# ask it something, at a chosen abstraction level
curl -s -X POST http://127.0.0.1:9091/v1/workbench/workspaces/photosynthesis/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"What do plants release during the day?","level":"INTRODUCTORY"}'
```

The answer carries the claims it used and the sources behind them. The knowledge store is a
single SQLite file (`data/knowledge/workbench.sqlite` by default), created on first use.

> The destructive load route **fails closed**: replacing a workspace that holds something
> requires `?replace=true` (or the body field `replaceExisting`), and it leaves a
> recoverable snapshot of what it replaced. The exact shape of that consent parameter is an
> open owner decision — see §7.

### The brain runtime (chain B)

```bash
./gradlew :matrix-api-gateway:installDist --no-daemon --console=plain
bash scripts/start-mind.sh          # production mode
```

It binds `127.0.0.1:8765` by default and **requires a credential in network-exposed
mode** — the security boundary is one decision, applied to every listener
([`scripts/security-boundary-check.sh`](scripts/security-boundary-check.sh)). Check which
mode you are in:

```bash
curl -s http://127.0.0.1:8765/health/live
```

The response carries `"mode"` and `"brain_available"`. In the default `stub` mode
`brain_available` is `false` and the real cycle does not run.

### From a fresh clone

```bash
bash scripts/fresh-clone-smoke.sh    # build, launch, query, then clean up
```

### Verifying it

```bash
# the targeted scopes — each with its own commit in the catalogue
./gradlew :matrix-core:test --tests "io.matrix.cognitive.*" --tests "io.matrix.knowledge.*" --rerun-tasks
./gradlew :matrix-brain-runtime:test --rerun-tasks
./gradlew :matrix-api-gateway:test --rerun-tasks

# the gates
bash scripts/quality-gate.sh
bash scripts/security-boundary-check.sh
bash scripts/w45-chain-isolation-check.sh
bash scripts/w45-cognitive-recovery-journey.sh
```

**Do not run `./gradlew test` for the whole repository without the owner's agreement** —
the `matrix-core` test JVM asks for 16 GiB, and two concurrent runs do not fit in the
machine's available memory (`AGENTS.md`, §Тестирование; the limits are tabulated in
[docs-v2/operations/RESOURCE-BUDGETS.md](docs-v2/operations/RESOURCE-BUDGETS.md)).

---

## 9. How engineers and researchers participate

The project is developed by one owner together with AI agents under a spec-driven
protocol; the rules are the same for people and for agents.

**Read first, in this order:**

1. [CONSTITUTION.md](CONSTITUTION.md) — the invariants and prohibitions. They are
   normative.
2. [AGENTS.md](AGENTS.md) — the working protocol.
3. [docs-v2/governance/AGENT-OPERATING-PROTOCOL.md](docs-v2/governance/AGENT-OPERATING-PROTOCOL.md)
   — the session initialisation, the task classification, the autonomy levels, the
   subagent protocol and the handoff format.
4. [docs-v2/governance/PROJECT-STATE.md](docs-v2/governance/PROJECT-STATE.md) — the current
   state, and what is blocked.

**The workflow.**

- A **feature** is a full specification cycle: specification → plan → tasks →
  implementation → acceptance against the specification's own criteria.
- A **bug fix** or a small change (≈30 lines) is a change with a test.
- An **experiment** is a hypothesis card in
  [docs-v2/research/HYPOTHESES.md](docs-v2/research/HYPOTHESES.md) *before* it runs
  (preregistration), and a report after — **whatever the outcome**. A refuted hypothesis
  is a result, not a failure, and it is indexed in
  [NEGATIVE-RESULTS-INDEX.md](docs-v2/research/NEGATIVE-RESULTS-INDEX.md).
- Every claim a document makes about the system must name the artefact that proves it.
  The vocabulary for that is the four-axis capability registry (§5).

**The rules that are not negotiable.** No randomness without a seed and no wall-clock or
LLM call on a decision path; every new class carries tests; a number without a reproducible
benchmark behind it is not published; and the forbidden vocabulary of
[`AGENTS.md`](AGENTS.md) and `CONSTITUTION.md` Article VI — including "AGI" and
"production-grade" — is not used to describe the system.

**Where to look for the history.** The wave records are in
[docs-v2/waves/](docs-v2/waves/) and in `docs-v2/engineering/W4*.md`; they are the record of
what was done and are not rewritten. The decisions are in
[docs-v2/governance/DECISION-REGISTER.md](docs-v2/governance/DECISION-REGISTER.md); the open
questions in
[docs-v2/governance/OPEN-QUESTIONS.md](docs-v2/governance/OPEN-QUESTIONS.md); the risks in
[docs-v2/governance/RISK-REGISTER.md](docs-v2/governance/RISK-REGISTER.md).

**Contributing.** [CONTRIBUTING.md](CONTRIBUTING.md) states the acceptance conditions.
[CODE_OF_CONDUCT](CODE_OF_CONDUCT) and [SECURITY](SECURITY) are in the repository root.

**No channels are invented here.** This project does not operate a forum, a chat, or a
support queue, and this README does not promise a response time — see
[OD-048](docs-v2/governance/DECISION-REGISTER.md), which records the deliberate
non-actions.

---

## 10. Research documentation, license, citation

### The research body

[docs-v2/research/](docs-v2/research/) holds the hypothesis registers, the preregistration
protocols, the experiment reports and the syntheses;
[RESEARCH-INDEX.md](docs-v2/research/RESEARCH-INDEX.md) is its map and
[NEGATIVE-RESULTS-INDEX.md](docs-v2/research/NEGATIVE-RESULTS-INDEX.md) is the index of what
was refuted.

### The specifications

[`docs-v2/specifications/`](docs-v2/specifications/) holds the normative contracts, each
with its own status; the index is
[`docs-v2/specifications/INDEX.md`](docs-v2/specifications/INDEX.md).

### License

**GNU Affero General Public License v3.0** — the full text is in
[LICENSE](LICENSE). The project also states a policy of ethical use, and contribution is
accepted only with agreement to the Constitution's prohibitions.

### Citation

There is no `CITATION.cff` in this repository, and this README does not invent one. Cite the
repository itself, at the commit you used, and cite the artefact behind each claim:

```text
MATRIX — a deterministic neuro-symbolic core.
Repository: https://github.com/AlexanderNarbaev/agi
Commit:    <the commit you read>
Capability claims: docs-v2/architecture/CAPABILITY-REGISTRY.yml
Test evidence:     docs-v2/engineering/TEST-CATALOGUE.yml
```

If a result matters to your work, cite the **artefact** that carries it — the wave report,
the experiment report, the formal specification with its model-check output, or the
committed benchmark with its raw samples — not this README. Every such artefact names the
command that produced it.

---

**A last word on the evidence standard.** A skipped test is not a pass. A proposed feature
is not implemented. An isolated class is not a production capability. A blocked build is
not supported. A GPU is optional. An approved proposal is not a shipped one. Where this
README cannot point at an artefact, it says nothing — and where the artefact records a
negative result, that result is here too.
