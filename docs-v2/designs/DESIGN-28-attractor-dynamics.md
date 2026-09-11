# DESIGN-28 — Attractor Dynamics (Fixed-Point Convergence)

> RUN 354. Из dynamical systems theory — аттракторы (устойчивые
> состояния системы). Применяем к ChainEnrichedOutput: chain
> сходится к attractor state, откуда мелкое возмущение возвращает
> обратно.

## 1. Источник

- Banach fixed-point theorem (1922) — contraction mappings
- Hopfield networks (1982) — энергия + аттракторы
- Contractive systems: x* = f(x*), Lipschitz < 1

## 2. AttractorDetector

```java
public final class AttractorDetector {
    /** Run chain N times with slight perturbations. If outputs
     *  converge to a fixed point, return the attractor. */
    public static AttractorState detect(
            BooleanChainRunner chain,
            boolean[] input,
            int iterations,
            double tolerance);

    public record AttractorState(
            boolean[] bits,
            int convergenceIteration,
            double basinRadius
    ) {}
}
```

## 3. Применение

- Stability check: if attractor exists → chain is "stable"
- Energy-based ranking: lower attractor energy = better chain
- Derangement detection: if no attractor → chain chaotic → HADES

## 4. CONSTITUTION

- I: pure
- V: тесты
