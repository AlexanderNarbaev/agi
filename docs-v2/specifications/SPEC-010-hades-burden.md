# SPEC-010 — HADES Burden Lifting (normative)

**Status**: v1 · singleton normative
**Package**: `io.matrix.hades`
**Related**: [SPEC-006](./SPEC-006-consciousness-deliberation.md), [DESIGN-04](../designs/DESIGN-04-hades-burden.md)

## Что

`hades/` — пакет self-healing подсистемы MATRIX. Когда агент
накапливает «бремя» (повторяющиеся ошибки, stuck neurons, derangement),
ритуал `BurdenLiftingRitual` снимает его через архивацию, а
`DerangementDetector` отслеживает симптомы. `Eleutheria` —
целевое состояние «свободы от бремени».

## Архитектура

```
HadesProtocol              — высокоуровневый контракт self-healing
 ├── BurdenLiftingRitual    — разовый ритуал снятия бремени
 │    ├── archive(failures) — заархивировать список ошибок
 │    └── lift()            — главный цикл ритуала
 ├── DerangementDetector    — мониторинг симптомов derangement-а
 │    ├── detect(state)     — вернуть DerangementReport
 │    └── threshold-tunable
 └── Eleutheria             — конечное состояние «свободы»
      └── verify(state)     — проверить достигнутость Eleutheria
```

## FR (классы и интерфейсы)

- `hades/BurdenLiftingRitual` — `archive(List<String>)` возвращает
  archive-id; `lift()` запускает цикл снятия бремени; конструктор
  принимает `(archivePath, MaxArchiveSize, Random?)`.
- `hades/DerangementDetector` — `detect(SystemState) → DerangementReport`
  с метриками `repetitionCount`, `stuckCounter`, `entropy`.
- `hades/Eleutheria` — `verify(SystemState) → boolean` и
  `distanceTo(SystemState) → double` (0.0 = полная свобода).
- `hades/HadesProtocol` — высокоуровневый façade:
  `tick(state) → Either<DerangementReport, EleutheriaCert>`.

## Инварианты

1. **Детерминизм (CONSTITUTION I)**: `DerangementDetector.detect(state)`
   — чистая функция. Random допустим только в `BurdenLiftingRitual`
   для разнообразия архивов (но не в decision-path).
2. **FROZEN-гейт (CONSTITUTION III)**: ритуал не может удалить
   этические нормы или ослабить `FROZENFNLGuardian`. `BurdenLiftingRitual.lift()`
   обязан пропустить архив через `ethics/frozen/StructuralSafetyGuard`.
3. **K_MAX=20 (CONSTITUTION II)**: detector использует boolean-формы
   для проверки stuck-neuron patterns.
4. **Coverage gate ≥82%** на всех hades-классах.
5. **Без агрессивного reset**: ритуал НЕ стирает `LongTermMemory`
   или `HierarchicalMemory`. Только архивирует поверхностные
   следы. INV-3 в [INVARIANTS](../engineering/INVARIANTS.md).

## BurdenLiftingRitual

Жизненный цикл:

```
1. detect → DerangementReport (если report.severity > threshold)
2. archive(List<String> failures) → archiveId
3. lift() → close-archive, reset transient state
4. verify(Eleutheria) → confirm freedom
```

Архив хранится на диске (path = `archivePath/YYYY-MM-DD-<archiveId>.log`).
Если `archivePath` недоступен — `BurdenArchiveException`.

## DerangementDetector

Метрики:

| Метрика | Источник | Threshold |
|---|---|---|
| `repetitionCount` | `BrainPipeline.responseHistory` | ≥ 3 → derangement |
| `stuckCounter` | `OpenAIChatResource.STUCK_THRESHOLD` | ≥ 10 → stuck neuron |
| `entropy` | `responseHistory.uniquenessRatio` | < 0.1 → collapse |

`detect()` возвращает `DerangementReport{ severity, metrics, recommendation }`.
`recommendation ∈ { ARCHIVE, RESET_TRANSIENT, FULL_RELOAD, NOOP }`.

## Eleutheria

Состояние «свободы от бремени»: достигается, когда

```
repetitionCount == 0 ∧ stuckCounter == 0 ∧ entropy > 0.3
```

`verify(state)` возвращает `true` если все три условия выполнены.
`distanceTo(state)` ∈ [0.0, 1.0] — нормированная «дистанция» до Eleutheria.

## Связь с существующим

- `OpenAIChatResource` ([api/OpenAIChatResource](../../matrix-core/src/main/java/io/matrix/api/OpenAIChatResource.java))
  использует `DerangementDetector` для circuit-breaker на stuck-нейронах.
- `agent/AgentBrainService` имеет `initializeRandom()` —
  эквивалент `FULL_RELOAD` recommendation.
- `lifecycle/ConsolidationCycle` ([DESIGN-07](../designs/DESIGN-07-consolidation-cycle.md))
  используется в `BurdenLiftingRitual.lift()` для дренажа backlogs.

## Тесты

`BurdenLiftingRitualTest` (12 тестов), `DerangementDetectorTest`
(15 тестов), `EleutheriaTest` (7 тестов), `HadesProtocolTest`
(9 тестов). Total 43 теста.

## TLA+ формализация

`HADES-Burden-Lift` — кандидат на TLA+ в следующей волне
([FORMAL-CONTRACTS](../architecture/FORMAL-CONTRACTS.md)).
Формализует: detect → archive → lift → verify как state-machine
с инвариантами severity bounds.
