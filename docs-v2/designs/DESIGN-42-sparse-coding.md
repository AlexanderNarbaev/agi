# DESIGN-42 — Sparse Coding (Olshausen-Field)

> RUN 382. Из computational neuroscience — sparse coding: данные
> представлены через небольшое количество активных нейронов из
> большого словаря. Применяем к chain neurons — какие минимально
> необходимы для типичных входов.

## 1. Источник

- Olshausen B.A., Field D.J. (1996) "Emergence of simple-cell
  receptive field properties by learning a sparse code for natural
  images"
- Olshausen B.A., Field D.J. (2004) "Sparse coding of sensory inputs"

## 2. SparseCoder

```java
public final class SparseCoder {
    /** Pure function. Find sparse representation: minimize
     *  ||x - D*a||^2 + λ||a||_1. Returns sparse code a. */
    public static double[] encode(double[] x, double[][] dictionary,
                                  double sparsityLambda);

    /** LASSO-style iterative soft-thresholding. */
    public static double[] lasso(double[] residual, double[][] dictionary,
                                 double threshold, int iterations);
}
```

## 3. Применение

- Chain interpretation: 99% neurons inactive → 1% active
- "Sparse firing" = efficient inference
- Power-saving: только активные neurons потребляют энергию

## 4. CONSTITUTION

- I: pure
- V: тесты
