package io.matrix.devloop;

import java.util.Objects;
import java.util.Set;

/**
 * The structured result of one scenario attempt — the observable effect side of an episode.
 *
 * <p>Both the "expected" (idealized correct) and "actual" sides of the
 * expected-vs-actual diff consumed by {@link FeedbackComposer} use this shape
 * (SPEC-000#fr-3). All fields are deterministic value data: no wall-clock, no randomness.
 *
 * @param scenarioId    identifier of the scenario that produced this outcome
 * @param skill         target skill exercised by the scenario
 * @param success       whether the attempt passed acceptance
 * @param effects       set of observed effects produced by the attempt
 * @param unmetCriteria names of success criteria that were not satisfied
 */
public record Outcome(
        String scenarioId,
        String skill,
        boolean success,
        Set<String> effects,
        Set<String> unmetCriteria) {

    public Outcome {
        Objects.requireNonNull(scenarioId, "scenarioId");
        Objects.requireNonNull(skill, "skill");
        effects = Set.copyOf(effects);
        unmetCriteria = Set.copyOf(unmetCriteria);
    }

    /** Factory for an idealized correct outcome (all effects present, no unmet criteria). */
    public static Outcome expected(String scenarioId, String skill, Set<String> effects) {
        return new Outcome(scenarioId, skill, true, effects, Set.of());
    }
}
