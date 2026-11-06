# W48 — TEST RECONCILIATION

**Status:** the reconciliation of the full-suite test evidence · **Date:** 2026-11-06
**Branch:** `develop` · **HEAD at the time of writing:** `e9a5c944`
**Task:** W48-06 · **Write scope:** this file only
**The preflight:** `docs-v2/engineering/W48-BASELINE.md`
**The source of the failures:** `docs-v2/engineering/W47-STATUS.md` §11
**The baseline of record:** `docs-v2/engineering/TEST-CATALOGUE.yml`

**The rule this document follows:** every number is traceable to a command or a file. The
**evidence is the JUnit XML**, not the Gradle console summary (`AGENTS.md`, Тестирование).
Where the evidence cannot answer, the cell says `UNKNOWN` and says why.

**The full suite was NOT run for this document.** The owner's approval `OD-041` covered
exactly one run, it was spent on 2026-11-05, and it is not re-usable. Everything measured
here is a **targeted scope**.

---

## 0. What this document is

It is the reconciliation the W48 preflight promised and did not contain: the W48
baseline's §4 says "The exact reconciliation — class, method, category, whether it existed
before W47, whether W48 fixes it — is `docs-v2/engineering/W48-TEST-RECONCILIATION.md`",
and that is this file.

It answers four questions for each of the **25 failures** of the W47 full run:

1. what exactly failed — the class and the method;
2. what kind of failure it is;
3. whether it existed before the W47 wave — **with the proof**, not with an assertion;
4. whether W48 fixed it, what it needs to become green, and where it stands.

It also settles the **24-or-25 discrepancy** that the W47 report leaves open (§2).

**What it is not:** it is not a re-run of the suite, it does not change the quality
baseline, and it does not weaken any gate. It records the state of the evidence.

---

## 1. The denominator

| | the value | the source | the label |
|---|---|---|---|
| the run | the **full `matrix-core` suite**, one test JVM, an 8 GiB heap, the other daemons stopped | `docs-v2/engineering/W47-STATUS.md` §10; the owner's approval is `OD-041` in `docs-v2/governance/DECISION-REGISTER.md` | MEASURED (at the W47 commit) |
| the classes | **1 226** | `docs-v2/engineering/W47-STATUS.md` §10 | MEASURED, **not re-verified here** |
| the executions | **9 051** | `docs-v2/engineering/W47-STATUS.md` §10 | MEASURED, **not re-verified here** |
| the skipped | **23** | `docs-v2/engineering/W47-STATUS.md` §10 | MEASURED, **not re-verified here** |
| the failures | **25** | `docs-v2/engineering/W47-STATUS.md` §10 and §11 | MEASURED; **reconciled here** — §2 |
| the JUnit XML of that run | **not retained** | the corpus under `matrix-core/build/test-results/test/` is overwritten by every test task; no copy is in git and none is in the wave's artefacts | UNKNOWN |

**What the denominator is not, stated plainly.** The W47 XML corpus is gone: the directory
holds only the results of the targeted runs of 2026-11-06 (§4). The four numbers above
(1 226 / 9 051 / 23 / 25) therefore rest on **the W47 report alone** — a retained document,
not a re-readable artefact. They are marked "not re-verified here" for that reason. The
**failure breakdown**, by contrast, is now re-derived from XML (§2, §4).

**What this document does not do.** It does not re-run the full suite, and it does not
claim that the suite is green. The suite is red in the same places it was red on
2026-11-05, minus the one failure W48 has fixed.

---

## 2. The 24-or-25 discrepancy — resolved

### 2.1 The discrepancy, as stated

`docs-v2/engineering/W47-STATUS.md` §11 says **25 failures**. Its own table accounts for
**22 native + 1 compliance + 1 property = 24**. The same section's prose says **"22
failures across 6 classes"** while the table names **eight** classes, and the row above it
says **"3 are examined below"** while the table examines **two** non-native failures.

Three mutually exclusive readings were possible:

| the reading | what it implies |
|---|---|
| (a) the native count is **23**, and the prose's "22" and "6 classes" are transcription errors | the total 25 is right; the breakdown is wrong; **no twenty-fifth failure exists** |
| (b) a **twenty-fifth failure** exists that the table forgot | the table is incomplete; the missing one has to be found |
| (c) the total is **24**, and "25" is wrong | the W47 report, the risk register and the W48 preflight are all wrong in the same place |

