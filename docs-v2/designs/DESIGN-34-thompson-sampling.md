# DESIGN-34 — Thompson Sampling (Bayesian Bandit)

> RUN 369. Из probability theory — Thompson sampling для
> explore/exploit tradeoff. Применяем к chain selection: каждый
> chain имеет beta-распределение reward, sampling из него
> выбирает chain для запуска.

## 1. Источник

- Thompson W.R. (1933) "On the likelihood that one unknown
  probability exceeds another in view of the evidence of two samples"
- Chapelle & Li (2011) "An Empirical Evaluation of Thompson Sampling"

## 2. ThompsonSampler

```java
public final class ThompsonSampler {
    /** Pure function (seeded). Returns index of best arm according
     *  to Thompson sampling: argmax_i sample(Beta(α_i, β_i)). */
    public static int sample(int[] successes, int[] failures, long seed);

    /** Update counts. */
    public static int[] updateCounts(int[] counts, int chosen, double reward);
}
```

## 3. Применение

- Chain selection: which model to run for given input
- Curriculum selection: which task to present
- Routing: which expert to consult

## 4. CONSTITUTION

- I: pure (seeded)
- V: тесты
