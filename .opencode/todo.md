# Mission: MATRIX MIND REALIZATION (MIND-W1..W11)

> Active mission: transform MATRIX from infrastructure draft to living mind.
> Source: 2026-09-25 prompt (Section 0 verified against actual state)

## MIND-W1: Cognitive Orchestration Layer — [COMPLETED + FIXES]

### Goal
Build the conductor that turns orphan libraries into one mind loop.
New Gradle module `matrix-brain-runtime` with `MindCycle` class implementing
the full pipeline per request.

### Sub-tasks
- [x] S1.1: Create matrix-brain-runtime Gradle module (depends on matrix-core, matrix-audit, matrix-observability) | commit 13df1171
- [x] S1.2: Implement BrcStep and MindResult DTOs (stage, fired, confidence, evidence[]) | commit 13df1171
- [x] S1.3: Implement MindCycle class with full pipeline (reflex -> signal -> salience -> [HDC|BIR|Tsetlin] -> MCTS -> modulators -> trace) | commit 13df1171
- [x] S1.4: Implement arithmetic composition (2+3=5 without teaching) | commit 13df1171
- [x] S1.5: Implement analogy by HDC similarity transfer (A:B :: C:?) | commit 13df1171
- [x] S1.6: Wire ProductionBrainClient to call MindCycle.think() | commit 13df1171
- [x] S1.7: Write 15+ integration tests covering each stage (delivered 22) | commit 13df1171
- [x] S1.8: Build, run tests, commit, push to both remotes, merge to develop | commit 4470b56e

### MIND-W1 review-fix items (Goal Guard cycle #1)
- [x] FIX: Add 2+2 sanity test (Phase 1.3 acceptance)
- [x] FIX: Add logic-puzzle test with confidence < 1.0 (Phase 4.5 acceptance)
- [x] FIX: SignalStage dead conditional (`List.of(tokens).get(0) == null`)
- [x] FIX: BirInferenceStage operator-precedence bug in math-neg
- [x] FIX: HdcRetrievalStage concurrent hazard (LinkedHashMap vs ConcurrentHashMap)
- [x] FIX: ProductionBrainClient hardcoded /home/alexandr-narbaev path
- [x] FIX: MCTS misleading audit evidence (reuses Tsetlin reply) — make honest
- [x] FIX: Add docs-v2 bilingual coverage of MindCycle (10-stage pipeline + BRC trace)

## MIND-W2: Persistent Mind — [ACTIVE]

### Goal
Replace in-memory KB with SqliteMemoryBackend + PersistentHierarchicalMemory.
Three tiers: episodic log (append-only NDJSON) → semantic HDC store → procedural rule base.

### Sub-tasks
- [ ] S2.1: Create matrix-persistence module with SqliteMemoryBackend (JDBC, no external deps)
- [ ] S2.2: Implement HierarchicalMemory tier (episodic / semantic / procedural) backed by SQLite
- [ ] S2.3: Persist SimpleKnowledgeBase documents across restart (durability)
- [ ] S2.4: Persist Tsetlin clauses across restart (procedural rules)
- [ ] S2.5: Contradiction detection at write time (CONSISTENCY_CHECKER reuse)
- [ ] S2.6: Wire teach/learn to write through to SQLite
- [ ] S2.7: Audit events flow to the same durable store
- [ ] S2.8: 10+ persistence round-trip tests (teach, kill, restart, recall)

### PASS checklist
- [ ] kill gateway -> restart -> previously taught facts still answer
- [ ] contradiction demo test passes
- [ ] persistence round-trip tests >= 10

## Section 0 Verification — Discrepancies Logged (resolved)

| Claim | Actual |
|-------|--------|
| main @ d4791443 | main @ 10da39a7 (cherry-pick merge landed) |
| 137k LOC matrix-core | 45.5k LOC in src/ |
| 161 LLM-residue files | 67 files (broader grep) |
| AuditResource in-memory | confirmed (ConcurrentLinkedDeque) |
| matrix-fpga/micro/ros2 empty | confirmed |
| PG/Redis on host | confirmed (5432/6379) |
| No git tags | confirmed |
| Native binary 126MB | confirmed (131860736 bytes) |
| Duplicate docs | confirmed (ecosystem/ vs ru/ecosystem/) |
