# W45.0 — TEST REPORT

**Status:** every number in this document was produced by an executed run and read out of the
JUnit XML the run left behind.
**Environment:** OpenJDK 25.0.4.1 (`JAVA_HOME=/home/alexandr-narbaev/jdk25`), Gradle 9.6.0,
Ubuntu 26.04.1 / Linux 7.0.0-42, Ryzen 9 9955HX, 32 threads, 59 GiB.
**Date:** 2026-11-03.

**The environment note that matters:** GraalVM's tooling is **not** present on this host
(`native-image`, `gu` and `gu-build` are absent and the JDK is not a GraalVM), and
`matrix-core/build/native` does not exist — **no native binary has ever been built here.**
Every result below is a **JVM** result. No native-image test was executed, and none is
counted as passing.

---

## 1. The scopes that were run

### Scope 1 — the cognitive kernel and everything under it (the W45 work)

```
export JAVA_HOME=/home/alexandr-narbaev/jdk25
./gradlew :matrix-core:test --tests "io.matrix.cognitive.*" --rerun-tasks
```

| | |
|---|---|
| Gradle task | `:matrix-core:test` |
| filter | `io.matrix.cognitive.*` — every class in the package and its sub-packages |
| test classes executed | **38** |
| test executions | **332** |
| failures | **0** |
| errors | **0** |
| skipped | **0** |
| JVM or native | **JVM** |

**What is in the scope.** The 38 classes are the W44 suite (the kernel, the solver, the
algorithms, the micro-world, the neuromodulation), the six classes the W45 Priority B work
added (`CognitiveStoreIdentityTest`, `CognitiveStoreMigrationTest`,
`CognitiveServiceIdentityTest`, `IdempotencyRegistryTest`, `EpisodeIdAllocatorTest`,
`CognitiveResourceTest`), and the four classes the W45 wave added
(`CognitiveContradictionDurabilityTest`, `CognitiveCycleBudgetTest`,
`CognitiveRegulatoryPersistenceTest`, `CognitiveConcurrencyTest`).

### Scope 2 — the W43 regression scope (matrix-core)

```
export JAVA_HOME=/home/alexandr-narbaev/jdk25
./gradlew :matrix-core:test \
  --tests "io.matrix.knowledge.*" --tests "io.matrix.memory.*" \
  --tests "io.matrix.brain.*" --tests "io.matrix.api.*" --tests "io.matrix.rag.*" \
  --rerun-tasks
```

| | |
|---|---|
| Gradle task | `:matrix-core:test` |
| filter | the five W43 packages, exactly as the W43 wave ran them |
| test classes executed | **177** |
| test executions | **1518** |
| failures | **0** |
| errors | **0** |
| skipped | **0** |
| JVM or native | **JVM** |

**This is the W43 baseline number exactly.** The W43 wave recorded 1518 tests and 0
failures for this scope; the W45 wave produces 1518 and 0. The count did not move, which is
the point: the knowledge store's schema v17 migration and the atomic epistemic write
changed no W43 behaviour.

### Scope 3 — the brain runtime and the API gateway

```
export JAVA_HOME=/home/alexandr-narbaev/jdk25
./gradlew :matrix-brain-runtime:test :matrix-api-gateway:test --rerun-tasks
```

| module | test classes | executions | failures | errors | skipped | JVM or native |
|---|---|---|---|---|---|---|
| `matrix-brain-runtime` | **119** | **914** | **0** | **0** | **2** | JVM |
| `matrix-api-gateway` | **42** | **237** | **0** | **0** | **0** | JVM |

**The brain runtime's 914 is the W44 baseline exactly** (914 tests, 0 failures, 2 skipped),
and the gateway's 237 is the W44 number for that module. Nothing in the brain runtime moved.

---

## 2. What was NOT run, and why

