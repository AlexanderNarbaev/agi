package io.matrix.cognitive.algorithms;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * W44.2 — the membership family's method that pays nothing up front.
 *
 * <p>It answers each query by walking the corpus. There is no build cost and no state, which is
 * exactly why it is the right method for a single query or for a corpus too small to be worth
 * indexing. Its weakness is that the cost is per query and it does not shrink as the query count
 * grows.</p>
 *
 * <p>The method declares NO precondition, and that is the point: it is the method the selector
 * falls back to when the indexed alternatives are not admitted. If the caller did not build an
 * index — or built one for a corpus of two entries — this is what runs, and it answers
 * correctly.</p>
 */
public final class LinearScanMembershipMethod
        implements AlgorithmMethod<MembershipProblem, MembershipProblem.MembershipOutcome> {

    /** The capability id of this method. */
    public static final String ID = "collection.membership.linear-scan";

    /** The version of this method. */
    public static final String VERSION = "1.0.0";

    /**
     * The number of queries the declared accuracy was measured over.
     *
     * <p>DERIVED from the validation corpus in the test, which asserts the corpus has exactly
     * this many cases.</p>
     */
    public static final int VALIDATION_CASES = 64;

    /**
     * The measured cost of one scan of the validation corpus, in milliseconds.
     *
     * <p>MEASURED on the W44.2 development machine over the validation corpus.</p>
     */
    public static final long MEASURED_COST_MILLIS = 1L;

    /**
     * When the declared evidence was measured.
     *
     * <p>MEASURED: the date of the validation run the evidence comes from.</p>
     */
    public static final String MEASURED_AT = "2026-11-02";

    private final AlgorithmCapability capability;

    /** Builds the method with its declared capability. */
    public LinearScanMembershipMethod() {
        this.capability = new AlgorithmCapability(
                ID,
                VERSION,
                Set.of(AlgorithmFamilies.MEMBERSHIP_LOOKUP),
                Set.of(),
                Set.of(MembershipProblem.class.getName()),
                Set.of(MembershipProblem.MembershipOutcome.class.getName()),
                new AlgorithmCapability.ComplexityProfile(
                        DeclaredCost.declare("O(Q*N)", MEASURED_COST_MILLIS, 0),
                        "O(1) beyond the corpus itself"),
                new AlgorithmCapability.ResourceProfile(
                        "O(N) to hold the corpus", true, false,
                        "none: the method always answers"),
                new AlgorithmCapability.AccuracyProfile(
                        "exact-match rate against the corpus", 1.0, VALIDATION_CASES,
                        "the W44.2 membership validation corpus", MEASURED_AT),
                new AlgorithmCapability.DeterminismProfile(true, "none: the method has no randomness"),
                new AlgorithmCapability.ReliabilityProfile(
                        "none: the method always answers", false,
                        "none"),
                new AlgorithmCapability.VerificationStrategy(
                        "the answers agree with the reference method on the validation corpus",
                        "io.matrix.cognitive.algorithms.MembershipProblem.MembershipOutcome.present"),
                null);
    }

    @Override
    public AlgorithmCapability capability() {
        return capability;
    }

    @Override
    public boolean preconditionsHold(MembershipProblem input) {
        return input != null;
    }

    @Override
    public MembershipProblem.MembershipOutcome execute(MembershipProblem input, ComputeBudget budget) {
        if (!preconditionsHold(input)) {
            throw new IllegalStateException("the membership problem is missing");
        }
        List<String> corpus = input.corpus();
        List<Boolean> answers = new ArrayList<>(input.queries().size());
        for (String query : input.queries()) {
            answers.add(corpus.contains(query));
        }
        return new MembershipProblem.MembershipOutcome(answers, corpus.size(), input.queries().size(),
                "a linear scan of the corpus; exact, with no index and no build cost");
    }
}