### 2.2 The arithmetic

**23 + 1 + 1 = 25.** **22 + 1 + 1 = 24.** The headline 25 closes exactly under reading
(a). The arithmetic alone does not decide it — the evidence does.

### 2.3 The evidence — two independent proofs

**Proof 1 — the W44 measurement, four days before the W47 run.** The W44 archaeology
retained the per-class failure counts in `docs-v2/engineering/W44-TEST-MAP.csv`, produced
by commit `ae6c017b` (2026-11-04) from the JUnit XML of that run. Over exactly the eight
classes the W47 §11 table names, it records:

| the class | the failures in the W44 XML |
|---|---|
| `io.matrix.nativebin.NativeBinaryCLITest` | 8 |
| `io.matrix.nativebin.NativeBinaryBackgroundServiceTest` | 3 |
| `io.matrix.nativebin.NativeBinaryStartupBenchmarkTest` | 3 |
| `io.matrix.benchmark.NativeBinaryLaunchBenchmarkTest` | 3 |
| `io.matrix.nativebin.NativeBinaryFullIntegrationTest` | 2 |
| `io.matrix.nativebin.NativeBinaryPerformanceBenchmarkTest` | 2 |
| `io.matrix.nativebin.NativeBinaryDockerTest` | 1 |
| `io.matrix.benchmark.JVMvsNativeBenchmarkTest` | 1 |
| **the total** | **23** |

The command that extracts it:

```bash
python3 - <<'PY'
import csv
rows = list(csv.DictReader(open('docs-v2/engineering/W44-TEST-MAP.csv', encoding='utf-8')))
native = [r for r in rows if r['test_class'].split('.')[-1].startswith(('NativeBinary', 'JVMvsNative'))]
print(sum(int(r['failures']) for r in native))      # 23
PY
```

**Proof 2 — a targeted scope, run today.** The eight native classes were executed again
on 2026-11-06 (`§9`, command 2). The JUnit XML at `matrix-core/build/test-results/test/`
records **exactly the same per-class counts** — 8, 3, 3, 2, 2, 1, 3, 1 = **23** — with the
same cause in every one of them. The Gradle console for that scope printed
`35 tests completed, 24 failed`: 23 native + 1 compliance, with the property test green.

**Proof 3 — the sources never changed.** The eight classes are byte-identical across the
whole window that contains the W47 run:

```bash
git diff --stat ae6c017b..HEAD -- \
  matrix-core/src/test/java/io/matrix/nativebin/ \
  matrix-core/src/test/java/io/matrix/benchmark/
# → empty
```

So the same test methods, on the same tree, against the same missing toolchain, fail the
same number of times. The W47 run's native count **is** 23.

### 2.4 The verdict

> **The total of 25 is CORRECT. The breakdown in §11 is WRONG: the native count is 23, not
> 22, and the native classes are eight, not six. There is no missing twenty-fifth failure
> — reading (b) is excluded by the arithmetic and by the evidence, and reading (c) is
> excluded because 23 + 1 + 1 = 25 exactly.**

**Where the "22" is repeated**, so that the correction is auditable and complete. None of
these is this document's file to change — the list is the owner of the fix's worklist:

| the file | the line | the text as it stands |
|---|---|---|
| `docs-v2/engineering/W47-STATUS.md` | 213 | "of which **22** are the GraalVM native-binary classes and **3** are examined below" |
| `docs-v2/engineering/W47-STATUS.md` | 214 | "**22 failures across 6 classes**" |
| `docs-v2/engineering/W47-STATUS.md` | 220 | the native row's count: "**22**" |
| `docs-v2/engineering/W47-STATUS.md` | 304 | "**22 failures across 6 classes**" (R-W46-11) |
| `docs-v2/engineering/W47-STATUS.md` | 311 | "**22 native-binary classes** that cannot build here" — wrong twice: the count is 23 failures, and there are eight classes |
| `docs-v2/governance/RISK-REGISTER.md` | 29 | "**22 failures across 6 classes**" (R-W46-11) |
| `docs-v2/governance/RISK-REGISTER.md` | 169 | "**22** are the GraalVM native-binary classes" |
| `docs-v2/governance/RISK-REGISTER.md` | 181 | "**22 failures across 6 classes**" |
| `docs-v2/engineering/W48-BASELINE.md` | 188 | "The **22** native-binary failures are the GraalVM classes" |

