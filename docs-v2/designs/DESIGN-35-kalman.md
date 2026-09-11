# DESIGN-35 — Kalman Filter for Chain State Estimation

> RUN 370. Из control theory / signal processing — Kalman filter для
> optimal state estimation под Gaussian noise. Применяем к
> neuron magnitudes: предсказываем magnitude на следующий шаг с
> учётом noise.

## 1. Источник

- Kalman R.E. (1960) "A new approach to linear filtering and prediction
  problems"

## 2. KalmanStateEstimator

```java
public final class KalmanStateEstimator {
    /** 1D Kalman filter for tracking neuron magnitude over time.
     *  state = magnitude, observation = noisy magnitude. */
    public record Estimate(double value, double variance) {}

    public static Estimate predict(Estimate prev, double processVariance);
    public static Estimate update(Estimate predicted, double observation,
                                   double measurementVariance);
}
```

## 3. Применение

- Smooth chain magnitude tracking (de-noising)
- Anomaly detection: high variance → something changed
- FROZEN-shutoff trigger: variance > threshold

## 4. CONSTITUTION

- I: pure
- V: тесты
