# CONTRIBUTING — MATRIX

MATRIX is a deterministic neuro-symbolic core: knowledge is truth tables and their compilable
forms, learning is separated from runtime, and a runtime decision is a verifiable boolean
chain. Two facts matter before you read anything else:

- **Implemented is not served.** A class can exist, be wired, be measured and still be dormant.
  `docs-v2/architecture/CAPABILITY-REGISTRY.yml` states every capability on four separate axes
  (`implementation` / `integration` / `verification` / `operational`), and a capability may not
  be marked `SERVED` without a named serving path.
- **A claim is a measurement or it is nothing.** Numbers appear in this repository only with
  the run that produced them (`CONSTITUTION.md`, Article VI).

You do not need the wave archive. The documents below are the current state; `docs-v2/waves/`
is history.

---

## 1. Two kinds of contribution

This repository has two contribution paths, and they are reviewed differently.

| | **ENGINEERING** | **RESEARCH** |
|---|---|---|
| the object | code, tests, documentation | a question with a preregistered gate |
| where it starts | an issue (bug report, feature request, RFC) | a hypothesis card in `docs-v2/research/HYPOTHESES-NEW.md` |
| what "done" means | the targeted test scope is green and the gates pass | the preregistered gate was **measured**, and the verdict is recorded — accepted **or** refuted |
| the evidence | the JUnit XML under `<module>/build/test-results/test/*.xml` | the experiment report under `docs-v2/research/reports/` |
| the failure mode we guard against | a silent behavioural change | a claim that was never measured |

Mixing them is the mistake we guard against most often: a research proposal must not become a
specification by the passage of time, and a research finding does not authorise a production
integration (`docs-v2/research/RESEARCH-INDEX.md` §0).

---

## 2. Prerequisites

- **JDK 25.** The build sets `sourceCompatibility = VERSION_25`. Check with `java -version` —
  the JDK the build sees must be 25, not just the JRE on your `PATH`.
- **Gradle 9.6.0** — but do not install it: use the wrapper `./gradlew`, which downloads the
  version pinned in `gradle/wrapper/gradle-wrapper.properties`. `./gradlew --version` prints
  the version actually in use.
- **Nothing else.** The SQLite driver is a declared dependency; the TLA+ tool is tracked in the
  repository under `tools/` with its digest recorded in `tools/README.md`.

There is no separate build for the two serving chains — they are Gradle modules of the same
repository, and the isolation between them is proved by a gate, not by convention.

---

## 3. Get it, build it, run it

```bash
git clone https://github.com/AlexanderNarbaev/agi.git
cd agi

# `develop` is the working branch. The default branch `main` is the release
# branch and currently lags it by 440 commits (measured 2026-11-14,
# `git rev-list --count origin/main..develop`) — a fresh clone that stays on
# `main` gets the older state of the project. The two branches hold the same
# project tree (`git rev-parse origin/main^{tree}` and
# `git rev-parse origin/develop^{tree}` are equal); `main` carries the
# released state as the squash of pull request #50, and `develop` carries the
# same work as its own sequence of commits.
git checkout develop

# The epistemic application (chain A) — the cognitive kernel and the workbench
./gradlew :matrix-core:quarkusDev          # then http://localhost:9091

# The gateway (chain B) — the brain runtime
./gradlew :matrix-api-gateway:writeRuntimeClasspath --no-daemon --console=plain
bash scripts/start-mind.sh                 # then http://localhost:8765
```

`README.md` §4 carries the full quick start, including the exact `curl` calls and the note
about the gateway's default stub mode. `./scripts/fresh-clone-smoke.sh` is the sanity check
from a fresh clone.

---

## 4. The architecture, in the two lines that matter

The system runs as **two serving chains that do not meet** (`docs-v2/architecture/RUNTIME-TOPOLOGY.md`):

| | **chain A** — the epistemic application | **chain B** — the brain runtime |
|---|---|---|
| process | Quarkus, `io.matrix.MatrixApplication` | JDK `HttpServer`, `io.matrix.api.MinimalHttpServer` |
| main surfaces | `/v1/workbench` (57 routes), `/v1/cognitive` (11 routes), `/v1` chat (6 routes), plus 36 more resources | `/v1/analyze`, `/v1/explain`, `/v1/learn`, `/v1/teach`, `/v1/sleep`, `/v1/goals` |
| what it owns | the epistemic store and the cognitive store — claims, cases, evidence, episodes, the request lifecycle | the brain cycle; it reaches **zero** classes of the epistemic or cognitive stores |

The isolation is measured, not assumed: `scripts/w45-chain-isolation-check.sh` resolves the
call graph from the compiled classes and fails when the two chains meet.
`scripts/w45-chain-isolation-selftest.sh` proves that the gate can go red.

---