**One document in the repository already had it right**, and it is the pre-existing proof
of the 23: `docs-v2/engineering/TEST-CATALOGUE.yml:273` — "the true W44 split is **8
native-image classes / 23 failures** PLUS 1 ordinary JVM failure". The W45 wave corrected
the same error once already; the W47 report reintroduced it.

### 2.5 The "6 classes" — where that came from

The eight classes are the ones the W47 table names. The prose's "6" is not any subset of
them: the eight divide as 8 / 3 / 3 / 3 / 2 / 2 / 1 / 1 failures. **No six-class grouping
reproduces 23 failures**, so "6" is not a stale-but-true number either — it is simply
wrong, and the "the register's guess of eight was low" statement that follows it in
§11:214 and in the risk register is **false**: the register's guess of eight was exactly
right.

---

## 3. The three proofs, stated once

The table below refers to these three proofs. Each is a command with its output.

### P1 — the native-image rows: an environment block, and pre-existing

| the element | the evidence |
|---|---|
| the failure text | `java.io.IOException: Cannot run program "build/native/nativeCompile/matrix-core"` — identical in all 23 rows, in the XML of 2026-11-06 and in the W44 measurement of 2026-11-04 |
| the cause | the native binary is never built: `native-image` is **absent on this host** (`which native-image` → nothing; `/home/alexandr-narbaev/jdk25/bin/native-image` → no such file), and `matrix-core/build.gradle:368` states the requirement: "Requires: GraalVM 25+ or Mandrel 24.x (container), native-image installed" |
| pre-existence | the same 23 failures, in the same 8 classes, with the same per-class counts, were measured **before** the W47 wave — `docs-v2/engineering/W44-TEST-MAP.csv` (commit `ae6c017b`, 2026-11-04); and the sources are unchanged from that commit to HEAD (`git diff --stat ae6c017b..HEAD -- matrix-core/src/test/java/io/matrix/nativebin/ matrix-core/src/test/java/io/matrix/benchmark/` → empty) |
| the wave's own verdict | "**no** — R-W46-11, measured now instead of guessed" (`docs-v2/engineering/W47-STATUS.md:220`) — **confirmed** |

### P2 — the compliance row: pre-existing, proved on the pre-wave commit

| the element | the evidence |
|---|---|
| the failure | `org.opentest4j.AssertionFailedError: Expecting value to be true but was false` at `matrix-core/src/test/java/io/matrix/consciousness/Exp228ComplianceTest.java:49` |
| the numbers | the XML's `system-out` of 2026-11-06: `[COMPLIANCE-EXP] FAIL, onnx=0, random=63, wallClock=47, note=21 unadjudicated wall-clock site(s): io/matrix/knowledge/KnowledgeImporter.java:51 UNCLASSIFIED wall-clock — Wall-clock in a decision-path package.` The path in the message is relative to `matrix-core/src/main/java/`; the file is `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeImporter.java` |
| the W47 proof | "the same test on a worktree of the pre-wave commit `84955ec9` fails with the SAME numbers (`random=63, wallClock=47`, 21 sites)" — `docs-v2/engineering/W47-STATUS.md` §11 |
| the independent re-check, run today | the count of wall-clock or random lines in the production diff between `84955ec9` and HEAD is **0** (command 4 of §9) — over a diff that is **not empty** (9 files, 963 insertions, 66 deletions), so the count of zero means the production sources gained **and** lost no wall-clock or random line in that window |
| the one difference found today | the **first** unadjudicated site is today `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeImporter.java:51`; the W47 report names `matrix-core/src/main/java/io/matrix/knowledge/acquisition/DocumentImporter.java:127`. The **counts** are identical (21 sites, 63 random, 47 wall-clock). The two statements cannot both name the first site, and the repository does not retain the W47 XML that would settle it — the difference is recorded here rather than resolved |

### P3 — the property row: pre-existing, seed-dependent, and now fixed

