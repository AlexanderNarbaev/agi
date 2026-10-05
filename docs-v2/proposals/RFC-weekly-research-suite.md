# RFC: weekly scheduled run of the research and property suites

| | |
|---|---|
| **Status** | PROPOSED — awaiting operator approval. **Nothing in `.github/` has been changed.** |
| **Author** | RECON-W32.34, operator decision D7 (option A: author the RFC, change nothing) |
| **Date** | 2026-10-05 |
| **Affects** | `.github/workflows/**` — **FROZEN**. This document proposes; it does not authorise. |
| **Decision needed from** | the operator, alone |

## 1. The problem, stated precisely

`matrix-core` has **54 skipped tests and 27 failures** that are only ever exercised when
somebody remembers to run the module locally. Three classes of test are affected:

1. **jqwik property tests** whose firing probability sits between roughly 84% and 99%. These
   do not fail deterministically, so a single local run is not evidence about them. The
   audit of the 67 failures established this as the mechanism behind the total moving between
   67, 68, 69, 70, 72 and 73 across runs on unchanged code — it was unattributed for weeks
   because nobody ran the suite repeatedly on purpose.
2. **The 54 skips**, of which 32 are the BitNet classes guarded in W32.34. A skip is
   invisible in a build result until you read the XML, and an invisible skip is how "never
   executed" came to be reported as ordinary debt.
3. **The 27 remaining failures**, which are research and test-harness issues, triaged in
   `docs-v2/quality/KnownFailures.md`.

Nothing here is a correctness risk to production code. The risk is to the *evidence*: a
metric that only exists when a human chooses to produce it will drift, and a drift nobody
measures is indistinguishable from stability.

## 2. What is proposed

A single scheduled workflow, weekly, that:

- runs `./gradlew :matrix-core:test :matrix-brain-runtime:test :matrix-api-gateway:test`
  with **no** `--tests` filter, so the aggregate numbers are authoritative;
- writes the JUnit XML and JaCoCo XML out as build artefacts;
- **fails the run** on any *new* failure relative to a committed baseline, and
  **does not** fail on the known ones recorded in `KnownFailures.md`;
- posts a comment on a single tracking issue with: total tests, failures, skips, the delta
  against the baseline, and the list of newly failing classes;
- explicitly reports the **skip count**, because a skip that nobody reads is the exact
  failure mode that produced the BitNet situation.

Deliberately **not** proposed: running on every push. A 17-minute full-module run on every
push trains everyone to ignore it, and a signal that is routinely ignored is worse than no
signal.

## 3. Why it is FROZEN, and what approval means

`.github/` is a FROZEN zone under Article VII. No file under it may change without an
explicit RFC and explicit operator approval. This document is that RFC. **It changes no CI
configuration, and no workflow may be added until the operator approves it.**

## 4. Baseline, and how "new failure" is decided

A committed file, `docs-v2/quality/ci-baseline.json`, listing failing class names and the
skip count at approval time. A run fails only if a class appears that is not in the list, or
if the skip count changes without a corresponding entry in `KnownFailures.md`.

This is deliberately conservative. A flaky property test that appears in the baseline is
absorbed; a genuinely new class is not. Absorbing flakiness into a baseline is a real cost
and is accepted knowingly — the alternative, failing on a 90%-probability test, produces an
alarming channel that is ignored within a month, which is the outcome this RFC exists to
prevent.

## 5. Cost

- Roughly 17 minutes of one runner per week.
- The artefact retention problem is real: JUnit XML for 8 111 tests plus JaCoCo is not small,
  and the retention policy must be set at authoring time, not discovered later. This is
  named here as an open implementation detail rather than left to whoever writes the YAML.

## 6. What this RFC does not fix

- It does not make BitNet tested. Only provisioning the 1.1 GB checkpoint does that
  (operator decision D1, option B). Until then BitNet remains **untested**, and a weekly run
  will keep reporting its 32 skips.
- It does not attribute the 27 failures. That is `KnownFailures.md` (D8), and it is a
  human judgement, not a CI feature.
- It does not shorten the 17-minute run. A sharded run would, at the cost of complexity that
  is not justified until the cadence proves the signal is being read.

## 7. Decision requested

Approve, or reject with a reason. If approved, the implementation must be a separate
commit that touches only `.github/workflows/`, and must be reviewed against the FROZEN-zone
0-diff requirement of every other wave.
