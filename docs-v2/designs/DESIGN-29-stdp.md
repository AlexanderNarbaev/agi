# DESIGN-29 — STDP (Spike-Timing-Dependent Plasticity)

> RUN 361. Из neuroscience — STDP: синапс укрепляется, если
> pre-synaptic нейрон активен ДО post-synaptic; ослабевает если
> после. Применяем к булевым нейронам через их magnitude.

## 1. Источник

- Bi G., Poo M. (1998) "Synaptic modifications in cultured hippocampal
  neurons"
- Markram H., Gerstner W., Sjöström P.J. (2011) "A history of spike-
  timing-dependent plasticity"

## 2. STDP для boolean neurons

Нет "спайков" в булевой системе, но есть событие "нейрон fired this
tick". Используем pre/post timestamps:

```
Δw = +A_plus · exp(-Δt / tau_plus)     if pre fires BEFORE post
Δw = -A_minus · exp(+Δt / tau_minus)   if pre fires AFTER post
```

Где Δt = post_time - pre_time.

## 3. StdpUpdate

```java
public final class StdpUpdate {
    public static final double DEFAULT_TAU_PLUS_MS = 20.0;
    public static final double DEFAULT_TAU_MINUS_MS = 20.0;
    public static final double DEFAULT_A_PLUS = 0.01;
    public static final double DEFAULT_A_MINUS = 0.012;

    /** Pure function. Update magnitude based on pre/post firing times. */
    public static double deltaMagnitude(long preTimeMs, long postTimeMs);
}
```

## 4. CONSTITUTION

- I: pure
- V: тесты
