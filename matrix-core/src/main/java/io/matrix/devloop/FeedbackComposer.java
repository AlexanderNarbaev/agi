package io.matrix.devloop;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Composes structured diagnostic {@link Feedback} from an expected-vs-actual outcome diff
 * (SPEC-000#fr-3).
 *
 * <p>The feedback is a counterexample for the learner: the set of expected-but-missing
 * effects, the set of observed-but-unexpected effects, and the unmet success criteria —
 * carried as typed data, never flattened to a string. Classification:
 *
 * <ul>
 *   <li>{@code COUNTEREXAMPLE} — attempt failed outright.</li>
 *   <li>{@code PARTIAL} — attempt succeeded but with a residual effect/criteria mismatch.</li>
 *   <li>{@code CORRECT} — attempt fully matched the expected outcome.</li>
 * </ul>
 *
 * <p>Minimal BRC-chain explanation (SPEC-000#fr-3) is a downstream consumer of this diff and
 * is out of scope here; the structured diff is its input.
 */
public final class FeedbackComposer {

    /**
     * Diff the actual outcome against the expected one.
     *
     * @param expected idealized correct outcome for the scenario
     * @param actual   observed outcome
     * @return structured feedback describing the discrepancy
     */
    public Feedback compose(Outcome expected, Outcome actual) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(actual, "actual");

        Set<String> missing = difference(expected.effects(), actual.effects());
        Set<String> unexpected = difference(actual.effects(), expected.effects());
        Set<String> unmet = Set.copyOf(actual.unmetCriteria());

        boolean noDiff = actual.success() && missing.isEmpty() && unexpected.isEmpty() && unmet.isEmpty();

        Feedback.FeedbackType type;
        if (!actual.success()) {
            type = Feedback.FeedbackType.COUNTEREXAMPLE;
        } else if (noDiff) {
            type = Feedback.FeedbackType.CORRECT;
        } else {
            type = Feedback.FeedbackType.PARTIAL;
        }

        return new Feedback(
                actual.scenarioId(), actual.skill(), noDiff, missing, unexpected, unmet, type);
    }

    private static Set<String> difference(Set<String> a, Set<String> b) {
        Set<String> result = new HashSet<>(a);
        result.removeAll(b);
        return Set.copyOf(result);
    }
}