## 5. Verify — the known-good scopes

**Run a targeted scope, not the whole suite.** The full `matrix-core` suite needs the owner's
approval and a resource estimate (`docs-v2/engineering/TEST-CATALOGUE.yml`, rule 5).

```bash
./gradlew :matrix-core:test --tests "io.matrix.cognitive.*"
./gradlew :matrix-core:test --tests "io.matrix.knowledge.*"
```

The catalogue names every scope with its exact filter, its last measured result and the commit
that result belongs to. The rules that matter:

- **the JUnit XML is the evidence**, not the Gradle console summary — it lives under
  `<module>/build/test-results/test/*.xml`;
- **a baseline is commit-specific** — a stale count is not a baseline;
- **a scope that could not run is not a pass.** The native-binary scope is named and refuses
  to run on a host without GraalVM.

The gates, all of which run in CI:

```bash
scripts/quality-gate.sh            # numeric literals outside named constants
scripts/formal-gate.sh             # every specification in formal/ is model-checked
scripts/security-boundary-check.sh # an anonymous write is refused
```

A benchmark that a claim depends on is a JMH run, not a timer:

```bash
./gradlew :matrix-core:jmh -PjmhBenchmark=Name
```

The method is `docs-v2/research/PROTOCOL.md`; the gate evidence is
`docs-v2/engineering/JMH-GATE-EVIDENCE.md`.

---

## 6. Engineering contributions

1. **Open an issue** that names the problem and the scope: a bug report, a feature request, or
   an RFC for an architectural change. The templates in `.github/ISSUE_TEMPLATE/` ask for the
   fields that make the change reviewable.
2. **Branch** with the naming the project uses — `feature/<id>-<short-description>`,
   `fix/<issue-number>-<short-description>`, and a `docs` branch for documentation only
   (`BRANCHING-STRATEGY.md`).
3. **Implement** with tests. Every new class needs a test (`AGENTS.md`). The project's test
   stack is JUnit 5, jqwik for properties, AssertJ for assertions.
4. **Verify** the targeted scope and paste the command and the XML path into the pull request.
5. **Open the pull request** using `.github/PULL_REQUEST_TEMPLATE.md`.

The rules that bind the code (they are in `CONSTITUTION.md`, and they are checked):

- **no LLM calls and no wall-clock in a decision path**; randomness only where the seed is a
  pure function of the state (`CONSTITUTION.md`, Article I);
- **the coverage gate stays at ≥82 % METHOD on `matrix-core`** (`CONSTITUTION.md`, Article V);
- **the frozen zones are not edited**: `CONSTITUTION.md`, `AGENTS.md`,
  `matrix-core/src/main/java/io/matrix/ethics/frozen/**`,
  `matrix-core/src/main/resources/avro/**`, `.github/workflows/**`
  (`CONSTITUTION.md`, Article III).

---

## 7. Research contributions

The research lifecycle already exists; **do not create a parallel registry**. There are two
hypothesis registers — `docs-v2/research/HYPOTHESES.md` (the older corpus) and
`docs-v2/research/HYPOTHESES-NEW.md` (the current format) — and a third is forbidden
(`docs-v2/research/RESEARCH-INDEX.md` §1). New cards go into
`docs-v2/research/HYPOTHESES-NEW.md`.

**The preregistration comes first.** A card is written and registered **before** the
experiment runs, with the gate fixed in advance, so that the result cannot be re-shaped after
the fact.

**The card format** (the five columns of `docs-v2/research/HYPOTHESES-NEW.md`):

```
| ID | One-line summary | Methodology sketch | Gate criterion | Prereq |
```

**What a submission must contain:**

| the item | where it lands |
|---|---|
| the research question | the `One-line summary` of the card |
| related work and references | the card's `Prereq` cell, and the EXP report's references section |
| the hypothesis | the card, registered **before** the run |
| the proposed method | the `Methodology sketch` cell |
| the dataset and its license | the EXP report — a dataset with no stated license is not usable |
| the baseline | the `Methodology sketch` cell — what the method is compared against |
| acceptance and falsification criteria | the `Gate criterion` cell — numeric thresholds, fixed in advance |
| the required CPU/GPU resources | the EXP report; the machine's limits are in `docs-v2/operations/RESOURCE-BUDGETS.md` |
| the expected execution cost | the EXP report — the wall-clock and the storage the run needs |
| reproducibility instructions | the EXP report — the exact command, the seeds, the corpus |
| limitations and safety considerations | the EXP report, and `CONSTITUTION.md` for the prohibitions |

**The evidence levels** (`docs-v2/research/PROTOCOL.md`) decide what a verdict may say:

