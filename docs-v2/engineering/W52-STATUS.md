# W52 — Completion report

**Status:** `CANONICAL` for the wave · **Date:** 2026-11-09 · **Branch:** `develop`
**The objective:** *"Develop MATRIX into a coherent, interacting cognitive computing system."*
**The primary success criterion, in the mandate's own words:** *"not the volume of code written
… a coherent, testable system in which perception, knowledge, memory, reasoning, learning,
action, reflection and extensibility work together."*

W51 built the pieces. W52 makes them **speak to each other**: the canonical vocabulary is
filled by the runtime that runs it, the bridge carries traffic in **both** directions, and the
system's memory of its own methods survives the death of the process.

---

## 1. The one-paragraph answer

MATRIX's canonical contracts are no longer a vocabulary the runtime never speaks. The
cognitive cycle now produces the **canonical form of itself** — the goal, the task, the
hypotheses, the prediction, the experiment, the action, the outcome, the observation, the
evidence, the belief, the event, the algorithm, the procedure, the skill, the evaluation, the
reflection, the world state and its transitions, the validity, the correlation, the
capability, the confidence and the provenance — and carries it in its outcome; the
**acceptance is a test** that drives the real kernel and asserts that every contract the
mandate names has a producer, and the two that the micro-world cannot produce are named as
absent rather than faked. The **bridge now has two directions**: chain B's brain runtime
offers what it observed, and chain A's epistemic core offers what it has **established** — the
right names the direction, the signature, the capability, the budget, the idempotency key
and the audit are the same governance on both sides, and the cross-process demonstration
proves both crossings with two real processes and measures chain B's brain growing by
exactly one piece of knowledge. The system's **profile of its own methods is durable**: the
ledger of measured costs was thrown away by every restart, and now it lives in the cognitive
database, so a process that starts knows what its own methods cost before it has run any of
them, and the selector's decision names the measurements *and* their outcomes. The epistemic
evaluation still reads **18 of 29** — and the wave that measured it three more times and
**left the threshold where it was**, because every configuration that made the answers better
made the abstentions worse.

---

## 2. The deliverables of this wave

| # | the deliverable | where it is | the evidence |
|---|---|---|---|
| 1 | **The canonical contracts get their producers** | `io.matrix.contract.CognitiveCycle`, `io.matrix.cognitive.canonical.CanonicalCycleBuilder`, `io.matrix.cognitive.canonical.CycleFacts` | `CanonicalContractCoverageTest` — the acceptance: the real kernel is driven and **every** canonical contract is asserted to have a producer; `CanonicalCycleBuilderTest` — 9 tests of the mapping's rules |
| 2 | **The explicit boundary between the two vocabularies** | `CanonicalCycleBuilder` (the pure mapping), `CycleFacts` (the bag that crosses it) | the runtime's own records stay the store's representation; the canonical form is the interchange form; CD-026 |
| 3 | **The world state holds the connection the domain asserts** | `io.matrix.cognitive.worldstate.StateRelation`, `WorldStateMemory` | the memory owns the count, the evidence and the confidence of the relation; the domain may not assert its own certainty; CD-027 |
| 4 | **The second direction of the bridge** | `io.matrix.bridge.KnowledgePublisher` (chain A), `io.matrix.api.bridge.KnowledgeBridge` (chain B), `POST /v1/bridge/knowledge` | `KnowledgeDirectionTest` (9 tests), `KnowledgePublisherTest` (2), `KnowledgeBridgeTest` (6) |
| 5 | **The protocol that governs both directions** | `io.matrix.contract.bridge` — the `KNOWLEDGE` right, version 2, the answer that carries the effect | the version rule: version 1 (the first direction) is still read, a version this build has never heard of is refused with its number named |
| 6 | **The gate that measures the second direction** | `scripts/w45-chain-isolation-check.sh` — **G10** | **25 checks, 0 failed**: neither side of the second direction reaches a class of the epistemic store, and the receiver reaches no class of chain A's bridge server |
| 7 | **The cross-process demonstration of both directions** | `scripts/w52-bridge-both-directions-demo.sh` | **10 checks, 0 failed**: chain B publishes an observation to chain A; chain A publishes established knowledge to chain B; chain B's brain takes it and its memory grows from **1 to 2** |
| 8 | **The durable profile of the system's own methods** | `cognitive_method_measurement` (the cognitive database), `io.matrix.cognitive.algorithms.MethodMeasurementStore`, `CostLedger` | `MethodProfileDurabilityTest` — 5 tests: the measurement is written, the store is closed, the store is opened again, and the selector weighs the restored measurement and names it |
| 9 | **The epistemic evaluation, re-run and measured three more times** | `docs-v2/engineering/W52-EPISTEMIC-EVALUATION.json`, `scripts/w51-epistemic-eval.py` | **18 of 29** — the same as W51, no regression; §5.4 holds the three measurements and the decision they forced |
| 10 | **The registers that tell the truth about all of it** | `docs-v2/architecture/CAPABILITY-REGISTRY.yml`, `docs-v2/governance/PROJECT-STATE.md`, `docs-v2/governance/ROADMAP.md` §4.11, `docs-v2/governance/DECISION-REGISTER.md` CD-026…CD-029 | the capability registry grows from **53 to 54** rows; the roadmap's §4.11 is closed task by task |

