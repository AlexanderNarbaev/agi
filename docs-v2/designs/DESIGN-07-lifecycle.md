# DESIGN-07 — Жизненный цикл элементов

**Статус: normative** · пересмотр (v2 rebuild).

## Что

Каулдрон — контролируемая самогенерация: ряды кандидатов с Φ на отложенном окне, off-heap arena. TaskCell — эфемерный ячейка-задача со spawn/изоляцией/смертью по бюджету. Sleep — фоновая активность + route-drain.

## Реализация

- `lifecycle/CauldronProtocol` (Φ-validation), `MatrixLifecycleManager`, `TaskCell` (эфемерный с бюджетом CPU/mem/wall/ttl).
- `lifecycle/FnlGate` (DESIGN-12): двухступенчатый карантин SHADOW→CANDIDATE→PROMOTED/DEMOTED c явными порогами.
- `lifecycle/ConsolidationCycle` (эта серия): батчево-детерминированное окно сна с route-drain.
- K8s CRD: `matrix-operator`/`TaskCellResource` (SignalModuleResource — для DESIGN-06).
- Планирование + AC-3 preconditions: `actions/PlanPreprocessor`.

Тесты: юниты + детерминированные gates.

## Отложено

- TLA+-спек `Memory-M4-Causal`.
- Фоновая активность Каулдрона: тесты на пакетные схемы.