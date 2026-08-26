# DESIGN-06 — Модули сигналов (контракт)

**Статус: normative** · пересмотр 2026-08-26 (v2 rebuild).

## Что

Преобразователи «мысль ⇄ медиа» как отдельные версионируемые модули. Контракт `encode(decode)`-пары через общий интерфейс, registry с правилами R1–R5 (детерминированный resolve, freeze, запрет ServiceLoader).

## Реализация

- Production: `signals/{TextSignalModule, AudioSignalModule, ImageSignalModule, SignalModule, SignalModuleRegistry}`.
- Прототип: `docs/research/prototype-java/`.
- Сериализованные обработчики (`compression/TruthTableMinimizer` и пр.).

Тесты: `signals/*`, `compression/*`, `prototype-java/*`.

## Метрики

Готовых perf-метрик нет (новые производственные модули появились в этой серии как side-effect); отложено.

## Отложено

- `text-embed-hash` — внешняя зависимость (эмбеддинги).
- `audio-events` — этап 3 DESIGN-06.
