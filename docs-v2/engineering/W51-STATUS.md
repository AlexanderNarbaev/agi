# W51 — Completion report

**Status:** `CANONICAL` for the wave · **Date:** 2026-11-08 · **Branch:** `develop`
**The objective:** *"Develop MATRIX into a coherent, interacting cognitive computing system."*
**The primary success criterion, in the mandate's own words:** *"not the volume of code written
… a coherent, testable system in which perception, knowledge, memory, reasoning, learning,
action, reflection and extensibility work together."*

---

## 1. The one-paragraph answer

MATRIX now has **one cognitive path that crosses a process boundary under a governed
contract**. The two serving applications stay separate processes with separate databases and
separate single writers; what changed is that chain B's observations now enter chain A's
epistemic model — through **one** named, versioned, authenticated, bounded, audited and
idempotent component, the **bridge**, whose contract (BRIDGE-1…10) was written down by the
repository itself in W45.2 and is implemented and gated here. Around that, the wave added the
**canonical cognitive contracts** (`io.matrix.contract`, 23 records), the **shared runtime**
that declares the parts of the system and what they can do, the **plugin architecture with
creator mode** and a real isolation boundary, an **environment learning loop** with
persistence and reuse, and a **measured Java/C boundary** whose answer was "the Java path
stays the selected path". The end-to-end demonstration performs **15 of 15** acceptance
points, and the isolation gate passes **22 of 22** checks with two new negative controls.

---

## 2. The fourteen deliverables of §15

| # | the deliverable | where it is | the evidence |
|---|---|---|---|
| 1 | **A repository audit and subsystem classification** | `docs-v2/engineering/W51-AUDIT.md` | 19 subsystems classified with KEEP / INTEGRATE / ADAPT / REWRITE CANDIDATE / ARCHIVE, each with a path, a caller and a reason |
| 2 | **An updated architecture map showing actual runtime interactions** | `docs-v2/engineering/W51-ARCHITECTURE-MAP.md` | the transitions with file:line, the contract table, the gate's rules, the memory roles and what is still disconnected |
| 3 | **Updated ADRs on the inter-chain model and the preserved boundaries** | `docs-v2/engineering/ADR-W51-01-cognitive-runtime-and-bridge.md` | what is preserved, what is amended, what is superseded, and the ten bridge requirements mapped to their implementation |
| 4 | **Canonical cognitive contracts and their compatibility strategy** | `io.matrix.contract` (23 records) | `CanonicalContractsTest` — **33 tests**; the version rule is `CognitiveContract.compatible`, the migration strategy is stated in the ADR |
| 5 | **How the integrated cognitive loop is implemented** | `io.matrix.cognitive.CognitiveKernel` (the cycle) + `io.matrix.runtime` (the composition) + `io.matrix.cognitive.worldstate` (the world state) | the demonstration's trace: **14 phases** — PERCEIVE, RESTORE, RESTORE, PREDICT, SELECT, COMPUTE, PROPOSE, PROBE, **WORLD**, OBSERVE, LEARN, REGULATE, REGULATE, ANSWER |
| 6 | **The connected subsystems with concrete production call paths** | the architecture map, §2 | every row carries a `file:line`; the gate's reachability census measures the closure from the compiled classes, not from the source |
| 7 | **The implemented environmental learning demonstration** | `io.matrix.learning.env` over the existing `io.matrix.cognitive.microworld` | **13 tests**; the episode narrows the hypothesis space from 16 to 1 with utility 0.9375, and the cases survive a restart |
| 8 | **The extended W50 epistemic evaluation, with per-case results** | `docs-v2/engineering/W51-EPISTEMIC-EVALUATION.md` + `scripts/w51-epistemic-eval.py` + `matrix-core/src/main/resources/epistemic-eval/` | the machine-readable dataset and the **committed** runner — neither existed before this wave |
| 9 | **The plugin architecture and a trace of the generated plugin's lifecycle** | `io.matrix.plugin` | **16 tests**; the audit holds the whole chain — PROPOSED → BUILDING → SANDBOXED → VALIDATED → SHADOW → ACTIVE |
| 10 | **Java/C benchmark methodology, equivalence results and measured performance** | `docs-v2/engineering/W51-JAVA-C-BOUNDARY.md` + `scripts/w51-native-kernel-bench.sh` | **31 tests**; the equivalence is exact over 2^18 rows; the decision is measured, not assumed |
| 11 | **Resource and configuration changes** | `docs-v2/engineering/W51-PERFORMANCE.md` | the quality gate is at **271 candidates against a baseline of 271** — the wave adds **zero** magic literals |
| 12 | **Test and CI results, including unresolved failures** | §5 of this report | the gate: **22 checks, 0 failed**; the wave's own scopes: **all green**; the four pre-existing CI findings: **still present**, none suppressed |
| 13 | **The remaining disconnected, experimental or archived subsystems** | `W51-AUDIT.md` §7 | twelve entries, each with the reason |
| 14 | **This report, with the four categories** | §7 of this report | |

---

## 3. What the system can now do, and what it cannot

### 3.1 What it can do — with the evidence

1. **Perceive a structured observation.** `CognitiveSignal` carries a channel and a payload; the
   kernel classifies it and writes a durable episode. *(demonstration point 3)*
