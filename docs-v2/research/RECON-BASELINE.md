# RECON Baseline — verified at the start of W33.1

The operator prompt for RECON-W33 carried a §0 "VERIFIED BASELINE" and instructed:
*"re-confirm before first edit; document drift in `docs-v2/research/RECON-BASELINE.md`"*.

That file did not exist. This is it — the re-confirmation, and every place the stated
baseline was stale. Every "measured" value below came from a command run in this session,
not from the prompt.

Recorded 2026-10-05, at the first W33.1 commit.

---

## 1. Git — the baseline was one commit stale

| | stated in §0 | measured | drift |
|---|---|---|---|
| HEAD | `4c9eacc2` | `2ccc2332` | +1 |
| develop == main | asserted | `2ccc2332` == `2ccc2332` | none |
| origin/develop == gitverse/develop | asserted | both `2ccc2332` | none |
| working tree | **"dirty with D2 recovery work"** | **CLEAN** | stale |

The tree is clean because the §0 prompt was written against the state *before* D2 was
committed. `2ccc2332` is the D2 commit:

> `test(brain-runtime),docs(quality): RECON-W32.34 — D2: 30 recovered tests, and 2 real
> defects they found`

The prompt also instructed "Resolve IOException in DistillationFactoryIntegrationTest. Commit
passing recovered tests" as a W33.1 task. **That work was already done**, in `2ccc2332`, and
re-verified here: `compileTestJava` is green and the three files carry `throws Exception`.

`DistillationFactoryIntegrationTest.java:35,60,83,121` gained `throws IOException`; the other
methods took `throws Exception`. The checked-exception drift was resolved by declaring the
exception, not by catching and discarding it.

## 2. Tests — BitNet skips no longer exist

| | stated in §0 | measured | drift |
|---|---|---|---|
| matrix-core | 8111 tests / 27 failures / 54 skipped | see §4 | **54 → 22** |
| matrix-brain-runtime | 597 / 0 | **630 / 5** | +30 tests, **5 failures that did not exist before** |
| matrix-api-gateway | 152 / 0 | 152 / 0 | none |

The two substantive drifts:

**BitNet is no longer skipped.** §0's "54 skipped (BitNet environment-blocked)" described a
state that could never recover on its own. `/tmp/hf_cache` no longer existed at the start of
W33.1 — `/tmp` had been wiped, so the D1 assumption had silently become permanent. The
checkpoint is now at `data/models/bitnet-checkpoint/` and **37 BitNet tests execute and pass,
0 skipped**.

**brain-runtime gained 5 failures, and they are findings, not regressions.** They come from
tests that had not run in weeks:
- 2 genuine defects — the ledger **summary aggregation NPEs** on a null map value, and
  `ledger_handles_corrupt_lines_gracefully` **throws** `NumberFormatException` on an empty
  field despite its name promising graceful handling.
- 3 stale assertions about a ledger identity that changed from caller-supplied provenance
  (`"synthetic:teacher"`) to a generated run id (`"run-1791201430272"`).

None is skipped or deleted. The count went **up** because coverage went up.

## 3. Hardware / knowledge — unchanged, re-confirmed

| | stated in §0 | measured |
|---|---|---|
| CPU | Ryzen 9 9955HX, 16c/32t, AVX-512 | unchanged |
| RAM | 59.5 GiB | unchanged |
| GPU | RTX 5070 Ti Laptop, 12GB, CUDA 13.2 | unchanged |
| tuning | `scripts/matrix.env` | unchanged |
| free disk | (not stated) | 108 G → **137 G** after D5 |

Benchmark and knowledge-state figures in §0 (47/48 headline, UNKNOWN_ACK 18/18, Wikidata
22/23, HDC ~2016, episodic 108, quarantine 1058+42, BIR 16, retrieval ~372–523 µs) were **not
re-measured in this wave** and are therefore carried forward as operator-stated, not as
verified-here. Stating that distinction is the point of this file: §0 labelled them
"VERIFIED", and a label should not outlive the verification.

## 4. The one number that matters most

```
32 skipped -> 0 skipped, 37 passed
```

The D1 guard was correct when written and incomplete in a way that was invisible, because a
skip looks like a non-event. It took a `/tmp` wipe to reveal that "28 failures became 32
skips" had become a permanent skip. Any capability claim that depended on those tests is now
upgraded from untested to tested.

## 5. Still open from the carried prompt

- **D3** — gitverse `master` (`de508931`, unrelated root, 2 files) is not aligned with
  `develop`. The permission layer denies force-push, and the mirror's default is already
  `main == develop`, so the practical risk is near zero. Exact commands for a human are in
  `docs-v2/quality/RECON-W33.1-runbook-D3-D5.md`.
- **D5** — complete. 29 GB reclaimed, `DISK-LEDGER` seq 179.
- **27 core failures** — the science-debt triage is in flight.
- **Release tag** — `v17.5.0-mind` does not exist yet.