---

## 3. What the system can now do, and what it cannot

### 3.1 It can

- **fill the canonical vocabulary from the runtime.** A cycle that runs produces the canonical
  form of itself, and the mapping is one named, pure, tested function. The null field of the
  canonical cycle is a fact: a cycle that did not produce a record carries no record, and the
  report says which contracts each cycle filled.
- **talk to itself in both directions.** Chain A's epistemic core offers what it established
  and chain B's brain takes it into its memory — under the same signature, the same
  capability check, the same budget, the same idempotency key and the same audit as the
  first direction.
- **remember how its own methods behave.** The profile of a method — its cost, its calls,
  its successes, its failures — is durable, and the selector's decision names it.
- **hold the connection its domain asserts, with its own evidence.** The world state holds
  that the assignment determines the lamp, and the memory — not the domain — owns the count
  of the observations that carried it, the evidence and the confidence.

### 3.2 It cannot

- **produce a validated procedure in the micro-world.** The procedural memory stays empty
  there, by the W45 finding: the lamp world's input domain is exhausted once the model
  settles, so the validation set can never hold the two independent observations the
  promotion requires. The canonical `Procedure` and `Skill` therefore have a producer but
  the micro-world cannot reach it, and the report says so.
- **make the two chains reason about each other's contents.** The second direction carries
  propositions and their standing; it does not carry the brain's answer back as a claim, and
  the epistemic store does not learn from chain B's brain.
- **separate the answerable from the unanswerable by coverage.** Measured three ways in §5.4:
  every configuration that cites more of the right evidence also cites more of the wrong one.

---

## 4. The architectural decisions of this wave

| the decision | where | what it fixes |
|---|---|---|
| **CD-026** — the canonical vocabulary is the *interchange* form; the runtime's own records stay the store's representation; the boundary is one named, tested mapping | `DECISION-REGISTER.md` | two vocabularies for one concept are a competing implementation; a rewrite of the durable path would be a refactor of a working path for no measured gain |
| **CD-027** — a domain may assert a connection; it may not assert how well supported it is | `DECISION-REGISTER.md` | the world-state memory owns the count, the evidence and the confidence of every relation it holds |
| **CD-028** — the bridge's second direction carries *established knowledge*, and the right names the direction | `DECISION-REGISTER.md` | the protocol is one protocol with two directions; the granted rights are the direction, so a message of the wrong direction is refused for the same reason a forged one is |
| **CD-029** — the relevance floor of the epistemic retrieval stays where it is | `DECISION-REGISTER.md` | three measured configurations: every one that made the answers better made the abstentions worse |

---

## 5. The measurements

### 5.1 The wave's own scopes

| the scope | the command | the result |
|---|---|---|
| matrix-core, the wave's scopes | `./gradlew :matrix-core:test --tests "io.matrix.contract.*" … --offline` | **1 352 tests, 0 failures, 0 errors** |
| the gateway's scopes | `./gradlew :matrix-api-gateway:test --offline` | green |
| the quality gate | `bash scripts/quality-gate.sh` | **PASS** — 271 literal candidates (shell 78, java 193) against a baseline of 271: the wave adds **zero** magic literals |
| the isolation gate | `bash scripts/w45-chain-isolation-check.sh` | **25 checks, 0 failed** — 22 of W51's and the wave's own G10 |
| the gate's negative control | `bash scripts/w45-chain-isolation-selftest.sh` | green |

