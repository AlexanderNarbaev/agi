package io.matrix.devloop;

import java.util.Objects;
import java.util.Set;

/**
 * Structured diagnostic feedback produced by {@link FeedbackComposer} from an
 * expected-vs-actual {@link Outcome} diff (SPEC-000#fr-3).
 *
 * <p>Deliberately not a string-only message: the diff is carried as typed sets so
 * downstream consumers (curriculum engine, memory consolidation) can reason over it
 * deterministically. A scalar reward, if any, is only a supplement (SPEC-000#fr-3).
 *
 * @param scenarioId        scenario the feedback refers to
 * @param skill             target skill
 * @param correct           true when the attempt fully matches expectations
 * @param missingEffects    effects expected but not observed
 * @param unexpectedEffects effects observed but not expected
 * @param unmetCriteria     success criteria that were not satisfied
 * @param type              classification of the discrepancy
 */
public record Feedback(
        String scenarioId,
        String skill,
        boolean correct,
        Set<String> missingEffects,
        Set<String> unexpectedEffects,
        Set<String> unmetCriteria,
        FeedbackType type) {

    public enum FeedbackType {
        /** Attempt failed outright — a counterexample for the learner (SPEC-000#fr-3). */
        COUNTEREXAMPLE,
        /** Attempt succeeded but with a partial mismatch in effects/criteria. */
        PARTIAL,
        /** Attempt fully matched the expected outcome. */
        CORRECT
    }

    public Feedback {
        Objects.requireNonNull(scenarioId, "scenarioId");
        Objects.requireNonNull(skill, "skill");
        missingEffects = Set.copyOf(missingEffects);
        unexpectedEffects = Set.copyOf(unexpectedEffects);
        unmetCriteria = Set.copyOf(unmetCriteria);
        Objects.requireNonNull(type, "type");
    }
}
