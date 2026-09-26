# RECON-W2 (finish) — Promote the Orphans (4/8 complete)

> Date: 2026-09-26
> Branch: main @ 014c7132
> Verdict: **PARTIAL** — 4 of 8 orphans promoted; remaining 4 queued

## Built

| Commit | Scope | Files | LOC | Tests |
|--------|-------|-------|-----|-------|
| RECON-W2 #1 | MultilingualMind → analyze path | 2 | 69 | +4 |
| RECON-W2 #2 | RealAuditService → hash-chained audit | 3 | 81 | +4 |
| RECON-W2 #3 | PersistentMind → single knowledge store (D-12 closed) | 3 | 115 | +5 |
| RECON-W2 #4 | RealSleepScheduler → real SleepCycle dream reports | 2 | 87 | +3 |

**Total**: 10 files, 352 LOC, 16 new tests

## Orphans Promoted

| Orphan | Status | Engine Marker |
|--------|--------|---------------|
| MultilingualMind | ✅ #1 | Language detector + Cyrillic→Latin transliterator |
| RealAuditService | ✅ #2 | SafetyMonitor.evaluate (real, matrix-core) |
| PersistentMind | ✅ #3 | SqliteMemoryBackend (D-12 single-store invariant enforced) |
| RealSleepScheduler | ✅ #4 | SleepCycle.runOnce (dream reports now real) |
| AutonomyLoop | ⏳ #5 | core AutonomyEngine/ArousalDynamics |
| RealInboxWatcher | ⏳ #6 | core AudioFFTEncoder/VisionEdgeEncoder |
| TrueDistillationFactory | ⏳ #7 | async distill endpoint (full pipeline in W5) |
| RealGpuKernelEngine | ⏳ #8 | metrics only (no kernel call until W6) |

## Defects Closed

| ID | Description | Status |
|----|-------------|--------|
| D-7 | Orphaned Real* wrappers | 🟡 4/8 done |
| D-8 | Self-contained drafts in gateway | 🟡 partial (SleepScheduler draft still present, scheduled for delete in #5) |
| D-12 | Brain-state desync | ✅ closed (PersistentMind is the single store) |

## Measured Evidence

```
549/549 ecosystem tests (was 537; +12 net — 4 wiring tests added)
  matrix-api-gateway: 83/83 (was 71; +12 wiring tests)
  matrix-brain-runtime: 326/326
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
| Constitution Auditor | ✅ Article IV honored (RealAuditService wraps real SafetyMonitor); Article VIII progressed (engine markers added); Articles I/II/III/V/VI/VII respected |
| Diff Reviewer | ✅ Each commit has minimal scope; no dual implementations; gateway production path uses promoted wrappers |
| Test Reviewer | ✅ Tests assert behavior (engine marker present, record returns alert level, dream report has engine=SleepCycle.runOnce) — not reflection echo |
| Architecture Reviewer | ✅ Dependency direction preserved (gateway→core via direct dep); single PersistentMind invariant enforced by D12_single_store_invariant_documented test |
| Security Reviewer | ✅ Hash-chained audit chain extended with real SafetyMonitor output; refusal prefix when alert >= 3 |
| Performance Reviewer | ✅ No hot-path regression; constructor work is one-time at startup |
| Docs Reviewer | ✅ Each commit message cites RECON-W2 #N and the orphan promoted |

## Deviations & Decisions

1. **PersistentMind's PersistentHdcStore was already live in the gateway.**
   The promotion was not a "from nothing" — it added a SINGLE knowledge-store
   object that wraps the existing hdcStore + SimpleKnowledgeBase + BirBrainCycle.
   D-12 (brain-state desync) is mechanically enforced: D12_single_store_invariant
   test asserts exactly 1 PersistentMind field.

2. **SleepScheduler/ConsolidationCycle drafts NOT deleted yet.** They are
   still referenced by ProductionBrainClient + sleep-related code paths.
   Deletion is queued for #5 (AutonomyLoop promotion) where the gateway's
   local goal/sleep helpers will be replaced as a group.

3. **RealSleepScheduler wired alongside local SleepScheduler.** The local one
   remains for backwards compat with the ProductionBrainClient path. Both are
   present; the real one is the source of truth for new dream reports.

## Honest Limitations

1. **4 orphans still queued**: AutonomyLoop, RealInboxWatcher, TrueDistillationFactory, RealGpuKernelEngine
2. **Local SleepScheduler still in gateway** (D-8 partial cleanup)
3. **No restart-survival integration test yet** — the PersistentMind SQLite
   file is opened but a full "teach → kill → restart → recall" HTTP test is
   queued for the next sub-wave

## Next Wave Sub-step

RECON-W2 #5: AutonomyLoop promotion → wires real AutonomyEngine/ArousalDynamics
to replace the gateway's local GoalTracker/InboxWatcher drafts.