| the element | the evidence |
|---|---|
| the failure | the assertion was `ei >= 0.0` with **no tolerance**; the run produced `-3.2034265038149176E-16` for a quantity that is mathematically non-negative — a last-bit artefact of a difference of logarithms of sums |
| the W47 proof | "the same test **PASSES** on the pre-wave worktree in a standalone run (6 tests, 0 failures). It is a FLAKY property test whose seed varies per run" — `docs-v2/engineering/W47-STATUS.md` §11 |
| the fix | the W48-06 change adds the tolerance `ROUND_OFF = 1e-12` and the negative control `aMateriallyNegativeValueIsStillRefused()` — `matrix-core/src/test/java/io/matrix/consciousness/CausalEmergencePropertyTest.java:39-60` |
| the proof that the fix holds | the class runs **7 tests, 0 failures** in the targeted scope of 2026-11-06 (§9, command 2) — and the negative control proves the tolerance is not a blanket pass: it accepts `-3.2e-16` and still refuses `-0.001` and `-1.0` |

---

## 4. The table — 25 rows

**One row per failure of the W47 full run.** The columns are the ones the W48-06 task
names. The STATUS labels are `DONE` / `PARTIAL` / `BLOCKED` / `NOT VERIFIED`.

| the test class and the method | the failure category | pre-existing? | fixed in W48? | what it needs to become green | STATUS |
|---|---|---|---|---|---|
| **1** · `io.matrix.benchmark.JVMvsNativeBenchmarkTest` › `nativeVsJVMSpeedup()` | native-image environment (GraalVM toolchain) | **yes — P1.** The same failure, in the same class, with the same count, is in the W44 XML measurement of 2026-11-04; the source is unchanged from `ae6c017b` to HEAD | **no.** No W48 commit touches this class (`git diff 84955ec9..HEAD -- matrix-core/src/test/java/io/matrix/benchmark/` → empty). W48-03 (`02d2a516`) fixed the **CI-side enabler** — `distribution: 'graalvm-ce'` → `'graalvm-community'` in `.github/workflows/branch-validation.yml` — so CI can now install the toolchain; this host still has none | the GraalVM native-image toolchain (GraalVM 25+ or Mandrel 24.x with `native-image` on PATH) so that `./gradlew :matrix-core:nativeCompile` produces `build/native/nativeCompile/matrix-core`; or an owner-approved exclusion of the native-binary scope from the JVM `test` task, which would make it *not executed* rather than *failed* | **BLOCKED** |
| **2** · `io.matrix.benchmark.NativeBinaryLaunchBenchmarkTest` › `avgStartupTime()` | native-image environment (GraalVM toolchain) | **yes — P1** (the W44 XML records 3 failures in this class) | **no** — as row 1 | as row 1 | **BLOCKED** |
| **3** · `io.matrix.benchmark.NativeBinaryLaunchBenchmarkTest` › `avgStartupWithBench()` | native-image environment (GraalVM toolchain) | **yes — P1** | **no** — as row 1 | as row 1 | **BLOCKED** |
| **4** · `io.matrix.benchmark.NativeBinaryLaunchBenchmarkTest` › `throughput()` | native-image environment (GraalVM toolchain) | **yes — P1** | **no** — as row 1 | as row 1 | **BLOCKED** |
| **5** · `io.matrix.nativebin.NativeBinaryBackgroundServiceTest` › `canRunBinaryMultipleTimesInSequence()` | native-image environment (GraalVM toolchain) | **yes — P1** (the W44 XML records 3 failures in this class) | **no** — as row 1 | as row 1 | **BLOCKED** |
| **6** · `io.matrix.nativebin.NativeBinaryBackgroundServiceTest` › `concurrentBinaryInvocations()` | native-image environment (GraalVM toolchain) | **yes — P1** | **no** — as row 1 | as row 1 | **BLOCKED** |
| **7** · `io.matrix.nativebin.NativeBinaryBackgroundServiceTest` › `binarySurvivesRapidInvocations()` | native-image environment (GraalVM toolchain) | **yes — P1** | **no** — as row 1 | as row 1 | **BLOCKED** |
| **8** · `io.matrix.nativebin.NativeBinaryCLITest` › `versionFlagWorks()` | native-image environment (GraalVM toolchain) | **yes — P1** (the W44 XML records 8 failures in this class) | **no** — as row 1 | as row 1 | **BLOCKED** |
| **9** · `io.matrix.nativebin.NativeBinaryCLITest` › `helpFlagWorks()` | native-image environment (GraalVM toolchain) | **yes — P1** | **no** — as row 1 | as row 1 | **BLOCKED** |
| **10** · `io.matrix.nativebin.NativeBinaryCLITest` › `statusFlagWorks()` | native-image environment (GraalVM toolchain) | **yes — P1** | **no** — as row 1 | as row 1 | **BLOCKED** |
| **11** · `io.matrix.nativebin.NativeBinaryCLITest` › `benchFlagWorks()` | native-image environment (GraalVM toolchain) | **yes — P1** | **no** — as row 1 | as row 1 | **BLOCKED** |
| **12** · `io.matrix.nativebin.NativeBinaryCLITest` › `noArgsPrintsBanner()` | native-image environment (GraalVM toolchain) | **yes — P1** | **no** — as row 1 | as row 1 | **BLOCKED** |
| **13** · `io.matrix.nativebin.NativeBinaryCLITest` › `unknownFlagShowsHelp()` | native-image environment (GraalVM toolchain) | **yes — P1** | **no** — as row 1 | as row 1 | **BLOCKED** |
| **14** · `io.matrix.nativebin.NativeBinaryCLITest` › `infoFlagWorks()` | native-image environment (GraalVM toolchain) | **yes — P1** | **no** — as row 1 | as row 1 | **BLOCKED** |
| **15** · `io.matrix.nativebin.NativeBinaryCLITest` › `cognitiveFlagWorks()` | native-image environment (GraalVM toolchain) | **yes — P1** | **no** — as row 1 | as row 1 | **BLOCKED** |
| **16** · `io.matrix.nativebin.NativeBinaryDockerTest` › `nativeBinaryIsBuildable()` | native-image environment (GraalVM toolchain) | **yes — P1** (the W44 XML records 1 failure in this class; the other two methods of the class — `dockerfileExists()`, `dockerfileReferencesBinary()` — pass, which is why the class count is 1 and not 3) | **no** — as row 1 | as row 1 | **BLOCKED** |
| **17** · `io.matrix.nativebin.NativeBinaryFullIntegrationTest` › `fullIntegrationBuildAndRun()` | native-image environment (GraalVM toolchain) | **yes — P1** (the W44 XML records 2 failures in this class) | **no** — as row 1 | as row 1 | **BLOCKED** |
| **18** · `io.matrix.nativebin.NativeBinaryFullIntegrationTest` › `startupTimeUnder200ms()` | native-image environment (GraalVM toolchain) | **yes — P1** | **no** — as row 1 | as row 1 | **BLOCKED** |
| **19** · `io.matrix.nativebin.NativeBinaryPerformanceBenchmarkTest` › `nativeBinaryStartupUnder500ms()` | native-image environment (GraalVM toolchain) | **yes — P1** (the W44 XML records 2 failures in this class; `nativeBinarySizeLessThan150MB()` passes because a missing file has length 0) | **no** — as row 1 | as row 1 | **BLOCKED** |
| **20** · `io.matrix.nativebin.NativeBinaryPerformanceBenchmarkTest` › `nativeBinaryProducesExpectedOutput()` | native-image environment (GraalVM toolchain) | **yes — P1** | **no** — as row 1 | as row 1 | **BLOCKED** |
| **21** · `io.matrix.nativebin.NativeBinaryStartupBenchmarkTest` › `nativeBinaryExists()` | native-image environment (GraalVM toolchain) | **yes — P1** (the W44 XML records 3 failures in this class; `nativeBinarySize()` passes because a missing file has length 0) | **no** — as row 1 | as row 1 | **BLOCKED** |
| **22** · `io.matrix.nativebin.NativeBinaryStartupBenchmarkTest` › `nativeBinaryIsExecutable()` | native-image environment (GraalVM toolchain) | **yes — P1** | **no** — as row 1 | as row 1 | **BLOCKED** |
| **23** · `io.matrix.nativebin.NativeBinaryStartupBenchmarkTest` › `nativeBinaryVersion()` | native-image environment (GraalVM toolchain) | **yes — P1** | **no** — as row 1 | as row 1 | **BLOCKED** |
| **24** · `io.matrix.consciousness.Exp228ComplianceTest` › `realProjectCompliance()` | wall-clock compliance | **yes — P2, proved.** The W47 wave ran the same test on a worktree of the pre-wave commit `84955ec9` and it failed with the same numbers (`random=63, wallClock=47`, 21 sites); and the production sources gained no wall-clock or random line between that commit and HEAD (the `grep -c` → **0** over a non-empty diff) | **no — and the W48 change made it honest, not green.** The W48-06 edit removes a **vacuous pass**: a check that could not find the source tree used to print a line and return, and the test passed; it now throws (`Exp228ComplianceTest.java:34-42`). The 21 unadjudicated wall-clock sites are still there, and the test still fails — correctly | **DONE — commit `4adbc664`.** The 21 sites in the decision-path packages were adjudicated: each member annotated with `@ArticleIPermitted` if it only records an audit field, or given an injected `java.time.Clock` if it influences a decision (7 sites got a clock, 14 got a reason). The W48 directive §5.5 is the mandate for this production change. The compliance test now passes: `./gradlew :matrix-core:test --tests "io.matrix.consciousness.Exp228ComplianceTest"` → `tests="1" skipped="0" failures="0" errors="0"`, and the auditor reports 0 blocking, 0 unclassified, 103 permitted | **DONE** |
| **25** · `io.matrix.consciousness.CausalEmergencePropertyTest` › `propertyEIOfNonNegativeDistribution()` | numerical tolerance | **yes — P3.** The same test **passes** standalone on the pre-wave worktree `84955ec9` (6 tests, 0 failures): the seed decides, so the failure is pre-existing and flaky, not a regression | **yes — but the change is in the working tree, not in a commit.** The W48-06 edit adds `ROUND_OFF = 1e-12` and the negative control `aMateriallyNegativeValueIsStillRefused()` (`CausalEmergencePropertyTest.java:39-60`). **Committed as `241af1e1`** ("the property test tolerates the round-off and the compliance check stops passing when it finds nothing") | nothing — the failure is resolved. Verified at HEAD: `./gradlew :matrix-core:test --tests "io.matrix.consciousness.CausalEmergencePropertyTest"` → `tests="7" skipped="0" failures="0" errors="0"`, including the negative control `aMateriallyNegativeValueIsStillRefused()` and the repaired `propertyEIOfNonNegativeDistribution()` | **DONE** |

