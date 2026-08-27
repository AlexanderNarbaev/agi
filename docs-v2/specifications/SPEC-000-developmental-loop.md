# SPEC-000 — Developmental Loop

**Статус: normative** · пересмотр (v2 rebuild).

## Что

Spec описывает curriculum-стек: оценка компетенций → выбор задач в ZPD (zone of proximal development) → обучение с диагностической обратной связью → scaffold decay → открытие разрешений через maturity gates MA-0…MA-5.

FR: `CompetenceAssessor`, `CurriculumEngine`, `FeedbackComposer`, `ScaffoldingManager`, `MaturityGateKeeper`, `ScenarioSpec`, метрика функционала φ.

## Реализация

Пакет `devloop/` (`io.matrix.devloop.*`): 12 классов.
- `CompetenceAssessor(EWMA, α=0.3)`, `CompetenceReport`.
- `CurriculumEngine.nextScenario(competence, catalog)` — детерминированный выбор: lowest-id scenario в полосе ZPD.
- `MaturityGateKeeper.advance(evidence)` — только вперёд (MA-0 → MA-1 → … → MA-5).
- `MaturityLevel` enum, `ScenarioSpec` record, `DifficultyBand`, `Outcome`, `Feedback`, `GateCriteria`, `ScaffoldingManager`, `FeedbackComposer`.

Тесты: `DevLoopTest` (юнит) + `DevLoopPropertiesTest` (jqwik: монотонность гейтов, ZPD-полоса, EWMA∈[0,1], scaffold∈[MIN,MAX]).

## Метрики

См. `research/PROTOCOL.md`. Gate: каждый новый MA-уровень требует ≥0.7 train-competence на holdout. Полный production-проход в `engineering/PLAN.md`.

## Отложено

- TLA+-спек `BRC-Step` для `reasoning/BrсChain` интеграции.