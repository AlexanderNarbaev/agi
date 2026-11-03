# W45 — Test-evidence reconciliation (RECON-W45.0 §1.1)

**Audit:** RECON-W45.0 · branch `develop` · HEAD `81f08478` · 2026-11-03
**Kind:** research only. No production source was changed, no branch was switched, nothing
was committed or pushed. No W44 document was modified. The matrix-core suite was **not**
run.
**Retained evidence used:** `W44-TEST-MAP.csv` · `W44-CLASS-INVENTORY.csv` ·
`W44-FILE-CENSUS.json` · `RECON-W44-ARCHAEOLOGY.md` · `W44-HYPOTHESIS-AND-FAILURE-MAP.md` ·
`W44-BENCHMARK.md` · `RECON-W44-STATUS.md` · `RECON-W44-BACKLOG.md` ·
`scripts/w44-audit-docmap.py` · `matrix-core/build.gradle`

---

## 0. What this document does, and what it refuses to do

The W44 wave left two aggregate test counts in circulation that do not agree, and a third
number that is not a test count at all. The directive requires the discrepancy to be
resolved **from retained evidence, not guessed**. This document reconstructs the exact
accounting, names the command or CSV column behind every number, and states plainly where
the retained evidence stops.

**The three circulating numbers:**

| number | where it comes from | what it measures |
|---|---|---|
| **1,320 classes with XML · 9,816 executions · 24 failures · 26 skipped** | `W44-TEST-MAP.csv`, summed over `executed == yes` | the W44 audit's **CSV census** of JUnit XML, **all 14 modules** |
| **8,817 executions · 24 failures · 18 skipped** | `RECON-W44-ARCHAEOLOGY.md` §1.1bis (Disclosure) and `W44-HYPOTHESIS-AND-FAILURE-MAP.md` §4 | **one Gradle console summary of one `matrix-core` run** |
| **1,179 source files containing tests** | `W44-BENCHMARK.md` §1, `RECON-W44-STATUS.md` §1 | **matrix-core `src/test/java` file count**, printed by `generateBuildInfo` |

They are **not three measurements of the same quantity**. §8 reconciles them.

**The `tests_run` column is the JUnit XML `<testsuite tests=…>` attribute.** That is fixed
by the generator, `scripts/w44-audit-docmap.py:129-134`:

```python
executed[f"{module}:{name}"] = {
    "tests": root.get("tests", "0"), "failures": root.get("failures", "0"),
    "errors": root.get("errors", "0"), "skipped": root.get("skipped", "0"), ...
}
```

**A note on the XML directory.** The directive states the temporary XML directory was
removed. At the moment of reconciliation (2026-11-03 08:07–08:16) it was still present and
**byte-consistent with the CSV**: all 1,320 `evidence_xml` paths existed and every
`tests`/`failures`/`errors`/`skipped` attribute matched the CSV row exactly (0 mismatches).
By 08:16 a concurrent Gradle run had rewritten `matrix-core/build/test-results/test`
(1,201 XML files → 1). **The XML corpus is therefore transient and is NOT retained
evidence.** Every number in §1–§9 is taken from a retained artefact. Where the XML was used
as corroboration only, it is marked **[transient]** and is confined to §6 (the failure
messages), §8.3 and §8.4.

---

## 1. The seven numbers

One command reproduces all of them.

```bash
cd /home/alexandr-narbaev/Projects/agi && python3 - <<'PY'
import csv, collections
tm=list(csv.DictReader(open('docs-v2/engineering/W44-TEST-MAP.csv',newline='',encoding='utf-8')))
inv=list(csv.DictReader(open('docs-v2/engineering/W44-CLASS-INVENTORY.csv',newline='',encoding='utf-8')))
I=lambda r,k:int(r[k] or 0)
ex=[r for r in tm if r['executed']=='yes']; nx=[r for r in tm if r['executed']!='yes']
print("classes discovered (test map rows) :",len(tm))
print("inventory test rows (test+jmh)      :",sum(1 for r in inv if r['source_set'] in ('test','jmh')))
print("classes WITH executed xml           :",len(ex))
print("classes WITHOUT evidence            :",len(nx))
print("tests started (sum tests_run)       :",sum(I(r,'tests_run') for r in ex))
print("failures / errors / skipped         :",sum(I(r,'failures') for r in ex),"/",
      sum(I(r,'errors') for r in ex),"/",sum(I(r,'skipped') for r in ex))
print("successful                          :",sum(I(r,'tests_run')-I(r,'failures')-I(r,'errors')-I(r,'skipped') for r in ex))
print("failing classes                     :",sum(1 for r in ex if I(r,'failures')>0))
print("matrix-core src/test/java files     :",len({r['file'] for r in inv if r['module']=='matrix-core' and r['source_set']=='test'}))
PY
```

