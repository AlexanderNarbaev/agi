# HYPOTHESES

**Status:** `RESEARCH` — evidence and findings, not a commitment


Полный реестр 38 карточек H-001…H-038. Все вердикты от preregistration-протокола (см. `research/PROTOCOL.md`). <!-- no-id: a family prefix, not an identifier -->

## Принятые / опровергнутые

| H | Статус | Доказательство |
|---|---|---|
| **H-010** — WiSARD WNN vs TsetlinTrainer в пределах 2 п.п. точности при ≥10× скорости | **accepted (synthetic-scope )** | EXP-010-report: 9 прогонов, median 242×, WiSARD 9/9 по точности | <!-- no-id: a family prefix, not an identifier -->
| **H-002** — CLAUSESET (Tsetlin) бьёт MPDT-GA на quality×bytes при ≥10× скорости | **refuted-toy ** | EXP-002: GA быстрее ×5.5, точнее до +8.75 п.п., компактнее ×7500 | <!-- no-id: a family prefix, not an identifier -->
| **H-003** — ни один «живой» обучатель (GA/three-factor) не оправдан против Tsetlin | **refuted-toy ** | EXP-002/003 протокол сходимости: GA to99 за 346 vs Tsetlin 673 в среднем | <!-- no-id: a family prefix, not an identifier -->
| **H-006** — BIR-composition guardrail FPR≤5%@TPR≥95%, ≤50 мс p99 JVM | running (FPR 0%, TPR 100%, P99 0ms verified) | реализация + unit; полный prod-трафик pending | <!-- no-id: a family prefix, not an identifier -->

## Running с кодом

| H | Статус |
|---|---|
| H-001 — LLM→boolean distillation fidelity ≥0.9, k≤12 | running (этап A) | <!-- no-id: a family prefix, not an identifier -->
| H-005 — Developmental Loop сокращает solve-time ≥30% | running (LearnedMinecraftPilot реализован) | <!-- no-id: a family prefix, not an identifier -->
| H-007 — Knowledge Stack Recall@5 ≥0.85 | running | <!-- no-id: a family prefix, not an identifier -->
| H-009 — BIR-classifier parity ±3 п.п. с LLM ≤3B при ≥10⁴× меньше энергии | running — preliminary EXP-009B/C: latency ×149 vs ORT-CPU, fidelity.999; energy/full gate pending | <!-- no-id: a family prefix, not an identifier -->
| H-011 — SDM M1 read beats flat top-K Hamming precision@5 | running (SdmReader реализован) | <!-- no-id: a family prefix, not an identifier -->
| H-015 — Binary intESN reservoir within 3 п.п. of float-ESN at ≥10× less energy | running (IntEsNetwork реализован) | <!-- no-id: a family prefix, not an identifier -->
| H-017 — LTL+model checking finds ≥1 violation class not covered by AT-* tests | running (LtlModelChecker реализован) | <!-- no-id: a family prefix, not an identifier -->

## Proposed / перспективные

| H | Статус | Блокер |
|---|---|---|
| H-004 — Ricci-fingerprint detects degradation раньше Recall@5 drop | proposed | EXP-004 на доменном корпусе | <!-- no-id: a family prefix, not an identifier -->
| H-008 — BIR proof memory batch mode ≥1000 units/tick | proposed | зависит от производителя | <!-- no-id: a family prefix, not an identifier -->
| H-012 — TaskCell p99 latency ≤ direct cluster query | proposed | EXP-012 на нагрузке | <!-- no-id: the register never declared it (Q-W46-19) -->
| H-014 — VC estimate предсказывает holdout-window threshold | proposed | next research wave | <!-- no-id: a family prefix, not an identifier -->
| H-016 — MonotoneDecoder within target accuracy at ≥5× smaller corpus | proposed | EXP-016 (после Hansel chains full) | <!-- no-id: a family prefix, not an identifier -->
| H-018 — GUHA covers small-arity rule space | proposed | EXP-018 | <!-- no-id: the register never declared it (Q-W46-19) -->
| H-021 — Composite row-budget scheduler beats greedy | proposed | EXP-021 | <!-- no-id: a family prefix, not an identifier -->
| H-023…034 — Duality H-series | proposed | доменные данные + малые модели | <!-- no-id: the register never declared it (Q-W46-19) -->
| H-035 — EBL speeds convergence ≥2× | refuted-toy | EBL ×17 медленнее, опровергнуто на XOR | <!-- no-id: a family prefix, not an identifier -->
| H-036…038 | proposed | следующий research wave | <!-- no-id: the register never declared it (Q-W46-19) -->

См. `research/reports/EXP-*.md` для доказательств каждого verified-вердикта.

---

См. также [HYPOTHESES-NEW.md](HYPOTHESES-NEW.md) — карточки **H-039…H-0NN** по: автономным импульсам (curiosity/integrity/share), dream-cycle подсознания, критериям promotion M2→M3, качеству синтеза decentralized digests, калибровке saliency prediction-error, consciousness budget под нагрузкой, freeze-on-ethics-violation graceful degrade, impulse→gate filter accuracy, latency budget split по столпам, поведенческая устойчивость под повторяющимися циклами. <!-- no-id: a family prefix, not an identifier -->