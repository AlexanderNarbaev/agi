package io.matrix.cognitive.algorithms;

import io.matrix.neuron.Trie;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * W44.2 — the membership family's exact indexed method.
 *
 * <p>It reuses {@code io.matrix.neuron.Trie}, the prefix tree the repository already has. A trie
 * answers a membership question exactly — there are no false positives and no false negatives —
 * and it does so in time proportional to the length of the key rather than to the size of the
 * corpus.</p>
 *
 * <p>That exactness is what makes it the right comparison for a scan: the two methods have the
 * SAME accuracy, so the only thing left to differ on is cost, and the selector has to pick the
 * cheaper one. A probabilistic filter would differ on accuracy too, and the comparison would say
 * less.</p>
 *
 * <p>The method declares {@link Preconditions#INDEX_PREBUILT}: the index is built by the caller,
 * once, and the caller decides whether that is worth it. The method also checks that the index
 * it was handed really is a trie, so a caller that asserts the fact wrongly is caught.</p>
 */
public final class TrieMembershipMethod
        implements AlgorithmMethod<MembershipProblem, MembershipProblem.MembershipOutcome> {

    /** The capability id of this method. */
    public static final String ID = "collection.membership.trie-index";

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
     * The measured cost of one indexed lookup of the validation corpus, in milliseconds.
     *
     * <p>MEASURED on the W44.2 development machine over the validation corpus. It excludes the
     * cost of building the trie, which the benchmark measures separately and reports: the build is
     * a one-off and the lookup is the recurring cost.</p>
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
    public TrieMembershipMethod() {
        this.capability = new AlgorithmCapability(
                ID,
                VERSION,
                Set.of(AlgorithmFamilies.MEMBERSHIP_LOOKUP),
                Set.of(Preconditions.INDEX_PREBUILT),
                Set.of(MembershipProblem.class.getName()),
                Set.of(MembershipProblem.MembershipOutcome.class.getName()),
                new AlgorithmCapability.ComplexityProfile(
                        DeclaredCost.declare("O(Q*L)", MEASURED_COST_MILLIS, 0),
                        "O(corpus length in characters) for the trie itself"),
                new AlgorithmCapability.ResourceProfile(
                        "O(corpus length in characters) for the index", true, false,
                        "the caller did not build an index, so the method is not admitted"),
                new AlgorithmCapability.AccuracyProfile(
                        "exact-match rate against the corpus", 1.0, VALIDATION_CASES,
                        "the W44.2 membership validation corpus", MEASURED_AT),
                new AlgorithmCapability.DeterminismProfile(true, "none: the method has no randomness"),
                new AlgorithmCapability.ReliabilityProfile(
                        "the caller did not build an index, so the method is not admitted", false,
                        "the scanning method takes over"),
                new AlgorithmCapability.VerificationStrategy(
                        "the answers agree with the scanning method on the validation corpus",
                        "io.matrix.cognitive.algorithms.MembershipProblem.MembershipOutcome.present"),
                LinearScanMembershipMethod.ID);
    }

    @Override
    public AlgorithmCapability capability() {
        return capability;
    }

    @Override
    public boolean preconditionsHold(MembershipProblem input) {
        return input != null && input.prebuiltIndex() instanceof Trie;
    }

    @Override
    public MembershipProblem.MembershipOutcome execute(MembershipProblem input, ComputeBudget budget) {
        if (!preconditionsHold(input)) {
            throw new IllegalStateException(
                    "precondition " + Preconditions.INDEX_PREBUILT + " does not hold: "
                            + "no trie was supplied for the corpus");
        }
        Trie index = (Trie) input.prebuiltIndex();
        List<Boolean> answers = new ArrayList<>(input.queries().size());
        for (String query : input.queries()) {
            answers.add(index.contains(query));
        }
        return new MembershipProblem.MembershipOutcome(answers, input.corpus().size(),
                input.queries().size(),
                "an exact lookup in a prebuilt trie; the build cost was paid by the caller");
    }
}
