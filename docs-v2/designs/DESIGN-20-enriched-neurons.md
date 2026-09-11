# DESIGN-20 — Enriched Neurons: Signal-Strength + Chemical Composition

> Phase P.1 — RUN 336. Решает пробел: в docs-v2 нет концепта «силы сигнала» и
> «химического состава» нейрона. Сейчас `TruthTable` хранит только 1 бит.
> Расширяем до `EnrichedNeuron` с magnitude + chemical vector.

## 1. Пробел

Из аудита (subagent-2, 2026-09-11):

> ⛔ Signal-strength — не существует как именованный концепт.
> ⛔ Chemical composition — не существует.

Ближайшие понятия в существующем коде:

- `EMP impulse (energy, precision) ∈ [0,1]²` (DESIGN-16) — per-modality,
  НЕ per-neuron
- `arousal ∈ [0,1]` (DESIGN-18) — глобальный уровень внимания
- `saliency_weights` — per-channel таблица весов
- `pressure` в `mediator/DriverState` — per-driver

Ни одно из этих не говорит о том, **что именно нейрон «выпускает наружу»**
в дополнение к биту. Этот пробел закрывает DESIGN-20.

## 2. Что такое EnrichedNeuron

Расширение `TruthTable` → `EnrichedNeuron`:

```java
public record EnrichedNeuron(
    TruthTable table,           // существующий boolean output (k bits in, 1 bit out)
    double magnitude,           // ∈ [0, 1], насколько «силён» сигнал
    double[] chemicalVector,    // k-dim, производный от input pattern + weight
    Neurotransmitter tag        // категориальный тег
) {
    public static final int CHEMICAL_DIM = 4;
}
```

### 2.1 Magnitude

Определяется из density таблицы:

```
density = cardinality(table) / 2^k
magnitude = sigmoid((density - 0.5) * 2 * σ)
```

σ — настраиваемый параметр (default 4.0). Magnitude монотонно по density,
`magnitude ∈ [0, 1]`.

**CONSTITUTION I совместимо**: magnitude — детерминированная функция
таблицы. Нет Random, нет wall-clock.

### 2.2 Chemical Vector (4D)

Производный от input pattern + weight. Не обучение, а извлечение
детерминированных признаков:

| Dimension | Формула | Семантика |
|---|---|---|
| **excitation** | (cardinality входов) / k | сколько бит «активно» |
| **inhibition** | 1 − excitation | сколько «подавлено» |
| **novelty** | `min(1, |input − table.mode()|)` | насколько вход далёк от «привычного» |
| **certainty** | `1 − 2·|density − 0.5|` | уверенность (1 = крайне поляризован) |

**CONSTITUTION I**: все 4 — детерминированные функции input pattern и
таблицы. Нет Random, нет wall-clock.

### 2.3 Neurotransmitter Tag

Категориальный тег, derived из chemical vector:

```java
public enum Neurotransmitter {
    DOPAMINE  (novelty > 0.7, certainty > 0.5),  // «новое и уверенное»
    SEROTONIN (certainty > 0.7, excitation < 0.3), // «спокойное»
    GABA      (inhibition > 0.7),              // «тормозящее»
    GLUTAMATE (excitation > 0.7, novelty > 0.3), // «возбуждающее»
    ACETYLCHOLINE (certainty < 0.4, novelty > 0.5), // «внимание/обучение»
    NOREPINEPHRINE (excitation > 0.5, novelty > 0.8) // «тревога/бдительность»
}
```

Тег не обучается, а вычисляется — это **функция** enriched-вектора, не параметр.

## 3. Цепочка становится enriched

Сейчас `BooleanChainRunner.evaluate()` возвращает `boolean[]` — слой выхода.
DESIGN-20 расширяет:

```java
public record ChainEnrichedOutput(
    boolean[] bits,                  // существующее
    double[][] magnitudePerLayer,   // [layers][neurons], для каждого нейрона
    double[][][] chemicalPerLayer,   // [layers][neurons][4]
    Neurotransmitter[][] tagsPerLayer // [layers][neurons]
) {}

public ChainEnrichedOutput evaluateEnriched(boolean[] input);
```

Стоимость: `O(layers × neurons × CHEMICAL_DIM)` дополнительных операций.
На 24 слоя × 915 нейронов × 4 = ~88 000 операций на форвард-пасс.
На RTX 5070 это < 1 ms; на CPU < 10 ms (измерим в EXP-MATRIX.59).

