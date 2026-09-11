# DESIGN-27 — Curriculum / ZPD Engine

> RUN 353. Из developmental psychology (Vygotsky) — Zone of Proximal
> Development. Применяем к system curriculum: выбираем задачи в
> ZPD полосе между текущим competence и target.

## 1. Источник

- Vygotsky L.S. (1978) "Mind in Society: The Development of Higher
  Psychological Processes"
- SPEC-000-developmental-loop.md (уже частично реализован)

## 2. CurriculumEngine

```java
public final class CurriculumEngine {
    public static final double DEFAULT_ZPD_LOWER = 0.6;
    public static final double DEFAULT_ZPD_UPPER = 0.85;

    /** Pick scenario in ZPD band: lowest id where competence is in
     *  [lower, upper]. Returns null if none. */
    public static Scenario selectNext(
            List<Scenario> scenarios,
            Map<ScenarioId, Double> competence,
            long seed);

    public record Scenario(
            ScenarioId id,
            int difficulty,           // 1-10
            String domain
    ) {}
}
```

## 3. Использование

- В существующем `devloop/CurriculumEngine` (SPEC-000) — заменяем
  логику выбора на ZPD
- Competence assessment через chain accuracy on holdout
- Per-domain: mathematics, language, vision, reasoning

## 4. CONSTITUTION

- I: deterministic (seed-based scenario selection)
- V: тесты