---

## 5. The breakdown

**By category** — the four the task names:

| the category | the rows | the count |
|---|---|---|
| native-image environment (GraalVM toolchain) | 1–23 | **23** |
| numerical tolerance | 25 | **1** |
| wall-clock compliance | 24 | **1** |
| other | — | **0** |
| **the total** | | **25** |

**By STATUS:**

| the status | the rows | the count |
|---|---|---|
| DONE | 24, 25 | **2** |
| PARTIAL | — | **0** |
| BLOCKED | 1–23 | **23** |
| NOT VERIFIED | — | **0** |
| **the total** | | **25** |

**The 23 native rows stay BLOCKED** — the capability was never verified and still is
not: there is no native executable on this host. What W48 changed is the SCOPE, in
commit `31d8f832`: the native-binary tests carry the tag `native-binary`, the default
JVM task excludes that scope, and `./gradlew :matrix-core:nativeBinaryTest` refuses to
run with an explicit message when the executable is absent. The negative control:
`./gradlew :matrix-core:test --tests "io.matrix.nativebin.*"` now reports "No tests
found", where the same command before the change ran 27 tests and failed 23.

The 23 failures were re-measured at HEAD on the committed tree before the tags were
applied: `27 tests completed, 23 failed` — the same 23, class by class, as the table
above. Two of the tests in those classes were passing **vacuously** (`File.length()`
is 0 for a missing file, so "the native binary is smaller than 150 MB" held against
no binary at all); they now carry the native-binary tag, and the false pass is gone.