2. **Hold structured knowledge with provenance and status.** `Claim` + `Evidence` + `Source`,
   with an explicit status vocabulary that separates an observation, an assertion, a candidate,
   a supported belief, a refuted belief, an unresolved proposition and a superseded one. The
   canonical `Belief` adds the same distinction to the interchange form.
3. **Refuse to answer where the evidence does not support an answer.** The retrieval path now
   has a named citability policy and a named abstention boundary; the W50 defect that let a
   candidate claim answer a question is closed.
4. **Learn through interaction.** The cognitive runtime narrows sixteen hypotheses to one
   **by probing, predicting before every probe, and comparing the prediction with the
   outcome** — measured in the demonstration as 16 → 4 → 2 → 1 over three cycles, each cycle
   a durable case. The cases are read back by a later session, which starts with **one**
   survivor instead of sixteen, and they survive a restart.
5. **Hold a world state.** `io.matrix.cognitive.worldstate.WorldStateMemory` keeps the
   system's estimate of the domain in the canonical `WorldState`, records every change as a
   canonical `StateTransition` with its cause, and is consulted **before** the system acts:
   it is what decides whether a probe is a discovery or a confirmation. It is a projection
   over the append-only case log, so it survives a restart without a sixth database.
6. **Select a method from measured evidence, and remember what the measurement was.**
   `AlgorithmSelector` admits a method only inside its declared preconditions, scores it from
   its measured accuracy and its measured cost, and refuses when nothing is admitted. **The
   kernel now measures the method it runs and records the measurement in the shared ledger**,
   so the cost term of a decision is the measurement once the method has been run once, and
   the declared complexity before that. The demonstration shows exactly that: the first
   cycle's decision says *"the cost came from DECLARED_COMPLEXITY"* and the second and third
   say *"the cost came from MEASURED"*.
7. **Reflect as decisions rather than prose.** Every cycle now ends with a **canonical
   `Reflection`** (`io.matrix.cognitive.reflection.CycleReflection`) that names what was
   attempted, which method was selected and why, what was predicted, what happened, what was
   learned and what remains uncertain — and produces the **machine-readable decisions** that
   can be carried out: prefer a method, withdraw a method, leave a proposition unresolved.
   The rule is the outcome, and it distinguishes three cases: a method whose prediction was
   **contradicted** is withdrawn; a method that answered and was confirmed is preferred; a
   method that **could not answer** is neither — it did not fail, the model was unsettled, and
   what remains unresolved is the proposition. The reflection is part of the cycle's trace
   (the `REFLECT` phase) and of the outcome.
8. **Create a capability, validate it, use it under a grant, and revoke it.** Creator mode
   recognises a gap, proposes a plugin, writes a declarative artefact, validates it against
   the cases the gap carried, walks it through the lifecycle, and the registry removes every
   service and handler on revocation.
9. **Cross a process boundary under a contract.** The bridge carries an observation from chain
   B to chain A; chain A records it as an **unverified candidate** and never as a fact.
10. **Survive a restart.** The demonstration closes the store and opens it again: the claims,
   the cases and the model are still there.
11. **Say what it does not know.** The abstention boundary, the `UNRESOLVED` verdict and the
    "lexical candidate — requires evidence" label are three places where the system refuses to
    guess.
12. **Know how far it can trust itself.** The **meta-memory**
    (`io.matrix.knowledge.meta.KnowledgeReliability`) is computed from the claims and the
    revisions the store holds: how many of the claims a source introduced were revised
    afterwards, how many of the claims a validation method validated were revised, and how
    much of the workspace is established at all — with the reasons carried next to the
    numbers. It is a projection, so it cannot disagree with the store and it survives a
    restart; and it does not decide what is citable, which remains the claim's own status.

### 3.2 What it cannot do — stated plainly

- **It does not have general intelligence**, human-like understanding, universal learning, or
  reliable autonomous self-improvement. This wave built a *coherent system with one working
  cognitive path*, not a mind.
- **It does not learn from an open world.** The environment it learns in is a two-variable
  Boolean world with one hidden rule. The loop is real; the world is small.
- **It does not generate arbitrary code.** The creator generates a **bounded family**: a
  numeric threshold classifier. A gap outside that family is **refused with a reason**, not
  faked.
- **Its plugin isolation is weaker than a WASM runtime.** It is a closed interpreter whose
  vocabulary contains no I/O operation at all — a stronger boundary than a classloader (which
  the mandate explicitly forbids calling a sandbox) and a weaker capability than a WASM
  runtime, which this repository cannot obtain offline.
- **It does not cross the boundary in the other direction.** Chain B is the client; chain A
  never asks chain B to think. That direction is not implemented.
- **The native path is not selected.** Measured, with the reasoning in
  `W51-JAVA-C-BOUNDARY.md`: the crossing costs more than the arithmetic it replaces.
- **The knowledge model is not a world model of the open world.** It is a workspace-scoped
  epistemic store plus a world state for one environment.
- **Four pre-existing CI findings are still open**, and no gate was weakened to hide them.

---

## 4. The architectural decision, in one page

