# WAL

**Статус: ephemeral.** Переписывается в конце каждой сессии.

## Активный фокус

Полная пересборка документации (v2 rebuild 2026-08-26): новая структура `docs-v2/` (SpecDriven, без историчности), singleton FROZEN CONSTITUTION/AGENTS, INDEX на docs-v2/. Старые документы идут в `docs-v2/archive/2026-08-pre-v2/`.

## Правила сессии

- Singleton FROZEN: `CONSTITUTION.md`, `AGENTS.md` (singleton normative, перезаписываются вместе).
- Все ссылки в новых документах — только на docs-v2/.
- Без историчности и запрещённых формулировок (AGI и т.п.).

## Что сделано (v2 rebuild 2026-08-26)

- `README.md`, `CONSTITUTION.md`, `AGENTS.md` — переписаны singleton.
- `docs-v2/INDEX.md` — единая навигация.
- `docs-v2/architecture/{OVERVIEW,MODULES,RUNTIME-TOPOLOGY,FORMAL-CONTRACTS}.md` — 4 файла.
- `docs-v2/specifications/{INDEX,SPEC-000,001,002,002-quantum,003}.md` — 6 файлов.
- `docs-v2/designs/DESIGN-{01..15}.md` — 15 файлов.
- `docs-v2/research/{HYPOTHESES,PROTOCOL}.md` + `reports/{EXP-002,003,009,010}-report.md` — 6 файлов.
- `docs-v2/engineering/{PLAN,INVARIANTS,STANDARDS-MATRIX,JMH-GATE-EVIDENCE,SDD-COVERAGE,RELEASE-NOTES}.md` — 6 файлов.
- `docs-v2/operations/{RUNBOOK,DEPLOYMENT}.md` — 2 файла.

Итого: 1 README + 2 singleton + 1 INDEX + 4 architecture + 6 specs + 15 designs + 6 research + 6 engineering + 2 operations = **43 файла** в новой структуре.

## Следующее действие

- Архивировать старые документы через `git mv` → `docs-v2/archive/2026-08-pre-v2/`.
- SDD-свип: спеки для топ-`needs-spec` (`reasoning/`, `mediator/`, `hades/`, `memory/`, `rag/`).
- Эксперименты на доменных корпусах (восстановить `models/training_data/` из git-истории точечно).

## Известные проблемы

- GPU EP: «Failed to find CUDA shared provider» в Java-ONNX; нужен системный CUDA 12 + cuDNN9 для onnxruntime_gpu.
- Kafka integration test: флейк метаданных брокера на медленном хосте.
- TLA+-спеки отсутствуют для топ-пакетов (`reasoning`, `mediator`, `hades`, `memory`, `rag`) — в `architecture/FORMAL-CONTRACTS.md` next-format-contracts.
## RUN 9.5 — 2026-09-04 18:18 — TRAINING FIX

### Root cause
`BitLinearTrainer.flippedTable()` had a semantics bug. It cleared cells
where bit `flippedBit` was 0, but `findBestFlip` chooses bit X by scoring
`tt.evaluate(cell ^ (1 << X))` — completely different semantics.

### Fix
Replace clear-cells-where-bit-0 loop with proper flip-bit semantics:

```java
for (int cell = 0; cell < cells; cell++) {
    if (original.evaluate(cell ^ flipMask)) newTable.set(cell);
}
```

### Verification
- `/v1/chain-debug/neuron` shows DIFFERENT hashes after training (n=50,
  n=100, n=200 all changed)
- `/v1/generate` output CHANGES after training: "Clement" disappears
  from "The capital of France is..." output
- ChainTrainerEndpoint now logs "N flipped, N written, M actually
  changed" where M > 0 (typically ~91% of flipped neurons actually
  changed)

### Commits
- `e300c353` WAL: RUN 9.5 — fix flippedTable bug in BitLinearTrainer (CRITICAL)
- `9e149d23` WAL: RUN 9.5 demo — training actually affects generation output

