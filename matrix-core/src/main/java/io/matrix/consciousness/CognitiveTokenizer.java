package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * W246 — Cognitive Tokenizer (BPE-style).
 *
 * <p>Inspired by Byte-Pair Encoding (BPE, Sennrich et al. 2016). Used
 * in GPT, Llama, Mistral tokenizers.
 *
 * <p>Algorithm:
 * 1. Start with character-level vocabulary
 * 2. Iteratively merge most frequent pair
 * 3. Repeat until target vocabulary size reached
 *
 * <p>In MATRIX: tokenize cognitive profile fields into discrete tokens.
 *
 * <p>CONSTITUTION VI compliance: tokenized cognitive state, not
 * phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random seeded.
 */
public final class CognitiveTokenizer {

    private final int vocabSize;
    private final long seed;
    private final Map<String, Integer> vocab;
    private final Map<Integer, String> reverseVocab;

    public CognitiveTokenizer(int vocabSize, long seed) {
        if (vocabSize < 1) throw new IllegalArgumentException("vocabSize must be >= 1");
        this.vocabSize = vocabSize;
        this.seed = seed;
        this.vocab = new HashMap<>();
        this.reverseVocab = new HashMap<>();
        // Initialize with character tokens (placeholder alphabet)
        for (int i = 0; i < 26; i++) {
            String ch = String.valueOf((char) ('a' + i));
            vocab.put(ch, i);
            reverseVocab.put(i, ch);
        }
    }

    /**
     * Tokenize a string.
     */
    public int[] tokenize(String input) {
        if (input == null) return new int[0];
        // Simple character-level tokenization
        List<Integer> tokens = new ArrayList<>();
        for (char c : input.toCharArray()) {
            String ch = String.valueOf(c).toLowerCase();
            Integer tokenId = vocab.get(ch);
            if (tokenId == null) {
                // Unknown token: use random new token
                tokenId = vocab.size() % vocabSize;
                vocab.putIfAbsent(ch, tokenId);
                reverseVocab.putIfAbsent(tokenId, ch);
            }
            tokens.add(tokenId);
            if (tokens.size() >= vocabSize) break;
        }
        int[] result = new int[tokens.size()];
        for (int i = 0; i < tokens.size(); i++) result[i] = tokens.get(i);
        return result;
    }

    /**
     * Decode token IDs back to string.
     */
    public String decode(int[] tokens) {
        if (tokens == null || tokens.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int t : tokens) {
            String ch = reverseVocab.get(t);
            if (ch != null) sb.append(ch);
        }
        return sb.toString();
    }

    /**
     * BPE merge: merge the most frequent pair.
     */
    public void mergeMostFrequent(String[] corpus) {
        if (corpus == null || corpus.length == 0) return;
        // Find most frequent adjacent pair
        Map<String, Integer> pairCounts = new HashMap<>();
        for (String word : corpus) {
            String[] chars = word.split("");
            for (int i = 0; i < chars.length - 1; i++) {
                String pair = chars[i] + "," + chars[i + 1];
                pairCounts.merge(pair, 1, Integer::sum);
            }
        }
        if (pairCounts.isEmpty()) return;
        String bestPair = null;
        int maxCount = 0;
        for (Map.Entry<String, Integer> e : pairCounts.entrySet()) {
            if (e.getValue() > maxCount) {
                maxCount = e.getValue();
                bestPair = e.getKey();
            }
        }
        if (bestPair == null || vocab.size() >= vocabSize) return;
        String merged = bestPair.replace(",", "");
        if (!vocab.containsKey(merged)) {
            int newId = vocab.size();
            vocab.put(merged, newId);
            reverseVocab.put(newId, merged);
        }
    }

    /** Get vocabulary size. */
    public int vocabSize() { return vocab.size(); }

    /** Get target vocab size. */
    public int targetVocabSize() { return vocabSize; }
}