| # | quantity | value | source column / document |
|---|---|---|---|
| 1 | **test classes discovered** | **1,386 top-level** (1,366 in `src/test/java` + 20 in `src/jmh/java`), in **1,386 files** | `W44-TEST-MAP.csv` rows; inventory `source_set` + `nested` |
| 2 | **test classes with executed XML** | **1,320** | `W44-TEST-MAP.csv` `executed == yes` |
| 2 | **test classes without evidence** | **66** | `W44-TEST-MAP.csv` `executed == not-executed` |
| 3 | **tests started** | **9,816** | sum of `tests_run` over the 1,320 rows |
| 3 | **successful tests** | **9,766** | `tests_run − failures − errors − skipped` = 9,816 − 24 − 0 − 26 |
| 3 | **failures** | **24** (in **9** classes) | sum of `failures` |
| 3 | **skipped** | **26** (in **10** classes) | sum of `skipped` |
| 4 | **classes requiring GraalVM native-image support** | **9** (8 failing + 1 `@Disabled`) — but only **8 of the 9 failing classes** are native-image | §5 |
| 5 | **native-image-infra failures / ordinary JVM regressions** | **23 / 1** | §6 |
| 6 | **executions that exercised ordinary JVM behaviour** | **9,767** evaluated an assertion (9,766 green + 1 red); **9,793** ran without a native binary (incl. 26 skipped) | §7 |
| 7 | **1,320 / 9,816 · 8,817 · 1,179** | three different quantities over two different scopes | §8 |

---

## 2. Test classes discovered — the two denominators differ, and why

```bash
python3 - <<'PY'
import csv
inv=list(csv.DictReader(open('docs-v2/engineering/W44-CLASS-INVENTORY.csv',newline='',encoding='utf-8')))
tm=list(csv.DictReader(open('docs-v2/engineering/W44-TEST-MAP.csv',newline='',encoding='utf-8')))
print("test map rows                :",len(tm))
print("inventory source_set=test    :",sum(1 for r in inv if r['source_set']=='test'))
print("inventory source_set=jmh     :",sum(1 for r in inv if r['source_set']=='jmh'))
print("inventory nested test/jmh    :",sum(1 for r in inv if r['source_set'] in ('test','jmh') and r['nested']=='yes'))
PY
```

| denominator | count | one row is |
|---|---|---|
| `W44-TEST-MAP.csv` rows | **1,386** | one **source file** under `src/test/java` or `src/jmh/java` |
| `W44-CLASS-INVENTORY.csv` rows with `source_set in (test, jmh)` | **1,470** | one **type**: 1,386 top-level + 84 nested |
| inventory rows with `source_set == test` only | **1,448** | 1,366 top-level + 82 nested |
| inventory rows with `source_set == jmh` only | **22** | 20 top-level + 2 nested |
| distinct files in the inventory, test + jmh | **1,386** | — |

**They differ by exactly 84, and the 84 are the nested types.** Both artefacts are
**file-keyed**: the inventory records the first top-level type of each file plus its
nested types; the test map records one row per file and nothing else. So:

* **top-level test classes = 1,386** — the two artefacts agree exactly;
* **test types including nested = 1,470** — the inventory is the wider denominator;
* **files containing tests = 1,386** — the inventory's `file` column, deduplicated.

**Both artefacts undercount the classes that are actually declared**, because a file that
declares more than one top-level type contributes only its first type to either artefact.
This is measurable on disk (11 files declare >1 top-level type):

```bash
python3 - <<'PY'
import re, glob
T=re.compile(r'^(?:public\s+|final\s+|abstract\s+|sealed\s+|non-sealed\s+|strictfp\s+)*(?:class|interface|enum|record)\s+(\w+)')
files=[p for p in glob.glob('**/src/test/java/**/*.java',recursive=True)+glob.glob('**/src/jmh/java/**/*.java',recursive=True)
       if not any(x in p.split('/') for x in ('build','node_modules','.venv','.git'))]
n=0; multi=[]
for f in sorted(files):
    tops=[T.match(l).group(1) for l in open(f,encoding='utf-8',errors='replace') if T.match(l)]
    n+=len(tops)
    if len(tops)>1: multi.append((f,tops))
print("files:",len(files),"declared top-level types:",n,"files with >1:",len(multi))
for f,t in multi: print("  ",f,"->",t)
PY
```

At census time this gave **1,386 files / 1,406 declared top-level types** — i.e. **20
top-level test classes exist that neither CSV records** (e.g.
`matrix-core/src/test/java/io/matrix/cluster/ClusterTypesTest.java` declares
`NeuronIdTest`, `NeuronInstanceTest`, `SignalTest`, `ClusterConfigTest`, but only the first
one gets a row). *(The live tree now reads 1,387 files / 1,407 types: a concurrent agent
added `matrix-core/src/test/java/io/matrix/cognitive/CognitiveContradictionDurabilityTest.java`
at 2026-11-03 08:15:16, i.e. **after** the W44 census — see §8.2.)*