| not run | why |
|---|---|
| the full `matrix-core` suite | **forbidden by the W45 directive.** The RECON-W44.0 wave already ran it once, unintentionally, and that execution is evidence — `W44-TEST-EVIDENCE-RECONCILIATION.md`. Repeating it is not a reason to spend the cost. |
| any native-image test | the tooling is not on this host, and the native binary has never been built here. The eight native-image classes are therefore **not executed and not counted** — see §4. |
| the whole repository | out of scope for this wave and forbidden by owner rule D-017 (the full run is reserved). |

**Therefore this report makes no claim about the repository as a whole.** It makes claims
about three scopes, with their exact filters and their exact denominators, and nothing more.

---

## 3. The new tests, and what each one proves

### `CognitiveContradictionDurabilityTest` — 13 tests

| test | what it proves |
|---|---|
| a session sees a contradicting observation holds it out of its hypothesis set | the live path holds a contradiction against a settled belief |
| **the W44 restoration path applied a held contradiction — the characterisation of the defect** | the defect is kept as a test, so it cannot come back unnoticed: the W44 reading applies the contradiction and empties the model |
| a held contradiction survives a restart and stays out of the active model | the durable disposition is read back and honoured |
| a later independent observation does not turn the old contradiction into active data | a later event does not release a conflict |
| the reference method reads the model-eligible observations, not every observation | the reference is a reference for the model in production |
| the reference method and the fast path read the same observations | one rule, one dataset |
| the case, its source, its evidence, the link and the disposition are all written | the atomic write leaves the whole structure |
| a review can adjudicate a held observation without deleting anything | the adjudication path adds a record and deletes nothing |
| a v16 database is migrated to v17 and its dispositions are backfilled | the migration is additive and conservative |
| a fresh database is built at the current schema version | the version chain reaches 17 |
| the case's origin is not the disposition | the two questions are not conflated |
| the disposition table is the one the store reads | the persisted form is the one the code writes |
| an environment that contradicts the model produces a held observation in a cycle | the end-to-end kernel path holds it |

### `CognitiveCycleBudgetTest` — 5 tests

| test | what it proves |
|---|---|
| a cycle that finishes inside its budget completes normally | the deadline does not fire spuriously |
| a cycle that runs out of budget ends in an explicit, persisted terminal state | the status is `RESOURCE_LIMITED`, the trace has a `TIMEOUT` step, the notes say what had happened |
| the timeout is persisted: the episode ends and the trace survives | the episode is closed and the trace is on disk |
| work that had already happened is not rolled back when the budget expires | a probe that already happened is not pretended away |
| the deadline reports the time that is left, and it never goes negative | the arithmetic |

### `CognitiveRegulatoryPersistenceTest` — 6 tests

| test | what it proves |
|---|---|
| the regulatory state is persisted and restored after a real restart | the files are closed and opened again and the drives are the same |
| the provenance of every regulatory change is recorded | every event has an id, a kind, a source and an episode |
| a repeated event is refused: a drive must not move twice for one event | the replay rule |
| the version of the regulatory state increases with every write | the version is monotonic |
| a cycle persists the state it produced, and says so in the trace | the cycle's own record shows the write |
| the cost ledger is PROCESS-LOCAL: a measurement never survives a restart | the ledger's scope is the contract, not an accident |

### `CognitiveConcurrencyTest` — 5 tests

| test | what it proves |
|---|---|
| concurrent observations produce independent episodes, predictions and traces | eight concurrent requests → eight episodes, each with its own trace |
| concurrent reads during writes see a consistent store | reads during writes never fail and never see a half-written episode |
| the episode sequence never hands out the same number twice | 32 concurrent allocations → 32 distinct numbers |
| the regulatory version advances once per write, with no lost update | 16 concurrent writes → versions 1…16, the highest is stored |
| the epistemic records of an observation are linked, never half-linked | every case has a source and every evidence points at a case that exists |

### The Priority B tests (the identity work)

`CognitiveStoreIdentityTest` (15), `CognitiveStoreMigrationTest` (4),
`CognitiveServiceIdentityTest` (8), `IdempotencyRegistryTest` (7),
`EpisodeIdAllocatorTest` (4), `CognitiveResourceTest` (6) — **44 tests, all green.**

---

## 4. The comparison with the W44 evidence

