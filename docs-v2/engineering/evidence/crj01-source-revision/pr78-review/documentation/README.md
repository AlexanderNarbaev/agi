# Source revision measurement publication

Code PR #78 merged normally at b226a5e45cd5e8fecb6a7bad0c3980ec8122e3f4.
The merged tree equals tested PR head f68ec498a6a6a50a34eecf0653a64385ed9d6c78.
The independent evaluator used that exact head. The documentation-only PR #79
head is c1c0f5d79baccc7393634c51b1f025e7f7a857ad. The independent coordinating
AI reviewed the scoped prose, registry changes and roadmap status.
The final reviewed diff SHA-256 is
8260d6cf083ce732a8a7daa54f08b5d4e9a7c1727590f994f7f6792c32690aaa.

Verified from the isolated worktree:

- git diff --check b226a5e45cd5e8fecb6a7bad0c3980ec8122e3f4...HEAD
- python3 scripts/docs-link-check.py --root . --strict
- python3 scripts/validate-current-state.py --root . --strict
- python3 scripts/validate-project-registry.py --root . --strict

The three strict validators took 1.29 seconds wall and 1.29 seconds CPU from
their retained time logs. The link checker reported zero strict failures.
Current-state contradictions were zero and registry defects were zero.
Documentation publication incurred zero provider calls and no local Gradle run.

The scoped Chain A measurement is PRODUCT_GAIN. The docs-only publication is
ENGINEERING_MAINTENANCE. Historical failure records remain indexed and the
registry operational classification remains EXPERIMENTAL. The evaluator
keeps the sealed fixtures and gold.

The final Fast PR run 45202078478 and Pages run 45202078580 passed.
The exact-head normal squash merge integrated documentation at
aa3fe6b2d75651005daab5585e697af8d90a3010 on 2026-11-23 15:12:56 UTC.
The reviewed documentation tree and verified fetched develop tree are identical.
