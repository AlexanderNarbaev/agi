# DESIGN-15 — AC-3 планирование (PlanPreprocessor)

**Статус: normative (skeleton → integrated 2026-08-26)** · пересмотр 2026-08-26 (v2 rebuild).

## Что

Перед исполнением шагов Плана (Design-13) над CSP с шагами устанавливаются:
- Имена переменных (`varIds`),
- Домены (`domainSizes`),
- Бинарные дуги (`constraints`).

Запуск AC-3 → fast-fail: пустой домен ⇒ `IllegalStateException("unsatisfiable_preconditions at step=name")`.

## Реализация

- `agent.planning.Ac3Solver(BitSet[] domains, int maxValue, List<BinaryConstraint> constraints)` с `solve()`.
- `actions.PlanPreprocessor.preprocess(List<PlanStep>)` — детерминированно оборачивает домены + объявляет arcs как identity-constraints (быстрый fail-fast); семантические предикаты arcs добавляются непосредственно перед исполнением.

Тесты: `PlanPreprocessorTest` (happy-path, missing-domain fail-fast, malformed-arc rejected, multi-step order preserved).

## Метрики / гейты

Не измерено (только корректность). Замер через AC-3-coupling с реальными доменами — следующая сессия.

## Отложено

- Семантические constraint-predicate-плагины (вызывающий интегрирует в BinaryConstraint).
- Связь с FNL-карантином (DESIGN-12).