### 5.2 The canonical contracts

- the acceptance: `CanonicalContractCoverageTest` drives the real kernel with a contradicting
  environment over twelve cycles and asserts that **every** contract the mandate names has a
  producer — twenty of them from the cycle, the `Plugin` from the plugin registry, and the
  `Procedure` and the `Skill` from the procedural memory;
- the demonstration's report carries the canonical section: **19 contracts produced** by the
  cycle it ran, and **3 named as absent** — `Skill`, `Procedure`, `Plugin` — with the reason
  in the report itself;
- the mapping's own rules are tested in `CanonicalCycleBuilderTest`: an absent fact becomes an
  absent record, a hypothesis with no support is UNRESOLVED, a hypothesis with contradictions
  is CONTESTED even when other cases support it, a procedure that was not validated is **not**
  marked reusable, and the evidence and the belief stay two records.

### 5.3 The bridge, in both directions

- the cross-process demonstration (`scripts/w52-bridge-both-directions-demo.sh`): **10 checks,
  0 failed**. Both chains run as separate processes on ports the operating system chose;
- the first direction: chain B's publisher is accepted by chain A;
- the second direction: chain A's publisher is accepted by chain B, and the answer carries the
  outcome — "the brain took the proposition";
- **the brain's memory is measured**: after the first offering it holds **1** item, after the
  second it holds **2** — the growth is exactly one, and the answer says how much the brain
  holds;
- a forgery in the second direction is refused with the signature named, exactly as in the
  first;
- the owner's epistemic database is byte-identical before and after.

### 5.4 The epistemic evaluation — 18 of 29, and three measurements that were not shipped

The evaluation was re-run against this wave's build: **18 of 29**, the same as W51 — the
wave's changes cost the epistemic model nothing. The eleven failures were then MEASURED
rather than argued about, by changing the relevance floor of the retriever and re-running the
whole evaluation each time:

| the configuration | the total | the answerable cases | the unanswerable cases |
|---|---|---|---|
| **0.25, IDF-weighted (the shipped one)** | **18 / 29** | 2 / 8 | **6 / 6** |
| 0.25, counted over the question's terms | 19 / 29 | 4 / 8 | 5 / 6 |
| 0.12, IDF-weighted | 15 / 29 | 6 / 8 | 1 / 6 |

**The reading:** the floor is what keeps the system from answering a question it cannot
answer. Every configuration that cites more of the right evidence also cites more of the wrong
one: the unanswerable "How many research vessels are surveying the mid-ocean ridges at the
moment?" and the answerable "How does subduction at a convergent boundary produce deep
earthquakes and volcanoes?" sit in the same band of coverage. **The floor therefore stays
where it is, and the failures stay failures.** The score was not bought with a threshold, and
no expectation was changed, no case was excluded and no assertion was weakened.

The causes of the eleven failures, named:

| the case | the category | the cause |
|---|---|---|
| A-02, A-04, A-05, A-06, M-01, M-03, P-01, P-02 | answerable, multi-claim | the relevance floor: the claim that carries the expected proposition does not reach it. The floor is measured with the IDF weight, and the IDF weight is a **ranking** device — it gives the question's rare, idiosyncratic words a high weight, so a claim that covers the question's *topic* words and not its framing is excluded |
| F-02 | false-premise | the lexical limit of the contradiction rule: five differently-worded denials of the premise do not have identical stems, so the system reports UNRESOLVED rather than CONTRADICTED. **The honest verdict is kept** |
| R-04, R-05 | revision | the lexical false positive of the revision rule: a sentence that reads as a denial of a claim the workspace holds is a denial in the words, not in the meaning |

### 5.5 The durable method profile

- the profile is written by the single writer of the cognitive database, in the table
  `cognitive_method_measurement`;
- the acceptance: the measurement is written, the store is closed, the store is opened again,
  and the selector's decision reads *"measured 5000ns; 2 call(s), 1 success(es), 1
  failure(es)"* — the measurement **and** its outcomes;
- a method that was never measured stays unmeasured, so the selector does not invent a
  measurement it does not have;
- the profile of one workspace does not leak into another;
- the process-local ledger still works and says that it is process-local.

---

## 6. What changed in the repository

