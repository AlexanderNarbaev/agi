# Mission: MATRIX MIND REALIZATION (MIND-W1..W11)

> Active mission: transform MATRIX from infrastructure draft to living mind.
> Source: 2026-09-25 prompt (Section 0 verified against actual state)

## MIND-W1: Cognitive Orchestration Layer — [COMPLETED + FIXES]

### Goal
Build the conductor that turns orphan libraries into one mind loop.

### Sub-tasks
- [x] S1.1: Create matrix-brain-runtime Gradle module | commit 13df1171
- [x] S1.2: Implement BrcStep and MindResult DTOs | commit 13df1171
- [x] S1.3: Implement MindCycle class with full pipeline | commit 13df1171
- [x] S1.4: Implement arithmetic composition (2+3=5 without teaching) | commit 13df1171
- [x] S1.5: Implement analogy by HDC similarity transfer | commit 13df1171
- [x] S1.6: Wire ProductionBrainClient to call MindCycle.think() | commit 13df1171
- [x] S1.7: Write 15+ integration tests (delivered 22) | commit 13df1171
- [x] S1.8: Build, test, commit, push, merge to develop | commit 4470b56e / PR #16

### MIND-W1 review-fix items (Goal Guard cycle #1)
- [x] FIX: 2+2 sanity test
- [x] FIX: logic-puzzle test with confidence < 1.0
- [x] FIX: SignalStage dead conditional
- [x] FIX: BirInferenceStage operator-precedence bug
- [x] FIX: HdcRetrievalStage concurrent hazard
- [x] FIX: ProductionBrainClient hardcoded absolute path
- [x] FIX: MCTS misleading audit evidence
- [x] FIX: MindCycle bilingual docs

**PR #16 MERGED into develop @ 1374c480**

## MIND-W2: Persistent Mind — [COMPLETED]

### Goal
Replace in-memory KB with a persistent HDC store; teach/learn survive restart.

### Sub-tasks
- [x] S2.1: Create PersistentHdcStore (NDJSON-backed, no external deps) | PersistentHdcStore.java (311 lines)
- [x] S2.2: HdcRetrievalStage migrated to support persistent mode | HdcRetrievalStage.java (160 lines)
- [x] S2.3: PersistentHdcStore documents survive restart (load on construction) | verified by test
- [x] S2.4: LinkedHashMap contents preserves insertion order across reopens | verified by test
- [x] S2.5: Contradiction detection at write time (DUPLICATE/POTENTIAL_CONFLICT/NOVEL) | checkContradiction()
- [x] S2.6: ProductionBrainClient.teach() writes through to PersistentHdcStore | verified
- [x] S2.7: MATRIX_MIND_DIR env var configures storage location | MinimalHttpServer update
- [x] S2.8: 10+ persistence round-trip tests (delivered 11) | PersistentHdcStoreIntegrationTest

### PASS checklist
- [x] kill gateway -> restart -> previously taught facts still answer | `mindcycle_persistent_mode_recovers_taught_fact` test passes
- [x] contradiction demo test passes | 3 contradiction tests
- [x] persistence round-trip tests >= 10 | 11 tests delivered

**PR #18 MERGED into develop @ 4d510640**

## MIND-W3: Sleep & Consolidation Engine — [ACTIVE]

### Goal
Background scheduler that replays the episodic buffer, promotes frequent
patterns to semantic tier, runs Tsetlin induction to generate candidate
rules from repeated episodes, prunes low-utility vectors.

### Sub-tasks
- [ ] S3.1: Implement SleepScheduler (trigger: idle N minutes, manual POST /v1/sleep, or cron endpoint)
- [ ] S3.2: Implement EpisodicLog (append-only NDJSON file; entries = {query, response, ts, hdc_id})
- [ ] S3.3: Implement ConsolidationCycle: replay episodic -> promote frequent -> Tsetlin induction -> prune low-utility
- [ ] S3.4: Generate dream report (JSON/markdown: learned, merged, forgotten, contradicted)
- [ ] S3.5: Forgetting is graceful: tombstones + GDPR-compatible
- [ ] S3.6: Wire POST /v1/sleep endpoint to MinimalHttpServer
- [ ] S3.7: Expose last dream summary via /v1/status
- [ ] S3.8: 10+ sleep/consolidation tests
- [ ] S3.9: Build, test, commit, push, PR, merge to develop

### PASS checklist
- [ ] teach 50 related facts in noisy phrasings -> sleep -> recall works via canonical form
- [ ] storage shrinks after sleep (consolidation dedupes)
- [ ] dream report generated
- [ ] determinism test (same episodes -> same consolidated state)

## MIND-W4: Autonomy, Goals & Stimuli — [QUEUED]

### Sub-tasks
- [ ] S4.1: Activate AutonomyEngine + GoalTracker + ArousalDynamics
- [ ] S4.2: Idle self-probing cycles (curiosity drive, mini-benchmarks)
- [ ] S4.3: Stimulus intake: file-watch on data/inbox/
- [ ] S4.4: Self-initiated actions logged to audit (FROZEN filter applies)
- [ ] S4.5: /v1/status exposes uptime cycles, active goals, arousal level, pending consolidations
- [ ] S4.6: 10+ tests

## MIND-W5: Distillation Factory — [QUEUED]

### Sub-tasks
- [ ] S5.1: ModelToMatrix pipeline (ONNX -> HDC codebook + BIR clauses + Tsetlin automata)
- [ ] S5.2: Merge semantics: append + resolve into single live matrix
- [ ] S5.3: Multi-source ingestion (BoolQ/LogiQA/CLUTRR + small ONNX models)
- [ ] S5.4: Distillation ledger (docs-v2/research/DISTILLATION-LEDGER.md)
- [ ] S5.5: CI guard: zero imports of io.matrix.api LLM classes from runtime paths

## MIND-W6: GPU Acceleration — [QUEUED]

## MIND-W7: Audit/Billing/Federation wired for real — [QUEUED]

## MIND-W8: Multilingual Mind — [QUEUED]

## MIND-W9: Hygiene + Docs + Showcase — [QUEUED]

## MIND-W10: Research Engine & Self-Extension — [QUEUED]

## MIND-W11: Grand Validation (release v16.0.0-mind) — [QUEUED]

---

## Section 0 Verification — Discrepancies Logged (resolved)

| Claim | Actual |
|-------|--------|
| main @ d4791443 | main @ 10da39a7 (cherry-pick merge landed) |
| 137k LOC matrix-core | 45.5k LOC in src/ |
| 161 LLM-residue files | 67 files (broader grep) |
| AuditResource in-memory | confirmed (ConcurrentLinkedDeque) — D-2 to be closed in W7 |
| matrix-fpga/micro/ros2 empty | confirmed — D-4 to be closed in W9 |
| PG/Redis on host | confirmed (5432/6379) — D-2/D-7 to be closed in W7 |
| No git tags | confirmed — D-5 to be closed in W9 |
| Native binary 126MB | confirmed (131860736 bytes) |
| Duplicate docs | confirmed (ecosystem/ vs ru/ecosystem/) — D-1 to be closed in W9 |

## Current Pipeline State

- main @ 10da39a7 (cherry-pick + W1500 + production brain)
- release/v1.0 @ 678b7091
- develop @ 4d510640 (MIND-W1 + MIND-W2 merged)
- PR #18 (MIND-W2 → develop) MERGED
- Next: PR develop → release/v1.0 → main
