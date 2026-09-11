# DESIGN-43 — Predictive Coding (Rao-Ballard)

> RUN 383. Из neuroscience — predictive coding: мозг постоянно
> генерирует предсказания сенсорного входа, ошибки идут вверх.
> Применяем к chain: predict next state, send error signal to gate.

## 1. Источник

- Rao R.P., Ballard D.H. (1999) "Predictive coding in the visual
  cortex"
- Friston K. (2005) "A theory of cortical responses"

## 2. PredictiveCoder

```java
public final class PredictiveCoder {
    /** Pure function. Compute prediction error: e = (observation - prediction).
     *  Returns error magnitude and corrected prediction. */
    public static PredictionError computeError(
            double[] observation, double[] prediction);

    public record PredictionError(
            double magnitude,    // ||e||
            double[] corrected    // prediction + e
    ) {}

    /** Update prediction with learning rate. */
    public static double[] update(double[] prediction, double[] error,
                                 double learningRate);
}
```

## 3. Применение

- HADES detection: high prediction error → derangement
- Curiosity: high PE → explore
- Arousal: PE rate = arousal signal (DESIGN-18)

## 4. CONSTITUTION

- I: pure
- V: тесты