---

## 3. Test classes with executed XML, and without

```bash
python3 -c "
import csv,collections
tm=list(csv.DictReader(open('docs-v2/engineering/W44-TEST-MAP.csv',newline='',encoding='utf-8')))
print(collections.Counter((r['kind'],r['executed']) for r in tm))"
```

| `kind` | `executed` | classes |
|---|---|---|
| test | yes | 1,278 |
| test | not-executed | 46 |
| benchmark | yes | 42 |
| benchmark | not-executed | 20 |
| **total** | | **1,386** |

**1,320 with executed evidence · 66 without.** Every one of the 1,320 has a non-empty
`evidence_xml` path pointing into `<module>/build/test-results/test/`.

**What the 66 without evidence are** (classified by reading the source file each row
points at; reproducible with the loop in §2 and the row's `file` column):

| # | class of cause | count | explanation |
|---|---|---|---|
| 1 | `src/jmh/java` benchmarks | **20** | the JMH source set is not executed by the `test` task; `matrix-core/build.gradle` runs it through a separate `jmh` configuration |
| 2 | fixture / helper files with no test annotation | **9** | e.g. `io.matrix.research.BitNetRealModelFixture`, `io.matrix.ethics.guardrail.TestConfig`, `io.matrix.rag.RecallAtKEvaluator` — no `@Test` in the file |
| 3 | file whose first declared type is not the row's class | **1** | `io.matrix.brain.runtime.actuation.CommandBusBddScenariosTest` (the file's first type is `is`) |
| 4 | real test class whose XML the generator could not key-match | **36** | see §8.3 — the executions exist in the XML corpus but are dropped by the `module:cls` key |

Category 4 is a **methodological defect of the W44 audit**, not an absence of tests: those
36 classes are in the source tree with `@Test` methods and did produce XML.

---

## 4. The executed accounting, per module

```bash
python3 - <<'PY'
import csv, collections
tm=list(csv.DictReader(open('docs-v2/engineering/W44-TEST-MAP.csv',newline='',encoding='utf-8')))
I=lambda r,k:int(r[k] or 0)
ex=[r for r in tm if r['executed']=='yes']; nx=[r for r in tm if r['executed']!='yes']
m=collections.defaultdict(lambda:[0]*6)
for r in ex:
    a=m[r['module']]; a[0]+=1; a[1]+=I(r,'tests_run'); a[2]+=I(r,'failures'); a[3]+=I(r,'errors'); a[4]+=I(r,'skipped')
for r in nx: m[r['module']][5]+=1
print(f"{'module':24}{'classes':>7}{'tests':>7}{'ok':>7}{'fail':>5}{'err':>4}{'skip':>5}{'no-ev':>6}")
t=[0]*6
for k in sorted(m):
    a=m[k]; ok=a[1]-a[2]-a[3]-a[4]
    print(f"{k:24}{a[0]:7d}{a[1]:7d}{ok:7d}{a[2]:5d}{a[3]:4d}{a[4]:5d}{a[5]:6d}")
    for i,v in enumerate(a): t[i]+=v
ok=t[1]-t[2]-t[3]-t[4]
print(f"{'TOTAL':24}{t[0]:7d}{t[1]:7d}{ok:7d}{t[2]:5d}{t[3]:4d}{t[4]:5d}{t[5]:6d}")
PY
```

| module | classes with XML | tests started | successful | failures | errors | skipped | classes without evidence |
|---|---|---|---|---|---|---|---|
| `matrix-core` | 1,137 | 8,484 | 8,438 | **24** | 0 | 22 | 62 |
| `matrix-brain-runtime` | 110 | 881 | 879 | 0 | 0 | 2 | 4 |
| `matrix-api-gateway` | 36 | 203 | 203 | 0 | 0 | 0 | 0 |
| `matrix-billing` | 6 | 71 | 71 | 0 | 0 | 0 | 0 |
| `matrix-audit` | 5 | 40 | 40 | 0 | 0 | 0 | 0 |
| `matrix-quality` | 10 | 40 | 40 | 0 | 0 | 0 | 0 |
| `matrix-observability` | 4 | 22 | 22 | 0 | 0 | 0 | 0 |
| `matrix-operator` | 4 | 20 | 20 | 0 | 0 | 0 | 0 |
| `matrix-spigot` | 2 | 22 | 20 | 0 | 0 | 2 | 0 |
| `matrix-sdk-java` | 2 | 13 | 13 | 0 | 0 | 0 | 0 |
| `matrix-tools-distill` | 1 | 4 | 4 | 0 | 0 | 0 | 0 |
| `pilots/compliance-bot` | 1 | 6 | 6 | 0 | 0 | 0 | 0 |
| `pilots/edu-assessor` | 1 | 5 | 5 | 0 | 0 | 0 | 0 |
| `pilots/smart-home-agent` | 1 | 5 | 5 | 0 | 0 | 0 | 0 |
| **total** | **1,320** | **9,816** | **9,766** | **24** | **0** | **26** | **66** |

