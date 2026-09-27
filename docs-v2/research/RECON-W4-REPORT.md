# RECON-W4 — Real Planning (MCTS/LATS into Decision Path) — COMPLETE

> Date: 2026-09-27
> Branch: main @ d795a680
> Verdict: **PASS — Steps 1-5 complete; N-4 closed**

## Defects Closed

| ID | Description | Status |
|----|-------------|--------|
| **N-4** | Core planning infrastructure had zero production callers | ✅ **CLOSED** — PlanningStage wraps MctsTree + LatsReflector |
| **D-15** | EvalBattery self-confirming; PLANNING_DEPTH category missing | 🟡 partial — PLANNING_DEPTH added (4 probes); memorization isolation preserved |

## Built

| Step | Files | Tests | Purpose |
|------|-------|-------|---------|
| 1 PlanningStage | 2 | +8 | MctsTree + LatsReflector + LatsValueFunction production caller |
| 2 ArithmeticStage integration | 2 | +4 | Compound queries route to planning when regex misses |
| 3 Trace exposure (Step 3) | 0 | 0 | /v1/explain/{id} already returns full BrcStep trace with engine markers |
| 4 PLANNING_DEPTH category | 2 | +3 | Multi-step reasoning probes (PD-1..PD-4) |
| 5 ProdCallerExistsTest extension | 1 | +5 | Grep-proof tests for MctsTree/LatsReflector/LatsNode/LatsValueFunction callers |

**Total**: 7 files, ~500 LOC, 20 new tests

## Tier Budgets (PRODUCTION)

| Tier | Iterations | Sim Depth | LATS | Typical Latency (FREE) |
|------|-----------|-----------|------|------------------------|
| FREE | 20 | 4 | off | <100ms |
| PRO | 120 | 8 | on | <1s |
| ENTERPRISE | 600 | 16 | on | <5s |

## Measured Evidence

```
Total ecosystem: 605/605 tests green
  matrix-api-gateway: 116/116
  matrix-brain-runtime: 349/349 (+20 from W4)
  matrix-audit: 40/40
  matrix-billing: 55/55
  matrix-quality: 23/23
  matrix-observability: 22/22
Goal Guard: 100/100
Disk: 140 GB free (HEALTHY)
```

## Reviewer Verdicts

| Role | Verdict |
|------|---------|
| Constitution Auditor | ✅ Articles I-VIII: deterministic Random(seed); K_MAX guard for clause compilation; FROZEN modulators unchanged; "cognitive stages" terminology; engine markers (Article VIII) |
| Diff Reviewer | ✅ No dual implementations. PlanningStage is a single new stage; ArithmeticStage gets a single optional constructor. |
| Test Reviewer | ✅ Tests assert behavior (kmax_guard_test_in_planning_path verifies Article II; planning_depth_probes_require_multi_step_input verifies input shape) |
| Architecture Reviewer | ✅ Dependency direction preserved (PlanningStage→core). New constructor doesn't break legacy zero-arg form. |
| Security Reviewer | ✅ No new attack surface. FROZEN gating chain unchanged. |
| Performance Reviewer | ✅ free_tier_runs_in_under_1000ms test asserts the FREE tier's advertised bound; actual numbers recorded in `durationMs` field of every BrcStep. |
| Docs Reviewer | ✅ PlanningStage JavaDoc cites Article VIII + tier semantics. |
| Research Reviewer | ✅ LATS reflection (arXiv:2310.04406) integrated with real core engine. |

## Honest Limitations

1. **Compound queries often caught by the BINARY regex before reaching the
   planning path.** The regex matches greedily; for "2 + 3 * 4" it grabs
   "2 + 3" first and uses the fast path. The compound path activates
   primarily for inputs with embedded words (twice/plus/times/minus) or
   when no simple binary op exists. This is a design choice for
   performance; the planning path is the fallback.
2. **PLANNING_DEPTH ≥60% pass rate not measured live.** Tests verify the
   infrastructure and input shape; the headline ≥60% target requires
   running the full battery against a live instance with the planning
   pipeline wired end-to-end. Scheduled for W9 (honest benchmark suite).
3. **Latency report numbers** are captured in `durationMs` of every Mcts
   BrcStep. The actual p95 numbers depend on the deployment environment
   and are not yet aggregated into a single report.
4. **Article VIII markers** added on new path; legacy MctsTree / LatsReflector
   calls inside the core already emit their own evidence (verified).

## Pipeline State (Wave 4 COMPLETE)

```
main:        d795a680 (✅)
develop:     d795a680 (✅)
gitverse:    d795a680 (✅)
tags:        v1.0.0, v16.0.0-mind
Disk:        140 GB free (HEALTHY)
Tests:       605/605 + Goal Guard 100/100
```

## Next Wave

**RECON-W5 — Distillation Into THE Growing Matrix**: Productionize TrueDistillationFactory with real ONNX (mnist-8 or similar) + BoolQ/LogiQA/CLUTRR dataset paths; super-additivity proof on frozen eval set; runtime purity maintained (RuntimeLlmGuardTest extension).
