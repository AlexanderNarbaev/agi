# DESIGN-02 — Композиция (Viewpoint)

**Статус: normative** · пересмотр 2026-08-26 (v2 rebuild).

## Что

Иерархия уровней (L0 BirUnit → L1 Capability-графы → L2 Viewpoint → L3 Persona → L4 Node). Ядро: `Viewpoint` — взвешенный ансамбль named-evaluators c детерминированным роутером (weight×score, тай-брейк = минимальное имя по конвенции).

## Реализация

`brain/`:
- `Viewpoint<S,T>` с `Member(name, weight, score, answer)` record и `add()`, `route(stimulus) → Optional<Member>`, `winner`, `decide`.
- Reused: `mediator/InstanceMediator` (над-уровень, отдельный пакет без спеки — `needs-spec`).

Тесты: `brain/ViewpointTest` (юнит; weight×score, тай-брейк по min-name, дедупликация).

## Метрики

Готовых метрик на новый `Viewpoint` пока нет (он сделан в этой серии, не benchmarked). Отложено в JMH-очередь.

## Отложено

- Связь L2 ↔ `mediator/*` и `mediator` ТЛА-спек.
