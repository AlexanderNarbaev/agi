# DESIGN-30 — Contrastive Hebbian Learning

> RUN 362. Из ML/biology — contrastive learning: нейрон укрепляется
> на positive examples, ослабевает на negative. Применяем к
> chain-level learning через +1/-1 feedback.

## 1. Источник

- Hadsell R., Chopra S., LeCun Y. (2006) "Dimensionality Reduction by
  Learning an Invariant Mapping" (Denoising/contrastive)
- Becker S., Hinton G. (1992) "Self-organizing neural network that
  discovers surfaces in random-dot stereograms"

## 2. ContrastiveNeuron

```java
public final class ContrastiveNeuron {
    /** Pure function. Update magnitude:
     *  +η on positive example (correct firing)
     *  -η on negative example (incorrect firing) */
    public static EnrichedNeuron contrastive(
            EnrichedNeuron neuron,
            boolean fired,
            boolean expected,
            double learningRate);
}
```

## 3. Применение

- LM head: positive feedback → strengthen correct, weaken incorrect
- Chain distillation: similar
- Spec self-supervised pretraining

## 4. CONSTITUTION

- I: pure
- V: тесты
