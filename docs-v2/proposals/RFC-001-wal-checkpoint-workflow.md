# RFC-001: WAL Checkpoint GitHub Actions Workflow

## Status
**REJECTED — owner decision 2026-09-18**

## Date
2026-09-18

## Author
Wave owner (auto-generated from W347-W351 staging session)

## Summary
Propose adding `.github/workflows/wal-checkpoint.yml` to automatically validate
wave checkpoint integrity on every push to `main` and `feature/*` branches.

## Status: REJECTED (2026-09-18)

**Owner decision:** Manual process preferred. Per AGENTS.md wave-commit rule,
every wave must be committed AND pushed manually to BOTH remotes (`origin` +
`gitverse`). No CI automation. RFC retained for historical record only.

Replacement rule (effective 2026-09-18):
1. After every wave: `git add -A && git commit -m "WAL: W<NUM> — <description>"`
2. Push to BOTH remotes: `git push origin main && git push gitverse main`
3. Update `SESSION.md` at project root with latest wave entry + previous link
4. Commit + push `SESSION.md` as part of the wave commit

This rule eliminates the need for any GitHub Actions workflow.


## FROZEN Zone Notice
**This RFC is required because `.github/workflows/**` is a FROZEN zone per `AGENTS.md`.**
Any workflow file added to that path requires explicit owner RFC mandate.

## Motivation
Per `SPEC-014 §Phase 4: Manual Checkpoint Process` (current rule, effective 2026-09-18):
- All wave work is committed and pushed manually at end of each wave session.
- `docs-v2/waves/wave-NNN-*.md` is created with actual git commit hash.
- `WAL.md` "Last WAL" pointer is updated.

A GitHub Actions workflow could provide **read-only validation** that:
1. Every wave file has a valid git commit hash (not `<pending>`).
2. The `Last WAL` pointer in `WAL.md` matches the latest wave file.
3. No `wave-NNN-*.md` references a future wave number.

## Proposed Workflow (read-only validation, NO auto-commits)
```yaml
name: WAL Checkpoint Validation
on:
  push:
    branches: [main, 'feature/*']
  pull_request:
    branches: [main]

jobs:
  validate-wave-checkpoints:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - name: Validate wave checkpoint hashes
        run: |
          for f in docs-v2/waves/wave-*.md; do
            grep -q '<pending>' "$f" && {
              echo "ERROR: $f contains <pending> hash"
              exit 1
            }
          done
      - name: Validate Last WAL pointer
        run: |
          LAST=$(grep -oP 'Last WAL: \K\d+' WAL.md | head -1)
          [ -f "docs-v2/waves/wave-${LAST}-"*.md ] || {
            echo "ERROR: WAV $LAST referenced but file not found"
            exit 1
          }
```

## Why This Differs From the Original Proposal

The original SPEC-014 §Phase 4 proposed using `stefanzweifel/git-auto-commit-action@v5`
which would **automatically create commits on push**. This was rejected because:

1. **FROZEN violation risk:** Auto-commits could push secrets or unwanted state.
2. **Owner visibility:** Auto-commits reduce owner control over what enters the repo.
3. **Silent drift:** Auto-commits can mask intentional or unintentional changes.

The new proposal is **read-only validation only** — it fails the CI if checkpoints
are missing/inconsistent, but does NOT create commits.

## Acceptance Criteria (if approved)
1. RFC owner approval recorded below.
2. Workflow file created at `.github/workflows/wal-checkpoint.yml`.
3. CI test confirms workflow runs on existing wave files.
4. CI test confirms workflow fails on a deliberately broken wave file.

## Alternatives Considered
- **Status quo (manual process only):** Currently in effect. RFC proposes no change unless approved.
- **Local pre-commit hook instead of CI:** Runs on developer's machine, not in CI. Doesn't catch pushes that bypass hooks.
- **External validation service (e.g., Dependabot):** Adds dependency. Out of scope.

## Owner Decision

- [x] **REJECTED** — Manual process remains in effect; no CI workflow added

**Decision date:** 2026-09-18
**Decided by:** Project owner (Alexander Narbaev)
**Notes:** Owner reviewed RFC and determined that the existing wave-commit rule
(commit + push to both remotes per wave) is sufficient and simpler. CI automation
introduces unnecessary complexity and FROZEN-zone risk. Manual process is the
chosen path forward.

## References
- `AGENTS.md` §FROZEN zones (`.github/workflows/**`)
- `SPEC-014 §Phase 4: Manual Checkpoint Process` (current rule)
- `docs-v2/waves/SESSION-W347-W351-summary.md` (canonical wave numbering)
- `docs-v2/proposals/PROPOSAL-wal-split-wave-based-architecture.md` (this RFC's origin)
