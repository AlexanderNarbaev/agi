# DESIGN-12 — FNL (карантин) и TaskCell

**Статус: normative** · пересмотр 2026-08-26 (v2 rebuild).

## Что

**FNL** — реестр карантина с теневым прогоном и явными гейтами: SHADOW→CANDIDATE→PROMOTED (или DEMOTED). Пороги per-stage передаются вызывающим.

**TaskCell** — эфемерная ячейка-задача: spawn с заданным бюджетом CPU/mem/wall/ttl; die по исчерпании.

## Реализация

- `lifecycle/FnlGate`: `enum GateState{SHADOW, CANDIDATE, PROMOTED, DEMOTED}`, `Map<String, GateState>`, `admit/advance/state` детерминированно.
- `lifecycle/TaskCell`: `State{CREATED, RUNNING, COMPLETED, FAILED, TIMEOUT, DESTROYED}`; `execute(TaskExecutor)`, `destroy()`, `isTimeout`.
- Связь с `lifecycle/CauldronProtocol`: каждый новый элемент стартует с `admit(...)` → `advance(score, threshold)` цикла.
- K8s: `matrix-operator/TaskCellResource` CRD.

Тесты: `FnlGateTest` (полная промоция, demotion, terminal stability); `TaskCellTest` интеграционный.

## Метрики / гейты

Готовых не измерено; EXP-012 (TaskCell p99 latency vs direct) — proposed.

## Отложено

- DOMAIN H-012: EXP.
- Адаптивный размер среза.
- Двойной карантин IMPORT_M4 ↔ DESIGN-08 federation.
