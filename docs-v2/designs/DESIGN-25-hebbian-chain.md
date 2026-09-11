# DESIGN-25 — Hebbian Learning at Chain Level

> RUN 351. Hebb's rule (1949): "neurons that fire together wire
> together". Расширяем LM-head level Hebbian на уровень цепочки —
> корректируем magnitude/chemical vector нейрона на основе
> co-activation с соседями.

## 1. Источник

- Hebb D.O. (1949) "The Organization of Behavior"
- Современные расширения: BCM rule, Oja's rule, STDP

## 2. ChainHebbian

```java
public final class ChainHebbian {
    /** Pure function. Updates magnitude of neurons that co-fire.
     *  Δmagnitude_i = η · m_i · Σ m_j for co-active j. */
    public static EnrichedNeuron strengthenOnCoFire(
            EnrichedNeuron neuron,
            List<EnrichedNeuron> coActive,
            double learningRate);

    /** Oja's rule: keeps weights bounded.
     *  Δmagnitude_i = η · m_i · (m_j - m_i · m_j²) */
    public static EnrichedNeuron ojaUpdate(
            EnrichedNeuron neuron,
            List<EnrichedNeuron> coActive,
            double learningRate);
}
```

## 3. Использование

- Co-active neurons (high joint magnitude) → strengthen
- Anti-active → weaken (Oja)
- Это улучшает signal-to-noise без изменения битовых таблиц

## 4. CONSTITUTION

- I: pure (deterministic given coActive set)
- V: новые тесты
- VII: Hebbian updates logging
