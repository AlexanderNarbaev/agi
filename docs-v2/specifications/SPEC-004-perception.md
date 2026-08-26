# SPEC-004 — Perception Pillar (Sensor Layer)

**Статус: normative** · пересмотр 2026-08-26 (v2 rebuild) · brain wave v1 · changelog 2026-08-26 — brain wave v1.

## Что

Сенсорный слой P-стороны brain-контура [DESIGN-03](../designs/DESIGN-03-pipeline.md) P-E-D pipeline. Преобразует непрерывный внешний сигнал в дискретный пакет, пригодный для BIR-ядра [SPEC-002](./SPEC-002-boolean-compute-layer.md) (K_MAX=20, CONSTITUTION II). Единый контракт «sensor packet» для всех модальностей; энкодеры версионируются через `signals/SignalModuleRegistry` [DESIGN-06](../designs/DESIGN-06-signal-modules.md) (правила R1–R5).

## Мотивация

- Изоляция непрерывности: вся вещественная арифметика и вероятности сосредоточены в прокси; ядро остаётся бинарным (CONSTITUTION I — детерминизм).
- Детерминированный resolve модулей через registry, запрет ServiceLoader (DESIGN-06 R3).
- Версионируемая `encode/decode`-пара позволяет эволюцию без переписывания ядра.
- `k-anonymous`-флаг даёт заготовку для телеметрии через `federation/Anonymizer` [DESIGN-08](../designs/DESIGN-08-federation.md) без слива отдельных пакетов.
- Детерминированная дискретизация исключает Random/wall-clock в путях решений (CONSTITUTION I).

## Архитектура

### Sensor Packet (контракт)

```
record SensorPacket(
  long timestamp,           // монотонный, от clock-proxy (детерминированный источник)
  String modality,          // "text" | "image" | "audio" | "numeric" | "temporal"
  Object rawPayload,        // до бинаризации (только для прокси-цепочки)
  long[] signal,            // 64-bit packed vector (DESIGN-06 §2 контракт)
  String encoderVersion,    // семантика модуля (SignalModule.version())
  boolean kanonymous        // тег для телеметрии: участвует в federation batch?
)
```

Инварианты: `signal.length ∈ N`, `signal[i] ∈ [0..2^64-1]`; `encoderVersion` зафиксирован на момент `encode`; `timestamp` монотонен в пределах сессии; `kanonymous=true` обязывает пройти `Anonymizer` перед отправкой.

### Конвейер

```
внеш.мир → [Quantizer] → [Normalizer] → [SignalModule.encode] → SensorPacket
                                              │
                                              ▼
                                       [BIR-ядро / BRC]
```

- **Quantizer** — адаптивный порог, параметризуется EMP-импульсом (energy/precision; см. [DESIGN-16](../designs/DESIGN-16-perception-federation.md)).
- **Normalizer** — приведение диапазонов, без рандома.
- **Encoder** — `SignalModule.encode(Object) → long[]` (DESIGN-06 R1–R5).
- **Binarizer** — финальный слой гарантирует строго бинарный вектор до передачи в ядро.

### Модальности

- `text` — `TextSignalModule` (production): строка → токен-bits.
- `image` — `ImageSignalModule`: pixmap → бинарные признаки.
- `audio` — `AudioSignalModule`: PCM-frame → бинарный вектор.
- `numeric` — скаляры (метрики, telemetry counters) → бинаризация через порог.
- `temporal` — временные ряды и clock-tick события; источник `timestamp` пакета.

Внешние AI-системы (LLM, API) обрабатываются через `signals/*`-прокси как недоверенный вход (см. `ethics/AdversarialInputFilter`).

## Метрики / гейты

- `validate()` на каждом модуле (DESIGN-06 §2 контракт, default-true).
- Round-trip identity на синтетических входах (юнит).
- Регрессия: число сломанных `encoderVersion` = 0 (gate CI).
- Готовых perf-метрик нет — отложено (см. DESIGN-06 «Метрики»).

## Реализация в коде

- `signals/` (`io.matrix.signals.*`): `SignalModule`, `TextSignalModule`, `ImageSignalModule`, `AudioSignalModule`, `SignalModuleRegistry`.
- Новый пакет `perception/{SensorPacket, Quantizer, Normalizer, SensorChain}`.
- Реестр сигналов — единственная точка resolve.
- Тесты: `signals/*` (round-trip, validate), `perception/*` (chain idempotency, монотонность `timestamp`).

## Отложено

- Видео-модальность (требует DSP-инфраструктуры).
- Семантический `text-embed-hash` — внешняя зависимость (DESIGN-06).
- FROZEN-статус encoder-контракта после DESIGN-06 stage 3 (`audio-events`) — нормативная заморозка.
- Per-modality EMP-импульсы — спецификация в [DESIGN-16](../designs/DESIGN-16-perception-federation.md).

См. также [DESIGN-06](../designs/DESIGN-06-signal-modules.md), [DESIGN-08](../designs/DESIGN-08-federation.md), [SPEC-002](./SPEC-002-boolean-compute-layer.md), [science/SUBSTRATE-MODELS.md](../science/SUBSTRATE-MODELS.md), [science/FOUNDATIONS.md](../science/FOUNDATIONS.md).