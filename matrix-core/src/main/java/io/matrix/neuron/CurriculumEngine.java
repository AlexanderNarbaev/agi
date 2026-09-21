package io.matrix.neuron;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * DESIGN-27 — Vygotsky Zone of Proximal Development.
 * Pure function with seeded RNG (CONSTITUTION I).
 */
public final class CurriculumEngine {

    public static final double DEFAULT_ZPD_LOWER = 0.6;
    public static final double DEFAULT_ZPD_UPPER = 0.85;

    private CurriculumEngine() {}

    public record Scenario(
            String id,
            int difficulty,
            String domain
    ) {}

    /** Pick scenario in ZPD band. Returns null if none. */
    public static Scenario selectNext(List<Scenario> scenarios,
                                      Map<String, Double> competence,
                                      long seed) {
        if (scenarios == null || competence == null) return null;
        double lower = DEFAULT_ZPD_LOWER;
        double upper = DEFAULT_ZPD_UPPER;
        // Walk through scenarios (deterministic order from seed)
        Random rng = new Random(seed);
        Scenario best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Scenario s : scenarios) {
            Double c = competence.get(s.id());
            if (c == null) continue;
            if (c < lower || c > upper) continue;  // outside ZPD
            // Pick the one closest to upper ZPD (most challenging)
            double distance = upper - c;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = s;
            }
        }
        return best;
    }

    /** Compute competence from accuracy: max accuracy 1.0 → 1.0. */
    public static double competenceFromAccuracy(double accuracy) {
        return Math.max(0.0, Math.min(1.0, accuracy));
    }
}
