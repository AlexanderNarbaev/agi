# Open decisions — awaiting the operator

**Status:** none of these can be resolved from the code. Each needs a human choice.
**Recorded:** 2026-10-05, at commit `434e856f` (RECON-W32.33).
**Why this file exists:** each item was deferred across several waves because deferring was
cheaper than guessing wrong. That was the right call while work remained unblocked. It stops
being the right call now, and the honest thing is to stop deferring and ask.

No option below has been taken. Where a recommendation is given it is marked, and the cost
of *not* taking it is stated too.

## CLOSED decisions (RECON-W38.0, 2026-10-17)

Rulings by the operator; evidence pointers below. D2 and D8 were closed in earlier waves;
the five below were ruled in this sweep.

- **D1 — BitNet: CLOSED.** The W32 record (environment-blocked, never executed; 27-28
  failures in 10 classes from one cause: the missing /tmp/hf_cache snapshot) was superseded
  in RECON-W33.1: the checkpoint was provisioned and the BitNet suites now execute for real
  (see KnownFailures.md, 'RECON-W33.1 — the BitNet skip record above is now HISTORICAL').
  Option B was taken. BitNet is tested, not merely documented.
- **D2 — six integration tests on stale branches: CLOSED** via RECON-W32.0/W38.0 recovery:
  EpisodicLogPersistenceTest recovered and green (3/0, commit 11249775);
  SleepAndConsolidationIntegrationTest preserved as
  docs-v2/quality/lost-capability-tests/SleepAndConsolidationIntegrationTest.java.txt
  (9 of 12 tests unportable — the ConsolidationCycle.run(EpisodicLog, PersistentHdcStore)
  API those tests were written against no longer exists).
- **D3 — gitverse/master: CLOSED (operator, 2026-10-05).** The operator deleted
  gitverse/master directly on the remote; verified gitverse holds exactly two heads
  (develop, main) aligned with develop; de508931 'Initial commit' matches no remote ref and
  is reachable from no local ref. See SESSION.md, W33.1 checkpoint.
- **D5 — disk cleanup: CLOSED.** The W32.34 ruling executed the reclaim (smoke corpora
  removed; data/ DISK-LEDGER seq 179-181 record ~137 GB reclaimed and data/smoke absent on
  disk as of RECON-W38.0 verification).
- **D6 — the TrueMindCycle.java:88 'kill' reflex: CLOSED AGAINST REMOVAL (W32.34).**
  The description in this file was factually wrong: it is not a benign simulation leftover,
  it is a safety refusal registration ('I cannot provide instructions intended to kill.'),
  and it was the only safety guard with no coverage at the time. Deleting it failed 4 tests;
  it remains load-bearing for Article I refusals. It stays. See SESSION.md RECON-W32.34
  'D6 is not executed, because the premise was false'.
- **D7 — weekly CI RFC: CLOSED — RFC APPROVED (operator, RECON-W38 pre-session).**
  .github/workflows/ci.yml is unfrozen for one minimal change: a scheduled weekly run of
  the research/property suites (landed as commit 6a15f178). No other CI behaviour altered.

D4 and D8 were closed in the W33-W34 sweeps (tagging with explicit caveats; research-debt
ownership per family) and are not re-litigated here.

---

## D1 — The 28 BitNet failures: red, or environment-blocked?

`matrix-core` has 28 failures in 10 BitNet test classes, all from one cause:
`BitNetModelLoadTest.java:28-30` hardcodes
`/tmp/hf_cache/models--microsoft--bitnet-b1.58-2B-4T/.../model.safetensors`. That path does
not exist, `/tmp` does not survive a reboot, and no Gradle task, script or CI step
provisions it. About twenty other test classes reference `hf_cache` and pass, because they
carry skip guards; these ten do not.

