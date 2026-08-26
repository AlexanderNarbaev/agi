# INVARIANTS

См. также `CONSTITUTION.md` (FROZEN, normative). Здесь — технические инварианты, проверяемые в коде/CI.

## K_MAX = 20

`bir/TruthTable.K_MAX = 20`, дублирование в `bir/FpgaBackend`/`bir.producers.monotone`. Увеличение требует пересмотра всех компиляторов и бэкендов.

## FROZEN-зоны

| Путь | Причина |
|---|---|
| `matrix-core/src/main/java/io/matrix/ethics/frozen/**` | математическая реализация запретов (Const. IV) |
| `matrix-core/src/main/resources/avro/**` | обратимо-совместимые Avro-схемы |
| `.github/workflows/**` | CI без ручных правок |
| `CONSTITUTION.md`, `AGENTS.md` | singleton normative |

## Детерминизм рантайма

- Рантайм-пути решений (`bir/*`, `reasoning/*`, `api/*`) НЕ используют `Random` без seed-параметра, `System.currentTimeMillis()`, LLM-вызовов.
- Multi-agent (Pekko) межпроцессное взаимодействие — Kafka/Pekko ack с явной order-стратегией.
- Периодические задачи (snapshot, drain, consolidation) работают в «фоновых» actor'ах с детерминированной последовательностью шагов.

## Coverage gate

JaCoCo ≥82% METHOD на matrix-core. Понижение только через RFC с обоснованием и временной отметкой.

## Четыре запрета (см. CONSTITUTION.md)

Реализованы в `ethics/EthicalFilter`, `StructuralSafetyGuard`, `LieDetector`, `frozen/FROZENFNLGuardian`. FROZEN-FNL — единственный математически проверяемый носитель (TLA+ `FrozenEthicalFNL`). Контракт: четыре запрета независимы от входа.

## Запрещённые claims

В коде, документации, отчётах, комментариях — запрещены (CONSTITUTION VI):
- «AGI», «общий искусственный интеллект», «сверхразум», и т.п.,
- абсолютные утверждения безопасности,
- непроверяемые численные характеристики.

## Структурные инварианты

- **INV-1** (`bir.Inv1SourceGuardTest`): прямые легаси `.evaluate()` запрещены вне whitelist (bir, ethics/frozen, neuron internals, TruthTableMinimizer). Source-scan страж без ArchUnit.
- **Шахты истории**: `x-matrix-trace` + `audit/HashChain` — append-only цепочка, tamper-evident.
- **K_KL по крайней мере**: каждый новый класс имеет JUnit + (если применимо) jqwik property-тест; запрет на «потестить позже».

## Контракты, ожидающие TLA+

- `BRC-Step`, `ConjugateBudgeter-DP`, `Memory-M4-Causal`, `MCTS-LATS-Visit` — next-format-contracts.
