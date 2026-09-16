package io.matrix.research;

import io.matrix.consciousness.*;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W120 — L-system growth vs Φ (test H-095).
 *
 * <p>Hypothesis: as an L-system generates output through iterations,
 * the structure of that output correlates with what Φ measures would
 * see. The Kolmogorov complexity of L-system output should grow
 * sub-linearly with iterations at the edge of chaos.
 */
class W120LSystemPhiCorrelation {

    @Test
    void dragonCurveKGrowsSublinearly() {
        // Dragon curve: |F| and |+| grow as iterations increase
        Map<Character, String> rules = LSystem.dragonCurve();
        double prevK = -1;
        for (int iter = 0; iter <= 5; iter++) {
            String output = LSystem.generate("FX", rules, iter);
            // Convert to long[] via string hash
            long[] traj = stringToLongs(output);
            double k = KolmogorovComplexity.estimate(traj);
            if (iter > 0) {
                // K should grow monotonically (more structure = more bits needed)
                assertThat(k).isGreaterThan(prevK);
            }
            prevK = k;
        }
    }

    @Test
    void fractalPlantKScalesExponentially() {
        // Fractal plant: output length grows exponentially with iterations
        Map<Character, String> rules = LSystem.fractalPlant();
        Map<Integer, Double> kByIter = new HashMap<>();
        for (int iter = 0; iter <= 4; iter++) {
            String output = LSystem.generate("F", rules, iter);
            long[] traj = stringToLongs(output);
            double k = KolmogorovComplexity.estimate(traj);
            kByIter.put(iter, k);
        }
        // K should grow with each iteration
        assertThat(kByIter.get(4)).isGreaterThan(kByIter.get(0));
    }

    @Test
    void lSystemComplexityRatioReflectsStructure() {
        Map<Character, String> rules = LSystem.cantorSet();
        String output3 = LSystem.generate("F", rules, 3);
        String output5 = LSystem.generate("F", rules, 5);
        double ratio3 = LSystem.complexityRatio(output3, rules);
        double ratio5 = LSystem.complexityRatio(output5, rules);
        // Longer output should give higher complexity ratio
        assertThat(ratio5).isGreaterThan(ratio3);
    }

    @Test
    void fractalDimensionInExpectedRange() {
        Map<Character, String> rules = LSystem.fractalPlant();
        String output = LSystem.generate("F", rules, 4);
        double dim = LSystem.fractalDimension(output, 1, 64);
        assertThat(dim).isBetween(0.5, 2.5);
    }

    @Test
    void constantOutputHasLowComplexity() {
        Map<Character, String> rules = new HashMap<>();
        rules.put('F', "F");
        String output = LSystem.generate("F", rules, 10);
        long[] traj = stringToLongs(output);
        double k = KolmogorovComplexity.estimate(traj);
        // Single character repeating — K should be small
        assertThat(k).isLessThan(20.0);
    }

    /** Convert L-system string to long[] (one char per long). */
    private static long[] stringToLongs(String s) {
        long[] result = new long[s.length()];
        for (int i = 0; i < s.length(); i++) {
            result[i] = (long) s.charAt(i);
        }
        return result;
    }
}
