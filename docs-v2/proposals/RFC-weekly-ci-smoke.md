# RFC: Weekly CI Job for the Fresh-Clone Smoke Test

- **Status:** PROPOSED — awaiting operator approval
- **Date:** 2026-09-29
- **Author:** RECON-W28 remediation (B-8)
- **Blocks:** nothing. The campaign proceeds; only the automation is deferred.
- **FROZEN-zone note:** this RFC does **not** modify `.github/workflows/**`. It asks
  for permission to do so in a separate, separately-authorised change. Until then no
  file under `.github/` is touched, which the campaign has verified
  (`git diff f832ae1e HEAD -- .github/` is 0 lines).

## Problem

`scripts/fresh-clone-smoke.sh` is the only automated proof that MATRIX can be built
from a clean tree and answer a question. It has silently rotted: it bound port 8765
unconditionally, so running it would have killed the operator's live gateway; it
recursively copied previous smoke directories into itself; and its gateway launch
produced a classpath whose entries were fused by embedded newlines, so **every fresh
clone died with `ClassNotFoundException` while the build printed `BUILD SUCCESSFUL`**.

None of that was caught by a test, because the smoke test is a shell script and
nothing ran it. The W25 wave marked the two-node smoke as a *manual* verification
step, and manual verification steps do not get run.

W25's acceptance criteria asked for a **weekly CI job** running this smoke. That job
was never created, because `.github/workflows/**` is a FROZEN zone and creating it
requires an explicit RFC mandate. This document is that request.

## Proposal

Add `.github/workflows/weekly-smoke.yml`:

- **Schedule:** `cron: '17 4 * * 1'` (Mondays 04:17 UTC — deliberately off the hour,
  to avoid the top-of-hour scheduler stampede that delays most cron jobs).
- **Trigger:** `schedule` + `workflow_dispatch` for on-demand runs.
- **Runner:** `ubuntu-latest`, JDK 25 (matching the project's documented toolchain).
- **Steps:**
  1. checkout with full history (the script's evidence includes a diff base)
  2. cache the Gradle wrapper and `~/.gradle/caches`
  3. `bash -n scripts/fresh-clone-smoke.sh` (syntax gate, ~0 s, fails fast)
  4. `bash scripts/fresh-clone-smoke.sh` on a non-conflicting port (`:8799`)
  5. upload `data/smoke/**/data/mind/gateway.log` as an artifact **on failure only**
- **Budget:** the smoke took ~6 min cold on this host. Weekly cost is negligible.
- **No secrets required.** The smoke is fully local and offline; the gateway runs in
  `MATRIX_MODE=production` against a temp registry.

## Why a workflow and not a Gradle test

A JUnit test cannot run this: the failure modes are shell-level (rsync exclusion
semantics, `tr`-based classpath assembly, port binding, background-JVM readiness),
and the thing being verified is a *clean clone*, which is a different filesystem
state from the one the test JVM is running in. Shelling out from JUnit to `rsync`
the whole repo would be slower and less faithful than a dedicated job.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| The job is red for weeks because nobody triages it | `workflow_dispatch` + artifacts; owner is the maintainer, same as the existing test gate |
| Weekly run hides a regression for up to 7 days | This is a *supplement* to the PR gate, not a replacement. The prompt-level requirement is weekly; a PR-triggered run is deliberately out of scope for this RFC so the change stays minimal. |
| `rsync` unavailable on the runner | `rsync` is present on `ubuntu-latest`; the script already requires it and the syntax gate catches a missing binary early |
| The smoke needs more disk than the runner has | The smoke excludes `.venv` and `data/smoke*`; cold copy measured at 238 MB. Budget 5 GB to be safe. |
| Port 8799 in use on the runner | Job is isolated per-run on a fresh VM, so no conflict is possible |

## Why it was not simply created

`.github/workflows/**` is a FROZEN zone in the MATRIX constitution. The standing
rule is that FROZEN zones are not edited without an explicit RFC mandate from the
owner **in that session**. Creating the file unilaterally would have been the
cheapest way to make B-8 look closed, and it would have violated a constitutional
boundary to do it. The blocker is therefore dispositioned as *needs operator
approval*, with the RFC written so approval is a one-word reply.

## Approval requested

**Q-B (from the W28 brief): approve submission of this RFC now, with the workflow
edit deferred to a separate authorised PR? Default recommendation: yes.**

Operator action required: reply "approved" and this file is accompanied by the
workflow in a follow-up change. Until then, `.github/` stays byte-identical and the
smoke remains a documented manual step in `MIND-VALIDATION-CHECKLIST-v3.md`.
