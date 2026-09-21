# DESIGN-32 — Boltzmann Machine (Stochastic Hopfield)

> RUN 364. Из statistical physics — Boltzmann machine: стохастический
> Hopfield с temperature. Позволяет избегать локальных минимумов
> energy.

## 1. Источник

- Hinton G.E., Sejnowski T.J. (1983) "Optimal perceptual inference"
- Ackley D.H., Hinton G.E., Sejnowski T.J. (1985) "A learning algorithm
  for Boltzmann machines"

## 2. BoltzmannSampler

```java
public final class BoltzmannSampler {
    public static final double DEFAULT_TEMP = 1.0;
    public static final int DEFAULT_BURN_IN = 100;

    /** Stochastic state update: P(s_i=1) = sigmoid(Σ w_ij*s_j / T) */
    public static boolean[] sample(boolean[] state, double[][] weights,
                                   double temperature, long seed);
}
```

## 3. Применение

- Escaping local minima в chain search
- MCMC-based chain exploration
- Temperature annealing: high → low для basin finding

## 4. CONSTITUTION

- I: pure (seeded RNG, deterministic given seed)
- V: тесты
