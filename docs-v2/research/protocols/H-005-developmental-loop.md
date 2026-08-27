# H-005 — Developmental Loop solves ≥30% быстрее preregistration & gates (EXP-005)

Протокол preregistered EXP-005: curriculum-стек (оценка
компетенций → выбор задач в ZPD → scaffold decay → maturity gates
MA-0…MA-5) на MinecraftPilot-сцэнариях против случайной выборки задач.
Running-статус: `LearnedMinecraftPilot` реализован; полный EXP-005 прогон
ещё не выполнен → карточка фиксирует gate-критерии до запуска.

## ID и привязка

- H-ID: H-005.
- EXP-ID: EXP-005.
- Соответствующий дизайн/спека (text-only): DESIGN-04 (продюсеры),
 SPEC-000 (curriculum-стек, `CompetenceAssessor`, `CurriculumEngine`,
 `MaturityGateKeeper`), CONSTITUTION I/VI.
- Источник вердикта (text-only): research/HYPOTHESES.md row «H-005 —
 Developmental Loop сокращает solve-time ≥30%», статус `running`.
- Источник чисел (text-only): research/reports/EXP-005-report.md — файл
 отсутствует на, см. секцию «Ограничения».

## Метрики и gates (численные пороги preregistered)

| Метрика | Gate (accept) | Gate (refute) | Уровень |
|---|---|---|---|
| Median solve-time Δ vs random baseline | ≤ −30% (one-sided 5% test) | > −10% | multi-seed |
| Final holdout accuracy non-inferiority | ≥ baseline − 2 п.п. | < baseline − 5 п.п. | multi-seed |
| Transfer rate (reused scenarios) | ≥ 50% | < 25% | multi-seed |
| Fraction solved within budget 10⁶ шагов | ≥ baseline − 5 п.п. | < baseline − 15 п.п. | multi-seed |
| Scaffold decay curve monotonic | decay ∈ [MIN, MAX], монотонна | колебания или stuck | unit + jqwik |
| EWMA competence ∈ [0, 1] | bounded на 1k шагов | любое расхождение | unit |
| Determinism | hash трассы стабилен на seed | любое расхождение | unit |
| MA-gate монотонность | только вперёд (MA-0 → MA-5) | регресс MA-уровня | jqwik |

«N итераций» из формулировки H-005 = число MA-переходов, на котором
измеряется дельта (фиксируется до запуска; предварительно N ∈ {3, 5}).

## Methodology

- Артефакт: `matrix-core/.../devloop/{CompetenceAssessor, CurriculumEngine,
 MaturityGateKeeper, ScaffoldingManager, FeedbackComposer}`,
 `pilot/LearnedMinecraftPilot` (агент), `MinecraftPilot` (контракт).
- Корпус: 50 фиксированных этюдов крафта (сет фиксируется до запуска,
 hash в `preregistration/`); split 40 train / 10 holdout.
- Контроль (baseline): `NaiveMinecraftPilot` (тот же агент,
 случайная выборка задач, без `CurriculumEngine`, без scaffold decay).
- Процедура: (1) grid-tuning scaffold decay rate (3 значения) и ZPD-
 полосы (3 значения); (2) full-train обоих режимов; (3) holdout solve-
 time + accuracy; (4) EWMA-трасса competence; (5) scaffold decay-
 профиль на 1k шагов.
- Протокол SPEC-000 FR-7: scaffold fading обязателен (без fading
 эксперимент недействителен → отдельный EXP с пометкой «no-decay»).

## Prereqs

- Реализованы `LearnedMinecraftPilot` + devloop (есть).
- `DevLoopTest` + `DevLoopPropertiesTest` (jqwik) зелёные.
- 50 этюдов зафиксированы (pre-registration artifact) — на 
 нет, BLOCKED-EXT: набор.
- JaCoCo gate ≥ 82% на `devloop/**`, `pilot/**` (CONSTITUTION V).
- Multi-seed: минимум 3 seed (42, 43, 44) для preliminary verdict.

## Methodology framework (text-only)

- Уровни доказательства — см. PROTOCOL.md в той же директории.
- Полный verdict — только в HYPOTHESES.md (running → accepted/refuted).
- Synthetic-scope пометка обязательна для любого preliminary verdict.

## Чего здесь НЕ утверждается (CONSTITUTION VI)

- Running-статус не экстраполируется: «Developmental Loop быстрее на
 30%» не публикуется до multi-seed замера.
- 50 этюдов — синтетический сценарий; реальный prod-domain (если
 появится) — отдельный EXP с собственным preregistration.
- Curriculum-движок детерминирован только при фиксированных seed;
 wall-clock-улучшение отделено от качественного (принимается по обоим).

## Ограничения (честный running-status на )

- EXP-005 прогон не выполнен → файл `research/reports/EXP-005-report.md`
 отсутствует. Любые числа выше помечены как gate-критерии, не
 наблюдения. До прогона статус H-005 = `running` без preliminary
 verdict.

Next: зафиксировать 50 этюдов + 3 seed (pre-registration artifact), затем
multi-seed прогон vs `NaiveMinecraftPilot` baseline; полный prod-domain
verdict — когда появится доменная нагрузка (BLOCKED-EXT: данные).