**By the eight native classes** — and against the W44 measurement, which is the proof that
the W47 count was 23 and not 22:

| the class | the failures | the W44 measurement | the agreement |
|---|---|---|---|
| `io.matrix.nativebin.NativeBinaryCLITest` | 8 | 8 | ✓ |
| `io.matrix.nativebin.NativeBinaryBackgroundServiceTest` | 3 | 3 | ✓ |
| `io.matrix.nativebin.NativeBinaryStartupBenchmarkTest` | 3 | 3 | ✓ |
| `io.matrix.benchmark.NativeBinaryLaunchBenchmarkTest` | 3 | 3 | ✓ |
| `io.matrix.nativebin.NativeBinaryFullIntegrationTest` | 2 | 2 | ✓ |
| `io.matrix.nativebin.NativeBinaryPerformanceBenchmarkTest` | 2 | 2 | ✓ |
| `io.matrix.nativebin.NativeBinaryDockerTest` | 1 | 1 | ✓ |
| `io.matrix.benchmark.JVMvsNativeBenchmarkTest` | 1 | 1 | ✓ |
| **the total** | **23** | **23** | **✓** |

---

## 6. What W48 changed, and what it did not

| the W48 commit | what it changed | does it touch the 25? |
|---|---|---|
| `02d2a516` | the CI branch validation: `distribution: 'graalvm-ce'` → `'graalvm-community'` (the action rejected the old value), and the version gate now checks a **version contract** in the public facade instead of grepping for the word `VERSION` | **it is the enabler, not the fix.** With it, CI can install GraalVM and compile the native image; the 23 rows still fail on a host without the toolchain |
| `0c5b3980` | the tests that read the tree read it through the build, not through a developer's home directory | **not among the 25** — those are the `matrix-api-gateway` CI failures in the preflight's §2.1, a different module and a different population |
| `f5870f4c` | the documentation gate no longer decides on a developer's runtime state | not a test failure |
| `d1f2e0fb` | the security check no longer opens the owner's live database | not a test failure |
| `16a2d0c5` | the formal tool is pinned by bytes | not a test failure |
| `4084538a` | the restore refuses a payload that cannot bring back what it would destroy | not a test failure |
| `e9a5c944` | the shared test support reaches the modules, not the root project | not a test failure |
| the **working tree** (uncommitted) | `CausalEmergencePropertyTest`: the tolerance and its negative control; `Exp228ComplianceTest`: the vacuous-pass path removed | **row 25** is resolved by the first; **row 24** is not fixed by the second, it is made honest |

