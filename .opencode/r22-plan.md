# R22-Plan: Wave RUN 22-30

## Goal

Continue implementation wave-by-wave without stopping. RUN 12-21 are
complete (313 tests, 0 failures). This plan covers RUN 22-30 — every
pending task from FINALSUMMARY, plus new opportunities that emerged
during RUN 12-21 implementation.

## RUNs

### RUN 22 — LmHead signed update API (high priority)

**Why**: documented blocker for RUN 19 negative-feedback training.
Negative feedback is currently no-op.

**Tasks**:
1. Add `LmHead.applyUpdate(boolean[] features, int token, double delta)`
   — signed weight change (positive=increment, negative=decrement).
2. Refactor existing `update(boolean[], int)` to delegate to
   `applyUpdate(features, token, +increment)`.
3. Refactor negative-sampling path to use `applyUpdate(features,
   negToken, -increment * 0.1)` so negatives are REAL decreases.
4. Update `LmHeadFeedbackTrainer.decrementForToken` to call
   `applyUpdate(chainOutput, token, -decrement_penalty)`.
5. Wire `negativeUpdates` counter to actually fire when delta < 0.
6. Add `LmHeadTest` cases for signed updates (decrement reduces score).
7. Commit + write EXP-MATRIX.21 report.

**Done when**: 5+ new tests pass, signed-update API in production,
EXP report shows negative feedback actually reduces score by ≥50%.

### RUN 23 — Confidence calibration (H-024)

**Why**: production LM head should expose a calibrated confidence
score (0..1) for each token prediction.

**Tasks**:
1. Add `LmHead.scoreWithConfidence(boolean[] chainOutput, int token)`
   returning `{score, confidence}`.
2. Implement softmax-normalized confidence over top-K candidates.
3. Add `LmHead.calibrate(score)` mapping raw score to calibrated
   probability via temperature scaling.
4. Add `ConfidenceCalibrationTest` verifying confidence matches
   observed accuracy on held-out set.
5. Commit + write EXP-MATRIX.22 (H-024) report.

**Done when**: temperature scaling tuned, calibration ECE ≤ 0.10.

### RUN 24 — Production observability

**Why**: we have telemetry counters but no metrics endpoint or
trace sampling. Need `/v1/metrics` for production monitoring.

**Tasks**:
1. New `MetricsResource`: `/v1/metrics` JSON endpoint exposing
   counters from all services (chat requests, LM head updates,
   cache hits, etc).
2. Add latency histograms for chat endpoint.
3. Wire observation from `OpenAIChatResource`.
4. Tests for metrics endpoint.

**Done when**: 5+ tests pass, latency histograms populated.

### RUN 25 — Schema migration scaffolding

**Why**: corpus evolves (RUN 17 used new schema). Need migration
support.

**Tasks**:
1. New `CorpusMigration` interface + default impl.
2. Versioning: `qa_pairs.json` has `version` field.
3. Migration log: `data/migrations.log`.
4. Add migration tests.

**Done when**: 3+ tests pass, version migration tested.

### RUN 26 — Multi-tenant data isolation

**Why**: H-043 (digest) needs tenant isolation. Production needs it.

**Tasks**:
1. Add `tenantId` to `QaCorpusIndex`.
2. Per-tenant namespace in retrieval.
3. Cross-tenant test verifies no leakage.

**Done when**: 3+ tests pass, isolation verified.

### RUN 27 — Constrained decoding guard

**Why**: safety classifier should restrict output tokens during
generation. Currently EthicalFilter only checks input.

**Tasks**:
1. New `OutputSafetyFilter` checking generated tokens.
2. Wire into `ChainTextGenerator`.
3. Tests: forbidden tokens blocked.

**Done when**: 3+ tests pass.

### RUN 28 — Performance baseline EXP

**Why**: need a baseline for future perf comparisons.

**Tasks**:
1. `Exp028PerformanceBaselineTest`: chain run, LM head score,
   corpus retrieval, byte tokenisation.
2. Measurements: p50, p99, max for each operation.
3. Write EXP-MATRIX.23 baseline report.

**Done when**: 4+ tests pass, baseline numbers recorded.

### RUN 29 — Goal Guard review cycle

**Why**: completion requires all gates to PASS. We have not run a
review cycle yet.

**Tasks**:
1. Trigger review cycle after RUN 22-28 done.
2. Address each finding.
3. Achieve completion.

**Done when**: completion ALLOWED.

### RUN 30 — Documentation final pass + sign-off

**Tasks**:
1. WAL.md final trim.
2. FINALSUMMARY §§XXXI-XXXIX for RUN 22-29.
3. context.md final state.
4. RELEASE-NOTES.md final.

## Estimated time

~3-4h total (auto-paced).
