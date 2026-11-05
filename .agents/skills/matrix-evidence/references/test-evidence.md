# Test evidence

## The artefact families

| the family | what it holds | what it proves |
|---|---|---|
| the JUnit XML corpus | one file per test class, under the module's `build/test-results/test/` | the classes, the tests, the failures, the skips — **the proof, not the console summary** |
| the audit CSVs | the reconciled capability evidence under `docs-v2/engineering/` | what was measured, and by which command |
| the retained test reports | the wave records under `docs-v2/engineering/` | the baseline at a named commit |
| the per-scope registry | `docs-v2/engineering/TEST-CATALOGUE.yml` | which scope was verified at which commit |

The reconciled baseline is `docs-v2/engineering/W45-TEST-EVIDENCE-RECONCILIATION.md`.

## The three denominators

**Classes, executions and files are not interchangeable**, and the artefact names which is
which.

| the denominator | it counts | the trap |
|---|---|---|
| a **class** | a test class | a class may hold one test or many |
| an **execution** | a test run | a parameterised test executes once per case |
| a **file** | a result file | one file per class, so the file count is the class count, not the test count |

A report that says "724 tests" and a report that says "9 432 tests" are not in conflict
until someone says which of the two it counted. **Restating a CSV column as a count
without re-deriving it is a defect.**

## The failure taxonomy

Separate the kinds **before** reporting either:

| the kind | what it is | what it is not |
|---|---|---|
| an ordinary JVM regression | a test that failed for a reason in the code | not an environment problem |
| a native-image infrastructure failure | the native build or the GraalVM toolchain, not the code | not a regression |
| a resource failure | the machine could not afford the run | not a failure of the code |

The rule: **a failure that is not understood is not reported as a defect of the code**, and
a defect that is not reproduced is not reported as a defect at all.

## The retention rules

- The **evidence** is the XML, not the console output.
- A number is published **with the commit it was measured at**.
- A **stale** baseline is not a baseline: `docs-v2/engineering/TEST-CATALOGUE.yml` carries
  the last verified commit, and a number from an older commit is quoted as older.
- The full `matrix-core` suite is **owner-held**. A targeted suite runs when the task names
  it; one `matrix-core` test run at a time.
