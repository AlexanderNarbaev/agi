package io.matrix.devloop;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic skill→competence estimation from an ordered outcome history (SPEC-000#fr-1).
 *
 * <p>Competence is estimated with an exponentially-weighted moving average (EWMA) over the
 * sequence of observed outcomes, one success/failure (1.0/0.0) per {@link #record(Outcome)}.
 * The smoothing constant {@link #ALPHA} is FIXED; there is no randomness and no wall-clock
 * anywhere in the decision path (determinism invariant). The initial competence of an
 * unseen skill is {@code 0.0}.
 *
 * <p>EWMA update rule: {@code c' = α·y + (1 − α)·c}, where {@code y ∈ {0,1}} is the latest
 * outcome. Because {@code c} starts in {@code [0,1]} and {@code α ∈ [0,1]}, every update is
 * a convex combination, so competence is bounded to {@code [0,1]} for all histories.
 */
public final class CompetenceAssessor {

    /** Fixed EWMA smoothing constant. Documented, never tuned at runtime. */
    public static final double ALPHA = 0.3;

    private final Map<String, Double> competence = new HashMap<>();

    /**
     * Record one outcome for its skill, updating that skill's EWMA competence.
     *
     * @param outcome observed outcome (success → {@code 1.0}, failure → {@code 0.0})
     */
    public void record(Outcome outcome) {
        Objects.requireNonNull(outcome, "outcome");
        double y = outcome.success() ? 1.0 : 0.0;
        String skill = outcome.skill();
        double prev = competence.getOrDefault(skill, 0.0);
        competence.put(skill, ALPHA * y + (1.0 - ALPHA) * prev);
    }

    /** Current competence estimate for a skill, or {@code 0.0} if never observed. */
    public double competence(String skill) {
        return competence.getOrDefault(skill, 0.0);
    }

    /** Immutable snapshot of the whole skill→competence map. */
    public Map<String, Double> competenceMap() {
        return Map.copyOf(competence);
    }
}