## 4. Использование в других компонентах

### 4.1 ChainTriggering (DESIGN-21)

Predicate теперь сравнивает **magnitude / chemical vector / tag**, не только
биты. Пример: «если novelty > 0.8 И certainty > 0.6, активируем curiosity-chain».

### 4.2 LM Head (новый use case)

LM head сейчас получает `boolean[] chainOutput`. Расширение:
`LmHead.scoreEnriched(chainOutput, magnitude, chemical)` — score с учётом
уверенности и novelty.

### 4.3 Federated Digests (DESIGN-08)

Digest каждого узла может включать **statistical summary** magnitude +
chemical vector, не только биты. Это улучшает H-043 (utility) для
federated noosphere.

### 4.4 HADES (DESIGN-10)

`DerangementDetector` может использовать magnitude distribution для
детекции «застрявания» (все нейроны с одинаковой magnitude = collapse).

## 5. CONSTITUTION / INVARIANT compliance

| Article | Compliance |
|---|---|
| I (determinism) | ✅ magnitude/chemical — pure functions of (table, input) |
| II (K_MAX=20) | ✅ TruthTable уже ограничен, enriched не меняет K |
| III (FROZEN-zones) | ✅ не трогаем этику |
| IV (prohibitions) | ✅ magnitude/chemical не влияют на этический гейт |
| V (coverage ≥82%) | требует новые тесты |
| VI (no forbidden claims) | ✅ не обещаем AGI, не обещаем «настоящую нейрохимию» |
| VII (audit) | ✅ magnitude/chemical логируются в HashChain |
| VIII (substrate-neutrality) | ✅ работает на JVM, не зависит от нейросетевых вызовов |

## 6. Что НЕ входит в DESIGN-20

- ❌ Сигнал-strength через **обучение** (нет, это не тренируется — derived)
- ❌ Химический composition как **обучаемая модуляция весов** (отдельный design)
- ❌ Реальная биологическая нейрохимия (мы не претендуем на это)
- ❌ Backprop через magnitude (нет градиентов — битовая система)

## 7. Open Questions (выносятся в Phase R/S)

- Q1: Хранить magnitude/chemical вместе с `TruthTable` или отдельно?
  → Решение: отдельно (`EnrichedNeuron` оборачивает `TruthTable`),
  чтобы legacy код не сломался.
- Q2: Magnitude как float или double?
  → Решение: float (4 байта × 21 960 нейронов = 88 КБ).
- Q3: Допускать ли magnitude вне [0, 1]?
  → Решение: нет, через `Math.max(0, Math.min(1, m))` на записи.
- Q4: Как сериализовать enriched neurons в chain-j.bin?
  → Решение: extended header с `MAGIC = "BLN\1"` + per-neuron magnitude/chem.

## 8. Acceptance Criteria

| # | Criterion | Evidence |
|---|---|---|
| 1 | `EnrichedNeuron` record + factory | `EnrichedNeuronTest` |
| 2 | magnitude ∈ [0,1] для всех 256 случайных таблиц k=14 | density property test |
| 3 | chemical vector 4D для каждого из 21 960 нейронов Qwen2.5-0.5B | `EnrichedNeuronChainTest` |
| 4 | magnitude + chemical воспроизводимы (deterministic) | same input → same output |
| 5 | evaluateEnriched() возвращает все 3 слоя данных | `ChainEnrichedOutputTest` |
| 6 | Сериализация в chain-j.bin roundtrip | `EnrichedSerializationTest` |

## 9. Implementation Plan (Phase Q.1)

```
io/matrix/neuron/
  EnrichedNeuron.java (record + factory + chemical computation)
  Neurotransmitter.java (enum + classification)
  EnrichedChainEvaluator.java (extends BooleanChainRunner.evaluateEnriched)
  EnrichedSerialization.java (extend BLN format)
```

Backward compatibility: TruthTable, BooleanChainRunner остаются без изменений.
EnrichedNeuron добавляется **поверх**, не вместо.

## 10. References

- CONSTITUTION.md Art. I, II, V, VIII
- DESIGN-01-units.md (BirUnit canonical)
- DESIGN-16-perception-federation.md (EMP impulse, parallel concept)
- DESIGN-18-consciousness-loop.md (arousal, parallel concept)
- FOUNDATIONS.md §3 (Hebbian local signal) — НЕ используем, потому что
  это про обучение, а DESIGN-20 про derived signal
