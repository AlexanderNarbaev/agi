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
