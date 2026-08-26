# H-007 — Knowledge Stack Recall@5 ≥0.85 preregistration & gates (EXP-007)

**Статус: normative · preregistration card** · пересмотр 2026-08-26 — brain wave v3 protocols · changelog 2026-08-26 — brain wave v3 protocols.

Протокол preregistered EXP-007: Knowledge Stack (M1 SDM +
Ricci-fingerprint curriculum-ordering + drift-fingerprint) без LLM-
контура (no-LLM config) на зафиксированном golden-наборе владельца.
Running-статус: реализация M0–M4 на месте; golden-набор владельца и
recall-бенч ещё не зафиксированы → карточка фиксирует gate-критерии.

## ID и привязка

- H-ID: H-007.
- EXP-ID: EXP-007.
- Соответствующий дизайн/спека (text-only): DESIGN-05 (memory M0–M4,
  `HierarchicalMemory`, `SdmReader`), SPEC-003 (knowledge topology,
  Ollivier–Ricci, drift-fingerprint), CONSTITUTION II/VI.
- Источник вердикта (text-only): research/HYPOTHESES.md row «H-007 —
  Knowledge Stack Recall@5 ≥0.85», статус `running`.
- Источник чисел (text-only): research/reports/EXP-007-report.md — файл
  отсутствует на 2026-08-26, см. секцию «Ограничения».

## Метрики и gates (численные пороги preregistered)

| Метрика | Gate (accept) | Gate (refute) | Уровень |
|---|---|---|---|
| Recall@5 на golden-наборе | ≥ 0.85 | < 0.70 после фикса конфига | multi-seed |
| Recall@1 (sanity, не gate) | ≥ 0.55 (≥ baseline − 5 п.п.) | < baseline − 15 п.п. | multi-seed |
| Precision@5 (companion) | ≥ 0.65 | < 0.45 | multi-seed |
| Latency p99 на 1k запросов | ≤ 50 мс JVM | > 200 мс | JMH-grade |
| Recall stability (5 seeds, std) | std ≤ 0.03 | std > 0.08 | multi-seed |
| Drift-fingerprint consistency | Σ bins ≈ 1 на 100 дампов | любое расхождение | unit + jqwik |
| Wasserstein-1 symmetry d(a,b)=d(b,a) | бит-в-бит на 1k пар | любое расхождение | unit |
| Determinism | hash recall-выдачи стабилен | любое расхождение | unit |
| No-LLM constraint | recall без сетевых LLM-вызовов | любой LLM-call в recall-пути | unit |

## Methodology

- Артефакты: `memory/HierarchicalMemory`, `memory/SdmReader`,
  `ktopo/{OllivierRicciCalculator, DriftFingerprint,
  FingerprintDistance, CurriculumOrderer}`.
- Корпус: golden-набор владельца (фиксируется до запуска; размер и
  hash в pre-registration); split 70/15/15 train/holdout/test.
- Процедура: (1) построить SDM M1 из train-эпизодов; (2) топология
  curriculum-ordering → первая компонента dense-first; (3) recall@5
  на holdout и test; (4) latency p99 (JMH-grade, 1k запросов);
  (5) drift-fingerprint консистентность; (6) determinism check.
- Recall-функция: Hamming-distance SDM + Kanerva radius threshold
  (см. EXP-011 preregistration для отдельной гипотезы по precision@5).
- Baseline: flat top-K по Хэммингу без SDM-структуры (тот же бюджет
  памяти). Это контроль для side-by-side, не gate.

## Prereqs

- Реализованы `SdmReader`, `HierarchicalMemory`, `ktopo/*` (есть).
- `KtopoPropertiesTest` (jqwik) зелёный: W1≥0, симметрия, Σ bins≈1,
  d(f,f)=0, curriculum-ordering покрывает все вершины.
- Golden-набор владельца — BLOCKED-EXT: данные (на 2026-08-26
  rag-system корпуса удалены).
- JaCoCo gate ≥ 82% на `memory/**`, `ktopo/**` (CONSTITUTION V).
- Multi-seed: минимум 3 seed (42, 43, 44) для preliminary verdict.

## Methodology framework (text-only)

- Уровни доказательства — см. PROTOCOL.md в той же директории.
- Полный verdict — только в HYPOTHESES.md (running → accepted/refuted).
- No-LLM конфиг обязателен; любой вызов LLM в recall-пути инвалидирует
  verdict (CONSTITUTION VI).

## Чего здесь НЕ утверждается (CONSTITUTION VI)

- Running-статус без зафиксированного golden не публикуется как
  «Knowledge Stack работает с Recall@5 0.85+».
- 0.85 — gate-критерий, не наблюдение (см. секцию «Ограничения»).
- Recall@5 ≠ Recall@1: 0.85 на @5 не имплицирует 0.85 на @1.
- Drift-fingerprint полезность ≠ Recall (см. H-004 отдельный EXP).

## Ограничения (честный running-status на 2026-08-26)

- EXP-007 прогон не выполнен → файл `research/reports/EXP-007-report.md`
  отсутствует. Любые числа выше помечены как gate-критерии, не
  наблюдения. Без golden-набора владельца (BLOCKED-EXT: данные) карточка
  остаётся `running` без preliminary verdict.

Next: восстановить/зафиксировать golden-набор владельца + 3 seed;
multi-seed прогон recall@5 vs flat-top-K baseline; затем перевод row H-007
в `accepted (synthetic-scope)` либо `refuted-toy` в HYPOTHESES.md.