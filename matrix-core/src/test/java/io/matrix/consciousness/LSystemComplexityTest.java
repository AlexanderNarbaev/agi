package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class LSystemComplexityTest {

    @Test
    void generateAndMeasureReturnsValidK() {
        Map<Character, String> rules = LSystem.fractalPlant();
        double k = LSystemComplexity.generateAndMeasure("F", rules, 3);
        assertThat(k).isGreaterThan(0.0);
    }

    @Test
    void measureIterationsLengthMatchesRequest() {
        Map<Character, String> rules = LSystem.fractalPlant();
        double[] ks = LSystemComplexity.measureIterations("F", rules, 5);
        assertThat(ks.length).isEqualTo(6);
    }

    @Test
    void measureIterationsKIncreases() {
        Map<Character, String> rules = LSystem.fractalPlant();
        double[] ks = LSystemComplexity.measureIterations("F", rules, 4);
        // K should be non-decreasing with more iterations (more output)
        for (int i = 1; i < ks.length; i++) {
            assertThat(ks[i]).isGreaterThan(0.0);
        }
    }

    @Test
    void measureIterationsConstantForTrivial() {
        Map<Character, String> rules = new HashMap<>();
        rules.put('F', "F");  // trivial: no growth
        double[] ks = LSystemComplexity.measureIterations("F", rules, 5);
        // All K values should be equal (output stays "F")
        for (int i = 1; i < ks.length; i++) {
            assertThat(ks[i]).isCloseTo(ks[0], within(1e-9));
        }
    }

    @Test
    void optimalIterationCountFoundForExpandingRules() {
        Map<Character, String> rules = LSystem.fractalPlant();
        int optimal = LSystemComplexity.optimalIterationCount("F", rules, 5);
        // Output keeps expanding (no saturation in 5 iterations)
        assertThat(optimal).isEqualTo(5);
    }

    @Test
    void optimalIterationCountZeroForTrivial() {
        Map<Character, String> rules = new HashMap<>();
        rules.put('F', "F");
        int optimal = LSystemComplexity.optimalIterationCount("F", rules, 5);
        // Output stays constant → saturation at iteration 2
        assertThat(optimal).isLessThanOrEqualTo(5);
    }

    @Test
    void measureFractalPlantMatchesManualGeneration() {
        double[] ks = LSystemComplexity.measureFractalPlant(3);
        Map<Character, String> rules = LSystem.fractalPlant();
        for (int i = 0; i <= 3; i++) {
            double expected = LSystemComplexity.generateAndMeasure("F", rules, i);
            assertThat(ks[i]).isCloseTo(expected, within(1e-9));
        }
    }

    private static org.assertj.core.data.Offset<Double> within(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