This reproduces `RECON-W44-ARCHAEOLOGY.md` §1.1bis line for line (the archaeology prints
9,816 without the "successful" column, which this document adds).

**The 10 classes carrying the 26 skipped** (`skipped > 0`):

| class | module | skipped |
|---|---|---|
| `io.matrix.MatrixIT` | matrix-core | 7 |
| `io.matrix.tsetlin.TsetlinGranmoReferenceTest` | matrix-core | 5 |
| `io.matrix.NativeSmokeTest` | matrix-core | 3 |
| `io.matrix.agent.PretrainedLoaderTest` | matrix-core | 2 |
| `io.matrix.tsetlin.EblH035Test` | matrix-core | 2 |
| `io.matrix.research.Exp326PostBitLinearBenchTest` | matrix-core | 1 |
| `io.matrix.research.Exp347MultiChainConsensusTest` | matrix-core | 1 |
| `io.matrix.tsetlin.Exp002SyntheticBringUpTest` | matrix-core | 1 |
| `io.matrix.brain.runtime.RealOnnxDistillationTest` | matrix-brain-runtime | 2 |
| `io.matrix.spigot.MatrixPluginTest` | matrix-spigot | 2 |

---

## 5. The nine failing classes, and the native-image claim

```bash
python3 -c "
import csv
tm=list(csv.DictReader(open('docs-v2/engineering/W44-TEST-MAP.csv',newline='',encoding='utf-8')))
for r in sorted((r for r in tm if r['executed']=='yes' and int(r['failures'] or 0)>0), key=lambda r:-int(r['failures'])):
    print(f\"{r['failures']:>3}  {r['kind']:9}  {r['test_class']}\")"
```

| class (FQN, from the CSV) | `kind` | failures | needs a native binary? |
|---|---|---|---|
| `io.matrix.nativebin.NativeBinaryCLITest` | test | **8** | **yes** |
| `io.matrix.nativebin.NativeBinaryBackgroundServiceTest` | test | **3** | **yes** |
| `io.matrix.nativebin.NativeBinaryStartupBenchmarkTest` | benchmark | **3** | **yes** |
| `io.matrix.benchmark.NativeBinaryLaunchBenchmarkTest` | benchmark | **3** | **yes** |
| `io.matrix.nativebin.NativeBinaryFullIntegrationTest` | test | **2** | **yes** |
| `io.matrix.nativebin.NativeBinaryPerformanceBenchmarkTest` | benchmark | **2** | **yes** |
| `io.matrix.nativebin.NativeBinaryDockerTest` | test | **1** | **yes** |
| `io.matrix.benchmark.JVMvsNativeBenchmarkTest` | benchmark | **1** | **yes** |
| `io.matrix.consciousness.Exp228ComplianceTest` | test | **1** | **NO** — see §6 |
| **total** | | **24** | 23 native + 1 not |

### 5.1 Verdict on "all 24 failures belong to nine native-image classes"

**The class count is exact. The attribution is not — one class is misattributed.**

* **"24 failures in nine classes" — CONFIRMED.** The CSV has exactly 9 rows with
  `failures > 0`, and their per-class failure counts are 8, 3, 3, 3, 2, 2, 1, 1, 1 = 24.
  The class list in `RECON-W44-ARCHAEOLOGY.md` §1.1bis and
  `W44-HYPOTHESIS-AND-FAILURE-MAP.md` §4 matches the CSV member for member. (The
  archaeology abbreviates the package of four entries — `NativeBinaryStartupBenchmarkTest`,
  `NativeBinaryBackgroundServiceTest`, `NativeBinaryPerformanceBenchmarkTest`,
  `NativeBinaryFullIntegrationTest` are all in `io.matrix.nativebin`, not in the root
  package; the CSV carries the full FQN.)
