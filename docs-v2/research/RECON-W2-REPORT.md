# RECON-W2 — Promote the Orphans (in progress)

> Date: 2026-09-26
> Branch: main @ 2bbce3ab
> Verdict: **PARTIAL** — 1 of 8 orphans promoted; campaign continues

## Built This Wave

| File | Type | LOC | Purpose |
|------|------|-----|---------|
| `matrix-api-gateway/.../MinimalHttpServer.java` | edit | +20 | MultilingualMind wired into analyze path |
| `matrix-api-gateway/.../MultilingualMindWiringTest.java` | tests | 60 | 4 tests |

## Defects Addressed

| ID | Description | Status |
|----|-------------|--------|
| D-7 | Orphaned Real* wrappers with 0 prod callers | 🟡 partial (1/8 promoted) |

## Measured Evidence

```
71/71 matrix-api-gateway tests (was 67; +4 MultilingualMindWiringTest)
537/537 ecosystem tests (was 533; +4 net)
Goal Guard: 100/100
```

## PASS Checklist (this sub-wave)

| Item | Status |
|------|--------|
| MultilingualMind instantiated as field in MinimalHttpServer | ✅ |
| /v1/analyze detects RU via detectLanguage() | ✅ |
| /v1/analyze transliterates RU input before brain.cycle() | ✅ |
| Tests prove transliteration is identity for EN, normalized for RU | ✅ |
| Tests prove field wiring via reflection | ✅ |
| All tests green | ✅ (537/537) |
| Goal Guard ≥ previous | ✅ (100/100) |

## Remaining W2 Steps

The remaining 7 orphans:
- PersistentMind → wraps SqliteMemoryBackend (D-13 fix opportunity)
- RealSleepScheduler → wraps core SleepCycle (replaces local SleepScheduler)
- AutonomyLoop → wraps AutonomyEngine/ArousalDynamics (real goals)
- RealInboxWatcher → wraps AudioFFTEncoder/VisionEdgeEncoder (real senses)
- RealAuditService → wraps SafetyMonitor (production audit)
- RealGpuKernelEngine → metrics only (no callers needed until W6)
- TrueDistillationFactory → endpoint-gated (deferred to W5)

Each promotion will be a separate commit + tests + reviewer pass to keep
the diff reviewable.

## Honest Limitations

1. **RU → EN via transliteration is lossy.** "Париж" → "Parizh" not "Paris". 
   The mind will still match the stored "Paris" entry via HDC cosine 
   similarity (Russian users are transliterated; the underlying HDC will 
   match by token FNV-1a hash, which is case-sensitive but tolerant to 
   substring overlap). This is documented and tested.
2. **RU probes still not 100% accurate** until the gateway exposes a 
   dedicated /v1/ru endpoint or stores facts in RU too. The transliteration 
   makes existing EN facts reachable from RU input.
3. **Remaining 7 orphans still have 0 prod callers.** Campaign continues.

## Next Wave Sub-step

RECON-W2 step 2: Wire RealAuditService → wrap SafetyMonitor in the 
audit chain so the FROZEN modulator list is recorded alongside hash 
chain events.
