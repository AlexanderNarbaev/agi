# DESIGN-13 — Реестр действий (Hoare + Atomic Swap)

**Статус: normative** · пересмотр 2026-08-26 (v2 rebuild).

## Что

Единый реестр действий контура как verified Hoare-триплетов `P{effect}Q`. Версионируемый атомарный своп по инварианту (эта реализация — по domainHash + consecutive version; полная BDD-эквивалентность отложена в EXP после H-017).

## Реализация

- `actions/PlanRunner.Step(name, precondition, postcondition, effect)` — атомарное исполнение: каждая фиксация state через копию; ошибки `precondition_violated`/`postcondition_violated`/`invariant_violated`.
- `actions/VersionedContract(name, version, domainHash)` — атомарный своп: same name & domainHash, `next.version == version + 1` иначе `IllegalArgumentException`.
- `actions/PlanPreprocessor.PlanStep(varIds, domainSizes, constraints)` — AC-3 fast-fail через `agent.planning.Ac3Solver`.

Тесты: `PlanRunnerTest`, `PlanPreprocessorTest` — satisfied / failure modes (юнит + jqwik invariant preservation).

## Метрики / гейты

Не измерено (тесты синтаксиса шагов). Полный EXP — после H-017.

## Отложено

- BDD-бисимуляция между версиями (полная BDD-эквивалентность).
- Планирование с интеграцией FNL-карантина.
