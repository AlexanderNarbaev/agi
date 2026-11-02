package io.matrix.cognitive.algorithms;

/**
 * W44.2 — the names of the precondition facts the portfolio understands.
 *
 * <p>A precondition fact is a NAMED statement about the input of a computation: "the weights are
 * non-negative", "the theory fits inside the atom budget", "the index has already been built".
 * The selector admits a method only when every fact the method declares is present in the
 * request's {@link ProblemRequest#preconditionFacts()}, so a method cannot be selected outside
 * the domain it declared.</p>
 *
 * <p>Why a shared vocabulary and not free text: the facts travel between the caller, the
 * capability and the decision record. If each method spelled its own wording, the selector would
 * be comparing strings that were never meant to be equal, and a mismatch would be a silent
 * refusal. Named constants make the vocabulary inspectable in one place.</p>
 *
 * <p>These are the facts this package's own methods use. A caller may put any other name in a
 * request; a method that does not declare it is simply unaffected.</p>
 */
public final class Preconditions {

    /**
     * The expression is a well-formed integer arithmetic expression.
     *
     * <p>Declared by the exact-arithmetic method and re-checked by the method itself, because a
     * caller that asserts the fact wrongly must not be able to make the method compute garbage.</p>
     */
    public static final String WELL_FORMED_EXPRESSION = "WELL_FORMED_EXPRESSION";

    /**
     * The theory fits inside the entailment checker's atom budget.
     *
     * <p>Past the budget the checker enumerates 2^n valuations, which is no longer affordable;
     * the honest answer there is UNDECIDED, not a guess.</p>
     */
    public static final String THEORY_WITHIN_ATOM_BUDGET = "THEORY_WITHIN_ATOM_BUDGET";

    /**
     * Every edge weight is non-negative.
     *
     * <p>Dijkstra's javadoc says "non-negative weights" and its code never checks it. A negative
     * edge makes the greedy expansion wrong, so the wrapper that owns Dijkstra declares this fact
     * and enforces it — the fact is the contract, and the wrapper is where the contract lives.</p>
     */
    public static final String NON_NEGATIVE_WEIGHTS = "NON_NEGATIVE_WEIGHTS";

    /**
     * A prebuilt index exists for the corpus.
     *
     * <p>Building an index costs time; querying it costs less than scanning. Whether that trade
     * pays depends on how many queries follow, which is a decision for the caller, not for the
     * method. The fact records that the caller has already decided to build it.</p>
     */
    public static final String INDEX_PREBUILT = "INDEX_PREBUILT";

    /**
     * The caller accepts a bounded result that may not be proven.
     *
     * <p>An anytime method can stop early and hand back what it has. That is useful and it is
     * also not the same thing as an answer, so the caller has to say that it will take one. The
     * fact is the difference between "the cheapest path is 7" and "nothing cheaper than 7 was
     * found in the nodes I could afford".</p>
     */
    public static final String BOUNDED_RESULT_ACCEPTED = "BOUNDED_RESULT_ACCEPTED";

    private Preconditions() {
    }
}
