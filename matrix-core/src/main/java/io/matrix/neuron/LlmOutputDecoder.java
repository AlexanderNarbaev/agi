package io.matrix.neuron;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * RUN 448 — LLM output → HDC concept extraction (DESIGN-54 §8, Level 5).
 *
 * <p>Inverse of {@link HdcAsLlmPreprocessor}: given an LLM output token
 * sequence (or HDC code), extract the most likely source concepts by
 * Hamming-distance lookup against a codebook of known concepts.
 *
 * <h2>Use case</h2>
 * <p>When LLM generates a long-form response, we extract concepts (named
 * entities, topics) by matching against a known concept codebook. This
 * enables downstream routing, summarization, and memory augmentation
 * without expensive dense-embedding lookups.
 *
 * <h2>API</h2>
 * <pre>
 *   LlmOutputDecoder dec = new LlmOutputDecoder(new Random(42));
 *   dec.registerConcept("artificial intelligence", tokens);
 *   List&lt;String&gt; concepts = dec.extractConcepts(llmOutputTokens, 5);
 * </pre>
 *
 * <h2>CONSTITUTION I</h2>
 * Caller supplies RNG. All operations deterministic given inputs.
 */
public final class LlmOutputDecoder {

    private final Random rng;
    private final long instanceSalt;
    private final Map<String, long[]> codebook;

    public LlmOutputDecoder(Random rng) {
        if (rng == null) throw new IllegalArgumentException("null rng");
        this.rng = rng;
        this.instanceSalt = rng.nextLong();
        this.codebook = new HashMap<>();
    }

    /**
     * Register a concept for future matching. The token array encodes the
     * concept into an HDC code via position-shifted XOR bundling.
     */
    public void registerConcept(String concept, int[] tokens) {
        if (concept == null) throw new IllegalArgumentException("null concept");
        if (tokens == null) throw new IllegalArgumentException("null tokens");
        long[] code = encodeTokens(tokens);
        codebook.put(concept, code);
    }

    /**
     * Register a concept from a string (using simple char-level encoding).
     */
    public void registerConceptFromText(String concept, String text) {
        if (concept == null) throw new IllegalArgumentException("null concept");
        if (text == null) throw new IllegalArgumentException("null text");
        int[] charTokens = new int[text.length()];
        for (int i = 0; i < text.length(); i++) {
            charTokens[i] = (int) text.charAt(i);
        }
        registerConcept(concept, charTokens);
    }

    /**
     * Extract top-K concepts from LLM output tokens by Hamming distance.
     * Returns concepts sorted ascending by distance.
     */
    public List<Match> extractConcepts(int[] llmOutputTokens, int k) {
        if (llmOutputTokens == null) throw new IllegalArgumentException("null tokens");
        if (k <= 0 || codebook.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        long[] queryCode = encodeTokens(llmOutputTokens);
        List<Match> all = new ArrayList<>(codebook.size());
        for (Map.Entry<String, long[]> e : codebook.entrySet()) {
            int d = HdcEncoding.hamming(queryCode, e.getValue());
            double sim = 1.0 - 2.0 * d / (double) HdcEncoding.DIM;
            all.add(new Match(e.getKey(), d, sim));
        }
        all.sort(Comparator.comparingInt(m -> m.distance));
        if (all.size() > k) return all.subList(0, k);
        return all;
    }

    /**
     * Extract top-K concepts from an LLM output string.
     */
    public List<Match> extractConceptsFromText(String llmOutput, int k) {
        if (llmOutput == null) throw new IllegalArgumentException("null output");
        int[] tokens = new int[llmOutput.length()];
        for (int i = 0; i < llmOutput.length(); i++) {
            tokens[i] = (int) llmOutput.charAt(i);
        }
        return extractConcepts(tokens, k);
    }

    /**
     * Get the HDC code for a registered concept.
     */
    public long[] getCode(String concept) {
        return codebook.get(concept);
    }

    /**
     * Number of registered concepts.
     */
    public int size() {
        return codebook.size();
    }

    /**
     * All registered concept labels.
     */
    public java.util.Set<String> concepts() {
        return new java.util.LinkedHashSet<>(codebook.keySet());
    }

    /**
     * Remove a concept.
     */
    public boolean remove(String concept) {
        return codebook.remove(concept) != null;
    }

    /**
     * Clear all concepts.
     */
    public void clear() {
        codebook.clear();
    }

    private long[] encodeTokens(int[] tokens) {
        long[] result = HdcEncoding.zero();
        for (int i = 0; i < tokens.length; i++) {
            long tokenSeed = ((long) tokens[i]) * 0x9E3779B97F4A7C15L
                    ^ ((long) i << 32) ^ instanceSalt;
            Random tokenRng = new Random(tokenSeed);
            long[] tokenCode = HdcEncoding.random(tokenRng);
            long[] positioned = HdcEncoding.permute(tokenCode, i);
            result = HdcEncoding.xor(result, positioned);
        }
        return result;
    }

    /** Match result: concept label, Hamming distance, bipolar similarity. */
    public static final class Match {
        public final String concept;
        public final int distance;
        public final double similarity;

        public Match(String concept, int distance, double similarity) {
            this.concept = concept;
            this.distance = distance;
            this.similarity = similarity;
        }

        @Override
        public String toString() {
            return "Match{concept=" + concept + ", dist=" + distance
                    + ", sim=" + String.format("%.3f", similarity) + "}";
        }
    }
}