| the path | the change |
|---|---|
| `matrix-core/src/main/java/io/matrix/contract/CognitiveCycle.java` | **new** — the canonical envelope of a cycle; a null field is a fact |
| `matrix-core/src/main/java/io/matrix/cognitive/canonical/CycleFacts.java` | **new** — the boundary bag between the runtime's records and the canonical ones |
| `matrix-core/src/main/java/io/matrix/cognitive/canonical/CanonicalCycleBuilder.java` | **new** — the mapping; pure, named, tested |
| `matrix-core/src/main/java/io/matrix/cognitive/worldstate/StateRelation.java` | **new** — the connection the domain asserts, without its certainty |
| `matrix-core/src/main/java/io/matrix/cognitive/worldstate/WorldStateMemory.java` | the memory owns the relations: the count, the evidence and the confidence |
| `matrix-core/src/main/java/io/matrix/cognitive/CognitiveKernel.java` | the cycle carries the canonical form in its outcome |
| `matrix-core/src/main/java/io/matrix/cognitive/CognitiveOutcome.java` | the outcome carries the canonical cycle |
| `matrix-core/src/main/java/io/matrix/cognitive/CognitiveStore.java` | the table `cognitive_method_measurement` and the two methods of the profile |
| `matrix-core/src/main/java/io/matrix/cognitive/algorithms/CostLedger.java` | the ledger is durable, and it loads the profile at construction |
| `matrix-core/src/main/java/io/matrix/cognitive/algorithms/MethodMeasurementStore.java` | **new** — where the measurement is kept |
| `matrix-core/src/main/java/io/matrix/cognitive/algorithms/AlgorithmSelector.java` | the decision names the measurement's outcomes |
| `matrix-core/src/main/java/io/matrix/contract/bridge/*` | the `KNOWLEDGE` right, the version rule, the answer that carries the effect, the sink that may answer |
| `matrix-core/src/main/java/io/matrix/bridge/KnowledgePublisher.java` | **new** — chain A's side of the second direction |
| `matrix-core/src/main/java/io/matrix/bridge/KnowledgePublisherCli.java` | **new** — the command-line face of it, for the cross-process demonstration |
| `matrix-api-gateway/src/main/java/io/matrix/api/bridge/KnowledgeBridge.java` | **new** — chain B's side: the governed receiver and the sink that teaches the brain |
| `matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java` | the route `POST /v1/bridge/knowledge` |
| `scripts/w45-chain-isolation-check.sh`, `scripts/w45-chain-isolation-reachability.py` | the rule **G10** and the two closures it measures |
| `scripts/w52-bridge-both-directions-demo.sh` | **new** — the cross-process demonstration of both directions |
| `docs-v2/engineering/W52-EPISTEMIC-EVALUATION.json` | **new** — this wave's evaluation, machine-readable |

---

## 7. The five categories

### 7.1 Implemented and verified

- **the canonical form of a cycle** — `CognitiveCycle`, `CycleFacts`, `CanonicalCycleBuilder`;
  the acceptance is `CanonicalContractCoverageTest`, and the mapping's own rules are tested
  in `CanonicalCycleBuilderTest` (9 tests);
- **the world state's relations** — `StateRelation` and the memory that owns them, with
  `WorldStateMemoryTest` (7 tests) covering the count, the evidence, the contested status and
  the restart;
- **the second direction of the bridge** — the protocol (`KnowledgeDirectionTest`, 9 tests),
  the publisher (`KnowledgePublisherTest`, 2 tests, over a real socket) and the receiver
  (`KnowledgeBridgeTest`, 6 tests);
- **the durable method profile** — `MethodProfileDurabilityTest` (5 tests), including the
  restart and the selector's reading of it;
- **the gate's G10** — 25 checks, 0 failed;
- **the cross-process demonstration of both directions** — 10 checks, 0 failed, with the
  brain's memory measured before and after;
- **the demonstration's canonical section** — the report says which contracts the cycle filled
  and which it did not.

### 7.2 Implemented but not fully verified

- **the second direction's durability.** Chain B's receiver keeps its request-id registry in
  the process, not in a database. The effect itself is idempotent — the brain's document id
  is derived from the content, so the same proposition taught twice is one document — but a
  restart of chain B between two deliveries of the same request id would teach twice. It is
  named here rather than hidden.
