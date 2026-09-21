# DESIGN-39 — Gray-Scott Reaction-Diffusion

> RUN 377. Из chemistry — Gray-Scott model: two chemicals U, V
> диффундируют и реагируют. Порождает узоры (пятна, полосы,
> спирали). Применяем к neuron-activation patterns для
> самоорганизации chain output.

## 1. Источник

- Pearson J.E. (1993) "Complex patterns in a simple system"
- Gray P., Scott S.K. (1984) "Autocatalytic reactions in the
  isothermal, continuous stirred tank reactor"

## 2. GrayScottSimulator

```java
public final class GrayScottSimulator {
    public static final double DEFAULT_DU = 1.0;     // diffusion of U
    public static final double DEFAULT_DV = 0.5;   // diffusion of V
    public static final double DEFAULT_FEED = 0.055;
    public static final double DEFAULT_KILL = 0.062;

    /** Step 2D reaction-diffusion by dt. */
    public static double[][] step(double[][] u, double[][] v, double dt);
}
```

## 3. Применение

- Нейроны как "химические вещества": active = U, suppressed = V
- Self-organizing activation patterns
- Pattern memory: Turing patterns

## 4. CONSTITUTION

- I: pure
- V: тесты
