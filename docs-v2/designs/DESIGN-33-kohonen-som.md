# DESIGN-33 — Kohonen Self-Organizing Map

> RUN 365. Из neuroscience (Kohonen 1982) — самоорганизующаяся карта
> для топологического представления данных. Нейрон-победитель +
> его соседи обновляются → формируется топографический порядок.

## 1. Источник

- Kohonen T. (1982) "Self-organized formation of topologically
  correct feature maps"
- SOM = "winner-take-all" + lateral neighborhood update

## 2. KohonenSOM

```java
public final class KohonenSOM {
    public static final double DEFAULT_LEARNING_RATE = 0.1;
    public static final double DEFAULT_RADIUS = 1.0;

    /** Train SOM: each input, find best-matching unit (BMU),
     *  update BMU + neighbors. */
    public static void train(double[][] som, double[] input,
                            double learningRate, double radius);

    /** Find BMU for input. */
    public static int findBMU(double[][] som, double[] input);
}
```

## 3. Применение

- Топологическое представление neuron space
- Curriculum ordering: related tasks cluster
- Visual: heat-map activation

## 4. CONSTITUTION

- I: pure (seeded)
- V: тесты
