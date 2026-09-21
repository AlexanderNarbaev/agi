package io.matrix.devloop;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Next-scenario selection within the zone of proximal development (SPEC-000#fr-2).
 *
 * <p>Given a current competence map and a catalog of {@link ScenarioSpec}, selects the
 * scenario whose {@link DifficultyBand} brackets the learner's competence in that scenario's
 * target skill. Selection is fully deterministic:
 *
 * <ol>
 *   <li>Filter to scenarios whose band {@link DifficultyBand#brackets(double) brackets}
 *       {@code competence.getOrDefault(targetSkill, 0.0)}.</li>
 *   <li>Tie-break by {@link ScenarioSpec#id()} using natural string order (lowest id wins).</li>
 *   <li>Return {@link Optional#empty()} when no scenario's band brackets the competence.</li>
 * </ol>
 *
 * <p>Prerequisite-skill gating is intentionally NOT applied here: SPEC-000#fr-2 scopes
 * selection to the difficulty band; prerequisites are metadata for other pipeline stages.
 */
public final class CurriculumEngine {

    /**
     * Choose the next scenario for the given competence profile.
     *
     * @param competence current skill→competence map (missing skill → {@code 0.0})
     * @param catalog    available scenarios
     * @return the lowest-id scenario whose band brackets current competence, or empty
     */
    public Optional<ScenarioSpec> nextScenario(
            Map<String, Double> competence,
            Collection<ScenarioSpec> catalog) {
        Objects.requireNonNull(competence, "competence");
        Objects.requireNonNull(catalog, "catalog");
        return catalog.stream()
                .filter(s -> s.difficultyBand()
                        .brackets(competence.getOrDefault(s.targetSkill(), 0.0)))
                .min(Comparator.comparing(ScenarioSpec::id));
    }
}
