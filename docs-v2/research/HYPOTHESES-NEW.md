# HYPOTHESES-NEW — brain wave v1 (H-039…H-050)

**Статус: normative · продолжает [HYPOTHESES.md](HYPOTHESES.md)** · пересмотр 2026-08-26 (brain wave v1) · changelog 2026-08-26 — brain wave v1.

12 новых карточек: сознание/подсознание, импульсы, consolidation gates, gossip, budget, ethics recovery, latency split, emergence. Каждая — **proposed**. Никаких подтверждений до прохождения preregistered EXP по [PROTOCOL.md](PROTOCOL.md). Целевые метрики — proposed; могут быть ужесточены или ослаблены при preregistration.

## H-039…H-050

| ID | One-line summary | Methodology sketch | Gate criterion | Prereq |
|---|---|---|---|---|
| **H-039** | Curiosity-impulse fires when prediction-error > θ_c в offline replay | Seed-фиксированный replay; варьировать θ_c; измерить precision/recall impulse против ground-truth «surprise»-меток | Precision@top-K ≥0.7 при recall ≥0.5 (synthetic-scope) | [SPEC-007](./../specifications/SPEC-007-subconscious.md) PredictionModel; M1 episode corpus |
| **H-040** | M2→M3 promotion criteria: prediction-error > δ AND integrity-check pass | Jqwik-свойство: `promotion ⊆ {episodes: err>δ ∧ integrity}`; falsification: random-promote vs criteria | Promotion precision ≥0.9 (synthetic-scope) | [SPEC-007](./../specifications/SPEC-007-subconscious.md) TR/REM; [DESIGN-12](./../designs/DESIGN-12-taskcell-fnl.md) FnlGate |
| **H-041** | Offline dream-replay beats online retention на F1 забывания/сохранения | Два arm: online-only vs online+REM; F1 против held-out episode queries | ΔF1 ≥0.05 в пользу REM arm (synthetic-scope) | [SPEC-007](./../specifications/SPEC-007-subconscious.md) REM-phase; PredictionModel |
| **H-042** | Consciousness-budget allocator respects per-stadia caps под нагрузкой | Stress-test: 10× impulses; измерить p99 per-stage latency; проверить cap | Ни один per-stage p99 не превышает cap в 95/100 прогонов | [SPEC-006](./../specifications/SPEC-006-consciousness-deliberation.md) DeliberationEngine.budget; [DESIGN-18](./../designs/DESIGN-18-consciousness-loop.md) |
| **H-043** | Decentralized digest synthesis (k-anonymous + DP-noise) сохраняет utility ≥0.7 | Anonymizer + DP-noise (ε, δ); измерить downstream task utility vs non-anonymized | Utility ≥0.7 × baseline при k=100, ε=1.0 (synthetic-scope) | [DESIGN-08](./../designs/DESIGN-08-federation.md) Anonymizer; M3 digest corpus |
| **H-044** | Saliency weights calibrate от prediction-error stream (online) | Tracker: predicted-vs-actual saliency; calibration error (ECE) после N циклов | ECE ≤0.1 после 1000 циклов (synthetic-scope) | [SPEC-006](./../specifications/SPEC-006-consciousness-deliberation.md) PredictionModel; perception events |
| **H-045** | Freeze-on-ethics-violation recovery через graceful degrade (не lockout) | Inject 4 запрета scenarios; измерить recovery path latency и safe-output rate | Recovery в течение budget; safe-output rate 100% (synthetic-scope) | [ethics/frozen/FROZENFNLGuardian](./../architecture/MODULES.md); ConsciousLoop freeze-mode |
| **H-046** | Subconscious impulse → conscious gate filter accuracy ≥0.9 | Генерировать impulse stream; ground-truth gate decision (mock FROZEN-FNL); измерить accuracy | Accuracy ≥0.9 на synthetic impulse corpus | [SPEC-006](./../specifications/SPEC-006-consciousness-deliberation.md) ActionGate; [SPEC-007](./../specifications/SPEC-007-subconscious.md) ImpulseGenerator |
| **H-047** | Cross-pillar latency budget split: perception<5ms, deliberation<50ms, action<10ms (p99) | JMH-grade замеры per-stage под realistic load | p99 per-stage в пределах target в 9/10 прогонов | [SPEC-006](./../specifications/SPEC-006-consciousness-deliberation.md) stages; [DESIGN-18](./../designs/DESIGN-18-consciousness-loop.md) latency table |
| **H-048** | Emergence of behavior: повторные циклы сохраняют стабильность поведения | N=1000 циклов; метрики: action-distribution entropy, decision-tree shape diff | Entropy drift ≤ε; shape diff ≤threshold (synthetic-scope) | ConsciousLoop integration; seed-fixed replay |
| **H-049** | Share-impulse fires при M3 quorum acceptance с порогом utility > θ_s | Gossip pipeline; варьировать θ_s; impulse rate vs M4 acceptance rate | Impulse precision ≥0.8 (synthetic-scope) | [DESIGN-08](./../designs/DESIGN-08-federation.md) MeshFederation; [DESIGN-12](./../designs/DESIGN-12-taskcell-fnl.md) FnlGate PROMOTED |
| **H-050** | Arousal dynamics: монотонно растёт при нарастающем prediction-error stream | Jqwik: arousal-update функция; falsification: counterexample sequence | Монотонность при strictly-increasing prediction-error | [DESIGN-18](./../designs/DESIGN-18-consciousness-loop.md) arousal; [SPEC-006](./../specifications/SPEC-006-consciousness-deliberation.md) AttentionRouter |

## Связь с существующими кластерами

- **H-039 / H-040 / H-041** — кластер «subconscious consolidation».
- **H-042 / H-047 / H-050** — кластер «conscious budget & latency».
- **H-043 / H-049** — кластер «federation & share».
- **H-044 / H-048** — кластер «online calibration & emergence».
- **H-045 / H-046** — кластер «ethics gate & filter».

## Чего здесь НЕ утверждается

- Никакие из H-039…H-050 не считаются подтверждёнными до записи `accepted/refuted` после EXP-протокола ([PROTOCOL.md](PROTOCOL.md)).
- Целевые метрики — proposed; могут быть ужесточены или ослаблены при preregistration.
- Никаких численных характеристик поведения системы, которые не измерены (CONSTITUTION VI).
- Циклы подсознания и сознания — инженерные конструкции ([SPEC-007](./../specifications/SPEC-007-subconscious.md), [SPEC-006](./../specifications/SPEC-006-consciousness-deliberation.md)), не претензия на биологическое соответствие.

См. [PROTOCOL.md](PROTOCOL.md), [HYPOTHESES.md](HYPOTHESES.md), [engineering/PLAN.md](../engineering/PLAN.md), [architecture/FORMAL-CONTRACTS.md](../architecture/FORMAL-CONTRACTS.md).
