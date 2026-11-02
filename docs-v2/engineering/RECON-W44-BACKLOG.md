# RECON — W44 backlog

**Directive:** W44 Cognitive Kernel Foundation · 2026-11-02
**Status:** the work that W44 did NOT finish, ordered by what blocks what.

---

## 1. Blocking — the wave is not complete without these

### 1.1 The forced-termination script — **OWNER-CONFIRMABLE, WRITER-READY**

The learning scenario survives a restart over the same file, asserted in-process
(`CognitiveKernelTest.theObservationBecomesACaseThatSurvivesARestart`). The W43 wave proved the
stronger property — a real `kill -9` of the application's own JVM — with
`scripts/w43-counterexample-journey.sh`, which raises the packaged Quarkus jar on a spare port
against a TEMPORARY database and asserts the state after the kill.

**What is needed:** the same script for the cognitive scenario. It should drive
`POST /v1/cognitive/observe` through the settling sequence, kill the JVM, restart it against
the same database and assert that the cases, the predictions, the errors, the promoted procedure
and the task history are all still there — and that the consolidation after the restart reports
no duplicates.

**Why it is ready:** the durability configuration it relies on is already in place —
`PRAGMA journal_mode=WAL` and `PRAGMA synchronous=FULL` in both `KnowledgeStore` and
`CognitiveStore`.

### 1.2 The full `matrix-core` suite

The wave ran the W43 targeted scope (1518 tests, 0 failures) and the whole W44 addition (256
tests, 0 failures). The module holds **8284** `@Test` methods in 1179 files, and the full run
is reserved by owner rule D-017 because it is an OOM risk. **A green CI run over the whole
module after the schema-16 migration is the remaining verification.**

---

## 2. The findings that are recorded but NOT fixed

These are real defects that this wave found and deliberately did not change. Each needs its own
decision.

### 2.1 `POST /v1/workbench/workspaces/{id}/load` erases contradiction history — **SEVERITY: HIGH**

`KnowledgeStore.deleteWorkspace` deletes `contradiction`, `evidence`, `derivation`, `revision`,
`claim`, `source`, `concept`, `representation`, `prerequisite` and `learning_goal` for the
workspace it is replacing (`KnowledgeStore.java:700-726`). The knowledge consolidator itself
only MARKS — it never deletes — so this path is the ONLY one that erases the record of what
contradicted what.

**Options:** (a) refuse the reload of a non-empty workspace unless the caller passes an explicit
`?replace=true`; (b) preserve `contradiction`, `evidence` and `revision` across the reload;
(c) detect and log the erasure of a non-empty contradiction history. Option (c) is the smallest
and the least likely to break a W43 test.

### 2.2 The GPU provider set is not observable — **SEVERITY: MEDIUM**

`OrtEnvironment.getAvailableProviders()` is never called anywhere in the repository.
`OnnxRuntimeAdapter.load()` registers CUDA and then sets
`activeProviders = new String[]{"CPUExecutionProvider"}` unconditionally, with the comment
"Best-effort: just record what we know". `OnnxRuntimeGpuTest` asserts only that the array is
not empty, which the hard-coded value satisfies. The host HAS a CUDA-capable GPU.

**Consequence:** no test can currently prove that a CUDA kernel ran. Any future GPU claim in
this repository is unbacked until the provider list is read from the runtime.

### 2.3 `BiochemicalNetwork` and its island — **SEVERITY: MEDIUM (design debt, not a bug)**

Ten synergy/antagonism/catalysis coefficients with no cited source, four duplicated sets of
production and decay values, five mood thresholds, and a bridge that reads two modulator ids
which are never registered. None of it is reachable. The wave documented it as INACTIVE and did
not revive it; the W45 direction is to either measure it or delete it, not to wire it.

### 2.4 The `graph` package — **SEVERITY: MEDIUM (design debt)**

159 tests, mutation-verified, zero production callers. `SleepConsolidator` documents its own
defect: the anneal guard is sound only for edges that entered `CONNECTED`, which is NOT every
edge the replay phase produces (`SleepConsolidator.java:251-268`). Wiring this into production
without fixing the guard would introduce a defect into a live path.

### 2.5 Three definitions of consolidation, five taxonomies of memory — **SEVERITY: LOW (design debt)**

`io.matrix.sleep.SleepCycle` (reachable, chain B), `io.matrix.memory.ConsolidationCycle`
(unreachable) and `io.matrix.knowledge.KnowledgeConsolidator` (reachable, chain A) are three
different mechanisms with three different meanings. `MemoryHierarchyTier` (M0-M2),
`HierarchicalMemory` (L0-L4), `MemoryHierarchy` (L1-L5), the M3 federation channels and the M4
noosphere are five taxonomies. None of them is reconciled with any other.

---

## 3. The deferred capabilities, with the reason

| Deferred | Reason | What would unblock it |
|---|---|---|
| A learned meta-controller over the portfolio | the directive forbids it before a deterministic, measurable baseline exists | the baseline is measured now; the next step is the oracle-gap measurement |
| Cross-modal grounding — two representations of one fact | the world model holds one representation | a resolution step with an explicit "equivalent / partially compatible / unresolved" verdict |
| Image, audio and video perception | the channel vocabulary exists; the interpretation does not | a perception subsystem per modality |
| A five-class memory hierarchy as code | two classes are implemented and used; the other three would be invented | a mapping from the existing taxonomies onto the five roles |
| A compute-provider SPI with capability discovery | the capability descriptor carries the resource needs the selector needs | a provider layer with a real CPU/GPU difference to discover |
| Energy accounting | there is no wattmeter on this host | hardware, or a documented model |
| Procedure demotion after a live disagreement | the promotion is validated and recorded, but never demoted | the drift experiment in the research agenda (Q2) |
| A cap on the number of live hypotheses | the space is currently unbounded | the experiment in the research agenda (Q4) |

---

## 4. The recommended order

1. the forced-termination script (1.1) — it is the last missing piece of the wave's own
   acceptance criteria;
2. the full `matrix-core` suite (1.2) — the verification the wave could not run;
3. the workspace-reload guard (2.1) — the one HIGH severity finding;
4. the oracle-gap measurement (research agenda Q1) — the precondition for a learned policy;
5. the drift experiment (Q2) — the precondition for safe reuse.

---

## 5. What the owner has to decide

| # | Decision | Why it needs the owner |
|---|---|---|
| 1 | Whether the workspace reload may erase a contradiction history | it changes an existing endpoint's behaviour and its W43 tests |
| 2 | Whether the full `matrix-core` suite may be run on this host | it is an OOM risk and the rule reserves it |
| 3 | Whether the inert biochemistry island is measured or deleted | deleting modules needs the owner's authorisation (the directive forbids it without it) |
| 4 | Whether the `graph` package is fixed and wired, or left as a library | the fix to the anneal guard is a real change to mutation-verified code |
| 5 | Whether the schema-16 `Case` table keeps its name | it is named `epistemic_case` because `CASE` is a reserved SQL word; the alternative is quoted identifiers in every query |