### Chain state
- Density: 27.6% (RUN 9) → 46.2% (RUN 9.5, after multiple training runs)
- 449 empty neurons / 21,960 total
- AutoTrainer disabled at boot via `MATRIX_AUTO_TRAIN_ENABLED=false`

## RUN 11 — Negative sampling + audit fixes (2026-09-04 22:21)

### What changed
- `LmHead.update(chainOutput, token, nNegatives)` — added negative sampling. Default K=5 in `LmHeadTrainer.train()`. For each positive (fingerprint, token) update, K random other tokens are decremented for the same fingerprint. Prevents mode collapse on common tokens (e.g., `:`).
- `LmHeadTrainer.train(limit, epochs)` now delegates to `train(limit, epochs, 5)`.
- `LmHeadTrainer.trainOne(q, a)` now delegates to `trainOne(q, a, 0)`.

### Audit findings fixed (Goal Guard review cycle #0)
1. Doc-vs-code lie: removed dangling `{@link #updateConcurrent}` reference in `LmHead.update()` Javadoc.
2. Thread-safety regression: restored `synchronized (tw)` blocks in `update`, `score`, and `save` after RUN 11's first pass had stripped them.
3. Non-deterministic RNG: replaced `new Random(targetToken * 31L ^ System.nanoTime())` with `new Random((long) targetToken * 0x9E3779B97F4A7C15L)` — deterministic across runs, no wall-clock in decision path (AGENTS.md compliant).

### Verification
- `./gradlew :matrix-core:test --rerun-tasks --tests "io.matrix.api.LmHeadTest"` → 7/7 PASS in 0.281s
- 2 new tests: `negativeSamplingAddsNegativeTokensToVocab`, `negativeSamplingPreservesPositiveSignal`
- 5 prior tests still green

### Honest framing
Negative sampling is small and tested at the unit level. Full-bench re-measure (HellaSwag / ARC-Easy with new LM head scoring) is NOT in this RUN — that's a future scope.

## RUN 11 — 2026-09-04 22:21 — LM head negative sampling

### Goal Guard cycle #0 (957557e3)
The Goal Guard plugin auto-applied three audit fixes before this commit:
- Doc-vs-code lie removed
- Thread-safety regression fixed (synchronized restored)
- Wall-clock RNG violation fixed (deterministic seed)

### RUN 11.1 (this commit, in progress)
Additional audit fixes per the FAIL verdict:
1. `nNegatives` opt-in (default 0, was hardcoded 5)
2. Vocab bound bug (`negMax = 200000`, was hardcoded `Math.min(200000, 100000)`)
3. Deterministic test assertions
4. Status exposes `nNegatives`

## RUN 11.2 — 2026-09-04 22:45 — UX doc-vs-code audit fixes

UX review flagged three doc-vs-code / doc-quality issues. Fixed:

1. **Duplicate `## Section XIX` heading in FINALSUMMARY.md**
   - Second occurrence renamed to `## Section XX — RUN 11.1 (…): LM head audit fixes` so the section sequence is X, XI, …, XVIII, XIX, XX — no more ambiguity.
2. **Stale `(End of file - total ~1280 lines)` annotation at line 1231** (mid-file)
   - Removed. File now ends cleanly at line ~1378 with a single end-of-file anchor.
3. **LmHeadResource.java Javadoc lied about endpoints**
   - Claimed `POST /v1/lm-head/reset — clear all weights` (no such endpoint exists).
   - Did not mention the new `?nNegatives=K` query param (the headline feature of the commit).
   - Fixed: Javadoc now accurately describes `POST /v1/lm-head/train?limit=N&epochs=M&nNegatives=K`
     with each param's default/max documented, plus `GET /v1/lm-head/status`.

No code logic changed; only docs and Javadoc. All targeted tests still green:
- `./gradlew :matrix-core:test --tests "io.matrix.api.*"` → 0 failures, 0 errors across
  LmHeadTest (7), SandboxResourceTest (8), QaCorpusIndexTest (12), BpeTokenizerTest (5),
  OpenAIChatResourceTest (17), and 14 other API test classes.