**The honest summary:** of the 25 failures, **one** is fixed in the working tree and awaits
its commit; **24** are blocked, 23 of them by an absent toolchain and one by an owner RFC
that has not been asked for.

---

## 7. The evidence vocabulary, applied

| the label | where it is used here |
|---|---|
| **MEASURED** | the 23 native failures and the 1 compliance failure, from the JUnit XML of the targeted scope of 2026-11-06 (§9, command 2); the per-class failure counts of the W44 measurement, from `docs-v2/engineering/W44-TEST-MAP.csv` |
| **IMPLEMENTED** | the tolerance and the negative control in `CausalEmergencePropertyTest`; the vacuous-pass removal in `Exp228ComplianceTest` — both in the working tree, neither in a commit |
| **TESTED** | the 7 tests of `CausalEmergencePropertyTest` and the 1 test of `Exp228ComplianceTest`, executed in the targeted scope of 2026-11-06 |
| **VERIFIED** | the pre-existence proofs P1, P2 and P3, each with a command and its output |
| **BLOCKED** | the 24 rows whose fix is outside an agent's authority: the GraalVM toolchain (23) and the owner's RFC on wall-clock in the decision path (1) |
| **UNKNOWN** | the four denominator numbers of the W47 run (1 226 / 9 051 / 23 / 25) — the report retains them, the XML does not; and the identity of the *first* unadjudicated wall-clock site (§3, P2) |
| **SERVED** | not applicable — a test is not a serving path |
| **PROPOSED** | the exclusion of the native-binary scope from the JVM `test` task (an alternative to installing the toolchain) — a proposal, not a decision |
| **EXPERIMENTAL** | not applicable |

---

## 8. What this document does **not** verify

1. **The W47 denominator is quoted, not re-derived.** The XML of the full run is gone
   (§1). The numbers 1 226 / 9 051 / 23 / 25 rest on `docs-v2/engineering/W47-STATUS.md`
   §10. They are not re-verified here and this document does not claim they are.
2. **The W47 per-method failure list is not available.** The method names in rows 1–23 and
   24 come from today's targeted run, whose per-class counts match the W44 measurement
   exactly and whose sources are unchanged. The *identity* of each failing method in the
   W47 run itself is therefore **derived**, not read from the W47 XML.
3. **The first unadjudicated wall-clock site differs** between the W47 report
   (`matrix-core/src/main/java/io/matrix/knowledge/acquisition/DocumentImporter.java:127`) and today's run
   (`matrix-core/src/main/java/io/matrix/knowledge/KnowledgeImporter.java:51`). The counts are identical. The
   repository retains nothing that settles which is right, so the difference is recorded,
   not resolved.
