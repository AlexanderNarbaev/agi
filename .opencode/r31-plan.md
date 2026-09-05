# R31-Plan: Wave RUN 31-40

## Goal

Continue wave-by-wave. RUN 12-30 are complete (393 tests, 0 failures).
This plan wires the RUN 22-30 standalone components into the
production hot paths + addresses remaining FINALSUMMARY items.

## RUNs

### RUN 31 — Wire SemanticExpander into QaCorpusIndex (high value)

**Why**: SemanticExpander exists but isn't wired into the retrieval
hot path. Without wiring, retrieval still uses pure token-overlap.

**Tasks**:
1. Add `searchWithExpansion(String query, int topK)` to QaCorpusIndex
   that uses SemanticExpander for fuzzy matching.
2. Backward compatible: existing `search(query, topK)` unchanged.
3. New `enableSemanticExpansion` config (default true).
4. Tests for the new method + comparison with plain search.

**Done when**: 3+ tests pass, expanded search finds at least one
related entry on a hand-crafted corpus.

### RUN 32 — Wire OutputSafetyFilter into ChainTextGenerator

**Why**: OutputSafetyFilter exists but isn't applied during chain
generation. Forbidden tokens can still appear.

**Tasks**:
1. Apply OutputSafetyFilter in ChainTextGenerator.generate()
2. Skip or replace forbidden tokens during token-by-token generation.
3. Tests for the integration.

**Done when**: 3+ tests pass, generated text contains no forbidden
control bytes.

### RUN 33 — Tenant endpoint resource

**Why**: TenantQaIndex exists but no production endpoint exposes
tenant-scoped retrieval.

**Tasks**:
1. New `TenantQaResource` (REST): GET /v1/tenant/{id}/search.
2. POST /v1/tenant/{id}/learn to add entries to tenant namespace.
3. Tests.

**Done when**: 3+ tests pass.

### RUN 34 — Batch training loop

**Why**: LmHeadTrainer applies updates one token at a time with
per-token synchronized blocks. For large corpora this is slow.

**Tasks**:
1. New `trainBatch(List<QaCorpusIndex.Entry> entries, int epochs)`
   method that accumulates updates and applies them in one pass.
2. Backward compatible.
3. Benchmark vs single-update path.

**Done when**: 3+ tests pass, batch is at least 2x faster.

### RUN 35 — H-024 calibration verification (real numbers)

**Why**: EXP-MATRIX.22 was structural only. Real ECE measurement
needed for H-024 acceptance.

**Tasks**:
1. New Exp035H024CalibrationTest: train LM head on production
   corpus, compute top-1 confidence per held-out question,
   measure ECE.
2. ECE threshold: ≤ 0.10 for H-024 acceptance.

**Done when**: 3+ tests pass, ECE number recorded.

### RUN 36 — Goal Guard review cycle attempt

**Why**: completion requires all gates to PASS.

**Tasks**:
1. Trigger review cycle (state provided).
2. Address any blocking findings.
3. If new work surfaces, plan RUN 37-38.

### RUN 37 — Documentation final pass

**Tasks**:
1. FINALSUMMARY §§XL-XLV for RUN 31-36.
2. WAL.md final trim.
3. RELEASE-NOTES.md update.

### RUN 38 — Additional improvements

Anything that emerges from RUN 31-36 implementation.

## Estimated time

~3-4h total.
