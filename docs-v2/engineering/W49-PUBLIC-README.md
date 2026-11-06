# W49 — THE PUBLIC README (TASK-W49-04)

**Status:** `ACTIVE` — the change log for the root README rewrite · **Date:** 2026-11-06
**Task:** `TASK-W49-04` — *"the root README — an English README with a Russian companion, a
clear language switch, and no stale claim"* (the task id and the wording are from
`docs-v2/governance/ROADMAP.md` §4.8)
**The decision this implements:** [OD-048](../governance/DECISION-REGISTER.md) — *Public
Research Presence*, accepted 2026-11-06.
**Files written:** `README.md` (rewritten), `README.ru.md` (new).
**Nothing else was written, and nothing was committed or pushed.**

**This document is not TASK-W49-03.** The public-presence audit — every high-visibility
public document compared with the repository — is a separate task with its own artefact
(`docs-v2/engineering/W49-PUBLIC-PRESENCE-AUDIT.md` <!-- no-link: TASK-W49-03's artefact; it does not exist yet -->, and it is not this file. This file
records what the README rewrite changed and what each sentence in it rests on.

---

## 1. The evidence standard applied

OD-048's binding part is the evidence standard: *"every public capability statement must be
traceable to `docs-v2/architecture/CAPABILITY-REGISTRY.yml`,
`docs-v2/engineering/TEST-CATALOGUE.yml`, `docs-v2/governance/PROJECT-STATE.md`, the wave
reports, the research protocols, the formal specifications with their model-check results,
or a committed benchmark and its output."*

Every claim in `README.md` and `README.ru.md` was therefore written against a named
artefact, and the artefact is linked **inline**, at the point of the claim. A claim with no
artefact was not written.

**The seven prohibitions, and where each one was load-bearing:**

| the prohibition | where it bit |
|---|---|
| a skipped test is not a pass | the brain-runtime scope records **2 skipped**; the corpus-dependent classes skip where the owner's corpus is absent; the README prints the skips in its own column |
| unrelated benchmarks are not aggregated into one number | §6.5 prints **six** separate rows — the forward pass, the raw compute, the end-to-end path, the ONNX point call, the BIR point call — each with its own CPU and CUDA medians, and never one "speed-up" |
| a proposed feature is not implemented | the scheduler's `FIRED` is written as *accepted*, not *executed*, and the consumer that was added in this wave is written as a **recorder, not an executor** — with its two recorded limits (an in-memory store, and a terminal outcome that means *waiting*) |
| an approved-but-unimplemented autonomy consumer is not implemented | the consumer exists in the tree and its twelve tests were executed, so the README states what it does **and what it does not do**; the scope is the ADR's, and the README quotes the ADR's own limits. If the wave had not landed it, the README would have said "approved and not implemented" — the sentence is not allowed to be softer than the tree |
| an isolated class is not a production capability | the two `DORMANT` registry rows (`W44-BIOCHEM`, `W44-CONNECTOME`) are named as implemented-and-unserved, with `serving_path: none` |
| the blocked native-image build is not supported | §7 states that the native scope has **0 executed** and **25 methods not executed**, and that the *artefact* is not verified although the *configuration* is |
| the GPU is optional (OD-047) | §6.5 says so in the first sentence, and the point call's **0.24×** — the GPU four times slower — is printed in the same table |
| an approved-but-unimplemented autonomy consumer is not implemented | §7 item 2, in the same words as SPEC-015 and the ADR |

**The negative results are not suppressed.** The GPU loses on a single point call; the
hypothesis register holds two **refuted** cards; the formal gate has two surviving mutants;
the release path is red; the static-analysis capability is NOT VERIFIED; the full-suite XML
was not retained; eleven hypothesis identifiers are cited as if declared and are not.

---

## 2. Every claim, and the artefact it rests on

| the claim in the README | the artefact |
|---|---|
| a deterministic neuro-symbolic core; TT/CLAUSESET/BDD; learning separated from inference; no LLM calls and no unseeded randomness on a decision path | [PROJECT-STATE.md §1](../governance/PROJECT-STATE.md); [C-01b](../architecture/CAPABILITY-REGISTRY.yml) |
| 36 registry rows, **17 `SERVED`** | [CAPABILITY-REGISTRY.yml](../architecture/CAPABILITY-REGISTRY.yml) — computed over the file's own `operational` axis, and re-read after a concurrent W49 change landed (see §6) |
| fourteen Gradle modules, two serving applications | [PROJECT-STATE.md §2](../governance/PROJECT-STATE.md); [W46-REPOSITORY-TOPOLOGY.md](W46-REPOSITORY-TOPOLOGY.md) |
| the research questions and their verdicts | [HYPOTHESES.md](../research/HYPOTHESES.md) — the cards quoted, and the verdicts as written there |
| eleven identifiers cited as if declared, and not declared | [OPEN-QUESTIONS.md, Q-W46-19](../governance/OPEN-QUESTIONS.md) |
| the capability ladder, levels 0–6 | [DESIGN-58](../designs/DESIGN-58-capability-levels-roadmap.md) |
| chain A: Quarkus, `127.0.0.1:9091`, 57 workbench routes, the cognitive kernel, the chat route | the registry rows `W44-EPISTEMIC`, `W44-COG-KERNEL`, `W44-BRAIN-LOOP`, `W46-SERVING-TOPOLOGY` |
| chain B: JDK `HttpServer`, `127.0.0.1:8765`, 28 routes | the registry row `W46-SERVING-TOPOLOGY` |
| the isolation, 179 shared classes, no crossing | [W46-ARCHITECTURE-TRUTH-MAP.md](W46-ARCHITECTURE-TRUTH-MAP.md); [W45.2-CHAIN-BOUNDARY.md](W45.2-CHAIN-BOUNDARY.md) |
| the module graph has no cycles — and the two class-level cycles and the 42-package SCC | the registry row `W46-MODULE-GRAPH` |
| the gateway's default mode is `stub`, and `/health/live` reports `brain_available: false` | the registry row `W44-STUB` |
| the four axes, and that they are four | [CAPABILITY-REGISTRY.yml](../architecture/CAPABILITY-REGISTRY.yml), its own header |
| the five test scopes, their class and method counts, their skips, and their commits | [TEST-CATALOGUE.yml](TEST-CATALOGUE.yml) — the numbers are read from the `result` blocks, not recomputed |
| the one full-suite run: 1 226 classes, 9 051 executions, 23 skipped, 25 failures; the XML not retained | [W48-TEST-RECONCILIATION.md §1](W48-TEST-RECONCILIATION.md) |
| the evidence is the JUnit XML, not the console summary | `AGENTS.md`, §Тестирование |
| the five gates and their recorded results | [TEST-CATALOGUE.yml](TEST-CATALOGUE.yml) — the `GATE-*` and `INTEGRATION-*` entries |
| the five mandatory CI jobs green on a clean runner, run `42206946030` at `f6e2be9f` | [W48-CI-RECONCILIATION.md §4.11](W48-CI-RECONCILIATION.md) |
| the release path red for want of credentials | the same document, §4.11.2; risk `R-W48-04` |
| 15 TLA+ specifications, the gate, and its two limits | the registry row `C-01c`; `formal/`; [W48-CI-RECONCILIATION.md §4.5](W48-CI-RECONCILIATION.md) for the two surviving mutants |
| the GPU: 12.06×, 11.68×, 11.47×; 23.73× raw; 12.69× end-to-end; 0.24× point call; 0.94× BIR | [W48-GPU-BENCHMARK.md §1, §5, §6, §7.1](W48-GPU-BENCHMARK.md) |
| the GPU is optional; the 12.6× is historical; 1 170 of 2 600 nodes stayed on the CPU | [OD-047](../governance/DECISION-REGISTER.md); the registry row `W46-GPU`; §7.2 of the same benchmark |
| the native image is BLOCKED; the configuration verified, the artefact not; the CI run died of OOM | the registry row `W48-NATIVE-IMAGE`; [OD-046](../governance/DECISION-REGISTER.md) |
| the scheduler's `FIRED` means *accepted*; the consumer records a bounded proposal and executes nothing; the store is in memory; 12 tests, 0 failures, 0 errors, 0 skipped | [SPEC-015](../specifications/SPEC-015-autonomy-impulses.md); [ADR-W48-AUTONOMY-IMPULSE-CONSUMER.md](ADR-W48-AUTONOMY-IMPULSE-CONSUMER.md); [OD-045](../governance/DECISION-REGISTER.md); the registry row `W49-AUTONOMY-PROPOSALS`; the JUnit XML `matrix-core/build/test-results/test/TEST-io.matrix.autonomy.ImpulseProposalConsumerTest.xml` (`tests="12" skipped="0" failures="0" errors="0"`, 2026-11-06); the source `matrix-core/src/main/java/io/matrix/autonomy/ImpulseProposalConsumer.java` |
| the static analysis is NOT VERIFIED; 1 414 findings, 981 `EI_EXPOSE_REP*` | [W48-STATUS.md §8](W48-STATUS.md) |
| the Helm chart's port mismatch | the same document, §8 item 11 |
| the two container smoke tests that never reached a verdict | the same document, §8 item 10 |
| the build and run commands | `matrix-core/build.gradle` (`quarkusBuild`), `matrix-api-gateway/build.gradle` (`installDist`), `scripts/start-mind.sh`, `scripts/fresh-clone-smoke.sh`; the routes and their shapes from `LearningWorkbenchResource` and `MinimalHttpServer` |
| the loopback bind and the default ports | `matrix-core/src/main/resources/application.properties`; the registry row `W46-SERVING-TOPOLOGY` |
| the destructive load fails closed | `LearningWorkbenchResource`'s own documentation; the registry row `W45-FAILCLOSED` |
| the resource limits: 16 GiB test JVM, one run at a time | `AGENTS.md`, §Тестирование; [RESOURCE-BUDGETS.md](../operations/RESOURCE-BUDGETS.md) |
| the licence | [LICENSE](../../LICENSE) — GNU Affero General Public License v3.0 |
| no `CITATION.cff` exists | the repository root; the README says so rather than inventing one |
| no channels, no response time | [OD-048](../governance/DECISION-REGISTER.md) — its "deliberate non-actions" |

---

## 3. What was deliberately NOT claimed, and why

| the claim that was available | why it is absent |
|---|---|
| **"9,432 tests / 1,282 suites"** | the registry row `C-01` records it as a number that corresponds to no current run; the README prints the per-scope counts with their commits instead |
| **"the GPU makes MATRIX 12.6× faster"** | the W48 benchmark bounds that digit at 4.3–9.0 % above every re-measurement and calls it historical. The README prints 12.06× / 11.68× / 11.47× with the measurement's own dispersion |
| **one "speed-up" number for the GPU** | five different quantities with five different denominators. A single aggregate would be exactly the aggregation the directive forbids |
| **"the native image is supported"** | there is no native binary on the host and no successful CI build; the capability is `BLOCKED` |
| ~~"the autonomy consumer records proposals"~~ | **it exists in the tree now** — see §7 of the README and the registry row `W49-AUTONOMY-PROPOSALS`. The README states exactly what it records and exactly what it does not do |
| **"the static analysis passes"** | the pinned engine cannot read Java 25 bytecode. The capability is NOT VERIFIED |
| **"the release pipeline works"** | it fails at its first step because the repository has no secrets configured |
| **"the full test suite is green"** | it was run once, with 25 failures of which 23 are native-binary. Two were fixed afterwards |
| **"the formal gate proves the models correct"** | one specification's deadlock check is off, one property is vacuous, and two mutants survived |
| **"MATRIX is production-grade" / "AGI"** | forbidden vocabulary (`AGENTS.md`, `CONSTITUTION.md` Article VI); and the README's own §7 contradicts it |
| **the 51 broken documentation links** | a W46.0 finding, not this task's; the README links only paths that resolve (44 distinct targets, all verified) |
| **a contributor chat, a forum, a response SLA** | OD-048's deliberate non-actions: no such channel exists and none is invented |
| **`docs/spec/` <!-- no-link: the path does not exist; it is named here as one of the stale paths -->, `WAL.md`, `wal/GLOBAL_WAL.md`** | the old README named paths that do not exist (`WAL.md` was renamed in W40; `wal/` is gitignored). Every path the new README names was checked to exist |
| **`H-001`, `H-017` <!-- no-id: the record names the identifiers it is about; none of them is declared in the tree -->, `H-038`** | no document in the tree carries those identifiers, so the repository's own link checker treats a mention of them as an undefined identifier. The README cites only cards that have a protocol document of their own |

---

## 4. What was removed from the previous README, and what made the removal necessary

The previous `README.md` was 121 lines, in Russian, and already carried the W46.2
corrections. What it still contained, and why it is gone:

| what | the evidence that made it wrong |
|---|---|
| `export JAVA_HOME=/home/alexandr-narbaev/jdk25` (twice) | a private home-directory path. The README now says to set `JAVA_HOME` to a JDK 25 installation and to check `java -version` and `javac -version` first — which is the part a stranger needs |
| "the branch `develop`, the commit `a6ded233…`, the last completed wave **W45.2**, the active one **W46**" | a commit hash and wave numbers presented as the current state, when the state has moved twice since. The README points at `PROJECT-STATE.md`, which is the canonical snapshot and which names its own commit |
| "the measured difference **×12.6**" (in the open-decisions list) | bounded by the W48 benchmark; the README quotes the three current runs and marks the digit historical |
| the gateway example as the first thing a reader would run | the gateway's default mode is `stub`. The new README leads with chain A — the epistemic application, whose routes serve the real work — and puts the mode check (`/health/live`) next to the gateway instructions |
| the Russian body | OD-048 asks for an English README with a Russian companion. The Russian file now says the same things, not the old text |

**What was not in the previous README** and is therefore not a removal: the "city of
minds" positioning, the "Hybrid Intelligence Encyclopedia", the W1500 claim and the
production-grade language. Those are in the **deployed Pages site**, which is
`TASK-W49-05`'s scope, not this one.

---

## 5. The verification performed

| the check | the command | the result |
|---|---|---|
| every link target exists | a direct existence check over the 44 (EN) and 43 (RU) distinct relative link targets | **all resolve** |
| the repository's own link checker | `python3 scripts/docs-link-check.py --strict` over the whole repository | **zero strict failures from `README.md` and `README.ru.md`**; the remaining strict failures are the missing W49 wave artefacts that other tasks produce (`W49-STATUS.md`, `W49-NATIVE-BUDGET.md`, `W49-PUBLIC-PRESENCE-AUDIT.md`, `docs/L0…L13` <!-- no-link: the paths are named as the missing ones; they belong to other tasks -->) |
| the same, scoped to the two files | `python3 scripts/docs-link-check.py --scope README.md --scope README.ru.md --strict` | the identifiers `H-017` <!-- no-id: the record names the identifiers it is about --> and `H-038` <!-- no-id: the record names the identifiers it is about --> were reported as undefined; both were removed and the finding was re-run to green |
| the two files agree | read side by side | the same ten sections, the same numbers, the same limitations, the same artefacts; the Russian file names the same paths |

**No build, no test run and no server start was performed by this task.** The README's
numbers are transcribed from the retained artefacts with their commits and dates, not
re-measured — which is the rule the registry enforces on every row.

---

## 6. The uncertainty

1. **The W48 documents are read as evidence, not re-run.** The GPU figures, the CI run and
   the test scopes are as recorded on 2026-11-06. A reader who wants them fresh has the
   commands: `scripts/gpu-benchmark.sh`, and the CI run `42206946030`.
2. **The route counts are the registry's.** "57 workbench routes" and "28 chain-B routes"
   come from the registry rows, not from a fresh census of the resource classes.
3. **The example request bodies are not executed.** The `ask` body uses the record
   `AskRequest(question, level)` and the level `INTRODUCTORY`, both read from the source;
   the response shape was not observed live in this task.
4. **The Russian file is a companion, not a translation.** It says the same things and
   cites the same artefacts; the wording is native Russian, not a transliteration.
5. **Two identifiers were dropped for a mechanical reason.** `H-001`, `H-017` <!-- no-id: the record names the identifiers it is about; none of them is declared in the tree --> and `H-038` <!-- no-id: the record names the identifiers it is about; none of them is declared in the tree -->
   have no document carrying those identifiers, so the repository's link checker treats a
   mention of them as an undefined reference. That is a property of the tree, not a verdict
   on the hypotheses — `H-001` is a card in the register, and the register's own row for it
   carries a `no-id` marker saying the table is a family prefix and not an identifier
   declaration. The README keeps the cards that have a protocol document and says so.
6. **The Pages site and the high-visibility pages are untouched** — `TASK-W49-05` and
   `TASK-W49-06` own them. The README's own §9 and §10 point at the canonical documents a
   reader needs, so the two will not disagree — but the site still carries the old
   positioning until its own task lands.
7. **The autonomy consumer landed while this task was running.** The directive this task
   implements was written when the consumer of OD-045 was *approved and not implemented*,
   and the first draft of the README said so. While the README was being written, the
   implementation landed in the working tree (`io.matrix.autonomy.ImpulseProposalConsumer`
   and its store and record, the registry row `W49-AUTONOMY-PROPOSALS`, and the JUnit XML
   with 12 tests, 0 failures, 0 errors, 0 skipped, executed 2026-11-06). The README was
   **corrected to match the tree**, and the wording was checked against the source: no
   process execution, no network call, no file write and no Chain A write appears in the
   consumer or in its store. The claim is therefore stated as it now stands, with the
   consumer's own two limits (an in-memory store, and a terminal outcome that means
   *waiting*) and with the sentence that it **executes nothing**. If the wave decides not
   to carry that implementation, this paragraph and README §7 item 2 must be revised
   together — the README must not be softer than the tree.
8. **Two counts are volatile by nature.** The registry's row count and its `SERVED` count
   (36 and 17) were re-read from the file after the concurrent change above, but the file
   is a live document: a reader who finds a different number is looking at a later state of
   the registry, and the registry's own `generated` block carries the provenance.