| level | what it may conclude |
|---|---|
| single-run JVM | preliminary only — it may not accept a hypothesis |
| JMH-grade | the gate for an acceptance criterion |
| multi-dataset × seeds | preliminary support or refutation, marked `synthetic-scope` |
| production-domain | a full verdict |

**A negative result is a result.** Never delete an experiment because its conclusion was
negative; a refuted hypothesis, a failed algorithm, an incorrect measurement and a run that
could not be completed are all evidence, and they are indexed in
`docs-v2/research/NEGATIVE-RESULTS-INDEX.md`. The statuses there are copied from the source
document, never inferred: `accepted`, `supported`, `refuted`, `refuted-toy`, `inconclusive`,
`not tested`, `superseded`. The `refuted` / `refuted-toy` distinction is load-bearing: a
refutation on a synthetic corpus does not transfer to a domain corpus.

**The report** goes to `docs-v2/research/reports/`, named the way the existing reports are
(`docs-v2/research/reports/EXP-002-report.md`), and the card's verdict is updated in
`docs-v2/research/HYPOTHESES-NEW.md` with a pointer to the report. **The report's numbers are scoped**: every measurement the project has made so
far is on synthetic, seed-controlled corpora, and every report says so. Write `synthetic-scope`
into the scope of your verdict unless your corpus is a real domain corpus.

**A research finding does not authorise a production integration.** Moving from a measured
result to a shipped path requires a decision, a document and a date — recorded in
`docs-v2/governance/DECISION-REGISTER.md`.

---

## 8. Pilot proposals

A pilot is a **domain question**: does a MATRIX capability do something useful on real data,
in a real workflow, with a real stakeholder? The template is
`.github/ISSUE_TEMPLATE/pilot_proposal.md`.

The rule that shapes the whole template: **a pilot proposal must not assume that MATRIX is
production-ready.** It must state, for the capability under evaluation, whether it is
**currently served** (with its serving path) or **research-only** (with the measurement that
would have to happen first). A proposal that assumes readiness is not reviewable, because the
acceptance criteria of an unserved capability are measurements, not integrations.

---

## 9. Review and acceptance

**How a contribution is accepted:** the objective checks decide, not a conversation about
preference. A pull request is accepted when the targeted scope is green, the gates pass, the
frozen zones are untouched, and the claims in its description are backed by the JUnit XML or
the gate's exit code it names.

**Who reviews:** the project is maintained by its owner together with AI agents
(`AGENTS.md`). There is no maintainer team, no review rota and no response time to promise —
so this guide promises none. An open issue or a pull request is how work enters the queue, and
the owner's decision is recorded in `docs-v2/governance/DECISION-REGISTER.md`.

**What happens to a proposal that is declined:** it stays in the issue tracker with the reason.
For a research question that was never run, the card stays `proposed` in
`docs-v2/research/HYPOTHESES-NEW.md`.

**Secrets, credentials and private data** never enter the repository. CI scans every push for
them.

---

## 10. The norms that bind every contribution

- **`CONSTITUTION.md`** is normative and singular. Its Article IV states the four prohibitions
  the system does not violate under any circumstances: it does not kill, does not torture, does
  not enslave, and does not reproduce itself without the operator's explicit consent.
- **The licence is AGPL-3.0** (`LICENSE`), and the same file appends additional conditions on
  the permitted use of the software. There is no second licence.
- **`CODE_OF_CONDUCT`** states how participants treat each other, and it names the one
  reporting channel that actually exists.
- **Claims are written to be checkable.** Forbidden: "AGI", "never lies", "cannot be used for
  harm", and any number without a reproducible measurement (`CONSTITUTION.md`, Article VI).

---

## 11. Where to read more

| the question | the document |
|---|---|
| What is the project today? | `docs-v2/governance/PROJECT-STATE.md` |
| What is planned, and how are tasks registered? | `docs-v2/governance/ROADMAP.md` |
| What was decided, and by whom? | `docs-v2/governance/DECISION-REGISTER.md` |
| What is open, and what blocks it? | `docs-v2/governance/OPEN-QUESTIONS.md` |
| What can the system actually do? | `docs-v2/architecture/CAPABILITY-REGISTRY.yml` |
| What actually runs, and on which ports? | `docs-v2/architecture/RUNTIME-TOPOLOGY.md` |
| Which verification scopes exist? | `docs-v2/engineering/TEST-CATALOGUE.yml` |
| How are experiments measured? | `docs-v2/research/PROTOCOL.md` |
| What is known to be false? | `docs-v2/research/NEGATIVE-RESULTS-INDEX.md` |
| What may a machine spend? | `docs-v2/operations/RESOURCE-BUDGETS.md` |
| What is the whole map? | `docs-v2/INDEX.md` |

The norms themselves: `CONSTITUTION.md`, `AGENTS.md`, `CODE_OF_CONDUCT`, `LICENSE`.
