package io.matrix.knowledge;

import io.matrix.neuron.HdcEncoding;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Whether two questions are the same question (owner directive §24).
 *
 * <p>The episodic log has to decide this: a user who asks the same thing
 * three ways should produce one gap, not three. The decision was originally
 * made by an EXACT key - the sorted set of the question's folded terms - and
 * that key is exact but strict: "what is photosynthesis" and "what is
 * photosynthesis in green plants" are different keys, although a user means
 * the same thing.
 *
 * <p>This class compares two ways of deciding it:
 *
 * <ul>
 *   <li><b>Exact overlap</b> - the Jaccard coefficient of the two token
 *       sets. It is exact, free, and interpretable.</li>
 *   <li><b>Hyperdimensional</b> - the HDC encoding: each content term is
 *       projected onto a random bipolar vector, the vectors of a question
 *       are bundled, and two questions are compared by the similarity of
 *       their bundles. This is the technique the repository's HDC
 *       components exist for.</li>
 * </ul>
 *
 * <p>Both are measured, on labelled pairs, and the one that separates the
 * pairs better is the one that is used. The comparison is the point: a
 * component is integrated when it is justified, and not because it exists.
 */
public final class QuestionSimilarity {

    /** The similarity at or above which two questions are the same one. */
    public static final double DEFAULT_THRESHOLD = 0.5;

    private final int dimension;
    private final Map<String, long[]> codes;
    private final Random random;

    public QuestionSimilarity(int dimension, long seed) {
        this.dimension = dimension;
        this.random = new Random(seed);
        this.codes = new HashMap<>();
    }

    /**
     * The similarity of two questions under both methods.
     *
     * @param exact the Jaccard coefficient of the two token sets
     * @param hyperdimensional the similarity of the two bundled HDC vectors
     */
    public record Comparison(double exact, double hyperdimensional) {
        /** Whether the hyperdimensional method separates the pair better. */
        public boolean hyperdimensionalIsBetter() {
            return hyperdimensional > exact;
        }
    }

    /** The exact overlap of two questions. */
    public static double exactOverlap(String a, String b) {
        Set<String> ta = new LinkedHashSet<>(TextFeatures.tokens(a));
        Set<String> tb = new LinkedHashSet<>(TextFeatures.tokens(b));
        if (ta.isEmpty() && tb.isEmpty()) {
            return 1.0;
        }
        Set<String> intersection = new LinkedHashSet<>(ta);
        intersection.retainAll(tb);
        Set<String> union = new LinkedHashSet<>(ta);
        union.addAll(tb);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    /** The hyperdimensional similarity of two questions. */
    public double hyperdimensionalSimilarity(String a, String b) {
        long[] va = bundle(a);
        long[] vb = bundle(b);
        return HdcEncoding.similarity(va, vb);
    }

    /** Compare two questions under both methods. */
    public Comparison compare(String a, String b) {
        return new Comparison(exactOverlap(a, b), hyperdimensionalSimilarity(a, b));
    }

    /**
     * The bundle of a question's content terms.
     *
     * <p>The random projection is seeded and therefore deterministic: the
     * same term always gets the same code, so the similarity of two
     * questions does not change between runs. CONSTITUTION Article I is
     * respected - the randomness comes from the caller, not from a clock.
     */
    private long[] bundle(String question) {
        List<String> terms = new ArrayList<>(new LinkedHashSet<>(TextFeatures.tokens(question)));
        if (terms.isEmpty()) {
            return HdcEncoding.zero();
        }
        long[][] codes = new long[terms.size()][];
        for (int i = 0; i < terms.size(); i++) {
            codes[i] = code(terms.get(i));
        }
        // The bundle is the MAJORITY VOTE over the term codes, which is what
        // HdcEncoding provides. (An XOR of bipolar codes is not a bundle:
        // it destroys the shared content instead of preserving it.)
        return HdcEncoding.bundle(codes);
    }

    private long[] code(String term) {
        return codes.computeIfAbsent(term, t -> {
            long[] v = new long[dimension / Long.SIZE];
            java.util.Random local = new java.util.Random(random.nextLong());
            long[] r = HdcEncoding.random(local);
            for (int i = 0; i < v.length; i++) {
                v[i] = r[i];
            }
            return v;
        });
    }
}