**Consequence that matters more than the count:** every one of those tests aborts at the file
check, so **no BitNet forward pass, prefill, KV-cache or sampling behaviour has ever been
executed.** BitNet is currently *untested*, not *failing for a known reason*.

| option | effect | cost |
|---|---|---|
| **A. Add `Assume.assumeTrue(Files.exists(...))` to the 10 classes** | suite 55 → ~27; the cause becomes explicit in code | converts "red" into "not executed"; the standing constraint says skipping tests is forbidden, so this is a **disposition change, not a fix** |
| **B. Provision the 1.1 GB weights and run them** | BitNet genuinely validated; may reveal real bugs | 1.1 GB download, ~17 GB on-disk for a 2B model, and unknown number of genuine failures underneath |
| **C. Leave red, document as environment-blocked** | nothing hidden; 55 stays the honest total | the number stays inflated by a cause that is not a code defect, and BitNet stays unvalidated |
| **D. Delete the 10 classes** | suite green immediately | **forbidden** — and destroys the specification of what BitNet is supposed to do |

**Recommendation: A now, B when disk and time allow.** A is honest *provided the report says
"environment-blocked, never executed" rather than "passing". B is the only option that
actually validates anything. **C if you would rather the count stayed honest and visibly
wrong than become right by exclusion.**

## D2 — Six integration tests that exist only on stale branches

Investigation of the remote branches (see `SESSION.md`, 2026-10-05) found that all
production classes are present in develop, but **six test files exist on old branches and
nowhere in develop**, while the classes they exercise are present:

| branch (both remotes) | test file | class under test |
|---|---|---|
| `feature/mind-w2-persistent-mind` | `SleepAndConsolidationIntegrationTest.java` | `RealSleepScheduler` |
| `feature/mind-w3-sleep-consolidation` | *(same file)* | `RealSleepScheduler` |
| `feature/mind-w4-autonomy-goals-inbox` | `AutonomyIntegrationTest.java` | `GoalTracker` |
| `feature/mind-w5-distillation-factory` | `DistillationFactoryIntegrationTest.java` | `TrueDistillationFactory` |
| `feature/true-w3-real-sleep` | `RealSleepSchedulerIntegrationTest.java` | `RealSleepScheduler` |
| `feature/true-w5-distillation` | `TrueDistillationFactoryIntegrationTest.java` | `TrueDistillationFactory` |
| `feature/true-w6-w7-gpu-multilingual` | `GpuKernelEngineIntegrationTest.java` | `GpuKernelEngine` |

| option | effect | cost |
|---|---|---|
| **A. Recover all six onto develop and run them** | real coverage returns; the classes get tested | they were written against an older API, so they may not compile as-is — that is the point of finding out |
| **B. Recover, fix to current API, keep whatever passes** | coverage returns, honestly scoped | effort per file; some may test behaviour that has since intentionally changed |
| **C. Leave them on the branches** | nothing changes | develop ships `TrueDistillationFactory`, `RealSleepScheduler` and `GpuKernelEngine` with **no integration test at all** |

**Recommendation: B.** The classes are real and shipped; the tests exist; leaving them
unrecovered means the only record that they ever existed is a remote branch nobody reads.

## D3 — `gitverse/master` points at "Initial commit"

`gitverse/master` is `de508931 Initial commit` — an unrelated root, not a stale snapshot of
this project. `origin` has no `master` at all.

| option | effect | cost |
|---|---|---|
| **A. Leave it** | nothing | a second default branch on a Russian mirror, containing an empty repo, invites someone to fetch from the wrong place |
| **B. Point `gitverse/master` at `develop`** | one obvious default | changes a default branch on a remote the operator owns |
| **C. Delete `gitverse/master`** | cleanest | destructive on a remote; not reversible by me |

**Recommendation: B**, because it makes the mirror's default branch useful rather than
empty, and it is a fast-forward of a *pointer*, not a history rewrite.

## D4 — Tag `v17.5.0-mind`

