package io.matrix.neuron;

import java.util.Map;
import java.util.Random;

/**
 * DESIGN-37 — L-System for curriculum generation.
 * Pure function (CONSTITUTION I with seeded RNG).
 */
public final class LSystem {

    private LSystem() {}

    /** Apply deterministic rules for `iterations` rounds. */
    public static String generate(String axiom, Map<Character, String> rules,
                                  int iterations) {
        if (axiom == null) throw new IllegalArgumentException("null axiom");
        if (rules == null) throw new IllegalArgumentException("null rules");
        String current = axiom;
        for (int i = 0; i < iterations; i++) {
            StringBuilder next = new StringBuilder();
            for (int c = 0; c < current.length(); c++) {
                char ch = current.charAt(c);
                String r = rules.get(ch);
                next.append(r != null ? r : String.valueOf(ch));
            }
            current = next.toString();
        }
        return current;
    }

    /** Stochastic L-system with seeded RNG. */
    public static String generateStochastic(String axiom,
                                            Map<Character, String[]> rules,
                                            int iterations, long seed) {
        if (axiom == null || rules == null) {
            throw new IllegalArgumentException("null");
        }
        Random rng = new Random(seed);
        String current = axiom;
        for (int i = 0; i < iterations; i++) {
            StringBuilder next = new StringBuilder();
            for (int c = 0; c < current.length(); c++) {
                char ch = current.charAt(c);
                String[] choices = rules.get(ch);
                if (choices == null || choices.length == 0) {
                    next.append(ch);
                } else {
                    next.append(choices[rng.nextInt(choices.length)]);
                }
            }
            current = next.toString();
        }
        return current;
    }
}