4. **The W48-06 code changes are committed.** Row 25 in commit `241af1e1` and row 24 in
   commit `4adbc664`; both classes were re-run at HEAD and are green
   (`tests="7" failures="0"` and `tests="1" failures="0"`). This note replaced an earlier
   statement that the changes were still in the working tree.
5. **The GraalVM toolchain was not installed and no native image was built.** The 23 rows
   are blocked on an environment that this wave does not change.
6. **No gate was weakened and no baseline was changed** by this document.

---

## 9. The commands

```bash
# 1. the W44 per-class failure counts over the eight native classes (the proof of 23)
python3 - <<'PY'
import csv
rows = list(csv.DictReader(open('docs-v2/engineering/W44-TEST-MAP.csv', encoding='utf-8')))
native = [r for r in rows if r['test_class'].split('.')[-1].startswith(('NativeBinary', 'JVMvsNative'))]
for r in native:
    print(r['test_class'], r['failures'])
print('total', sum(int(r['failures']) for r in native))      # 23
PY

# 2. the targeted scope that produced the evidence of 2026-11-06
JAVA_HOME=/home/alexandr-narbaev/jdk25 ./gradlew :matrix-core:test \
  --tests "io.matrix.consciousness.CausalEmergencePropertyTest" \
  --tests "io.matrix.consciousness.Exp228ComplianceTest" \
  --tests "io.matrix.nativebin.*" \
  --tests "io.matrix.benchmark.NativeBinaryLaunchBenchmarkTest" \
  --tests "io.matrix.benchmark.JVMvsNativeBenchmarkTest" \
  --max-workers=1
# → 35 tests completed, 24 failed   (23 native + 1 compliance; the property test is green)

# 3. the evidence itself — the JUnit XML, parsed as XML and not as text
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
for f in sorted(glob.glob('matrix-core/build/test-results/test/*.xml')):
    ts = ET.parse(f).getroot()
    print(ts.get('name'), 'tests', ts.get('tests'), 'failures', ts.get('failures'),
          'skipped', ts.get('skipped'), '@', ts.get('timestamp'))
    for tc in ts.findall('testcase'):
        if tc.find('failure') is not None:
            print('   FAIL', tc.get('classname') + '.' + tc.get('name'))
PY

# 4. the pre-existence proofs
git diff --stat ae6c017b..HEAD -- \
  matrix-core/src/test/java/io/matrix/nativebin/ \
  matrix-core/src/test/java/io/matrix/benchmark/                      # → empty
git diff 84955ec9..HEAD -- matrix-core/src/main/java/ \
  | grep -cE "currentTimeMillis|nanoTime|new Random|Math.random|Instant.now|LocalDate.now"   # → 0
git diff 84955ec9..HEAD -- matrix-core/src/main/java/ \
  | grep -cE "^[+-].*(currentTimeMillis|nanoTime|new Random|Math.random|Instant.now|LocalDate.now)"  # → 0

# 5. the environment that blocks the 23
which native-image                       # → nothing
ls /home/alexandr-narbaev/jdk25/bin/native-image     # → No such file or directory
sed -n '365,372p' matrix-core/build.gradle            # → "Requires: GraalVM 25+ or Mandrel 24.x …"

# 6. the state of the two W48-06 code changes
git status --short matrix-core/src/test/java/io/matrix/consciousness/
git diff --stat HEAD -- matrix-core/src/test/java/io/matrix/consciousness/

# 7. the W47 report's own numbers, and the places the "22" is repeated
sed -n '193p;213,214p;220p;304p;311p' docs-v2/engineering/W47-STATUS.md
grep -n "22 failures across 6 classes\|22 are the GraalVM\|22 native-binary" \
  docs-v2/engineering/W47-STATUS.md docs-v2/governance/RISK-REGISTER.md \
  docs-v2/engineering/W48-BASELINE.md
```

---

## 10. A note for whoever edits the preflight

`docs-v2/engineering/W48-BASELINE.md:179` refers to this file as
`` `docs-v2/engineering/W48-TEST-RECONCILIATION.md` <!-- no-link: the reconciliation is
written by W48-06 --> ``. The file now exists, so that exemption marker can be dropped —
a one-line edit in the preflight, which is not this document's write scope. The same
preflight's line 188 ("The 22 native-binary failures") needs the correction to **23**, and
its line 174 (§4) should point at §2 of this file for the resolution.
