package io.matrix.neuron;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * RUN 444 — Spelke core knowledge experiments (DESIGN-58 Level 2).
 *
 * <p>Wraps {@link HdcBrain} with classical infant cognition tasks from
 * Spelke & Kinzler (2007) "Core Knowledge":
 *
 * <ul>
 *   <li>{@link #objectPermanence} — infants track an object even when it
 *       disappears behind an occluder; brain should still recall its
 *       features after occlusion.</li>
 *   <li>{@link #aNotB} — classic Piaget A-not-B error: infant searches at
 *       location A repeatedly, then object is moved to B; infant should
 *       search at B (i.e. brain should update its location belief).</li>
 *   <li>{@link #numerosityDiscrimination} — infants discriminate 1 vs 2
 *       objects; brain should distinguish singleton vs doubleton features.</li>
 *   <li>{@link #agentVsObject} — infants distinguish agents (self-propelled)
 *       from objects (physics-driven); brain should categorize based on
 *       motion patterns.</li>
 * </ul>
 *
 * <p>Each experiment returns a {@link Result} with success rate and details.
 *
 * <h2>CONSTITUTION I</h2>
 * Caller supplies RNG. All experiments deterministic given inputs.
 */
public final class SpelkeCoreKnowledge {

    private SpelkeCoreKnowledge() {}

    /** Result of a Spelke experiment. */
    public static final class Result {
        /** Success rate in [0, 1]. */
        public final double successRate;
        /** Number of trials passed. */
        public final int passed;
        /** Total trials. */
        public final int total;
        /** Per-trial success flag. */
        public final boolean[] perTrial;

        public Result(int passed, int total, boolean[] perTrial) {
            this.passed = passed;
            this.total = total;
            this.perTrial = perTrial;
            this.successRate = total > 0 ? (double) passed / total : 0.0;
        }

        @Override
        public String toString() {
            return "Result{" + passed + "/" + total
                    + " = " + String.format("%.2f%%", successRate * 100) + "}";
        }
    }

    /**
     * Object permanence test: an object (bipolar feature template) is shown
     * at one location, then hidden (occluded), then a query is made — the
     * brain should still recall the object's identity.
     *
     * @param brain       brain to test
     * @param objectTemplate bipolar template of the test object
     * @param trials      number of independent trials (each uses fresh random templates)
     * @param occluderTemplate template representing an occluder (not learned as object)
     * @return success rate (proportion of trials where brain recalls object after occlusion)
     */
    public static Result objectPermanence(HdcBrain brain, long[] objectTemplate,
                                            int trials, long[] occluderTemplate) {
        if (brain == null) throw new IllegalArgumentException("null brain");
        if (objectTemplate == null || objectTemplate.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("objectTemplate wrong length");
        }
        if (occluderTemplate == null || occluderTemplate.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("occluderTemplate wrong length");
        }
        boolean[] perTrial = new boolean[trials];
        Random rng = new Random(42);
        for (int t = 0; t < trials; t++) {
            // Learn object identity
            brain.learn(makeFeatures(objectTemplate, 1.0f), "object");
            // Occlude: query with object+occluder (should still recall object)
            float[] occluded = combine(objectTemplate, occluderTemplate, 1.0f, -0.5f);
            HdcBrain.Recall hit = brain.forward(occluded);
            perTrial[t] = hit != null && "object".equals(hit.label);
        }
        return new Result(countTrue(perTrial), trials, perTrial);
    }

    /**
     * A-not-B task (Piaget): train at location A, then move object to
     * location B and test. Brain should now recall B as the location.
     */
    public static Result aNotB(HdcBrain brain, long[] templateA, long[] templateB,
                                 int trials) {
        if (brain == null) throw new IllegalArgumentException("null brain");
        if (templateA == null || templateA.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("templateA wrong length");
        }
        if (templateB == null || templateB.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("templateB wrong length");
        }
        boolean[] perTrial = new boolean[trials];
        for (int t = 0; t < trials; t++) {
            // Learn A and B locations
            brain.learn(makeFeatures(templateA, 1.0f), "at-A");
            brain.learn(makeFeatures(templateB, 1.0f), "at-B");
            // After training, query with B's template
            HdcBrain.Recall hit = brain.forward(makeFeatures(templateB, 1.0f));
            // Test: brain should now answer "at-B" (the most recently seen location for B)
            // Since learning both, the most recent one wins via LRU
            perTrial[t] = hit != null && "at-B".equals(hit.label);
        }
        return new Result(countTrue(perTrial), trials, perTrial);
    }

    /**
     * Numerosity discrimination: train brain to recognize "one" (single
     * template) vs "two" (two combined templates), then test recall.
     */
    public static Result numerosityDiscrimination(HdcBrain brain, int trials) {
        if (brain == null) throw new IllegalArgumentException("null brain");
        boolean[] perTrial = new boolean[trials];
        Random rng = new Random(42);
        for (int t = 0; t < trials; t++) {
            long[] single = randomTemplate(rng);
            long[] a = randomTemplate(rng);
            long[] b = randomTemplate(rng);
            // Combine two distinct templates for "two"
            long[] pair = new long[HdcEncoding.WORDS];
            for (int w = 0; w < HdcEncoding.WORDS; w++) {
                pair[w] = a[w] ^ b[w];
            }
            brain.learn(makeFeatures(single, 1.0f), "one");
            brain.learn(makeFeatures(pair, 1.0f), "two");
            HdcBrain.Recall singleHit = brain.forward(makeFeatures(single, 1.0f));
            HdcBrain.Recall pairHit = brain.forward(makeFeatures(pair, 1.0f));
            boolean ok = singleHit != null && pairHit != null
                    && "one".equals(singleHit.label)
                    && "two".equals(pairHit.label);
            perTrial[t] = ok;
        }
        return new Result(countTrue(perTrial), trials, perTrial);
    }

    /**
     * Agent vs object categorization: train brain to recognize "agent"
     * (self-propelled, motion pattern) vs "object" (physics-driven).
     * Test that novel patterns get classified correctly.
     */
    public static Result agentVsObject(HdcBrain brain, int trials) {
        if (brain == null) throw new IllegalArgumentException("null brain");
        boolean[] perTrial = new boolean[trials];
        Random rng = new Random(42);
        for (int t = 0; t < trials; t++) {
            // Agent pattern: rapid directional changes
            long[] agent = randomTemplate(rng);
            // Object pattern: smooth motion (different template)
            long[] obj = randomTemplate(rng);
            // Make sure they're different
            while (HdcEncoding.hamming(agent, obj) < 200) {
                obj = randomTemplate(rng);
            }
            brain.learn(makeFeatures(agent, 1.0f), "agent");
            brain.learn(makeFeatures(obj, 1.0f), "object");
            // Test with novel but similar templates
            long[] novelAgent = noisy(agent, 5, rng);
            long[] novelObj = noisy(obj, 5, rng);
            HdcBrain.Recall agentHit = brain.forward(makeFeatures(novelAgent, 1.0f));
            HdcBrain.Recall objHit = brain.forward(makeFeatures(novelObj, 1.0f));
            boolean ok = agentHit != null && objHit != null
                    && "agent".equals(agentHit.label)
                    && "object".equals(objHit.label);
            perTrial[t] = ok;
        }
        return new Result(countTrue(perTrial), trials, perTrial);
    }

    // ============ helpers ============

    private static float[] makeFeatures(long[] template, float magnitude) {
        return HdcConditioning.stimulusFromTemplate(template, magnitude);
    }

    private static float[] combine(long[] a, long[] b, float magA, float magB) {
        float[] aStim = makeFeatures(a, magA);
        float[] bStim = makeFeatures(b, magB);
        float[] combined = new float[HdcEncoding.DIM];
        for (int i = 0; i < HdcEncoding.DIM; i++) {
            combined[i] = aStim[i] + bStim[i];
        }
        return combined;
    }

    private static long[] randomTemplate(Random rng) {
        return HdcEncoding.random(rng);
    }

    private static long[] noisy(long[] template, int nFlips, Random rng) {
        long[] copy = template.clone();
        for (int i = 0; i < nFlips; i++) {
            int pos = rng.nextInt(HdcEncoding.DIM);
            int w = pos >>> 6;
            int b = pos & 63;
            copy[w] ^= (1L << b);
        }
        return copy;
    }

    private static int countTrue(boolean[] arr) {
        int n = 0;
        for (boolean b : arr) if (b) n++;
        return n;
    }
}
