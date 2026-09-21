# Wave 22: E2E Integration Test Suite — Specification

**Status:** Draft
**Author:** MATRIX SSC
**Mode:** Specification-Driven Development (SDD)
**Date:** 2026-07-19

## 1. Motivation

Wave 14-21 added many independent components (FROZENFNLGuardian,
TombstoneService with pluggable storage, HeadlessBotRegistry, etc.) but
no **end-to-end test** that exercises them together. A failure in
integration between these pieces (e.g. a wrong signature passed between
FROZENFNLGuardian and FROZENGDPREscalator) would only be caught in
production.

This wave defines an E2E integration test that exercises the full
ethics → audit → GDPR pipeline end-to-end. It also covers the
headless-bot loop, since bots produce the actions that the FROZEN FNL
evaluates.

## 2. Goals

1. Provide **one** E2E test class that covers the full happy path.
2. Provide **regression tests** for the most likely integration breaks
   (e.g. FROZEN chain + Tombstone desync, weight ingestion → neuron pool).
3. All tests must run in < 30 seconds combined (no network, no DB).
4. All tests must be **deterministic** — same input always produces
   the same result.

## 3. Scope

### In scope

- **E2E test:** action text → FROZENFNLGuardian → REJECT → FROZENGDPREscalator
  → TombstoneService → audit verification.
- **Bot loop E2E:** HeadlessBotRegistry tick → MinecraftBotSensor event
  → audit-friendly output.
- **Weight ingestion E2E:** in-memory WeightImporter (mocked safetensors)
  → TruthTable pool → neuron count verification.
- **Persistence E2E:** in-memory TombstoneStorage → restore from snapshot.

### Out of scope

- Real network/DB/Postgres/Kafka/S3 (mocked or in-memory).
- Performance benchmarks (separate JMH tests).
- HTTP integration via Quarkus Test Framework (covered by existing
  MatrixResource tests).

## 4. API Contract

```java
public final class E2EIntegrationTestSuite {
    // Each test is annotated with @Test and runs the full flow.
    @Test void fullPipeline_approvedAction_noTombstone();
    @Test void fullPipeline_rejectedAction_createsTombstone();
    @Test void bot_tick_emitsSensorEvent_withAudit();
    @Test void tombstoneRestore_preservesAuditChain();
    @Test void weightImport_producesTruthTables();
}
```

## 5. Data Schemas

### 5.1 Pipeline Scenario (REJECT case)

```yaml
input: "Kill the enemy"
expect:
  fznl_guardian.evaluate:
    verdict: REJECTED
  frozengdpr_escalator:
    tombstones_created: 1
    tombstone:
      subjectId: "test-subject"
      resourceType: "Action"
      reason: "frozen.axiom.no_killing"
      requesterId: "FROZENGDPREscalator"
  audit_chain:
    size: 2   # 1 attestation + 1 decision
    verified: true
```

### 5.2 Bot Tick Scenario

```yaml
setup:
  registry: HeadlessBotRegistry
  botId: "alpha"
action:
  tick_once()
expect:
  bot:
    alive: true
    stepsSurvived: 1
  sensor:
    peek: not empty
```

### 5.3 Weight Import Scenario

```yaml
setup:
  importer: WeightImporter (in-memory test shard)
  model: "TinyLlama/TinyLlama-1.1B-Chat-v1.0"
expect:
  shards_downloaded: 1
  total_neurons: ≥ 10
  truth_table_size: 64  # 2^6 for k=6
```

## 6. Acceptance Criteria

| # | Criterion | Verification |
|---|-----------|---------------|
| AC-1 | `fullPipeline_approvedAction_noTombstone` passes | gradle test |
| AC-2 | `fullPipeline_rejectedAction_createsTombstone` passes | gradle test |
| AC-3 | `bot_tick_emitsSensorEvent_withAudit` passes | gradle test |
| AC-4 | `tombstoneRestore_preservesAuditChain` passes | gradle test |
| AC-5 | `weightImport_producesTruthTables` passes | gradle test |
| AC-6 | All 5 tests run in < 30s | `time` benchmark |
| AC-7 | No flaky tests (run 10 times, all pass) | manual loop |
| AC-8 | All tests assert at least one specific value (no empty `assertThat(true)`) | code review |

## 7. Constraints

- **No network:** mock all external services.
- **No DB:** use in-memory storage only.
- **No sleep:** no `Thread.sleep` in tests; use deterministic tickers.
- **No randomness without seed:** always use `new Random(42L)` or similar.

## 8. Test Data

Reuse existing factories:
- `MinecraftBotSensor.BotClient.alwaysConnected()` for the bot loop.
- `FrozenEthicalFNL.canonical()` for the FROZEN network.
- `InMemoryTombstoneStorage` for tombstones.
- A pre-built safetensors byte array (8 bytes LE header length + JSON header
  + 16 bytes of FP32 zeros) for weight ingestion.

## 9. Open Questions

- **None** — all decisions deferred to the implementation.

## 10. Definition of Done

- [ ] Spec reviewed and approved (this file).
- [ ] Implementation matches contract 1:1.
- [ ] All 8 acceptance criteria pass.
- [ ] All commits pushed to `origin main` and `gitverse main`.
- [ ] CRITICAL_GAPS.md updated.
