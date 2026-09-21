package io.matrix.verification;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Property-based testing utilities for TruthTable and Evolution.
 * 
 * Provides generators and verifiers for property-based testing
 * of core Matrix components.
 */
@ApplicationScoped
public class PropertyBasedVerifier {

    private static final Logger log = LoggerFactory.getLogger(PropertyBasedVerifier.class);

    /**
     * Verify TruthTable always produces binary output.
     * Property: For any input i, output ∈ {0, 1}
     */
    public boolean verifyBinaryOutput(int[] outputs) {
        for (int output : outputs) {
            if (output != 0 && output != 1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Verify evolution fitness is non-decreasing.
     * Property: fitness(gen+1) >= fitness(gen)
     */
    public boolean verifyMonotonicFitness(double[] fitnessHistory) {
        for (int i = 1; i < fitnessHistory.length; i++) {
            if (fitnessHistory[i] < fitnessHistory[i - 1] - 1e-9) {
                return false;
            }
        }
        return true;
    }

    /**
     * Verify FROZEN neurons are immutable.
     * Property: neuronSet(before) == neuronSet(after)
     */
    public boolean verifyFrozenImmutability(Set<String> before, Set<String> after) {
        return before.equals(after);
    }

    /**
     * Verify consensus safety.
     * Property: No two different values agreed in same round
     */
    public boolean verifyConsensusSafety(Map<Integer, List<String>> roundValues) {
        for (var entry : roundValues.entrySet()) {
            List<String> values = entry.getValue();
            if (values.size() > 1) {
                String first = values.get(0);
                for (String v : values) {
                    if (!v.equals(first)) return false;
                }
            }
        }
        return true;
    }

    /**
     * Generate random boolean array for testing.
     */
    public boolean[] generateRandomBoolean(int length, Random rng) {
        boolean[] result = new boolean[length];
        for (int i = 0; i < length; i++) {
            result[i] = rng.nextBoolean();
        }
        return result;
    }

    /**
     * Generate random fitness history for testing.
     */
    public double[] generateMonotonicFitness(int generations, Random rng) {
        double[] history = new double[generations];
        history[0] = rng.nextDouble();
        for (int i = 1; i < generations; i++) {
            history[i] = history[i - 1] + rng.nextDouble() * 0.1;
        }
        return history;
    }
}