- **the method profile's durability in the presence of a crash.** The profile is written
  inside the same transaction discipline as the rest of the cognitive store, but the wave
  measured the restart and not the crash.

### 7.3 Designed but not implemented

- **the outcome of a task travelling back from chain B as a claim.** The second direction
  carries propositions and their standing; the brain's answer does not yet come back as a
  claim the epistemic store can weigh.
- **the `Procedure` and the `Skill` in a domain that can validate one.** The producers exist;
  the micro-world cannot reach them. A domain with an independent validation set would.
- **a per-method reliability that reaches the score.** The profile is measured and named in
  the decision, but the selector's score still weighs accuracy and cost; the reliability is
  reported, not yet scored.

### 7.4 Blocked

- **the eight citation failures of the epistemic evaluation** are blocked on a discriminator
  that does not exist yet: something that tells "the workspace holds the right evidence for
  this question" from "the workspace holds a claim that shares a few words with it", without
  a threshold. §5.4 holds the three measurements that show the threshold cannot be it.
- **the two revision failures** are blocked on a relation that is read from the words and not
  from the meaning. The system's own reason says so, in every decision it records.

### 7.5 Deferred

- **the six ARCHIVE candidates** of the W51 audit: the owner's call, and this wave did not
  make it.
- **the four inherited CI findings**: Security Scan (SpotBugs cannot read the class-file
  version), ShellCheck (182 findings at head, against 210 at `a098f283`), TruffleHog's
  unverified placeholders, and the native build's heap exhaustion. None of them is this
  wave's, and none of them is suppressed here.

---

## 8. The honest limits, in one list

1. the epistemic evaluation reads **18 of 29**, the same as W51; the eleven failures are named
   with their causes and **no expectation was changed**;
2. the relevance floor was measured in three configurations and **left where it was**, because
   every configuration that made the answers better made the abstentions worse;
3. the micro-world cannot promote a validated procedure, so the canonical `Procedure` and
   `Skill` have producers the demonstration cannot reach — the report names them as absent;
4. chain B's request-id registry for the second direction is process-scoped;
5. the method profile's reliability is reported in the decision but is not yet part of the
   score;
6. the two chains inform each other in one direction of meaning each: chain A offers what it
   established, chain B offers what it observed — and neither offers the other's reasoning;
7. the wave adds **zero** magic literals, and the quality gate is unchanged at 271;
8. the four inherited CI findings are still there, and the wave did not suppress them.

---

## 9. How to reproduce everything in this report

```bash
# the wave's own scopes
JAVA_HOME=$HOME/jdk25 ./gradlew :matrix-core:test --tests "io.matrix.contract.*" \
  --tests "io.matrix.plugin.*" --tests "io.matrix.runtime.*" --tests "io.matrix.bridge.*" \
  --tests "io.matrix.learning.env.*" --tests "io.matrix.nativekernel.*" \
  --tests "io.matrix.w51.*" --tests "io.matrix.safety.*" --tests "io.matrix.knowledge.*" \
  --tests "io.matrix.benchmark.w51.*" --tests "io.matrix.cognitive.*" --offline

# the gateway's scopes
JAVA_HOME=$HOME/jdk25 ./gradlew :matrix-api-gateway:test --offline

# the canonical contracts: the acceptance
JAVA_HOME=$HOME/jdk25 ./gradlew :matrix-core:test \
  --tests "io.matrix.cognitive.canonical.*" --offline

# the bridge: the isolation gate and its negative control
bash scripts/w45-chain-isolation-check.sh
bash scripts/w45-chain-isolation-selftest.sh

# the bridge: BOTH directions, across two processes
bash scripts/w52-bridge-both-directions-demo.sh

# the demonstration, with its machine-readable trace
JAVA_HOME=$HOME/jdk25 ./gradlew :matrix-core:w51Demonstration --offline

# the epistemic evaluation, with its machine-readable report
JAVA_HOME=$HOME/jdk25 python3 scripts/w51-epistemic-eval.py \
  --report docs-v2/engineering/W52-EPISTEMIC-EVALUATION.json

# the quality gate
bash scripts/quality-gate.sh
```

---

## 10. The state of the working tree

The wave's changes are committed on `develop`. The merge into `main` is the owner's, and no
agent performs it: the W52 pull request is opened and left for review.
