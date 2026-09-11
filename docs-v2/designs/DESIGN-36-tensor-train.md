# DESIGN-36 — Tensor Network Decomposition

> RUN 372. Из physics/quantum — тензорные сети (TT/MPS разложение)
> для компактного представления многомерных массивов. Применяем
> к truth tables большой размерности: TT-rank снижает память с
> экспоненциальной до линейной.

## 1. Источник

- Oseledets I.V. (2011) "Tensor-train decomposition"
- Orús R. (2014) "A practical introduction to tensor networks"

## 2. TensorTrain

```java
public final class TensorTrain {
    /** TT-decomposition: T[i1,...,id] = G1[i1] · G2[i2] · ... · Gd[id]
     *  Each Gi is a small tensor (2D or 3D). Pure function. */
    public static double[][][][] decompose(double[][] table, int[] ttRanks);

    /** Reconstruct from TT cores. */
    public static double[][] reconstruct(double[][][][] cores, int[] dims);
}
```

## 3. Применение

- Truth tables большой k (>20): используем TT для компактного хранения
- Однако K_MAX=20 в BIR-формах — TT может дать 2-3x компрессии
  даже при k=14
- Бонус: TT-rank-based similarity между нейронами

## 4. CONSTITUTION

- I: pure
- V: тесты
