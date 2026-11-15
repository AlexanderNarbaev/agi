# CI-SIMPLIFICATION-RFC — the owner-authorized narrowing of the CI lanes

**Status:** `ACTIVE` — being written by the DevEx/CI workstream of the EPISTEMIC-INTEGRITY
initiative.
**Date:** 2026-11-15 · **Owner authorization:** the directive of 2026-11-15, §4 (a narrowly scoped
CI simplification of `.github/workflows/**`; it does **not** amend the constitution, the
82 % method-coverage standard or any protected runtime invariant) and **OD-053** (the owner's
answer of 2026-11-15: the FAST PR lane's checks are required on `develop`).

**What this document will contain:** the measured "before" (per-workflow durations and the job
minutes per push, taken from the real GitHub runs), the four target lanes — FAST PR, LOCAL
AGENT, MANUAL/RELEASE and the credential-gated PUBLISH lane — the exact before/after workflow
changes, what moves from automatic to manual and what triggers each manual lane, the rollback,
the validation plan, and the honest cost accounting (what will be saved and what will not).
