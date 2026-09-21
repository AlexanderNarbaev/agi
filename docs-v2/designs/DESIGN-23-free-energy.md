# DESIGN-23 — Free Energy Minimization for Chain Convergence

> RUN 349. Алгоритм из physics — variational free energy minimization
> для оценки конвергенции булевой цепочки. Заменяет простой
> "плотность = хорошо" на принципиальный variational bound.

## 1. Источник

- Friston K. (2010) "The free-energy principle: a unified brain theory?"
- Helmholtz machine / variational inference в ML
- Foundation: любая self-organizing система минимизирует surprise (KL
  divergence между model posterior и true posterior)

## 2. Free Energy для BooleanChain

Для булевой цепочки с N нейронами и входом `x`:

```
F = E_q[log q(θ) - log p(x,θ)]
  ≈ ⟨surprise⟩ - ⟨accuracy⟩
  = KL(q(θ|x) || p(θ)) - log p(x|θ)
```

Для boolean-цепочки упрощаем:

```
F[chain, x] = α · DensityMismatch - β · ConsensusError

DensityMismatch = mean(|density - 0.5|)      // насколько поляризована
ConsensusError  = 1 - mean(magnitude)        // насколько "уверен" выход
```

Где α, β — настраиваемые веса (default 1.0, 0.5).

## 3. FreeEnergyEvaluator

```java
public final class FreeEnergyEvaluator {
    public static final double DEFAULT_ALPHA = 1.0;
    public static final double DEFAULT_BETA = 0.5;

    public static double freeEnergy(ChainEnrichedOutput output) {
        double densityMismatch = meanDensityMismatch(output);
        double consensusError = 1.0 - output.meanMagnitude();
        return DEFAULT_ALPHA * densityMismatch - DEFAULT_BETA * consensusError;
    }

    public static boolean hasConverged(ChainEnrichedOutput output, double threshold) {
        return freeEnergy(output) < threshold;
    }
}
```

## 4. Применение

- **Convergence detection**: chain "сошёлся" когда free energy < threshold
- **Trigger source**: novelty-curiosity predicate становится `F > δ_high`
- **HADES**: high F → "застрял" → BurdenLiftingRitual
- **Training**: minimization F as objective (вместе с accuracy)

## 5. CONSTITUTION compliance

| Article | Compliance |
|---|---|
| I (determinism) | ✅ F — pure function of output |
| IV (prohibitions) | ✅ не влияет на этику |
| VI (no forbidden claims) | ✅ не "consciousness", это numerical metric |
| VII (audit) | ✅ F logging в HashChain |

## 6. Acceptance

- FreeEnergyEvaluator computes F deterministically
- hasConverged returns boolean
- Free energy on Qwen 24-layer chain reported