| | W44 evidence | W45 evidence | what moved |
|---|---|---|---|
| matrix-core, cognitive scope | 257 tests, 0 failures (W44 wave) | **332 tests, 0 failures** | +75 tests, all new; the W44 assertions that encoded the defects were **changed**, not deleted (W45 decision D10) |
| matrix-core, W43 scope | 1518 tests, 0 failures | **1518 tests, 0 failures** | nothing |
| matrix-brain-runtime | 914 tests, 0 failures, 2 skipped | **914 tests, 0 failures, 2 skipped** | nothing |
| matrix-api-gateway | 237 tests, 0 failures | **237 tests, 0 failures** | nothing |
| the whole matrix-core suite | 8817 executions, 24 failures, 18 skipped | **not run** | forbidden by the directive |
| the GraalVM-dependent classes | 8 classes, 23 failures | **not run** | the tooling is absent on this host; they are **not counted as passing** |

### The eight native-image classes, listed so they are not forgotten

`io.matrix.nativebin.NativeBinaryCLITest` (8 failures), `NativeBinaryStartupBenchmarkTest`
(3), `NativeBinaryBackgroundServiceTest` (3),
`io.matrix.benchmark.NativeBinaryLaunchBenchmarkTest` (3),
`NativeBinaryPerformanceBenchmarkTest` (2), `NativeBinaryFullIntegrationTest` (2),
`NativeBinaryDockerTest` (1), `io.matrix.benchmark.JVMvsNativeBenchmarkTest` (1) — **23
failures in the W44 run**, all of them needing
`build/native/nativeCompile/matrix-core`.

**And the ninth class, which the archaeology misclassified:**
`io.matrix.consciousness.Exp228ComplianceTest` (1 failure) is **not** a native-image test. It
scans `matrix-core/src/main/java` and fails a wall-clock compliance rule
(`random=63, wallClock=47, 21 unclaimed wall-clock site`, `KnowledgeImporter.java:51`). The
correct split is therefore **8 native-image classes / 23 failures, plus 1 ordinary JVM
failure** — see `W45-TEST-EVIDENCE-RECONCILIATION.md` §5.

---

## 5. The W44 tests this wave changed

Four assertions in three files encoded the defects the wave fixes. Each change is recorded in
`W45-DECISION-LOG.md` D10; the list is here so that a reviewer sees them in the diff.

| file | test | the change |
|---|---|---|
| `CognitiveKernelTest` | `onceTheModelSettlesTheFastPathIsSelected` | now asserts that **no** procedure is promoted and that the refusal says why |
| `CognitiveKernelTest` | the `observe(...)` / `cycle(...)` helpers | the signal id is now unique per request instead of the payload's hash |
| `MicroWorldSessionTest` | `TEST-LEARN-004` | the adversarial observation now contradicts a **settled** prediction, and the test says why |
| `MicroWorldSessionTest` | `TEST-LEARN-005` | **new** — an observation against an **unsettled** prediction is learning, not a conflict |
| `ProcedureStoreTest` | the two `new Procedure(...)` calls | the record gained the `validationKind` component |

**No test was deleted.**

---

## 6. The tests the wave could not write

| what | why |
|---|---|
| a test that the **micro-world settles** through the kernel with the W44 rule | it does not: with the W44 rule the model stalls at two survivors. The rule was changed (D3) rather than the test being written to accommodate the deadlock. |
| a test that a **promoted procedure is demoted** by a live disagreement | **nothing is promoted** (D5), so there is no live instance to demote. The mechanism exists (`Procedure.invalidatedAt`, `ProcedureStore.invalidate`) and is unit-tested; the end-to-end path has no instance until a domain exists where promotion can fire. **This is recorded as an open gap, not as a passing test.** |
| a test that the **250 ms budget** is met in production | the budget is a configured convention, not a calibrated one. The wave did not measure the cycle's latency distribution, and no claim about the latency is made. |
| a test of the **destructive workspace reload** | that endpoint's behaviour is one of the three owner decisions, and it is not changed without approval. |
