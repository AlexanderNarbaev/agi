# DESIGN-24 — Synaptic Pruning (Biology-Inspired Compaction)

> RUN 350. Из neuroscience — synaptic pruning удаляет слабые синапсы
> во время развития мозга. Применяем к boolean-цепочке: нейроны с
> низкой magnitude "обрезаются", остальные "укрепляются".

## 1. Источник

- Huttenlocher P.R. (1979) "Synaptic density in human frontal cortex"
- Synaptic pruning: ~50% синапсов удаляется в детстве
- Биологический аналог: "use it or lose it"

## 2. SynapticPruner

```java
public final class SynapticPruner {
    public static final double DEFAULT_PRUNE_THRESHOLD = 0.2;

    /** Pure function. Returns a new FnlRegistry-derived list with
     *  weak neurons removed. Neurons below threshold marked as
     *  pruned (carries lineage). */
    public static List<EnrichedNeuron> prune(
            List<EnrichedNeuron> neurons,
            double pruneThreshold,
            double strengthenFactor);

    /** Pruning + compaction ratio. */
    public static double pruningRatio(int before, int after);
}
```

## 3. Правила

- Magnitude < pruneThreshold → remove
- Magnitude ≥ pruneThreshold AND top 10% → magnitude *= strengthenFactor
- Остальные — без изменений

## 4. Константный размер пула

После pruning размер FnlRegistry может уменьшиться. Это даёт
**прогрессивную компрессию** без потери качества (только слабые
нейроны удаляются).

## 5. CONSTITUTION compliance

- I: pure function
- VII: lineage запись (pruned-neurons-cleared-not-removed — для аудита)