**The previous strict separation between Chain A and Chain B is revised — in exactly one
place.** The W45.2 decision ("no bridge, no implicit write path, no automatic trust, no new
network surface") is kept, except for its "no bridge" clause, which is superseded by the
contract the same document already specified.

| | |
|---|---|
| **What is preserved** | two processes; each owns its runtime and its files; each SQLite file keeps its single writer; the snapshot, recovery and fencing guarantees; the writer lock and the ownership token; the fact that chain B reaches **zero** of the store's own classes and **zero** of the classes that name the epistemic database |
| **What is amended** | "no bridge" → **the bridge, and only the bridge**; "no new network surface" → the bridge adds **no port** and **no protocol of its own** — it reuses chain A's existing HTTP port under the new prefix `/v1/bridge` |
| **What is added** | a versioned, signed, bounded, audited, idempotent crossing that may add **an observation, a candidate or an evidence link** and nothing else |

**The direction is chain B → chain A**, as BRIDGE-3 specifies: *"The chain B side is the client
and holds no ambient trust."* Chain B's conclusions are reports, not facts, and they arrive as
such.

**The gate proves it.** The isolation gate keeps G1–G6 and gains **G7** (the shared contract
layer is pure), **G8** (the bridge is the only crossing) and **G9** (the chain B bridge client
reaches no class of the store). The negative control gained **C3** (the contract layer opens a
database) and **C4** (chain B reaches the chain A bridge server), and both make the gate red.

**One correction was made during the wave, and it is recorded.** The first version of G8
compared chain B's closure with the store's *whole* closure and reported 36 "crossings" — the
shared substrate (`io.matrix.bir`, `io.matrix.neuron`, `io.matrix.tsetlin`,
`io.matrix.compression`). That was wrong: `W45.2-CHAIN-BOUNDARY.md` §4.4 says the shared
substrate is not part of the store, and G2 uses the store's **own** classes. G8 now uses the
same distinction.

---

## 5. The measurements

### 5.1 The wave's own test scopes — all green

**The final run of the wave: 1 323 tests in `matrix-core`, 0 failures, 0 errors, 0 skipped**,
over the scopes the wave touched. The full `matrix-core` suite is still not run — that is
the owner's rule, and the reason is in §7.4.

| the scope | the tests | the result |
|---|---|---|
| `io.matrix.contract.*` | 33 | **0 failures** |
| `io.matrix.contract.bridge.*` | 63 | **0 failures** |
| `io.matrix.bridge.*` (the chain A service) | 13 | **0 failures** |
| `io.matrix.plugin.*` | 21 | **0 failures** |
| `io.matrix.safety.*` (§11, of which 27 are the new matrix) | 98 | **0 failures** |
| `io.matrix.runtime.*` | 16 | **0 failures** |
| `io.matrix.learning.env.*` | 13 | **0 failures** |
| `io.matrix.nativekernel.*` | 31 | **0 failures** (with the library absent: 21 pass, 10 aborted by assumption, 0 fail) |
| `io.matrix.w51.*` (the demonstration) | 3 | **0 failures** |
| `io.matrix.benchmark.w51.*` (the meters of §10) | 17 | **0 failures** |
| `io.matrix.knowledge.*` (the W50 fixes of §6 and the meta-memory) | 645 | **0 failures** |
| `io.matrix.cognitive.*` (the kernel, the micro-world, the world-state memory, the reflection) | 364 | **0 failures** |
| `io.matrix.brain.runtime.bridge.*` (in matrix-brain-runtime) | 3 | **0 failures** |

### 5.2 The gates

| the gate | the result |
|---|---|
| the chain isolation gate (G1–G9) | **22 checks, 0 failed**; G7 reaches **19** contract classes from chain B, **0** of them impure; G9 reports the bridge client reachable and reaching **0** store classes |
| the cross-process demonstration | **10 checks, 0 failed** |
| the negative control (C1–C4) | **12 checks, 0 failed** — the gate goes red on each of the four breakages and names the violation each time |
| the quality gate | **271 magic-literal candidates against a baseline of 271** — the wave adds **zero** |

### 5.3 The end-to-end demonstration

`./gradlew :matrix-core:w51Demonstration` → **15 of 15** acceptance points demonstrated, and
the machine-readable trace at `docs-v2/engineering/W51-DEMONSTRATION.json`.

The cycle it runs — and the learning loop it runs afterwards, through the same runtime, until
the model has settled:

```
episode ep-sig-w51-1 · status COMPLETED
method   micro-world-hypothesis-enumeration  (score 1.0 over 1 admitted candidate;
         accuracy evidence 1.0 over 16 cases; cost basis DECLARED_COMPLEXITY)
predict  false  (basis: surviving-hypotheses)
trace    PERCEIVE · RESTORE · RESTORE · PREDICT · SELECT · COMPUTE · PROPOSE · PROBE ·
         WORLD · OBSERVE · LEARN · REGULATE · REGULATE · REFLECT · ANSWER
         (15 steps; the two RESTOREs are the session and the world-state memory, WORLD is
          the state transition the probe caused, and REFLECT is the cycle's evaluation of
          itself)
```

### 5.4 The W50 epistemic evaluation

`docs-v2/engineering/W51-EPISTEMIC-EVALUATION.md` (and its machine-readable `.json`).
**The baseline was reproduced on the wave's own runner first**: a clean worktree at
`a098f283`, built and measured with the same runner, the same dataset and the same
protocol, gave exactly **12 PASS / 17 FAIL** — the W50 number, on the wave's instrument.
Only then were the fixes credited. **Both numbers come from the same runner on the same
dataset; none of the 29 cases was excluded and no expectation was changed.**

| the category | before (`a098f283`) | after (W51 §6) | the change |
|---|---|---|---|
| answerable | 2/8 | 2/8 | — |
| unanswerable | 2/6 | **6/6** | **+4** — U-01, U-03, U-04, U-06 |
| false premise | 4/5 | 4/5 | — |
| multi-claim | 1/3 | 1/3 | — |
| knows what it does not know | 2/2 | 2/2 | — |
| revision | 1/5 | **3/5** | **+2** — R-01, R-02 |
| **the total** | **12/29** | **18/29** | **+6** |

**The eleven that still fail are named, and they fail honestly.** F-02 (the system answers
`UNRESOLVED` where the dataset expects `CONTRADICTED`, because five differently worded
denials do not reconcile — an honest `UNRESOLVED` was kept and the expectation was not
adjusted); R-04 (a supersession that looks **correct** — the document really does deny the
alternative the workspace held — and the dataset's expectation was left alone anyway); R-05
(a lexical false positive); and **seven cases of the second half of D2** — the retrieval
ranks by the coverage of the question's terms and cites what it ranked, not what the
question needs. That last one is the honest remainder of the defect, and it was not
papered over.

**Four existing test expectations changed, and each became stricter.** Each of the old
expectations encoded the defect itself: the import test asserted that every imported claim
stays `CANDIDATE` (it is now `SUPPORTED` — never `VERIFIED` — and only through the
validator); the retriever test asserted that a refuted claim is *present* in the answer's
evidence (it is now absent from the evidence and named in the limitations); and the two
support-score tests changed by 0.01 because a refuted claim no longer drags its confidence
into the mean of the **citable** claims. No assertion was weakened and no case was removed.

**The evidence inside the run:** 348 `SUPPORTED` claims and **0** `CANDIDATE` in the seed
scope; **2 584 of 2 584** claims carry the span they were extracted from; the `workspace`
table is no longer empty; the source `s-publication-66bb6e0c` is no longer rewritten.

**The tests:** the knowledge scope went from **628 tests, 0 failures** to **641 tests, 0
failures** — thirteen new tests (`EpistemicPolicyTest` 11, `EpistemicDurabilityTest` 2).

### 5.5 The Java/C boundary

**The decision: the Java path stays the selected path.** The native path is 17–20× faster
than the BIR *reference API* — but only **0.75–1.02×** against the same reference code called
on flat data, and **67 %** of the native call is the marshalling. The 18× is the cost of the
reference API's two-dimensional arrays, not the cost of the C. The policy carries
`nativeEnabled = false`; turning it on would need a new measurement, not a configuration
change.

### 5.6 The measured hot paths

`docs-v2/engineering/W51-PERFORMANCE.md` — the three paths the wave integrated, measured on
the same machine with the same harness (the numbers are the median of three runs; the
machine was not idle, so the percentiles below are the stable ones):

| the path | p50 | bytes per call | the verdict |
|---|---|---|---|
| the choice of a method (`AlgorithmSelector.decide`) | 2 024 / 1 994 / 2 013 ns for a portfolio of 1 / 2 / 5 | 2 800 / 3 464 / 5 720 | **no bottleneck.** The decision does not get slower with more candidates; only the allocation grows, by roughly 600–730 B per candidate |
| the run of an episode (`MicroWorldEpisodeRunner.run`) | 161 935 ns for 4 steps | 293 258 per episode | the heaviest path measured, **but it is not on a serving path** — the number to watch is ≈ 73 KB per step |
| the crossing of the bridge (`BridgeReceiver.receive`) | 12 013 ns | 31 121 | **the one path where the measurement gave grounds for a change** — and the change was made |

**The one change the measurement justified, and its proof.** The audit chain of the receiver
was a copy-on-write list: every append copied the whole array of records, so an append cost
O(n) in the records already written. The measurement found **+17 635 B/call** between a
1 000-message chain and a 10 000-message chain, and the copy-on-write array predicted the
growth to within 2 %. The chain is now a `ConcurrentLinkedQueue` — constant-time append,
nothing copied — and the same harness measures **13 372 / 13 203 / 13 072 / 13 033 B/call**
for chains of 1 000 / 2 500 / 5 000 / 10 000. **The growth is gone** (339 B across a tenfold
growth, where before it was 17 635 B), and the absolute cost per call fell as well. The
behaviour did not change: the same records, in the same order, and the same snapshot handed
out. The other two findings — the payload parse at 46 % of the path and the hex digest at
13 % — were **not** acted on, because the digest is what makes a replay detectable (BRIDGE-8)
and what makes an audit record identifiable (BRIDGE-7).

### 5.7 A fourth defect the wave's own reading found — and fixed

**The method-selection layer had no memory, and the adapter's own javadoc said so.**
`KernelSelector` holds a `CostLedger` "shared and lives across cycles", and the ledger is
what the selector reads for a method's cost: `assessCost` prefers the measured nanos and
falls back on the declared complexity when there is no measurement. **Nothing ever wrote to
it.** The kernel ran the method and threw the timing away, so the ledger was empty for the
life of the process and **every** decision in **every** cycle weighed `DECLARED_COMPLEXITY`.
The demonstration's own decision record said so: *"the cost came from DECLARED_COMPLEXITY"*.

**The fix.** The kernel measures the method it runs — through the ledger, which records the
cost and whether the call completed — and the next cycle's decision weighs the measurement.
The demonstration now shows the transition: the first cycle says `DECLARED_COMPLEXITY`, the
second and the third say `MEASURED`.

### 5.8 A fifth defect the wave's own test scope found — and fixed

The wave's final test scope ran the **cognitive** package together with the **knowledge**
package for the first time, and one test failed:
`ObservationDispositionDurabilityTest.aRecordingAndAnAdjudicationOfTheSameCaseDoNotUndoEachOther`.
It failed with `SQLiteException: [SQLITE_BUSY] The database file is locked`, and it failed
**about one run in three** — a flake, not a regression, and a pre-existing one: the test
exercises only `KnowledgeStore`, which this wave did not touch until the flake was found.

**The cause.** The store's write transactions were DEFERRED: they took the write lock only
when they first wrote. When a second connection in the same process had committed in the
meantime, SQLite could not upgrade the transaction's snapshot and failed **at once** with
`SQLITE_BUSY` — and the busy timeout does not apply to that case, because waiting cannot
help a snapshot that is already stale. The two stores (`KnowledgeStore` and
`CognitiveStore`) open the **same file**, so this was reachable in production.

**The fix.** Both stores now open their connection with the driver's
`transaction_mode=IMMEDIATE`, so a write transaction takes the lock when it *begins* and
can never be in the state where it cannot be upgraded. The `CognitiveStore` also gained the
`busy_timeout` it never had. The property was verified against the driver, not assumed:

```
the connection's current transaction mode: IMMEDIATE
without the property:                    DEFERRED
```

**The evidence.** The failing test was run **eight times** after the fix with no failure,
and the wave's whole scope — **1 323 tests, 0 failures, 0 errors** — is green with the
cognitive and knowledge packages running together.

### 5.9 The CI findings that remain open

| the finding | the state | the disposition |
|---|---|---|
| SpotBugs cannot read Java 25 bytecode (plugin 6.1.7, engine 4.9.3 → ASM 9.7.1 → major 68 vs 69) | **still present** | the `security` job yields no static-analysis evidence. **Not suppressed.** Fixing it means moving the engine to a version whose ASM reads major 69 — a build change that belongs to the owner. |
| TruffleHog: five unverified PostgreSQL-like findings | **still present; they are documentation placeholders**, not secrets | all five live in `.veai/skills/**` (`deployment-patterns/SKILL.md:340`, `docker-patterns/SKILL.md:39,162`, `prisma-patterns/SKILL.md:221,224`) and hold literal `user`/`pass`/`host`/`db`. No ignore file exists, and **none was added**. |
| ShellCheck: 106 findings across 41 files | **still present, and the wave REDUCED it** | measured with ShellCheck 0.10.0 on the same scope (`find . -name "*.sh" -exec shellcheck {} +`): the tree at `a098f283` had **210** findings and the wave's tree has **182**. **The wave's own four scripts are clean — zero findings** — and the twenty-eight it removed are in the two gate scripts the wave extended. The job is red on `main` at `67b3410d`, `79045c57` and `7a0ed0cc` as well, so it is pre-existing and was not this wave's to fix. |
| The native-image job dies of memory exhaustion | **still present** | `native.yml:28-34` sets no memory limit; `-J-Xmx8g` with `--gc=epsilon` is a hard wall. **Native-image compilation is not a prerequisite of this wave's deliverable**, and the gate was not weakened to make it green. |

### 5.10 The state of pull request #48 on the day it was opened

The owner authorised the commit and the push on 2026-11-08, and pull request #48
(`develop` → `main`) was opened. **It is not merged by an agent.** Its own CI:

| the job | the state | the cause |
|---|---|---|
| Build & Test (Java) | **green** | the full suite on a clean runner |
| Container Images (build + smoke) | **green** | — |
| Web UI Tests | **green** | — |
| Quality gates | **green** (after a re-run) | the first attempt failed to resolve the SpotBugs plugin from the Gradle plugin portal — a transient repository failure, not a code defect; the same head passed in the push run |
| Compile all T-01 modules | **green** | the pull-request attempt hit the same transient plugin-portal failure |
| CONSTITUTION compliance | **green** | — |
| Model-check TLA+ specs | **green** | — |
| Validate ecosystem docs (RU/EN parity) | **green** | — |
| Security Scan | **red** | the inherited findings of §5.9: SpotBugs yields no static analysis (6 645 `Unsupported class file major version` lines) and TruffleHog exits 183 with **0 verified secrets** and 8 unverified documentation placeholders |
| ShellCheck | **red** | the inherited findings of §5.9, reduced by the wave and untouched by it |
| native-build | **red** | the inherited heap budget of §5.9: `OutOfMemoryError: Java heap space` in the image build |

**One defect of the wave's own was found by the CI and fixed:** the reconnaissance inventory
linked the virtual environment's and the web console's installed directories, which exist on
a developer machine and are gitignored — so the link resolved locally and broke in the clean
checkout. The sentence now names them in words, and the gate passes in a clean worktree.

---

## 6. What changed in the repository

### 6.1 New production code

| the package | what it is | the tests |
|---|---|---|
| `io.matrix.contract` | the canonical cognitive contracts (23 records) and the wire envelopes | 33 |
| `io.matrix.contract.bridge` | the bridge protocol — pure, shared by both chains | 63 |
| `io.matrix.bridge` | the chain A bridge service, the sink, the durable journal and the HTTP surface | 13 |
| `io.matrix.brain.runtime.bridge` | the chain B bridge client and its configuration | — |
| `io.matrix.runtime` | the shared runtime: the components, their capabilities, their dependencies, their health | 6 |
| `io.matrix.plugin` | the plugin registry, the lifecycle, the audit, the service ownership, the declarative sandbox, the validator, creator mode | 16 |
| `io.matrix.learning.env` | the episode adapter over the existing micro-world | 13 |
| `io.matrix.nativekernel` | the FFM loader, the policy and the benchmark of the Java/C boundary | 31 |
| `io.matrix.w51` | the end-to-end demonstration | 3 |
| `matrix-core/src/main/c/matrixkernel` | the C kernel | — |

### 6.2 Modified existing code

| the file | the change | the reason |
|---|---|---|
| `matrix-api-gateway/.../ProductionBrainClient.java` | an optional bridge publisher on the serving path | chain B must actually be able to publish, or the bridge is a class nobody calls |
| `matrix-core/src/main/java/io/matrix/cognitive/{KernelSelector,KernelRegulator}.java` | the classes and their constructors became `public` | the demonstration composes the kernel from outside the package; a package-private adapter is a barrier to composition |
| `matrix-core/build.gradle` | one task, `w51Demonstration` | the documented local evaluation path |
| `scripts/w45-chain-isolation-{check,selftest}.sh`, `scripts/w45-chain-isolation-reachability.py` | G7–G9 and the controls C3–C4 | BRIDGE-10 requires the gate to be extended to the bridge before the bridge is merged |
| `io/matrix/knowledge/**` | the eight W50 defects | §6 of the mandate |

**Nothing was deleted.** The wave added files and made three small changes to existing ones;
the classification in `W51-AUDIT.md` §2 says what may be archived, and the archiving is the
owner's call.

---

## 7. The four categories

### 7.1 Implemented and verified

- the canonical cognitive contracts, with their invariants tested (33);
- the bridge protocol (63), the chain A service (13) and the chain B client — every one of
  BRIDGE-1…BRIDGE-10 mapped to a class and a test;
- the extended isolation gate (22 checks, 0 failed) with two new negative controls;
- the shared runtime and its composition (6);
- the environment learning adapter, its persistence and its reuse (13) — and the loop itself
  run **through the cognitive runtime**, measured as sixteen hypotheses narrowing to four, to
  two, to one over three cycles, each cycle a durable case;
- **the world-state memory** — `io.matrix.cognitive.worldstate`, **6 tests**; the estimate is
  the canonical `WorldState`, the changes are the canonical `StateTransition`, the memory is
  consulted before the system acts, and it is a projection over the append-only case log, so
  it survives a restart without a sixth database;
- **the write path's transaction mode** — the two stores that share the owner's database file
  now write IMMEDIATE transactions, which removes a `SQLITE_BUSY` failure that occurred in
  about one run in three (§5.7);
- **the method-performance memory** — the kernel now measures the method it runs and records
  the measurement in the ledger the selector reads, which is the difference between "the
  selector can consider historical measured performance" and "the ledger is always empty";
- **the cycle's reflection** — `io.matrix.cognitive.reflection.CycleReflection`, **5 tests**;
  the canonical `Reflection` and `Evaluation` contracts had no producer and now have one,
  and the reflection's decisions are carried by the outcome and by the trace;
- **the meta-memory** — `io.matrix.knowledge.meta.KnowledgeReliability`, **4 tests**; the last
  of §4's six memory roles that had no carrier now has one, and it is computed from the
  durable claims and revisions rather than from a store of its own;
- the plugin architecture, the declarative sandbox, the validator and creator mode (21);
- **the §11 safety matrix** — 27 tests over eleven failure modes: an unreachable peer, a
  plugin that fails to initialise, a plugin that will not stop, a duplicate delivery, a
  retry that has run out, an exhausted budget, a stale snapshot, an incompatible contract
  version, a corrupted artefact, an ungranted capability and a restart in the middle of a
  learning episode. **Three real defects were found by that matrix and fixed in this wave**,
  each with a test of its own:
  - the sandbox accepted a wall-clock budget and never read it — the budget is now enforced
    after every step (`DeclarativeSandbox`);
  - `RetryPolicy` had no consumer anywhere in the repository — the bridge publisher is now
    that consumer, and it retries a transport failure within the policy's bounds and does
    **not** retry a refusal (`BridgePublisher`);
  - the capability of a plugin was checked only at activation, so a grant narrowed
    afterwards was not enforced — the grant is now consulted on **every call**
    (`PluginServiceRegistry.invoke`);
- the Java/C boundary: the C kernel, the FFM loader, the policy, the equivalence and the
  benchmark (31) — **with the decision that the Java path stays selected**;
- the end-to-end demonstration: 15 of 15 acceptance points (3);
- **the cross-process demonstration** — `scripts/w51-bridge-demo.sh`, **10 checks, 0 failed**:
  chain A is started as a real Quarkus process with a shared secret, chain B's publisher runs
  in a **separate JVM**, and the message crosses over HTTP. The measured result: the first
  publication is `ACCEPTED`; a **true retry** of the same request id is answered with the
  **original** outcome and the **same audit id**; the **same request id reused for a
  different payload** is `REFUSED` with the conflict named; a **forged signature** is
  `REFUSED` with the signature named; the bridged workspace of chain A holds **exactly one**
  claim after three deliveries; and the owner's epistemic database is **byte-identical**
  before and after;
- the W50 epistemic fixes and the committed evaluation runner.

### 7.2 Implemented but not fully verified

- **The cross-process demonstration starts the real chain A**, but it does **not** start the
  real chain B: chain B's side is exercised by its publisher class in its own JVM, with the
  same protocol and the same signer the brain runtime uses. Running the brain runtime's own
  HTTP server as the publisher would add a process and nothing else.
- **The plugin's isolation is verified by construction and by a source-level test**, not by an
  adversarial program that tried to escape. The test proves the interpreter's vocabulary has
  no I/O; it does not prove the absence of a defect in the interpreter itself.
- **The creator's family is one capability wide.** The lifecycle is real for any gap the
  generator can fill, and the refusal for the others is real — but the generator is small.
- **The world-state memory is proved on one domain.** It is written generically — a fact, an
  entity, a property, a transition — and the micro-world's adapter is the only one that
  exists. A second environment would need a second adapter, and none was written.
- **The learning loop is proved on one environment** — a two-variable Boolean world with one
  hidden rule. The loop is real; the world is small.
- **The method-performance ledger is process-local.** It is a cache of what the methods cost
  *in this process*, deliberately not persisted: a decision that weighed a measurement taken
  in an earlier run would depend on a stale number from another execution. After a restart
  the ledger is empty and the first decision falls back on the declared complexity, which is
  what the demonstration shows in its first cycle.
- **The Java/C numbers are one host, one JVM, one compiler.** The ratios were reproducible
  across two runs; the absolute nanoseconds are machine-dependent. Energy, GPU and
  native-image were **not** measured.

### 7.3 Designed but not implemented

- the reverse bridge (chain A asking chain B to think) — the direction BRIDGE-3 does not
  cover;
- a real WASM/WASI sandbox for plugins — the repository has no such runtime and the
  environment has no network to obtain one;
- episodic, semantic, procedural, working, world-state and meta-memory as **six** distinct
  stores — the honest answer is still that they are roles over two stores, not six databases,
  and the mandate forbids the six databases. **One of the six gaps was closed in this wave:**
  the world-state memory now exists (`io.matrix.cognitive.worldstate`), as a projection over
  the case log rather than as a table;
- **meta-memory over the COGNITIVE cycle's own history** — the system's record of how its
  methods behaved over time is the ledger, and it is process-local by design; the reflections
  are durable, but nothing yet projects them into a per-method reliability profile that
  survives a restart. The **knowledge** facet of the meta-memory is closed
  (`io.matrix.knowledge.meta.KnowledgeReliability`); this remaining facet is not;
- a lease or a multi-writer for the epistemic store — deliberately not built; the single
  writer is a guarantee, not a limitation to be fixed;
- the six subsystems marked ARCHIVE in `W51-AUDIT.md` §7.

### 7.4 Blocked

- **The four CI findings of §13 are blocked on the owner's decision or on a dependency
  upgrade**: SpotBugs needs an engine whose ASM reads Java 25; the native-image job needs a
  runner with more memory than the standard one has (the repository's own research says
  ≥16 GB); ShellCheck's 106 findings are pre-existing and were not this wave's to fix
  silently; the TruffleHog findings are documentation placeholders and are harmless, but no
  ignore file was added because adding one is a decision about the repository's secret
  policy.
- **The full `matrix-core` test suite is not run.** It asks the test JVM for 16 GiB, and
  `AGENTS.md` reserves it for the owner. Every scope this wave touched was run individually
  and is green.

### 7.5 Deferred

- the archive of `io.matrix.chain`, `graph.Connectome`, `RecurrentSdm`, `SdmReader`,
  `MemoryHierarchy`, `BudgeterHomeostat`, `ProcedureStore`, `SparseHDC`,
  `HdcAsLlmPreprocessor` and `io.matrix.embodied.*`;
- the MPDT rewrite — the Java class is a genetic algorithm misnamed, and the C is a stub in a
  module that is not built;
- the HDC consolidation — two stacks, one served;
- the GPU path, the sleeping/agent-loop contour and the Minecraft pilot;
- the environment learning beyond the two-variable world.

---

## 8. The honest limits, in one list

1. The demonstration runs **in one process**; the cross-process claim rests on the gate and on
   the bridge's two halves, not on a two-server run.
2. The creator generates **one family** of capability.
3. The native path is **not selected**, on measured grounds.
4. Four CI findings are **open and unsuppressed**.
5. The full test suite is **not run**, by the repository's own rule.
6. The audit is **static**: it resolves the calls the compiler emitted, and it does not cover
   reflection, JNI or a native image built from a different class set.
7. The W50 evaluation's runner was **reconstructed** before it was committed; the numbers
   before and after the fixes come from the same reconstructed logic, and the committed
   runner is the one to trust from now on.
8. The system's knowledge is **workspace-scoped**. It does not model the open world.
9. **There are still two vocabularies, and the wave did not merge them.** The canonical set
   in `io.matrix.contract` is the **interchange** form — the bridge, the plugin system, the
   world-state memory, the reflection and the evaluation use it. The cognitive package keeps
   its own, older, internal records (`io.matrix.cognitive.Prediction`, `Goal`, `Task`,
   `Procedure`, `Experiment`, `Hypothesis`), and they are what the store's schema, the
   W45.1 recovery contracts and the existing tests are written against. Merging them is a
   refactor of a working, durable path and was deliberately not attempted in this wave.
   The measured consequence: of the canonical contracts, the ones with a production user
   today are `Entity`, `Property`, `WorldState`, `StateTransition`, `Observation`,
   `Evidence`, `Belief`, `Plugin`, `Evaluation`, `Reflection`, `Capability`, `Confidence`
   and `Provenance` — and the ones that are still interchange-only are `Relation`, `Event`,
   `Hypothesis`, `Prediction`, `Experiment`, `Action`, `Outcome`, `Goal`, `Task`, `Skill`,
   `Procedure`, `Algorithm`, `ValidityInterval` and `Correlation`. They are meaningful data
   contracts and they are versioned, but nothing in the runtime fills them yet, and this
   report says so rather than implying otherwise.

---

## 9. The state of the working tree, and why nothing is committed

**The wave's work is in the working tree, uncommitted, and that is deliberate.**
`docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` §3 puts *committing and pushing* in the
class of Level 3 operations: **"no commit and no push without the owner's explicit
authorisation. Everything stays in the working tree, uncommitted, until the owner says
otherwise."** The W51 directive did not name the commit as an authorised operation, and the
wave's own record says so explicitly in §3.3 of the roadmap. So nothing was committed, and
nothing was pushed, and **PR #47 (`develop` → `main`) was left open and untouched** — it is
the owner's merge, not an agent's.

**What is in the tree:** **34 modified tracked files and 59 new files, and no deletion of any
tracked file.** The new packages are `io/matrix/contract/**` (the canonical contracts and
the bridge protocol), `io/matrix/bridge/**`, `io/matrix/runtime/**`, `io/matrix/plugin/**`,
`io/matrix/learning/env/**`, `io/matrix/nativekernel/**`, `io/matrix/benchmark/w51/**`,
`io/matrix/w51/**`, `io/matrix/cognitive/worldstate/**` (the world-state memory),
`io/matrix/cognitive/reflection/**` (the cycle's reflection),
`io/matrix/knowledge/meta/**` (the meta-memory), `matrix-core/src/main/c/matrixkernel/**`,
`matrix-brain-runtime/**/bridge/**`, the documents of the wave, four scripts and the tests of
all of it.

**The modified tracked files** are the eight `io/matrix/knowledge/**` classes and four of
their tests (the W50 fixes), `io/matrix/cognitive/{KernelSelector,KernelRegulator}.java`
(public, so the kernel can be composed from outside its package), `CognitiveKernel.java`
(the world-state memory, the method measurement and the reflection), `CognitiveOutcome.java`
(the reflection), `CognitiveStore.java` and `KnowledgeStore.java` (the IMMEDIATE
transactions), `KnowledgeWorkbench.java` (the meta-memory), `ProductionBrainClient.java` and
`MinimalHttpServer.java` (the bridge on chain B's serving path), `matrix-core/build.gradle`
(one task), the three isolation-gate scripts, `docs-v2/{INDEX.md,governance/ROADMAP.md,governance/DECISION-REGISTER.md,architecture/RUNTIME-TOPOLOGY.md}`.

**What is NOT in the tree:** `.dsh-project-memory/admission-shadow.jsonl` — the harness's
own journal, which is not the repository's.

**One risk the wave's own audit named:** `io/matrix/contract/**` is still untracked, and the
W50 fixes in `io/matrix/knowledge/**` compile only because that package is present in the
working tree. **The package must be committed for the fixes to survive a fresh checkout.**
That is the first thing the commit should contain.

---

## 10. How to reproduce everything in this report

```
export JAVA_HOME=/home/alexandr-narbaev/jdk25

# the end-to-end demonstration, with its machine-readable trace
./gradlew :matrix-core:w51Demonstration

# the CROSS-PROCESS demonstration: chain A as a real process, chain B's publisher in another
bash scripts/w51-bridge-demo.sh

# the isolation gate, and its negative control
bash scripts/w45-chain-isolation-check.sh
bash scripts/w45-chain-isolation-selftest.sh

# the wave's own scopes
./gradlew :matrix-core:test --tests "io.matrix.contract.*" \
  --tests "io.matrix.plugin.*" --tests "io.matrix.runtime.*" \
  --tests "io.matrix.bridge.*" --tests "io.matrix.learning.env.*" \
  --tests "io.matrix.nativekernel.*" --tests "io.matrix.w51.*" --offline

# the Java/C boundary: the build, the measurement and the decision
bash scripts/w51-native-kernel-bench.sh

# the quality gate
bash scripts/quality-gate.sh --report
```

**`JAVA_HOME` is required:** the system JDK on this machine is a JRE without `javac`, and the
build fails with *"does not provide the required capabilities: [JAVA_COMPILER]"* without it.
