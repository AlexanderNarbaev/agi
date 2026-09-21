package io.matrix.devloop;

/**
 * A closed difficulty interval {@code [min, max]} for a {@link ScenarioSpec}.
 *
 * <p>Difficulty is normalized to {@code [0, 1]}, matching the zone-of-proximal-development
 * success-probability framing of SPEC-000#fr-2 (default {@code p ∈ [0.2, 0.8]}). A
 * scenario is in the ZPD for a learner when its band {@link #brackets(double)} the
 * learner's current competence.
 */
public record DifficultyBand(double min, double max) {

    public DifficultyBand {
        if (Double.isNaN(min) || Double.isNaN(max)) {
            throw new IllegalArgumentException("difficulty must be finite");
        }
        if (min < 0.0 || max > 1.0) {
            throw new IllegalArgumentException("difficulty must be normalized to [0, 1]: min=" + min + ", max=" + max);
        }
        if (min > max) {
            throw new IllegalArgumentException("min <= max required: min=" + min + ", max=" + max);
        }
    }

    /** Whether {@code competence} lies inside this band (inclusive bounds). */
    public boolean brackets(double competence) {
        return competence >= min && competence <= max;
    }

    /** Band width {@code max - min}. */
    public double width() {
        return max - min;
    }
}
