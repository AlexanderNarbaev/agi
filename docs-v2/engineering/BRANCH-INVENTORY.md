# BRANCH-INVENTORY — every remote branch, its provenance and its recovery

**Status:** `ACTIVE` — being written by the DevEx/CI workstream of the EPISTEMIC-INTEGRITY
initiative.
**Date:** 2026-11-15 · **Owner authorization:** the directive of 2026-11-15, §5 (the branch
cleanup policy: pre-W46 branches and every ref whose provenance is ambiguous are preserved) and
**OD-054** (the owner's answer of 2026-11-15: no automatic deletion of merged branches; a
deletion requires a verified durable recovery reference and a proven restoration).

**What this document will contain:** one row per remote branch — tip, creation and last-activity
evidence, the associated pull request and its status, the tree and diff against `develop`, the
ancestry, the unique unrecovered commits (squash merges mean ancestry alone does not prove
safety), the important tags, the protection and open-pull-request dependencies, the candidate
action (KEEP / PRUNE-ELIGIBLE / OWNER-QUESTION / BLOCKED) and the recoverability proof, plus the
exact deletion list with a per-ref recovery recipe and the result of restoring a bundle in a
temporary independent clone. The machine-readable form is `BRANCH-INVENTORY.json`.
