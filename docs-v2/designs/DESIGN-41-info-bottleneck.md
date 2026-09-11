# DESIGN-41 — Information Bottleneck (Tishby)

> RUN 381. Из information theory — Information Bottleneck (IB)
> principle: optimal representation compresses input X while
> preserving information about target Y. I(X;T) minimized,
> I(T;Y) maximized. Применяем к chain activation: найти subset
> neurons максимально informative о chain output.

## 1. Источник

- Tishby N., Pereira F.C., Bialek W. (1999) "The information
  bottleneck method"
- Tishby N., Shwartz R. (2011) "Extracting relevant information
  from high-dimensional data"

## 2. InfoBottleneck

```java
public final class InfoBottleneck {
    /** Pure function. Select top-K neurons maximizing I(T;Y) under
     *  I(T;X) budget. Simplified: greedy by mutual information. */
    public static int[] selectNeurons(int[][] activations,
                                    int[] targets, int k);
}
```

## 3. Применение

- Critical path selection в chain: какие neurons важны для решения
- Distillation: какие neurons можно удалить без потери информации
- Curriculum: какие neurons нужно активировать для разных задач

## 4. CONSTITUTION

- I: pure
- V: тесты
