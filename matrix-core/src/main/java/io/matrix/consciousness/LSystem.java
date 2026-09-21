package io.matrix.consciousness;

import java.util.HashMap;
import java.util.Map;

/**
 * W110 — L-systems (Lindenmayer 1968), mathematics of creativity.
 *
 * <p>An L-system is a parallel string-rewriting system. Each step
 * replaces every symbol in the current string according to production
 * rules, all in parallel. Originally developed by Aristid Lindenmayer
 * (1968) to model the growth of filamentous fungi, L-systems have
 * become a foundational formalism in:
 * <ul>
 *   <li>Plant growth modeling (Prusinkiewicz &amp; Lindenmayer 1990)</li>
 *   <li>Procedural generation in computer graphics</li>
 *   <li>Algorithmic complexity — Wolfram's work on cellular automata
 *       and rule-110 universality</li>
 *   <li>Mathematical models of morphogenesis (Turing 1952)</li>
 * </ul>
 *
 * <p>Cross-disciplinary synthesis (META-R1 R-F): L-systems connect to
 * the mathematics of creativity:
 * <ul>
 *   <li>Gödel: self-reference and incompleteness — an L-system rule
 *       can refer to itself recursively.</li>
 *   <li>Kolmogorov: high complexity from short programs — simple rules
 *       generate complex outputs.</li>
 *   <li>L-systems: explicit algorithm for "generation from rules".</li>
 * </ul>
 *
 * <p>In MATRIX cognitive architecture, L-systems model generative
 * processes in cognition — how simple rules (schemas) generate complex
 * behavior (plans, sentences, theories).
 *
 * <p>CONSTITUTION VI compliance: a model of generative grammar,
 * not a phenomenal consciousness claim.
 */
public final class LSystem {

    private LSystem() {}

    /**
     * Apply production rules for n iterations starting from axiom.
     *
     * @param axiom initial string (e.g., "F")
     * @param rules production rules: each symbol → replacement string
     * @param iterations number of parallel rewrite steps
     * @return the generated string after n iterations
     */
    public static String generate(String axiom, Map<Character, String> rules, int iterations) {
        String current = axiom;
        for (int i = 0; i < iterations; i++) {
            StringBuilder next = new StringBuilder(current.length() * 2);
            for (int j = 0; j < current.length(); j++) {
                char c = current.charAt(j);
                String replacement = rules.get(c);
                if (replacement != null) {
                    next.append(replacement);
                } else {
                    next.append(c);
                }
            }
            current = next.toString();
        }
        return current;
    }

    /**
     * Compute the Kolmogorov-style complexity proxy of an L-system output:
     * the ratio of output length to rule set size. A high ratio means
     * compact rules generate complex outputs.
     */
    public static double complexityRatio(String output, Map<Character, String> rules) {
        if (rules.isEmpty()) return 0.0;
        double totalRules = rules.values().stream().mapToInt(String::length).sum();
        if (totalRules == 0) return 0.0;
        return (double) output.length() / totalRules;
    }

    /**
     * Build canonical rules: F → F[+F]F[-F]F (a classic plant-like L-system).
     * This is the "Fractal plant" axiom/ruleset.
     */
    public static Map<Character, String> fractalPlant() {
        Map<Character, String> rules = new HashMap<>();
        rules.put('F', "F[+F]F[-F]F");
        return rules;
    }

    /**
     * Build a Cantor-set style L-system: F → F+F-F-F+F
     */
    public static Map<Character, String> cantorSet() {
        Map<Character, String> rules = new HashMap<>();
        rules.put('F', "F+F-F-F+F");
        return rules;
    }

    /**
     * Build a Dragon-curve L-system:
     * X → X+YF+
     * Y → -FX-Y
     */
    public static Map<Character, String> dragonCurve() {
        Map<Character, String> rules = new HashMap<>();
        rules.put('X', "X+YF+");
        rules.put('Y', "-FX-Y");
        return rules;
    }

    /**
     * Build a Koch snowflake rule: F → F+F-F-F+F
     * (Same as Cantor, but with different axiom and rotation)
     */
    public static Map<Character, String> kochSnowflake() {
        Map<Character, String> rules = new HashMap<>();
        rules.put('F', "F+F-F-F+F");
        return rules;
    }

    /**
     * Compute fractal dimension of an L-system output string by box-counting:
     * count symbol density at multiple scales.
     *
     * <p>Returns an estimate of the fractal dimension in [1, 2].
     * Linear strings → 1; space-filling curves → 2.
     */
    public static double fractalDimension(String output, int minScale, int maxScale) {
        if (output == null || output.isEmpty()) return 0.0;
        if (minScale < 1) minScale = 1;
        if (maxScale < minScale) return 0.0;

        // Count non-blank symbols at each scale
        java.util.List<int[]> counts = new java.util.ArrayList<>();
        for (int scale = minScale; scale <= maxScale; scale *= 2) {
            int boxes = 0;
            for (int i = 0; i < output.length(); i += scale) {
                int end = Math.min(i + scale, output.length());
                for (int j = i; j < end; j++) {
                    char c = output.charAt(j);
                    if (c != ' ' && c != '\t') {
                        boxes++;
                        break;
                    }
                }
            }
            counts.add(new int[] { scale, boxes });
        }

        // Linear regression: log(counts) vs log(1/scale)
        int n = counts.size();
        double[] logInvScale = new double[n];
        double[] logCount = new double[n];
        for (int i = 0; i < n; i++) {
            logInvScale[i] = Math.log(1.0 / counts.get(i)[0]);
            logCount[i] = Math.log(Math.max(1, counts.get(i)[1]));
        }
        double meanX = 0, meanY = 0;
        for (int i = 0; i < n; i++) {
            meanX += logInvScale[i];
            meanY += logCount[i];
        }
        meanX /= n; meanY /= n;
        double num = 0, den = 0;
        for (int i = 0; i < n; i++) {
            double dx = logInvScale[i] - meanX;
            num += dx * (logCount[i] - meanY);
            den += dx * dx;
        }
        if (den == 0) return 1.0;
        return num / den;
    }
}
