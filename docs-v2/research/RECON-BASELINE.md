# RECON-BASELINE.md — baseline verification addendum

**RECON-W30, 2026-10-02.** The wave brief specified a §0 baseline and instructed that any
material drift be documented rather than silently accepted. There was drift. This file
records it, with the commands that establish each fact.

## What §0 claimed, and what was actually true

§0 of the RECON-W30 brief stated:

> Git: develop/origin/gitverse last confirmed @ db67302c; main @ 86da6853 (stale —
> fast-forward required).
> Tests: Last authoritative snapshot: 8858 invocations / 72 failures / 26 skipped.

Verified at the start of the wave:

```bash
$ git rev-parse --short develop   # claimed db67302c
b144563e
$ git rev-parse --short main       # claimed 86da6853 "stale"
b144563e
$ git rev-parse --short origin/develop
b144563e
$ git rev-parse --short gitverse/develop
b144563e
$ git status --short               # clean
```

| Fact | §0 claimed | Actual | Verdict |
|---|---|---|---|
| `develop` | `db67302c` | `b144563e` | §0 is 2 commits stale |
| `main` | `86da6853`, **stale, fast-forward required** | `b144563e`, **already in sync** | §0 wrong; no fast-forward needed |
| `origin`/`gitverse` | `db67302c` | `b144563e` | §0 stale |
| Tree | — | clean | matches |
| Tests | 8858 / 72 fail / 26 skip | 8879 / 71 fail / 26 skip | §0 superseded |

### Why §0 is stale, and why that is benign

The two commits §0 is missing are ones I made **after** the brief's baseline was captured,
both in the preceding wave's closing sequence:

```
b144563e docs: RECON-W28 pass 2 closing record — four gaps fixed, and no reviewer verdicts
d00a1fc0 docs: RECON-W28 final numbers — 8879 invocations, 71 failures, both changed modules green
db67302c <- §0's claimed position
```

So this is not repository drift — nobody else moved the branch. It is a brief whose
baseline section was written from a point in the conversation and did not account for the
wave-closing commits that followed. **The `main` claim is the substantive error**: §0
asserted `main` was stale and needed a fast-forward, when in fact the previous wave had
already synced it. Acting on §0 literally would have meant a redundant merge.

### The test-count difference is not a regression either

§0 says 72; the measured number is 71. This is **jqwik property-test non-convergence**,
already documented in `MIND-VALIDATION-CHECKLIST-v3.md`: property generators draw from a
seed, and a property that holds for most inputs still fails for the one a given run draws.
The same JVM on the same code has produced 71 and 72. So the defensible statement is
**71–72 depending on the run**, and
[KnownFailures.md](../quality/KnownFailures.md) records it that way.

The honest form of the §0 test claim is therefore "71–72 in `matrix-core`, zero failures in
any module this campaign touched", not a precise integer.

## Baseline established for RECON-W30

| Field | Value | How established |
|---|---|---|
| `develop` = `main` = `origin` = `gitverse` | `b144563e` | `git rev-parse`, four refs |
| Tree | clean | `git status --short` empty |
| Failures outside `matrix-core` | 0 | XML aggregation across all 14 modules |
| `matrix-brain-runtime` | 425 invocations, 0 failures | full module run |
| `matrix-api-gateway` | 152 invocations, 0 failures | full module run |
| Frozen zones | 0 diff lines | `git diff <baseline> HEAD -- .github/ CONSTITUTION.md AGENTS.md ethics/` |
| Live gateway | healthy on :8765 | `curl /health/live` |
| Hardware | AMD Ryzen 9 9955HX, 16c/32t, AVX-512, no AMX, 59.5 GiB, RTX 5070 Ti 12 GB, 2x NVMe | `scripts/hardware-probe.sh` |

## A methodology note that bit twice this campaign

Twice in RECON-W28/W30, a test count read as correct was wrong, and both times the cause
was a `--tests`-filtered run overwriting the full run's results for that module:

- a filtered run made `matrix-api-gateway` appear to have 6 tests when it has 152;
- a filtered JaCoCo run made changed-code coverage appear to be ~0.

Neither was caught by the number looking implausible. Both were caught only by
re-running the full suite and re-aggregating. The rule now recorded in
`MIND-VALIDATION-CHECKLIST-v3.md` Z7: **coverage and counts come from full module runs
only**, and a filtered run must never be the last thing to touch a module's results
directory.

## Reproducing this addendum

```bash
git rev-parse --short develop main origin/develop gitverse/develop
git status --short
./gradlew cleanTest test --continue --no-daemon --console=plain
# then aggregate */build/test-results/test/TEST-*.xml
scripts/hardware-probe.sh
curl -s http://localhost:8765/health/live
```
