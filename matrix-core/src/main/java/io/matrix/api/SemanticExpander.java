package io.matrix.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RUN 30 — Semantic query expansion via character n-grams.
 *
 * <p>Boosts retrieval for queries that don't share exact tokens with
 * the indexed corpus. Splits both query and corpus tokens into
 * character trigrams, then a token is considered "fuzzy-match" if
 * it shares at least {@code threshold} trigrams with the query.
 *
 * <p>Example: query "квантовый компьютер" vs indexed "квантовые системы".
 * Exact-token match finds nothing, but trigram overlap (квантов, антов,
 * нтовов, ...) gives partial credit.
 *
 * <p>This is NOT a substitute for true embeddings (which would require
 * a 200M-param model). It's a cheap heuristic for cases where the user
 * types a related phrase and the system finds a plausible match.
 *
 * <p>Deterministic: no random source, no wall-clock (AGENTS.md compliant).
 */
public class SemanticExpander {

    /** N-gram size for character-level expansion. */
    public static final int NGRAM_SIZE = 3;

    /** Minimum trigram overlap ratio to consider a fuzzy match. */
    public static final double DEFAULT_THRESHOLD = 0.3;

    private final int ngramSize;
    private final double threshold;

    public SemanticExpander() {
        this(NGRAM_SIZE, DEFAULT_THRESHOLD);
    }

    public SemanticExpander(int ngramSize, double threshold) {
        if (ngramSize < 2) ngramSize = 2;
        if (threshold < 0) threshold = 0;
        if (threshold > 1) threshold = 1;
        this.ngramSize = ngramSize;
        this.threshold = threshold;
    }

    /**
     * Expand a query into a set of tokens, including:
     * <ul>
     *   <li>Original tokens (lowercased)</li>
     *   <li>Character n-grams of each token</li>
     *   <li>Fuzzy-matched tokens from the candidate vocabulary that share
     *       enough trigrams with the query</li>
     * </ul>
     *
     * @param query          the user query
     * @param vocabTokens    candidate vocabulary (e.g., from QaCorpusIndex)
     * @return expanded token set
     */
    public Set<String> expand(String query, Set<String> vocabTokens) {
        Set<String> out = new HashSet<>();
        if (query == null || query.isBlank()) return out;

        // 1. Add original tokens
        Set<String> qTokens = tokenize(query);
        out.addAll(qTokens);

        // 2. Add character n-grams of each query token
        Set<String> qNgrams = new HashSet<>();
        for (String tok : qTokens) {
            qNgrams.addAll(charNgrams(tok));
        }

        // 3. For each vocab token, compute trigram overlap with qNgrams.
        //    If overlap >= threshold × |vocabTrigrams|, include it as a
        //    fuzzy match.
        if (vocabTokens != null && !vocabTokens.isEmpty()) {
            for (String vocab : vocabTokens) {
                Set<String> vNgrams = charNgrams(vocab);
                if (vNgrams.isEmpty()) continue;
                long common = 0;
                for (String ng : vNgrams) {
                    if (qNgrams.contains(ng)) common++;
                }
                double ratio = (double) common / vNgrams.size();
                if (ratio >= threshold) {
                    out.add(vocab);
                }
            }
        }

        return out;
    }

    /** Jaccard similarity between two sets of strings. */
    public double jaccard(Set<String> a, Set<String> b) {
        if (a == null || b == null) return 0.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) inter.size() / union.size();
    }

    /** Character n-grams of a single token. Pads with # to keep short tokens matchable. */
    public Set<String> charNgrams(String token) {
        Set<String> out = new HashSet<>();
        if (token == null || token.isEmpty()) return out;
        String padded = "#" + token.toLowerCase() + "#";
        if (padded.length() < ngramSize) {
            out.add(padded);
            return out;
        }
        for (int i = 0; i <= padded.length() - ngramSize; i++) {
            out.add(padded.substring(i, i + ngramSize));
        }
        return out;
    }

    private Set<String> tokenize(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) return out;
        // Same tokenization rules as QaCorpusIndex: lowercase, split on non-word.
        String lower = text.toLowerCase();
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                buf.append(c);
            } else {
                if (buf.length() > 1) out.add(buf.toString());
                buf.setLength(0);
            }
        }
        if (buf.length() > 1) out.add(buf.toString());
        return out;
    }

    public int ngramSize() { return ngramSize; }
    public double threshold() { return threshold; }
}