* **"every one of them needs a GraalVM native image" — FALSE for one class.**
  `io.matrix.consciousness.Exp228ComplianceTest` does not touch a native image at all. Its
  source (`matrix-core/src/test/java/io/matrix/consciousness/Exp228ComplianceTest.java`)
  locates `matrix-core/src/main/java` and calls `BrainLoopCompliance.check(target)`, which
  counts ONNX imports, `Random` uses and wall-clock uses in the **production sources**.
  The failure message is `org.opentest4j.AssertionFailedError: Expecting value to be true
  but was false`, and the class's own `system-out` states the cause:

  ```
  [COMPLIANCE-EXP] FAIL, onnx=0, random=63, wallClock=47, note=21 unadjudicated
  wall-clock site(s): io/matrix/knowledge/KnowledgeImporter.java:51  UNCLASSIFIED
  wall-clock  — Wall-clock in a decision-path package. …
  ```

  The archaeology's sentence that these nine "assert the presence and the startup time of
  `build/native/nativeCompile/matrix-core`" is therefore wrong for this ninth class: it
  asserts a **source-policy** property, and it is red because 21 wall-clock sites in
  `matrix-core/src/main/java` are unadjudicated.

**Correct statement of the claim:**

> All 24 failures are in nine classes. **Eight** of those classes (23 failures) need a
> GraalVM native image that this repository does not build. The ninth
> (`io.matrix.consciousness.Exp228ComplianceTest`, 1 failure) is an ordinary
> source-compliance regression.

### 5.2 The full set of classes that *require* native-image support

Searching the test sources for the native-binary marker gives **11** candidate files:

```bash
grep -rln "nativeCompile\|native-image\|nativeImage\|GraalVM\|graalvm" \
  --include="*.java" matrix-core/src/test matrix-core/src/jmh matrix-brain-runtime/src/test
```

| class | runs in JVM mode? | outcome in the retained evidence |
|---|---|---|
| the 8 failing native-binary classes (§5 table) | no | **23 failures** — the binary does not exist |
| `io.matrix.NativeSmokeTest` | no — `@Disabled("Requires GraalVM native image or full infrastructure…")` | **3 skipped**, 0 failures; a dedicated task `nativeSmokeTest` exists but is `enabled = false` and `dependsOn 'buildNative'` (`matrix-core/build.gradle:521-531`) |
| `io.matrix.nativeimage.NativeImageTest` | **yes** — "These tests run in JVM mode but validate the reflect-config.json completeness" | 37 tests, **0 failures** |
| `io.matrix.nativeimage.NativeCompilationTest` | **yes** — "These tests run in JVM mode to validate that all classes required for native compilation are properly configured" | 42 tests, **0 failures** |

**So the number of test classes that genuinely require a built GraalVM native image is
nine: eight that failed plus `io.matrix.NativeSmokeTest` that was skipped.** The other two
`nativeimage` classes only *describe* the native-image configuration; they execute
ordinary JVM reflection assertions and are green.

**The native binary was never built:**

```bash
ls matrix-core/build/native            # → No such file or directory
ls matrix-core/build/native/nativeCompile/matrix-core   # → No such file or directory
```

---

## 6. Failures caused by missing native-image infrastructure vs ordinary JVM regressions

Every failure message is in the retained CSV's evidence path (and was read from the XML
while it was still on disk, 2026-11-03 08:07):

| failure kind | failures | classes | message |
|---|---|---|---|
| **missing native-image infrastructure** | **23** | 8 | 18 × `java.io.IOException: Cannot run program "build/native/nativeCompile/matrix-core": Exec failed, error: 2 (No such file or directory)`; 5 × `org.opentest4j.AssertionFailedError: Expecting value to be true but was false` on the binary's presence / executability |
| **ordinary JVM regression** (source-policy compliance) | **1** | 1 | `io.matrix.consciousness.Exp228ComplianceTest` — `random=63, wallClock=47, 21 unadjudicated wall-clock site(s)` in `matrix-core/src/main/java` |

**The evidence for the split is the failure text itself, not an inference:**

* the 18 `IOException`s name the missing artefact literally —
  `build/native/nativeCompile/matrix-core`, the path `matrix-core/build.gradle`'s
  `buildNative` task would produce;
