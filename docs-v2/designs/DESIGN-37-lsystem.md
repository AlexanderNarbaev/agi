# DESIGN-37 — L-System Rewriting for Curriculum Generation

> RUN 373. Из mathematical biology (Lindenmayer 1968) — L-systems:
> формальная грамматика для роста/переписывания строк. Применяем
> к curriculum generation: правила порождают новые задачи
> детерминированно.

## 1. Источник

- Lindenmayer A. (1968) "Mathematical models for cellular
  interactions in development"
- Prusinkiewicz & Lindenmayer (1990) "The Algorithmic Beauty of
  Plants"

## 2. LSystem

```java
public final class LSystem {
    /** Pure function. Apply rules iteratively to grow the string. */
    public static String generate(String axiom, java.util.Map<Character,
            String> rules, int iterations);

    /** Stochastic L-system with seeded RNG. */
    public static String generateStochastic(String axiom,
            java.util.Map<Character, String[]> rules, int iterations, long seed);
}
```

## 3. Применение

- Curriculum generation: axiom = basic task, rules expand
- L-system program = task hierarchy
- CurriculumEngine picks tasks from generated curriculum

## 4. CONSTITUTION

- I: pure (seeded for stochastic)
- V: тесты
