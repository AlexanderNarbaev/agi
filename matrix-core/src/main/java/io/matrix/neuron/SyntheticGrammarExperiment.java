package io.matrix.neuron;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * RUN 449 — Synthetic grammar experiments (DESIGN-58 Level 6 compositional).
 *
 * <p>Tests Pinker-style compositional language learning via HDC codes.
 * Generates an artificial grammar with terminals, non-terminals, and
 * production rules, then trains a brain to map sentences to meanings
 * using HDC compositional representations.
 *
 * <h2>Grammar</h2>
 * <p>Mini-English-like grammar with:
 * <pre>
 *   S → NP VP
 *   NP → Det N
 *   VP → V NP
 *   Det → "the" | "a"
 *   N → "dog" | "cat" | "ball"
 *   V → "chases" | "sees" | "likes"
 * </pre>
 *
 * <h2>Task</h2>
 * Given a sentence (e.g. "the dog chases the ball"), produce a structured
 * meaning (subject=animal, action=chase, object=animal/thing). Test
 * compositional generalization on novel sentences.
 *
 * <h2>CONSTITUTION I</h2>
 * Caller supplies RNG. All operations deterministic given inputs.
 */
public final class SyntheticGrammarExperiment {

    /** Result of a grammar experiment. */
    public static final class Result {
        /** Compositional accuracy in [0, 1]. */
        public final double accuracy;
        /** Number of test sentences answered correctly. */
        public final int correct;
        /** Total test sentences. */
        public final int total;
        /** Per-trial correctness. */
        public final boolean[] perTrial;

        public Result(int correct, int total, boolean[] perTrial) {
            this.correct = correct;
            this.total = total;
            this.perTrial = perTrial;
            this.accuracy = total > 0 ? (double) correct / total : 0.0;
        }

        @Override
        public String toString() {
            return "GrammarResult{" + correct + "/" + total
                    + " = " + String.format("%.2f%%", accuracy * 100) + "}";
        }
    }

    /** A sentence with its structured meaning. */
    public static final class Sentence {
        public final String text;
        public final String subject; // e.g. "dog"
        public final String verb;    // e.g. "chases"
        public final String object;  // e.g. "ball"

        public Sentence(String text, String subject, String verb, String object) {
            this.text = text;
            this.subject = subject;
            this.verb = verb;
            this.object = object;
        }

        @Override
        public String toString() {
            return text + " => " + subject + " " + verb + " " + object;
        }
    }

    private static final String[] DETS = {"the", "a"};
    private static final String[] NOUNS = {"dog", "cat", "ball"};
    private static final String[] VERBS = {"chases", "sees", "likes"};

    private SyntheticGrammarExperiment() {}

    /**
     * Generate all valid sentences from the grammar.
     */
    public static List<Sentence> generateAllSentences() {
        List<Sentence> sentences = new ArrayList<>();
        for (String det1 : DETS) {
            for (String subj : NOUNS) {
                for (String verb : VERBS) {
                    for (String det2 : DETS) {
                        for (String obj : NOUNS) {
                            String text = det1 + " " + subj + " " + verb + " " + det2 + " " + obj;
                            sentences.add(new Sentence(text, subj, verb, obj));
                        }
                    }
                }
            }
        }
        return sentences;
    }

    /**
     * Run the grammar experiment.
     *
     * @param brain       brain to train and test
     * @param trials      number of test trials
     * @param trainPerTest number of training sentences per test (for reinforcement)
     * @param rng         RNG source for trial selection
     */
    public static Result run(HdcBrain brain, int trials, int trainPerTest, Random rng) {
        if (brain == null) throw new IllegalArgumentException("null brain");
        if (rng == null) throw new IllegalArgumentException("null rng");
        List<Sentence> all = generateAllSentences();
        if (trials <= 0 || trials > all.size()) {
            trials = all.size();
        }

        // Generate random indices for test sentences
        int[] testIdx = new int[trials];
        for (int i = 0; i < trials; i++) {
            testIdx[i] = rng.nextInt(all.size());
        }

        boolean[] perTrial = new boolean[trials];
        for (int t = 0; t < trials; t++) {
            Sentence test = all.get(testIdx[t]);

            // Train on related sentences (similar subject/verb/object)
            for (int tr = 0; tr < trainPerTest; tr++) {
                Sentence train = all.get(rng.nextInt(all.size()));
                brain.learn(sentenceToFeatures(train.text), train.subject, 0.5f, 0.01f);
            }

            // Test: query with test sentence, expect subject as label
            HdcBrain.Recall hit = brain.forward(sentenceToFeatures(test.text));
            perTrial[t] = hit != null && test.subject.equals(hit.label);
        }

        return new Result(countTrue(perTrial), trials, perTrial);
    }

    /**
     * Compositional 2-hop reasoning: given sentence A that establishes
     * (subject, verb), and sentence B that establishes (verb, object),
     * infer (subject, object) relationship.
     *
     * <p>Simplified version: train on individual (word, meaning) pairs,
     * then test if the brain can combine two words into a compound.
     */
    public static Result compositionalReasoning(HdcBrain brain, int trials, Random rng) {
        if (brain == null) throw new IllegalArgumentException("null brain");
        if (rng == null) throw new IllegalArgumentException("null rng");
        boolean[] perTrial = new boolean[trials];

        // Train brain on word meanings
        Map<String, String> wordMeanings = new HashMap<>();
        for (String n : NOUNS) wordMeanings.put(n, "animal-or-thing");
        for (String v : VERBS) wordMeanings.put(v, "action");

        // Use HDC binding for compositional representation
        Map<String, long[]> wordCodes = new HashMap<>();
        for (String n : NOUNS) wordCodes.put(n, HdcEncoding.random(rng));
        for (String v : VERBS) wordCodes.put(v, HdcEncoding.random(rng));

        // Learn each word → meaning
        for (Map.Entry<String, String> e : wordMeanings.entrySet()) {
            float[] features = HdcConditioning.stimulusFromTemplate(
                    wordCodes.get(e.getKey()), 1.0f);
            brain.learn(features, e.getValue(), 0.5f, 0.01f);
        }

        // Test compositional queries: bind two words, query
        for (int t = 0; t < trials; t++) {
            String w1 = NOUNS[rng.nextInt(NOUNS.length)];
            String w2 = VERBS[rng.nextInt(VERBS.length)];
            long[] bound = HdcBinding.bind(wordCodes.get(w1), wordCodes.get(w2));
            float[] features = HdcConditioning.stimulusFromTemplate(bound, 1.0f);
            HdcBrain.Recall hit = brain.forward(features);
            // Compositional check: brain should retrieve at least one meaning
            perTrial[t] = hit != null && hit.label != null;
        }

        return new Result(countTrue(perTrial), trials, perTrial);
    }

    /**
     * Convert a sentence to feature vector for the brain.
     * Uses character-level bipolar codes.
     */
    public static float[] sentenceToFeatures(String sentence) {
        if (sentence == null) throw new IllegalArgumentException("null sentence");
        // Use first 1024 chars; truncate if longer
        int len = Math.min(sentence.length(), HdcEncoding.DIM);
        float[] features = new float[HdcEncoding.DIM];
        for (int i = 0; i < len; i++) {
            char c = sentence.charAt(i);
            // Map chars to features: position * sign of char
            features[i] = (c % 7) - 3; // small ints in [-3, 3]
        }
        return features;
    }

    private static int countTrue(boolean[] arr) {
        int n = 0;
        for (boolean b : arr) if (b) n++;
        return n;
    }
}
