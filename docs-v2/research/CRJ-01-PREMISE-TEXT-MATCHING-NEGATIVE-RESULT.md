# CRJ-01 — Premise text matching negative result

**Status:** `CANONICAL`

**Recorded:** 2026-11-23 · **Finding:** `refuted-toy` · **Decision:** PIVOT

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

**The retained evidence now resides IN THE REPOSITORY**, at
`docs-v2/engineering/evidence/crj01-premise-negative/` — so a reader on another machine
does not need the session's temporary directory, and the record cannot point at a path
that only existed on one machine. Paths below are relative to that directory. The report
publishes group outcomes and digests.

**What is retained, and what is deliberately not.** The manifest, the synthetic
fixtures' manifest (`oracle.json`), the frozen documents, the evaluator's runner and
frozen test, the **baseline and candidate JUnit XMLs**, the per-run `command.txt`,
`resource.txt`, `exit-status.txt` and `gradle.log`, the evaluation report, and the
session's own commission, checkpoint and dispatch record — all of it, with the manifest
verifying (**11 of 11 files OK**). The evaluator's **candidate working copy** (174 MB of
build output) is **not** retained: it is machine state, and the repository does not carry
build output. The evaluator's **gold** stays with the evaluator, as the mandate requires
("do not expose ... hidden gold"); the digests above let the owner verify the retained
evidence against the gold they hold, without the repository publishing it.

**The manifests are classified, so a reader knows what verifies where.**
`docs-v2/engineering/evidence/crj01-premise-negative/MANIFESTS.md` says, for every manifest,
whether it is **RETAINED** (every named file is in the repository), **HELD BACK** (the
digests are published, the evaluator's cases are not), or **POINT-IN-TIME** (it records a
payload as it was proposed, and the tree has since moved). The consolidation audit of
2026-11-24 found this classification was missing, and found that the retained set was
**incomplete**: the `gradle.log` of each run was excluded by the repository's `*.log` rule,
so three manifests named a file the repository did not carry and could not be verified on
another machine. The `.gitignore` now excepts the retained evidence's logs explicitly, and
every manifest that names only retained files verifies in full.

**One artifact of the baseline run is not on this machine at all.** The baseline's
`FrozenPremiseJourneyTest.java` (digest `6b7c3779…`) was replaced in the evaluator's
working directory when the correction was applied, and its original bytes were not kept
anywhere. Its **digest** survives in `evidence-baseline/RUNNER.sha256` and in the table
above; the baseline's **result** — JUnit XML, rendered evidence, command, exit status and
resource measurement — is fully retained.

| Evidence | SHA-256 |
|---|---|
| `PREMISE-MILESTONE-CHECKPOINT.md` | `f461176938ee1d1655321ff7ae3f0cdf4096fa896e4f532adadb69975655cc72` |
| `MANIFEST.sha256` | `a2072ba21519b431abf999bb52acf4006029edfa65e886397dca82d08be6dc21` |
| `runner/RUNNER.sha256`, corrected baseline and candidate runner | `7a863083d63163563617ff468508047bcf3dedfcde176a48ed79d7f95f2e7563` |
| Baseline `evidence-baseline-rendered/TEST-io.matrix.crj.runner.FrozenPremiseJourneyTest.xml` | `5ee482547f02316898c676b4ced435c4b59161bd882e13979897452d91a92d8a` |
| Candidate `evidence-candidate/TEST-io.matrix.crj.runner.FrozenPremiseJourneyTest.xml` | `2bec4faa9daeb986d1954aacb896cdf6970da55b10ee3168b93af18f003d18f1` |

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
