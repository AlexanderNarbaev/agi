package io.matrix.knowledge.benchmark;

import io.matrix.knowledge.Claim;
import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.TextFeatures;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A BM25 index over the claims of a workspace — the retrieval baseline the
 * W42 mission requires (RECON-W42 §14: "Knowledge retrieval | Measured
 * against BM25 and lexical baselines").
 *
 * <p><b>Why this exists.</b> MATRIX's own retriever
 * ({@code io.matrix.knowledge.KnowledgeRetriever}) scores a claim by the
 * IDF-weighted share of the question's terms it covers, and reports the
 * claims it used. That is a reasonable scoring function, but "reasonable"
 * is not a measurement: without a baseline, no improvement to the retrieval
 * can be attributed to anything. BM25 is the standard baseline to compare
 * against.
 *
 * <p><b>The formula.</b> Robertson & Zaragoza's probabilistic relevance
 * framework (2009, <i>The Probabilistic Relevance Framework: BM25 and
 * Beyond</i>, Foundations and Trends in Information Retrieval 3(4)) — the
 * reference is the primary source, not a blog post:
 *
 * <pre>
 *   score(D, Q) = SUM over q in Q of
 *       IDF(q) * (f(q,D) * (k1 + 1)) / (f(q,D) + k1 * (1 - b + b * |D| / avgdl))
 *
 *   IDF(q) = ln( (N - df(q) + 0.5) / (df(q) + 0.5) + 1 )
 * </pre>
 *
 * <p>The {@code +1} inside the logarithm is the probabilistic IDF variant:
 * it keeps the IDF non-negative for a term that appears in every document,
 * so a common term lowers the score without flipping its sign. The default
 * parameters are {@code k1 = 1.2} and {@code b = 0.75}, the values the
 * original paper reports as robust across collections.
 *
 * <p><b>What this is not.</b> This is a baseline, not a proposal. It scores
 * the same propositions with a different function; it knows nothing about
 * warrants, provenance, scope or derivation, and it cannot answer a
 * question. Its only job is to answer the question "would a standard
 * lexical ranker have found the claims MATRIX found?".
 */
public final class Bm25Index {

    /** The term-frequency saturation parameter. */
    public static final double DEFAULT_K1 = 1.2;

    /** The document-length normalisation parameter. */
    public static final double DEFAULT_B = 0.75;

    private final Map<String, Claim> claimsById = new LinkedHashMap<>();
    private final Map<String, List<String>> termsByClaim = new LinkedHashMap<>();
    private final Map<String, Integer> documentFrequency = new HashMap<>();
    private final Map<String, Double> idfByTerm = new HashMap<>();
    private final double averageLength;
    private final int documentCount;

    public Bm25Index(KnowledgeSnapshot snapshot) {
        this(snapshot, DEFAULT_K1, DEFAULT_B);
    }

    public Bm25Index(KnowledgeSnapshot snapshot, double k1, double b) {
        for (Claim claim : snapshot.claims()) {
            List<String> terms = TextFeatures.tokens(claim.proposition());
            if (terms.isEmpty()) {
                continue;
            }
            claimsById.put(claim.id(), claim);
            termsByClaim.put(claim.id(), terms);
            for (String term : new java.util.LinkedHashSet<>(terms)) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }
        this.documentCount = termsByClaim.size();
        long total = 0L;
        for (List<String> terms : termsByClaim.values()) {
            total += terms.size();
        }
        this.averageLength = documentCount == 0 ? 0.0 : (double) total / documentCount;
        for (Map.Entry<String, Integer> entry : documentFrequency.entrySet()) {
            idfByTerm.put(entry.getKey(), idf(entry.getValue()));
        }
        this.k1 = k1;
        this.b = b;
    }

    private final double k1;
    private final double b;

    /** The probabilistic IDF of a term with the given document frequency. */
    public double idf(int frequency) {
        return Math.log((documentCount - frequency + 0.5) / (frequency + 0.5) + 1.0);
    }

    /** The BM25 score of a claim against the query terms. */
    public double score(String claimId, List<String> queryTerms) {
        List<String> terms = termsByClaim.get(claimId);
        if (terms == null) {
            return 0.0;
        }
        Map<String, Integer> frequencies = new HashMap<>();
        for (String term : terms) {
            frequencies.merge(term, 1, Integer::sum);
        }
        double length = terms.size();
        double total = 0.0;
        for (String queryTerm : queryTerms) {
            Integer frequency = frequencies.get(queryTerm);
            if (frequency == null) {
                continue;
            }
            double idf = idfByTerm.getOrDefault(queryTerm, 0.0);
            double numerator = frequency * (k1 + 1.0);
            double denominator = frequency + k1 * (1.0 - b + b * length / averageLength);
            total += idf * numerator / denominator;
        }
        return total;
    }

    /** The claims ranked by BM25 against the query, best first. */
    public List<Scored> rank(List<String> queryTerms) {
        List<Scored> scored = new ArrayList<>();
        for (String claimId : termsByClaim.keySet()) {
            scored.add(new Scored(claimsById.get(claimId), score(claimId, queryTerms)));
        }
        scored.sort((left, right) -> Double.compare(right.score(), left.score()));
        return scored;
    }

    /** The claims ranked by BM25 against a raw question. */
    public List<Scored> rank(String question) {
        return rank(TextFeatures.tokens(question));
    }

    /** How many claims the index covers. */
    public int documentCount() {
        return documentCount;
    }

    /** The average claim length in tokens. */
    public double averageLength() {
        return averageLength;
    }

    /** One scored claim. */
    public record Scored(Claim claim, double score) {
    }
}