## RUN 12 — Brain Loop wired into /v1/chat (2026-09-05 12:50)

- New io.matrix.reasoning.BrainLoopService (ApplicationScoped): production wiring of the
  nine-stage ConsciousnessLoop. tick(BitSet) returns Trace(tickId, phasePath, attentionScore,
  predictionError, actionsSubmitted).
- OpenAIChatResource now invokes brainLoop.tick(observation) before generation; emits
  X-Matrix-Trace header on every chat response.
- 9/9 BrainLoopServiceTest pass, 19/19 OpenAIChatResourceTest pass (header present when wired,
  absent when not).
- Total tests: 41/41 (+9 from RUN 12).

## RUN 13 — SDD-sweep specs for 5 top packages (2026-09-05 12:58)

- SPEC-008-reasoning-brcchain.md (BrcChain, BrcStep, BrcState)
- SPEC-009-mediator-hierarchy.md (InstanceMediator, GoldenRatioAllocator)
- SPEC-010-hades-burden.md (BurdenLiftingRitual, DerangementDetector, Eleutheria)
- SPEC-011-memory-hierarchy.md (HierarchicalMemory L1/L2/L3, DriftSignal)
- SPEC-012-rag-boolean.md (BooleanIndex, HybridBooleanRag, RrfFusion, ExactTermGuard)
- INDEX.md and PLAN.md updated. 633 new lines of normative documentation.

## RUN 14 — TLA+ structural smoke tests (2026-09-05 13:00)

- 10/10 TlaSpecSmokeTest: structural validation for 7 TLA+ specs in formal/.
- Per-spec invariants verified: ComposeAssociative (BRC), shadow price λ (DP), Monotonicity
  (M4 Causal), TreeAcyclic (MCTS/LATS), ChainMonotonic (HashChain).
- FORMAL-CONTRACTS.md updated with RUN 14 section.

## RUN 15 — ChainFeatureCache + real chain features (2026-09-05 13:05)

- New io.matrix.api.ChainFeatureCache: SHA-256 keyed cache for question → boolean[]
  chain output. Disk-backed at data/chain_feature_cache.bin (~24 MB).
- LmHeadTrainer.trainOne uses featureCache.getOrCompute(question) instead of FNV-1a
  hash fingerprint. Real chain output is corpus-aligned.
- 10/10 ChainFeatureCacheTest pass; 7/7 LmHeadTest still pass.

## RUN 16 — H-043 + H-046 verification (2026-09-05 13:07)

- EXP-MATRIX.15 (H-046): accuracy = 0.915 ≥ 0.9 PASS, precision = 1.000, recall = 0.742.
- EXP-MATRIX.14 (H-043): utility = 1.000 ≥ 0.7 PASS at k=100, ε=1.0.
- Both HYPOTHESES-NEW.md rows updated with measurement-anchored accepted verdicts.
- 10/10 EXP tests pass.

## RUN 17 — production-corpus EXP reruns (2026-09-05 13:10)

- EXP-MATRIX.16: production corpus 6,607 pairs, multilingual (997/1000 cyrillic).
- Per-pair latency: 0.030 ms. JSON parser: 100% fidelity.
- Both EXP-009 and EXP-010 synthetic-scope verdicts hold on production corpus.

## RUN 18 — native build attempts (RFC blocked) (2026-09-05 13:13)

- Local GraalVM CE 25.0.2 IS installed; class-init list extended from 5 to 17 entries.
- Build still fails with cascading UnsupportedFeatureException + NoClassDefFoundError
  for io.netty.resolver.dns + org.tukaani.xz.
- User RFC required for: Mandrel token / Scala-Pekko replacement /
  --report-unsupported-elements-at-runtime fallback.

## RUN 19 — continuous LM head training via feedback (2026-09-05 13:21)

- New io.matrix.api.LmHeadFeedbackTrainer: POST /v1/chat/feedback now trains LM head.
- Honest caveat: LmHead.update is sign-positive only. Negative feedback does NOT
  decrement weights (signal preserved in store for future re-training).
