# CRJ-01 — Premise text matching negative result

**Status:** CANONICAL · **Recorded:** 2026-11-23 · **Finding:** `refuted-toy` · **Decision:** PIVOT

The tested text-matching candidate failed to establish claim-level premise refutation on
the independently authored synthetic served-path controls. The baseline and candidate each
passed 2 of 7 frozen control groups across 12 trials. The candidate passed all 18 targeted
developer tests, including its literal-claim regression. The frozen measurement yielded zero
additional passing groups. The candidate remains local and received no scientific promotion.

## Measurement

| Frozen control group | Baseline | Candidate |
|---|---|---|
| Explicit and scoped contradiction | Failed, UNSUPPORTED | Failed, UNSUPPORTED |
| Competing readings | Failed, UNSUPPORTED | Failed, UNSUPPORTED |
| Missing evidence | Passed | Passed |
| Topical evidence without contradiction | Passed | Passed |
| Workspace-local identity | Positive prerequisite failed, UNSUPPORTED | Positive prerequisite failed, UNSUPPORTED |
| Changed source revision | Unverified, reimport retained prior active claims | Unverified, reimport retained prior active claims |
| Correct source and wrong span | Positive prerequisite failed, UNSUPPORTED | Positive prerequisite failed, UNSUPPORTED |

The JUnit XMLs report five unsuccessful groups per run. The revision group is one of those
five and remains unverified. The unchanged count therefore includes two confirmed passes,
four semantic failures and one unresolved revision prerequisite on each revision.
The initial baseline had harness setup defects. The evaluator corrected those before the
candidate ran and kept the source and gold bytes fixed. Both comparisons use the corrected
runner. This was independent AI-assisted internal evaluation under evaluator control.
Codex helped adapt the candidate and ran the independently authored evaluator when its
original agent reached its usage limit. The experiment leaves the full CRJ-01 split
unfrozen and establishes no learning transfer.

## Retained primary evidence

The retained evidence resides in the local CRJ-01 handoff directory
`/home/alexandr-narbaev/Projects/agi/matrix-crj-provenance.5BER3h/handoff`.
Paths below are relative to that directory. The report publishes group outcomes and
digests. The evaluator retains the fixtures and gold.

| Evidence | SHA-256 |
|---|---|
| `PREMISE-MILESTONE-CHECKPOINT.md` | `f461176938ee1d1655321ff7ae3f0cdf4096fa896e4f532adadb69975655cc72` |
| `evaluator-evidence.KZ8jhJaC/MANIFEST.sha256` | `a2072ba21519b431abf999bb52acf4006029edfa65e886397dca82d08be6dc21` |
| `evaluator-evidence.KZ8jhJaC/runner/RUNNER.sha256`, corrected baseline and candidate runner | `7a863083d63163563617ff468508047bcf3dedfcde176a48ed79d7f95f2e7563` |
| Baseline `evidence-baseline-rendered/TEST-io.matrix.crj.runner.FrozenPremiseJourneyTest.xml` under the evaluator directory | `5ee482547f02316898c676b4ced435c4b59161bd882e13979897452d91a92d8a` |
| Candidate `evidence-candidate/TEST-io.matrix.crj.runner.FrozenPremiseJourneyTest.xml` under the evaluator directory | `2bec4faa9daeb986d1954aacb896cdf6970da55b10ee3168b93af18f003d18f1` |

The paired baseline is `0549d27ee561afdf94214cbabe661c2532e08bae`. The local candidate is
`b69c9aa7d0cd652bf7b349c862277786ecd766f5`. Both evaluator runs used Gradle 9.6.0 with
`--offline --no-daemon --no-build-cache --max-workers=2 :matrix-core:test --tests io.matrix.crj.runner.FrozenPremiseJourneyTest`.
Each has its own retained `command.txt`, `resource.txt` and JUnit XML next to the run.
Read the two XML suite counters to reproduce 7 groups and 5 failures per run.
Re-derive the developer total by summing the suite counters in
`premise-candidate-two-xml` (14 + 1 + 3). The exact targeted test command is in
`premise-candidate-two.time`.

Native DeepSeek Harness with MiniMax-M3.1-Flash consumed 98,089 provider-reported
tokens in six scoped calls. Sum the `usage[].totalTokens` fields in five
`premise-turn.*/result.json` files and `premise-dsh-red.mihylp8f/result.json`.
The synthetic subscription preflight was separate. Developer tests took 23.21 seconds
wall time and 56.35 seconds CPU. The corrected baseline and candidate evaluators
took 10.40 and 21.65 seconds wall time, with 44.74 and 82.56 seconds CPU respectively.
Compile and cache differences prevent a cognitive latency comparison. Provider cost,
CPU and per-case allocation were `NOT_INSTRUMENTED`.

## Decision

**RESEARCH_INFORMATION_GAIN · PIVOT.** Stop optimizing the tested recognizer.
The source revision control exposed a separate unresolved prerequisite. Importing changed
bytes under the same filename retained both sets of observations without establishing
which revision was applicable to current refutation. The next falsifiable experiment must
explicitly select or supersede a source revision in one workspace, preserve reconstructable
history there and leave the other workspace's original refutation intact. A filename or
content hash alone cannot establish a supersession relationship. This experiment does not
resolve the general scoped contradiction problem.
