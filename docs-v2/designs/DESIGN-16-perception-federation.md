# DESIGN-16 — Perception Federation

**Статус: normative** · пересмотр 2026-08-26 (v2 rebuild) · brain wave v1 · changelog 2026-08-26 — brain wave v1.

## Что

Федеративный слой восприятия над [SPEC-004](../specifications/SPEC-004-perception.md): каждая модальность работает как локальный sensor-block; агрегация телеметрии и обмен encoder-модулями через ELSP + k-анонимизатор [DESIGN-08](../designs/DESIGN-08-federation.md). EMP-импульсы (energy/precision) настраиваются per modality.

## Мотивация

- Децентрализация: инстансы публикуют/получают encoder-модули без центрального узла.
- Приватность телеметрии: k-анонимность (DESIGN-08, `k ≥ 2` enforced; `k=100` per DESIGN-08 §5) скрывает отдельные пакеты за порогом.
- Anti-replay: строго монотонный `seq` на стороне получателя (ELSP v1/v2).
- Постквант-готовность: ML-DSA профиль (JEP 497) без внешнего dep.
- Непрерывность мониторинга без раскрытия отдельного пакета: `SensorPacket.kanonymous` маршрутизируется через Anonymizer.

## Архитектура

### Локальный sensor-block (per modality)

```
┌────────────────────────────────────────┐
│ modality M (text | image | audio | …)  │
│  ┌─────────────────────────────────┐   │
│  │ SignalModule(encode/decode)     │   │
│  │ EMP(energy, precision)          │   │  ← per-modality настройка
│  │ Quantizer / Normalizer          │   │
│  └─────────────────────────────────┘   │
│  SensorPacket → [k-flag] → Telemetry   │
└────────────────────────────────────────┘
```

EMP-импульс — пара `(energy, precision) ∈ [0,1]×[0,1]`:
- `energy` → бюджет CPU/cycles на `encode` (влияет на частоту дискретизации).
- `precision` → глубина квантования (число уровней перед бинаризацией).

Настройка EMP per modality позволяет: текст держать на высокой `precision` (лингвистика важна), audio — на низкой `energy` (дешёвая дискретизация); `energy=0` ⇒ модуль отключается (zero-cost режим для неактивных каналов).

### Federated aggregation

Поток:

```
local SensorPacket ──→ kanonymous batch ──→ ElspChannelMlDsa ──→ peer
                       (k-anonymizer)         (ML-DSA sign)
                                              ↓
                                       seq‖payload verify
                                       monotonic lastAcceptedSeq (CAS)
```

Реализация (DESIGN-08):
- `federation/ElspChannel` (Ed25519, ELSP v1).
- `federation/ElspChannelMlDsa` (ML-DSA, постквант v2, JEP 497).
- `federation/Anonymizer(kThreshold)` — k-анонимный батч; `isAnonymous(hash) = count ≥ k`.
- `federation/ArtifactSigner` — sign/verify на encoder-артефактах.

### Каналы

- `channel/telemetry`: SensorPacket-батчи с `kanonymous=true`; обязательный Anonymizer-проход.
- `channel/modules`: подписанные `SignalModuleManifest` (модальность + `encoderVersion` + hash); receive → `SignalModuleRegistry.register` с проверкой подписи + freeze после DESIGN-06 stage 3.

## Метрики / гейты

- **ELSP roundtrip**: tamper → reject; replay → reject (монотонная CAS-последовательность `lastAcceptedSeq`).
- **k-анонимность**: пакет с уникальной модальностью не пройдёт при `count < k`; `k ≥ 2` enforced.
- **EMP**: `energy=0` ⇒ zero-cost режим; настройка валидируется `[0,1]×[0,1]`.
- Готовых perf-метрик нет — отложено.

## Реализация в коде

- `signals/{SignalModule, SignalModuleRegistry}` — локальный sensor-block.
- `perception/{EmpImpulse, LocalSensorBlock}` — фасад модальности.
- `federation/{ElspChannel, ElspChannelMlDsa, Anonymizer, ArtifactSigner}`.
- Тесты: `signals/*`, `federation/{ElspChannelMlDsaTest, AnonymizerTest, ArtifactSignerTest}`, `perception/*` (round-trip, EMP bounds, Anonymizer threshold).

## Отложено

- Динамическая пере-настройка EMP на основе federated-телеметрии (требует EXP).
- Кросс-инстанс сжатие encoder-артефактов.
- EDGE-3 (имплант-профиль) — вне горизонта (DESIGN-08).
- mTLS для peer-interconnect — внешняя зависимость (DESIGN-08).
- Ансамблевая + групповые подписи (DESIGN-08 отложено).

См. также [SPEC-004](../specifications/SPEC-004-perception.md), [DESIGN-06](../designs/DESIGN-06-signal-modules.md), [DESIGN-08](../designs/DESIGN-08-federation.md).