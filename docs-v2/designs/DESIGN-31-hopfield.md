# DESIGN-31 — Hopfield Auto-Association

> RUN 363. Из physics/neuroscience (Hopfield 1982) — content-addressable
> memory. Применяем к boolean цепочкам: по частичному входу
> восстанавливаем полный attractor state.

## 1. Источник

- Hopfield J.J. (1982) "Neural networks and physical systems with
  emergent collective computational abilities"
- Associative memory: complete pattern из partial cue

## 2. HopfieldAssociator

```java
public final class HopfieldAssociator {
    public static final double DEFAULT_TAU = 1.0;
    public static final int DEFAULT_MAX_ITER = 20;

    /** Pure function. Iteratively update state to minimize energy
     *  E = -0.5 * Σ_{i,j} w_ij * s_i * s_j. Returns attractor. */
    public static boolean[] associate(boolean[] input, double[][] weights,
                                     int maxIter, double tau);
}
```

## 3. Применение

- Запоминание patterns (snapshots цепочки)
- Recall из partial cue: 30% bits → 100% recovered
- HADES: проверка, дошёл ли chain до attractor

## 4. CONSTITUTION

- I: pure
- V: тесты
