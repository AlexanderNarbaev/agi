package io.matrix.consciousness;

import java.util.HashMap;
import java.util.Map;

/**
 * W123 — L-system complexity measurement.
 *
 * <p>Combines L-system string generation with Kolmogorov complexity
 * measurement. Given an axiom and rules, generate the L-system output
 * for n iterations, then compute its complexity via KolmogorovComplexity.
 *
 * <p>Provides:
 * - generateAndMeasure(): generate string + compute K
 * - measureIterations(): scan n iterations to find the K growth curve
 * - optimalIterationCount(): iteration count where K begins to saturate
 *
 * <p>CONSTITUTION VI compliance: complexity measurement,
 * not a phenomenal consciousness claim.
 */
public final class LSystemComplexity {

    private LSystemComplexity() {}

    /**
     * Generate an L-system output and compute its Kolmogorov complexity.
     *
     * @param axiom starting string
     * @param rules production rules
     * @param iterations number of rewrite steps
     * @return K estimate in bits
     */
    public static double generateAndMeasure(String axiom, Map<Character, String> rules, int iterations) {
        String output = LSystem.generate(axiom, rules, iterations);
        long[] traj = stringToLongs(output);
        return KolmogorovComplexity.estimate(traj);
    }

    /**
     * Measure K across n iterations.
     *
     * @return array of K values, length = iterations+1
     */
    public static double[] measureIterations(String axiom, Map<Character, String> rules, int iterations) {
        double[] ks = new double[iterations + 1];
        String current = axiom;
        ks[0] = KolmogorovComplexity.estimate(stringToLongs(current));
        for (int i = 1; i <= iterations; i++) {
            StringBuilder next = new StringBuilder(current.length() * 2);
            for (int j = 0; j < current.length(); j++) {
                char c = current.charAt(j);
                String replacement = rules.get(c);
                next.append(replacement != null ? replacement : c);
            }
            current = next.toString();
            ks[i] = KolmogorovComplexity.estimate(stringToLongs(current));
        }
        return ks;
    }

    /**
     * Find the iteration at which K begins to saturate.
     * "Saturation" = consecutive iterations differ by less than 1%.
     *
     * @return iteration count where saturation first detected
     */
    public static int optimalIterationCount(String axiom, Map<Character, String> rules, int maxIterations) {
        double[] ks = measureIterations(axiom, rules, maxIterations);
        for (int i = 2; i < ks.length; i++) {
            if (ks[i - 1] > 0) {
                double relDelta = Math.abs(ks[i] - ks[i - 1]) / ks[i - 1];
                if (relDelta < 0.01) {
                    return i;
                }
            }
        }
        return maxIterations;
    }

    /** Convert L-system string to long[] (one char per long). */
    private static long[] stringToLongs(String s) {
        long[] result = new long[s.length()];
        for (int i = 0; i < s.length(); i++) {
            result[i] = (long) s.charAt(i);
        }
        return result;
    }

    /** Convenience: measure fractal plant across iterations. */
    public static double[] measureFractalPlant(int iterations) {
        Map<Character, String> rules = LSystem.fractalPlant();
        return measureIterations("F", rules, iterations);
    }
}
