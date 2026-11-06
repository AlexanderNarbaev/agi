package io.matrix.cognitive.algorithms;

/**
 * W44.2 — the problem families the portfolio serves.
 *
 * <p>A family is the coarse question ("which kind of computation is this?"), not the algorithm
 * that will answer it. The selector never sees a family without a method set: it asks the
 * registry for the methods whose capability declares that family, and it decides among exactly
 * those. A family with no method is a refusal, not a fallback to "the default algorithm" —
 * there is no default.</p>
 *
 * <p>The names are constants rather than string literals at the call sites because the selector
 * compares them by equality: a typo in a family name would silently produce an empty candidate
 * set and a REFUSED decision that looks like a policy outcome rather than a typo.</p>
 */
public final class AlgorithmFamilies {

    /** Exact integer arithmetic over an expression; results are {@code BigInteger}. */
    public static final String EXACT_ARITHMETIC = "arithmetic.exact-integer";

    /** Whether a conclusion follows from a set of premises and rules. */
    public static final String DEDUCTIVE_REASONING = "reasoning.deductive-entailment";

    /** Single-source shortest paths over a weighted directed graph. */
    public static final String SHORTEST_PATH = "graph.shortest-path";

    /** Membership of a key in a corpus. */
    public static final String MEMBERSHIP_LOOKUP = "collection.membership";

    /** A previously validated procedure, reused inside its applicability domain. */
    public static final String REUSABLE_PROCEDURE = "procedure.reuse";

    private AlgorithmFamilies() {
    }
}