- 7/7 LmHeadFeedbackTrainerTest pass.

## RUN 20 — E2E bilingual QA stress test (2026-09-05 13:25)

- 1000 mixed queries through QaCorpusIndex. p99 latency: 2 μs.
- 997/1000 Cyrillic (Russian-dominant corpus). 1000/1000 ethical approvals.
- Hit rate: 0.000 — disjoint-sample test setup; honest CONSTITUTION VI report.
- 6/6 Exp020E2EBilingualStressTest pass.

## RUN 21 — documentation stabilization (2026-09-05 13:26)

- FINALSUMMARY grew from ~1380 → ~1700 lines (Sections XXI-XXX covering RUN 12-20).
- context.md updated to RUN 21 state (79 tests passing).
- INDEX.md, PLAN.md, FORMAL-CONTRACTS.md, HYPOTHESES-NEW.md all updated.

Total tests after RUN 12-21: 79/79 pass.

## RUN 22 — LmHead signed update API (2026-09-05 14:43)

- New LmHead.applyUpdate(boolean[], int, double) — single source of truth
  for weight mutation. Both positive (training) and negative (feedback)
  paths route through it.
- New telemetry: positiveUpdateCount, negativeUpdateCount.
- LmHeadFeedbackTrainer.decrementForToken now calls
  applyUpdate(features, token, -0.1) — replaces RUN 19 no-op placeholder.
- 12 LmHeadTest (5 RUN 22 added) + 8 LmHeadFeedbackTrainerTest
  (1 RUN 22 added, 1 updated). All 20 pass.

## RUN 23 — confidence calibration (2026-09-05 14:45)

- New LmHead.scoreWithConfidence(boolean[], int, int[]) returns
  ScoreWithConfidence{score, confidence} with softmax-calibrated confidence.
- Temperature scaling via setTemperature(T); default T=1.0.
- 3 new LmHeadTest (15 total, all pass).

## RUN 24 — production observability metrics endpoint (2026-09-05 14:47)

- New /v1/metrics JSON endpoint (MetricsResource) aggregates counters.
- 5 MetricsResourceTest pass.

## RUN 25 — schema migration (2026-09-05 14:48)

- New CorpusMigration migrates bare-array corpus (v1) into versioned
  envelope (v2) with stable IDs.
- 6 CorpusMigrationTest pass.

## RUN 26 — multi-tenant data isolation (2026-09-05 14:49)

- New TenantQaIndex wraps QaCorpusIndex with per-tenant namespace.
- 9 TenantQaIndexTest pass; no cross-tenant leakage (security property).

## RUN 27 — constrained-decoding output safety filter (2026-09-05 14:50)

- New OutputSafetyFilter mirrors EthicalFilter but for generated tokens.
- 11 OutputSafetyFilterTest pass.

## RUN 28 — performance baseline (2026-09-05 14:51)

- Exp028PerformanceBaselineTest captures real baseline numbers:
  chain p50=90ns, qa p50=15us, lmhead p50=71ns, full p50=471ns.
- 5 tests pass.

## RUN 29 — sparse weight storage diagnostics (2026-09-05 15:04)

- New LmHead memory-footprint diagnostics:
  denseMemoryBytes, sparseMemoryBytes, sparsityRatio,
  nonZeroWeightCount, totalWeightSlots.
- Honest finding: with current Hebbian decay, ALL slots end up
  non-zero. Sparse storage break-even requires changing decay
  to floor-at-zero.
- 4 new LmHeadTest (19 total, all pass).
- EXP-MATRIX.24 documents the honest finding.

## RUN 30 — semantic query expansion (2026-09-05 15:09)

- New SemanticExpander: query + vocab → expanded token set with
  character-trigram fuzzy matches.
- 11 SemanticExpanderTest + 6 Exp025SemanticRetrievalTest (all pass).
- EXP-MATRIX.25 documents the Cyrillic regex bug (?U flag required).
- Honest caveat: cheap heuristic, NOT a substitute for true embeddings.