W32 is functionally complete: 5 waves landed, `matrix-core` went 8106/67 → 8111/55 with every
fix traceable to a named defect, brain-runtime 597/0, gateway 152/0, quality gate 0, FROZEN
0-diff, 23 → 1 bare silent catch. The tag has not been created because external Goal Guard
state is unavailable and an independent review verdict was never observed.

| option | effect | cost |
|---|---|---|
| **A. Tag now** | a releasable point exists | 55 core failures and 7 never-run BitNet classes ship inside the tag's implied claim of a working suite |
| **B. Tag after D1 and D2** | the tag's numbers are the best available | waits on 1.1 GB download and six test recoveries |
| **C. Tag with an explicit caveats file in the release notes** | honest and available now | requires writing the caveats, which is work I can do immediately |

**Recommendation: C.** A tag whose notes state "BitNet never executed; six integration tests
never merged; 55 research-debt failures" is more useful than no tag, and more honest than a
tag that says nothing.

## D5 — Disk cleanup (operator-gated since W28)

| path | size | note |
|---|---|---|
| `data/smoke-old` | ~8.8 GB | smoke-test output from a superseded wave |
| `~/.cache/w28-scratch/clone-repro` | ~20 GB | a clone-reproduction scratch dir |

**Recommendation: delete both.** Neither is referenced by the build, tests, quality gate or
any script I could find. I have not touched them, and I will not without this answer.

## D6 — `TrueMindCycle.java:88` benign "kill" reflex

A line in the cognitive cycle still contains a literal `"kill"` reflex from earlier
simulation work. It is believed benign, and it has been carried as an open item across many
waves precisely because I could not prove benign by inspection alone.

| option | effect | cost |
|---|---|---|
| **A. Remove it and run the full suite** | one fewer unknown in the most safety-critical file | if something does depend on it, the suite is the detector — which is why it is safe to try |
| **B. Prove it unreachable, then leave it** | nothing changes | the proof is the work |
| **C. Leave as-is** | nothing | an unexplained string in the decision path of the core cycle |

**Recommendation: A.** It is a single-line removal with a full-suite gate behind it.

## D7 — Weekly CI RFC under `.github/`

`.github/` is FROZEN, so CI changes require an RFC plus explicit approval. The proposed
change is a weekly scheduled run of the research/property suites, which are currently only
exercised when someone runs them locally.

| option | effect | cost |
|---|---|---|
| **A. Author the RFC, change nothing** | the decision is documented and reviewable | no CI until it is approved |
| **B. Approve and implement** | the flaky property tests would be caught in CI | touches a FROZEN zone |

**Recommendation: A.** I can write the RFC; only the operator can unfreeze.

## D8 — Ownership of the 55 research-core failures

Now attributed: **0 are caused by CUDA**, 28 are the missing BitNet fixture (D1), the rest
are genuine research/test-harness issues with a known family breakdown. Nothing is silently
ignored, but nothing is *owned* either.

| option | effect | cost |
|---|---|---|
| **A. Accept as research debt with written justification per family** | the number stops being a mystery | requires accepting that some may stay red indefinitely |
| **B. Burn down over W33+** | the suite converges toward green | substantial effort, and F4 (the `KolmogorovComplexity` 64-bit floor) is a **live scientific question, not a test bug** — fixing it means deciding the estimator's semantics |

**Recommendation: A for F9/F6/F7, B for F2/F3/F4/F5/F10/F11.** F4 in particular should not
be "fixed" by adjusting thresholds until green; that would settle a scientific claim by
editing its test.

---

## Not asked, because I am proceeding

**`KnowledgeExchangeProtocol.fromJsonLine` has no shape validation.** A torn federated line
becomes a `Fact` with empty `id`/`input`/`answer` and is then taught as `"fed-<node>- => "`.
That is a fabrication-shaped defect in the same class this campaign exists to close, and it
needs no decision — it gets fixed.
