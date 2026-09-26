# RECON-W3 Part A — Truthfulness Surgery — COMPLETE

> Date: 2026-09-27
> Branch: main @ 1bcac12b
> Verdict: **PASS — N-1 and N-2 closed permanently**

## Defects Closed

| ID | Description | Status |
|----|-------------|--------|
| N-1 | Dual instances: drafts masquerading as production | ✅ **CLOSED** — drafts deleted; SingleInstanceGuardTest enforces |
| N-2 | /v1/distill fake success (future-tense claim) | ✅ **CLOSED** — endpoint returns honest not-implemented |

## Built

| Commit | Scope | Files | LOC | Tests |
|--------|-------|-------|-----|-------|
| RECON-W3 Part A #1 | N-1/N-2 fixes + 3 guard tests | 4 | ~210 | +12 |
| RECON-W3 Part A #2 | Delete draft SleepScheduler | 2 | -40 | (refactor) |
| RECON-W3 Part A #3 | Delete draft GoalTracker/ConsolidationCycle | 11 | -220 | (-35 deleted-draft tests) |

**Net**: 17 files, ~-50 LOC, 12 new guard tests + 35 draft tests removed

## New Mechanical Guards (Article VIII)

1. **SingleInstanceGuardTest** (4 tests): structural test asserting handlers invoke the
   SAME object type that was constructed as "promoted". Prevents N-1 recurrence.
   Asserts `draftRefs == 0` for `sleepScheduler.triggerNow` (the canonical simulacrum).
2. **NoFutureClaimsTest** (3 tests): source-level scan ensures no endpoint returns
   synthetic 'distilled' JSON; requires honest 'not-implemented' + planned-wave markers.
3. **ProdCallerExistsTest** (5 tests): each promoted engine (RealSleepScheduler,
   AutonomyLoop, PersistentMind, RealAuditService, RealInboxWatcher) must have
   ≥1 method call in decision path, not just constructor.

## Production Wiring Truth Table

| Engine (promoted) | Constructor | Decision-path caller | Engine marker in JSON |
|---|---|---|---|
| RealSleepScheduler | ✅ always | handleSleep.triggerNow(), handleStatus.lastDream() | "SleepCycle.runOnce" |
| AutonomyLoop | ✅ when prod brain loaded | handleGoals.proposeGoal(), handleStatus.engine() | "AutonomyEngine.proposeGoal" |
| PersistentMind | ✅ when prod brain loaded | initialized once at startup | "SqliteMemoryBackend" |
| RealAuditService | ✅ always | analyze path .record() | "SafetyMonitor.evaluate" |
| RealInboxWatcher | ✅ always | startup .scan() | "AudioFFTEncoder" / "VisionEdgeEncoder" |
| RealGpuKernelEngine | ✅ always | GET /v1/gpu .snapshot() | "GPU" / "CPU" / "unavailable" (honest) |
| TrueDistillationFactory | ✅ always | POST /v1/distill (returns honest stub) | "RECON-W5" (planned) |

## Measured Evidence

```
Total ecosystem: 547/547 tests green (was 570 → -35 deleted-draft tests + 12 new = -23)
  matrix-api-gateway: 116/116 (+12 new wiring tests)
  matrix-brain-runtime: 291/291 (-35 deleted-draft tests; only tests of drafts removed)
  matrix-audit: 40/40
  matrix-billing: 55/55
  matrix-quality: 23/23
  matrix-observability: 22/22
Goal Guard: 100/100
Disk: 141 GB free (HEALTHY)
```

## Reviewer Verdicts

| Role | Verdict |
|------|---------|
| Constitution Auditor | ✅ Articles IV, VII, VIII honored. No draft shadows the promoted engine in any handler. |
| Diff Reviewer | ✅ Each commit has minimal scope; old drafts removed in same commit as promotion; no orphaned imports. |
| Test Reviewer | ✅ SingleInstanceGuardTest is structural (asserts structure not implementation echo). NoFutureClaimsTest scans source for forbidden patterns. ProdCallerExistsTest asserts regex presence. |
| Architecture Reviewer | ✅ Dependency direction preserved. handleGoals routes through autonomyLoop → matrix-core/goals/GoalTracker (correct upward dependency). |
| Security Reviewer | ✅ RealAuditService routes through real SafetyMonitor. No security regression. |
| Performance Reviewer | ✅ No hot-path regression. Draft deletion reduces memory by ~3 small classes. |
| Docs Reviewer | ✅ Comments in MinimalHttpServer mark "RECON-W3 Part A: real engine is always non-null". |
| Research Reviewer | (n/a — Part A is cleanup, not research) |

## Honest Limitations

1. **35 tests removed** — these were testing the deleted drafts. No regression in mind
   behavior; the tests exercised code that no longer exists. New tests for the promoted
   engines exist (RealSleepSchedulerWiringTest, AutonomyLoopWiringTest, etc.).
2. **EpisodicLog still in gateway** (not a draft, used by realSleepScheduler indirectly).
3. **handleStatus fallback logic** still has some defensive null checks; could be tightened
   in a follow-up but doesn't violate truthfulness since the real engine is checked first.
4. **autonomyLoop.goals()** may return null in stub mode; handleGoals returns 503 then
   (honest, not synthetic).

## Next Wave: RECON-W3 Part B — Close the Learning Loop

- EpisodeFeatureExtractor: episodic log → bit-vector features via PersistentHdcStore.hashToVector
- RuleInductionEngine: train TsetlinTrainer.trainBatch + MpdtGaProducer.trainBatch in parallel,
  select best by fidelity, emit Bir object with provenance (seed, episodeRange, fidelity)
- BirRegistry + BirInferenceStage rewrite: real BooleanRuntime.evaluate over registered rules
- Sleep consolidation: replay → induction → CONSISTENCY_CHECKER gate
- EvalBattery GENERALIZATION ≥70% target (transitivity demos, cross-lingual)
- Restart-survival integration test (D-13 closure)