* the 5 assertion failures are the same fact asserted rather than executed — the class
  javadocs show it: `NativeBinaryStartupBenchmarkTest` ("Verifies the GraalVM native-image
  binary **exists, is executable**, and starts in under 500 ms"), `NativeBinaryDockerTest`
  ("Verifies `Dockerfile.native-slim` exists and references the native binary") and
  `NativeBinaryFullIntegrationTest` ("End-to-end verification: build + run + verify
  output") all hinge on `build/native/nativeCompile/matrix-core`;
* the 1 remaining failure carries a `system-out` that names production source files and
  counts of `Random`/wall-clock — nothing to do with a native image.

**There is no failure that is an ordinary behavioural regression of the cognitive core.**
The one non-native failure is a **policy/static-analysis** failure, not a behavioural one:
it fires on the *source text* of `matrix-core/src/main/java`, not on runtime behaviour.

---

## 7. Tests that actually exercised ordinary JVM behaviour

| population | executions | note |
|---|---|---|
| total executions in the retained evidence | **9,816** | `sum(tests_run)` |
| − executions that needed the (absent) native binary | −23 | 8 classes; they failed before reaching ordinary JVM behaviour |
| **executions that ran without a native binary** | **9,793** | |
| − skipped | −26 | 10 classes; never evaluated |
| **executions that evaluated an assertion on the JVM** | **9,767** | **9,766 green + 1 red** |
| **ordinary JVM behaviour exercised and green** | **9,766** | 99.99 % of all executions |

**The single red ordinary-JVM execution** is `Exp228ComplianceTest.realProjectCompliance`
— a static source-policy check, not a behavioural assertion (see §6).

**By module**, the green ordinary-JVM population is:

| module | ordinary-JVM executions (green) |
|---|---|
| `matrix-core` | **8,438** (the 23 native-image failures are already outside the "successful" figure) |
| `matrix-brain-runtime` | **879** |
| `matrix-api-gateway` | **203** |
| `matrix-billing` | **71** |
| `matrix-audit` | **40** |
| `matrix-quality` | **40** |
| `matrix-observability` | **22** |
| `matrix-operator` | **20** |
| `matrix-spigot` | **20** |
| `matrix-sdk-java` | **13** |
| `matrix-tools-distill` | **4** |
| the three pilots | **16** |
| **total** | **9,766** |

---

## 8. Reconciliation of the three denominators

### 8.1 The three measures, side by side

| | **1,320 / 9,816** | **8,817** | **1,179** |
|---|---|---|---|
| **what it is** | the W44 audit's CSV census of JUnit XML | one Gradle run's console summary | a source-file count |
| **scope** | **all 14 modules** (12 modules + 3 pilots) | **`matrix-core` only** | **`matrix-core` only** |
| **unit** | one executed test class / one test execution | one test execution | one `.java` file under `src/test/java` |
| **produced by** | `scripts/w44-audit-docmap.py` summing the XML `<testsuite>` attributes | one Gradle invocation of `:matrix-core:test --tests "io.matrix.*"` | `matrix-core/build.gradle:165-219` (`generateBuildInfo`) |
| **recorded in** | `W44-TEST-MAP.csv`; `RECON-W44-ARCHAEOLOGY.md` §1.1bis; `W44-HYPOTHESIS-AND-FAILURE-MAP.md` §4 | `RECON-W44-ARCHAEOLOGY.md` §1.1bis (Disclosure); `W44-HYPOTHESIS-AND-FAILURE-MAP.md` §4 ("of 8817 executions") | `W44-BENCHMARK.md` §1; `RECON-W44-STATUS.md` §1; `RECON-W44-BACKLOG.md` |
| **failures** | 24 | 24 | n/a — a source count has no failures |
| **skipped** | 26 | 18 | n/a |

### 8.2 Why they differ

**(a) 8,817 vs 9,816 — different scopes, and one is not a subset of the other.**
8,817 is the `matrix-core` console summary of a single run; 9,816 is the CSV census of
*every* module's XML. They must not be compared as two totals of the same thing. The
comparable decomposition is:

```
CSV census, matrix-core rows .............  8,484 executions (24 failures, 22 skipped)
CSV census, all other modules .............  1,332 executions ( 0 failures,  4 skipped)
                                          ---------
CSV census, total .........................  9,816 executions (24 failures, 26 skipped)
```

The 8,817 is *not* a sub-total of the 9,816: the run that produced it counted 18 skipped
while the CSV's matrix-core rows count 22, and it is a run total, not an XML census.
**Arithmetic that does hold:** 8,817 = 8,775 passed + 24 failed + 18 skipped, and the CSV's
matrix-core rows give 8,438 passed + 24 failed + 22 skipped. The failure count agrees
exactly (24) in both; the passed counts do not, because the CSV loses executions to
attribution (§8.3).

**(b) 1,179 is not an execution count at all.** It is `matrix-core/src/test/java` **file
count**, printed by the build itself:

```groovy
// matrix-core/build.gradle:217
logger.lifecycle("matrix-build-info: ${testMethods} @Test methods across ${allTestFiles.files.size()} test files")
```

with `allTestFiles = fileTree('src/test/java') { include '**/*.java' }`. The same task
counts `testMethods` as **source lines whose trimmed text starts with `@Test`** = **8,284**,
which `W44-BENCHMARK.md` §1 and `RECON-W44-STATUS.md` §1 report as "8284 `@Test` methods
across 1179 test files". The census-time inventory agrees exactly:

```bash
python3 -c "
import csv
inv=list(csv.DictReader(open('docs-v2/engineering/W44-CLASS-INVENTORY.csv',newline='',encoding='utf-8')))
print(len({r['file'] for r in inv if r['module']=='matrix-core' and r['source_set']=='test'}))"
# → 1179
```

*Why 8,284 `@Test` methods and 8,817 executions differ:* a `@Test` method is not an
execution. `generateBuildInfo` counts only source lines whose trimmed text starts with
`@Test`, so `@ParameterizedTest`, `@RepeatedTest`, `@TestFactory`, jqwik `@Property` and
JUnit `@Nested` executions are all outside the 8,284 while being inside the 8,817. The
two numbers are therefore not convertible into one another; they are a source-side and a
runtime-side measure of the same module.

**(c) The live tree has drifted since the census.** The census-time figure is 1,179. Today
`find matrix-core/src/test/java -name '*.java'` returns more, because a concurrent agent
added `matrix-core/src/test/java/io/matrix/cognitive/CognitiveContradictionDurabilityTest.java`
at **2026-11-03 08:15:16** — i.e. *after* `W44-TEST-MAP.csv` was written (07:28:04) and
after `RECON-W44-ARCHAEOLOGY.md` (07:29:16). The retained CSV is therefore the correct
authority for the census-time numbers, and any re-run of the file count today will not
reproduce 1,179 without excluding that file.

### 8.3 The CSV is a strict undercount — and the size of the gap is provable for one module

The generator keys each XML by `f"{module}:{cls}"`, where `cls` is **the first type
declared in the source file** (`scripts/w44-audit-docmap.py:165-168`). Any XML whose
`<testsuite name=…>` does not equal that key is silently dropped:

* a file that declares **more than one top-level test class** produces several XMLs but
  only one CSV row — the other classes' executions are lost (11 files are affected, §2);
* a **`@Nested` class** produces an XML named `Outer$Inner`, which can never match a
  file-level key (84 nested types are in the inventory);
* a **jqwik `@Property` container** produces one XML *per property*, named after the
  property — e.g. `Pilot #5: Cauldron — FNL auto-generation via GA` — which never matches
  either.

**The gap is provable from retained evidence alone for `matrix-brain-runtime`:**

| source | measurement |
|---|---|
| `W44-BENCHMARK.md` §1 / `RECON-W44-STATUS.md` §1 — the module's own full run | **914** executions, 0 failures, 2 skipped |
| `W44-TEST-MAP.csv` — the same module's CSV rows | **881** executions, 0 failures, 2 skipped |
| **difference** | **33 executions lost to key-matching** (the 33 is retained-evidence arithmetic; the "9 XML files" behind it is **[transient]**) |

The same mechanism applies to every module, so **9,816 is a floor, not a total.** The true
execution count of the XML corpus is higher. How much higher is *not* computable from the
retained artefacts, because the XML corpus is not retained.

### 8.4 The one residual that cannot be closed

`matrix-core`: the run console reports 18 skipped; the CSV's matrix-core rows report 22
skipped. The difference is **exactly 4 skipped executions**, with the failures (24) and the
passed count agreeing. **[transient]** In the XML snapshot taken at 08:07, the matrix-core
XML corpus totalled 8,821 = 8,775 passed + 24 failed + 22 skipped — the same 8,775 passed
the console summary implies, plus the 4 extra skips. So the residual is **four executions
that were skipped in the later XML corpus but had run (and passed) in the earlier console
run** — consistent with the audit's later narrow group runs re-executing four
conditionally-skipped tests. **The retained evidence does not identify which four**, and
the XML corpus has since been rewritten. The class-level skip table in §4 is the closest
retained approximation: the per-class skip counts 7, 5, 3, 2, 2, 1, 1, 1 (matrix-core)
admit several 4-subsets, so no unique identification is possible.

---

## 9. Environment conditions

| condition | value | how it was established |
|---|---|---|
| **JVM / JDK** | **OpenJDK 25.0.4.1** (build `25.0.4.1+1-1-26.04.4-Ubuntu`, 2026-08-18) | `W44-BENCHMARK.md` header ("**JVM:** OpenJDK 25.0.4.1"); `RECON-W44-STATUS.md` §1 ("The build needs `JAVA_HOME=/home/alexandr-narbaev/jdk25`"); `matrix-core/build.gradle:30-31,377` and `build.gradle:9-10` (toolchain 25); verified live with `/home/alexandr-narbaev/jdk25/bin/java -version` |
| **Build tool** | **Gradle 9.6.0** | `gradle/wrapper/gradle-wrapper.properties` |
| **Test runner** | JUnit Platform (`useJUnitPlatform()`, `matrix-core/build.gradle:101`) + jqwik | `matrix-core/build.gradle` |
| **Host** | AMD Ryzen 9 9955HX, 32 threads, 59 GiB RAM | `W44-BENCHMARK.md` header; confirmed live (`nproc` = 32, `free -g` = 59, `/proc/cpuinfo`) |
| **GPU** | NVIDIA GeForce RTX 5070 Ti Laptop GPU present | `W44-BENCHMARK.md` header (not exercised by these tests) |
| **OS** | **Ubuntu 26.04.1 LTS, Linux 7.0.0-42-generic x86_64** | `/etc/os-release`, `uname -srm` on the reconciliation host. `W44-BENCHMARK.md` does **not** name the OS; the host is the same machine by CPU/RAM/GPU identity. `[speculative]` for the *W44 run itself*, established for the reconciliation host |
| **GraalVM native-image tooling** | **ABSENT** | `command -v native-image` → nothing; `command -v gu` → nothing; `command -v gu-build` → nothing. The JDK in use is **OpenJDK, not GraalVM**, so `-Dquarkus.native.enabled=true` cannot work here |
| **Native binary** | **never built** | `matrix-core/build/native` does not exist; `matrix-core/build.gradle:521-531` registers `nativeSmokeTest` with `enabled = false` and `dependsOn 'buildNative'` — the ordinary `test` task never produces a native image |
| **Docker** | `/usr/bin/docker` present | `command -v docker`. Irrelevant to the 24 failures: they are missing-binary failures, not missing-Docker failures |
| **Full-run duration** | **24 minutes** for the `matrix-core` full suite | `RECON-W44-ARCHAEOLOGY.md` §1.1bis (Disclosure) |

---

## 10. What the retained evidence does **not** establish

1. **The total number of executions actually performed.** The XML corpus is not retained;
   9,816 is a floor. The loss is provable for `matrix-brain-runtime` (33 executions, §8.3)
   but not computable for the repository as a whole.
2. **Which four `matrix-core` executions were skipped in the XML corpus but not in the
   console summary** (§8.4). Not identifiable from the retained artefacts.
3. **The number of test classes that exist in the source tree**, as opposed to the 1,386
   file-keyed rows. 20 additional top-level test classes are declared in 11 files and are
   recorded by neither CSV (§2).
4. **Which of the 66 classes without evidence were executed and passed** outside the
   retained XML (the 36 in §3 category 4 did run; the 20 JMH benchmarks did not).
5. **The OS of the W44 run itself.** Established for the reconciliation host only.
6. **Whether the 24 failures would still be 24 today.** The source tree has changed since
   the census (a concurrent agent added a test file at 08:15 on 2026-11-03), and the suite
   was deliberately **not** re-run.

---

## 11. How to re-run this reconciliation

```bash
cd /home/alexandr-narbaev/Projects/agi
git rev-parse --abbrev-ref HEAD && git rev-parse HEAD     # develop / 81f08478
python3 - <<'PY'                                          # §1, §3, §4, §5
import csv, collections
tm=list(csv.DictReader(open('docs-v2/engineering/W44-TEST-MAP.csv',newline='',encoding='utf-8')))
I=lambda r,k:int(r[k] or 0)
ex=[r for r in tm if r['executed']=='yes']
print(len(tm), len(ex), sum(I(r,'tests_run') for r in ex),
      sum(I(r,'failures') for r in ex), sum(I(r,'skipped') for r in ex))
PY
python3 - <<'PY'                                          # §2
import csv
inv=list(csv.DictReader(open('docs-v2/engineering/W44-CLASS-INVENTORY.csv',newline='',encoding='utf-8')))
print(len({r['file'] for r in inv if r['source_set'] in ('test','jmh')}),
      len({r['file'] for r in inv if r['module']=='matrix-core' and r['source_set']=='test'}))
PY
ls matrix-core/build/native || echo "no native binary"   # §5, §9
```

**The matrix-core suite was not run.** The directive forbids it, the result is already
evidence, and the source tree is being modified concurrently.

---

## 12. Summary for the next wave

1. The W44 accounting is **internally consistent**: 1,320 classes with XML → 9,816
   executions, 24 failures, 0 errors, 26 skipped, 9,766 successful.
2. **8,817 is not a competing total** — it is one `matrix-core` run's console summary, and
   it is not comparable with the all-module census. **1,179 is not a test count** — it is
   a file count for one module.
3. The CSV is a **floor**, not a total: it loses executions to a key-matching defect that
   is provable at 33 executions in `matrix-brain-runtime` alone.
4. **The "nine native-image classes" claim is half right.** Nine classes hold the 24
   failures; **eight** need a native image (23 failures); the ninth,
   `io.matrix.consciousness.Exp228ComplianceTest`, is an ordinary source-policy
   regression (21 unadjudicated wall-clock sites in `matrix-core/src/main/java`).
5. **Ordinary JVM behaviour is green**: 9,766 green executions across all 14 modules, with
   one static-analysis failure and 23 native-image failures as the only reds.
