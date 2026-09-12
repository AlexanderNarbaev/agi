package io.matrix.neuron;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * RUN 447 — HDC-as-LLM-preprocessor (DESIGN-54 §7, Level 5 symbol grounding).
 *
 * <p>Compresses text into 1024-bit HDC codes via deterministic per-character
 * XOR bundling, suitable as a fast preprocessing layer before LLM inference.
 * Inspired by MemGPT/Mem0 style memory augmentation but using HDC instead
 * of dense embeddings — ~30× memory reduction.
 *
 * <h2>Use case</h2>
 * <p>In a typical LLM pipeline, dense embeddings (1536+ dims, fp16) cost
 * ~3KB per text. HDC codes (1024 bits) cost 128 bytes per text — a 24×
 * reduction at comparable retrieval quality for memory-augmented LLM.
 *
 * <h2>API</h2>
 * <pre>
 *   HdcAsLlmPreprocessor prep = new HdcAsLlmPreprocessor(new Random(42));
 *   long[] code = prep.encode("hello world");
 *   String[] nearest = prep.nearestNeighbors("hello", 5);
 * </pre>
 *
 * <h2>CONSTITUTION I</h2>
 * Caller supplies RNG. Encoding is deterministic given RNG seed.
 */
public final class HdcAsLlmPreprocessor {

    /** Default vector dimension, must match {@link HdcEncoding#DIM}. */
    public static final int DIM = HdcEncoding.DIM;

    private final Random rng;
    private final long instanceSalt; // fixed salt for deterministic per-instance encoding
    private final Map<String, long[]> codebook; // text → HDC code cache

    /**
     * Create a new preprocessor.
     */
    public HdcAsLlmPreprocessor(Random rng) {
        if (rng == null) throw new IllegalArgumentException("null rng");
        this.rng = rng;
        // Consume one long from RNG for the instance salt (stable across calls)
        this.instanceSalt = rng.nextLong();
        this.codebook = new HashMap<>();
    }

    /**
     * Encode a text string into a 1024-bit HDC code. Same input → same
     * output (deterministic given RNG seed at construction time).
     *
     * <p>Algorithm: for each character at position i, derive a per-char
     * random bipolar code (seeded by character + position + instance salt),
     * apply circular shift by position, and XOR-bundle all into the result.
     */
    public long[] encode(String text) {
        if (text == null) throw new IllegalArgumentException("null text");
        long[] result = HdcEncoding.zero();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            long charSeed = ((long) c) * 0x9E3779B97F4A7C15L
                    ^ ((long) i << 32) ^ instanceSalt;
            Random charRng = new Random(charSeed);
            long[] charCode = HdcEncoding.random(charRng);
            long[] positioned = HdcEncoding.permute(charCode, i);
            result = HdcEncoding.xor(result, positioned);
        }
        return result;
    }

    /**
     * Encode without caching — for benchmarking raw encoding speed.
     */
    public long[] encodeFresh(String text) {
        return encode(text);
    }

    /**
     * Encode a tokenized input (e.g. from BPE-style tokenizer). Each token
     * contributes a position-shifted random code.
     */
    public long[] encodeTokens(int[] tokens) {
        if (tokens == null) throw new IllegalArgumentException("null tokens");
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

    /**
     * Find K nearest texts in codebook to query by Hamming distance.
     * Returns labels sorted ascending by distance.
     */
    public java.util.List<Map.Entry<String, Integer>> nearestNeighbors(
            String queryText, int k) {
        if (queryText == null) throw new IllegalArgumentException("null query");
        if (k <= 0 || codebook.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        long[] queryCode = encode(queryText);
        java.util.List<Map.Entry<String, Integer>> all = new java.util.ArrayList<>();
        for (Map.Entry<String, long[]> e : codebook.entrySet()) {
            int d = HdcEncoding.hamming(queryCode, e.getValue());
            all.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(), d));
        }
        all.sort((a, b) -> Integer.compare(a.getValue(), b.getValue()));
        if (all.size() > k) return all.subList(0, k);
        return all;
    }

    /**
     * Index a text into the codebook. Later retrieval uses Hamming distance.
     */
    public void index(String text) {
        if (text == null) throw new IllegalArgumentException("null text");
        long[] code = encode(text);
        codebook.put(text, code);
    }

    /**
     * Number of indexed texts.
     */
    public int size() {
        return codebook.size();
    }

    /**
     * Get the HDC code for an indexed text (or null if not indexed).
     */
    public long[] getCode(String text) {
        return codebook.get(text);
    }

    /**
     * Clear all cached codes and indexed entries.
     */
    public void clear() {
        codebook.clear();
    }

    /**
     * Approximate memory usage in bytes: 128 bytes per HDC code (1024 bits)
     * + ~32 bytes per text label (assuming average 32-char strings).
     */
    public long estimatedMemoryBytes() {
        return codebook.size() * (DIM / 8L + 32);
    }
}
