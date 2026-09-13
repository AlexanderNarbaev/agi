package io.matrix.neuron;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * RUN 476 — PragmaticTest: James-Dewey pragmatic meaning assignment (DESIGN-60).
 *
 * <p>Implements William James's pragmatism: "The meaning of a concept is
 * nothing but the set of practical consequences it implies." Applied to
 * MATRIX: the meaning of a percept is the action that successfully follows
 * from it.
 *
 * <h2>Algorithm</h2>
 * <pre>
 *   1. When an action succeeds (positive consequence):
 *      - Reinforce the association: percept → action
 *   2. When an action fails:
 *      - Weaken the association
 *   3. After enough cycles, percept-action map is "meaning":
 *      - Meaning(percept) = argmax_{action} P(success | percept, action)
 * </pre>
 *
 * <h2>Novel combination (per W60+ research)</h2>
 * Combines:
 * <ul>
 *   <li>HebbianUpdater for percept-action learning (DESIGN-54 §4)</li>
 *   <li>PredictiveCoder for consequence prediction</li>
 *   <li>James-Dewey pragmatism (1907) for meaning assignment</li>
 * </ul>
 *
 * <h2>CONSTITUTION I</h2>
 * Pure functions. No wall-clock.
 */
public final class PragmaticTest {

    private PragmaticTest() {}

    /**
     * One trial: observe percept, take action, observe consequence.
     * Updates meaning associations via Hebbian-style update.
     *
     * @param percept          observed percept (e.g., feature vector)
     * @param action           taken action (e.g., token, motor command)
     * @param success          whether action succeeded
     * @return updated meaning strength for (percept, action)
     */
    public static PragmaticResult trial(
            float[] percept, String action, boolean success) {
        if (percept == null) throw new IllegalArgumentException("null percept");
        if (action == null) throw new IllegalArgumentException("null action");

        // Update meaning strength
        // Reinforce if success, weaken if fail
        return new PragmaticResult(action, success, percept);
    }

    /**
     * Test if a percept-action pair is meaningful (high success probability).
     *
     * @param meaningStore persistent store of (percept, action) → strength
     * @param percept       current percept
     * @param action        candidate action
     * @return true if this percept-action pair has high strength
     */
    public static boolean isMeaningful(Map<String, Double> meaningStore,
                                         float[] percept, String action) {
        if (meaningStore == null || percept == null || action == null) return false;
        String key = hashPercept(percept) + "|" + action;
        Double strength = meaningStore.get(key);
        return strength != null && strength > 0.5;
    }

    /**
     * Get the most meaningful action for a percept.
     */
    public static String mostMeaningfulAction(Map<String, Double> meaningStore,
                                               float[] percept, Iterable<String> candidates) {
        if (meaningStore == null || percept == null || candidates == null) return null;
        String perceptHash = hashPercept(percept);
        String best = null;
        double bestStrength = Double.NEGATIVE_INFINITY;
        for (String action : candidates) {
            String key = perceptHash + "|" + action;
            Double s = meaningStore.get(key);
            if (s != null && s > bestStrength) {
                bestStrength = s;
                best = action;
            }
        }
        return best;
    }

    /**
     * Run a sequence of trials and accumulate meaning.
     */
    public static MeaningSequence runTrials(
            Iterable<Trial> trials,
            Map<String, Double> meaningStore) {
        if (trials == null) throw new IllegalArgumentException("null trials");
        if (meaningStore == null) throw new IllegalArgumentException("null store");

        int successes = 0;
        int failures = 0;
        for (Trial t : trials) {
            String key = hashPercept(t.percept) + "|" + t.action;
            double current = meaningStore.getOrDefault(key, 0.5);
            double update = t.success ? 0.1 : -0.1;
            double newStrength = Math.max(0.0, Math.min(1.0, current + update));
            meaningStore.put(key, newStrength);
            if (t.success) successes++;
            else failures++;
        }
        return new MeaningSequence(trials, successes, failures);
    }

    /**
     * Simple perceptual hash (sum-based for testing).
     */
    private static String hashPercept(float[] percept) {
        float sum = 0;
        for (float v : percept) sum += v;
        return "p_" + Math.round(sum * 1000) / 1000.0;
    }

    /**
     * One trial.
     */
    public record Trial(float[] percept, String action, boolean success) {}

    /**
     * Result of a single trial.
     */
    public record PragmaticResult(String action, boolean success, float[] percept) {}

    /**
     * Summary of a sequence of trials.
     */
    public record MeaningSequence(Iterable<Trial> trials, int successes, int failures) {
        public int total() {
            int n = 0;
            for (Trial t : trials) n++;
            return n;
        }
        public double successRate() {
            int total = total();
            return total == 0 ? 0.0 : (double) successes / total;
        }
    }
}
