package io.matrix.cognitive.algorithms;

import io.matrix.neuron.BloomFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * W44.2 — the membership family's probabilistic indexed method.
 *
 * <p>It reuses {@code io.matrix.neuron.BloomFilter}, the hash-based membership filter the
 * repository already has. The filter answers "definitely not present" or "possibly present", so
 * it has no false negatives and a small but non-zero false-positive rate.</p>
 *
 * <p><b>Why it is in the portfolio when the trie is exact.</b> Because the accuracy profile is
 * part of the score, and this method is the one that shows the trade-off being made honestly. Its
 * declared accuracy is BELOW the exact methods', measured over a named corpus with a denominator
 * attached, so the selector has to weigh "slightly cheaper, slightly less certain" rather than
 * pretending the two are interchangeable. A reader who wants the exact answer sees the score
 * favour the trie.</p>
 *
 * <p>The declared accuracy is a LOWER BOUND on the measured rate on the validation corpus, and
 * the test asserts that the measurement clears the bound. Declaring a floor rather than an
 * equality is deliberate: the false-positive rate depends on the corpus and the filter's
 * parameters, so a floor is the claim that stays true.</p>
 *
 * <p>The method declares {@link Preconditions#INDEX_PREBUILT} and checks that the object it was
 * handed really is a Bloom filter, for the same reason the trie method does: a caller that
 * asserts the fact wrongly must be caught by the method.</p>
 */
public final class BloomFilterMembershipMethod
        implements AlgorithmMethod<MembershipProblem, MembershipProblem.MembershipOutcome> {

    /** The capability id of this method. */
    public static final String ID = "collection.membership.bloom-filter";

    /** The version of this method. */
    public static final String VERSION = "1.0.0";

    /**
     * The lower bound on the measured exact-match rate over the validation corpus.
     *
     * <p>MEASURED on the W44.2 development machine: the observed rate over the corpus is at or
     * above this value, and the test asserts that. The bound is below one because the filter has
     * false positives, and it is not one because the filter has no false negatives and its false
     * positives are rare at the validation corpus's size.</p>
     */
    public static final double DECLARED_ACCURACY_FLOOR = 0.95;

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
     * <p>MEASURED on the W44.2 development machine over the validation corpus, excluding the
     * one-off build cost.</p>
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
    public BloomFilterMembershipMethod() {
        this.capability = new AlgorithmCapability(
                ID,
                VERSION,
                Set.of(AlgorithmFamilies.MEMBERSHIP_LOOKUP),
                Set.of(Preconditions.INDEX_PREBUILT),
                Set.of(MembershipProblem.class.getName()),
                Set.of(MembershipProblem.MembershipOutcome.class.getName()),
                new AlgorithmCapability.ComplexityProfile(
                        DeclaredCost.declare("O(Q)", MEASURED_COST_MILLIS, 0),
                        "O(m bits) for the filter itself"),
                new AlgorithmCapability.ResourceProfile(
                        "O(m bits) for the filter", true, false,
                        "the caller did not build a filter, so the method is not admitted"),
                new AlgorithmCapability.AccuracyProfile(
                        "exact-match rate against the corpus, at or above the declared floor",
                        DECLARED_ACCURACY_FLOOR, VALIDATION_CASES,
                        "the W44.2 membership validation corpus", MEASURED_AT),
                new AlgorithmCapability.DeterminismProfile(true, "none: the filter has no randomness"),
                new AlgorithmCapability.ReliabilityProfile(
                        "the caller did not build a filter, so the method is not admitted", false,
                        "the scanning method takes over"),
                new AlgorithmCapability.VerificationStrategy(
                        "the answers agree with the scanning method except for the filter's false "
                                + "positives, which the declared floor accounts for",
                        "io.matrix.cognitive.algorithms.MembershipProblem.MembershipOutcome.present"),
                LinearScanMembershipMethod.ID);
    }

    @Override
    public AlgorithmCapability capability() {
        return capability;
    }

    @Override
    public boolean preconditionsHold(MembershipProblem input) {
        return input != null && input.prebuiltIndex() instanceof BloomFilter;
    }

    @Override
    public MembershipProblem.MembershipOutcome execute(MembershipProblem input, ComputeBudget budget) {
        if (!preconditionsHold(input)) {
            throw new IllegalStateException(
                    "precondition " + Preconditions.INDEX_PREBUILT + " does not hold: "
                            + "no Bloom filter was supplied for the corpus");
        }
        BloomFilter filter = (BloomFilter) input.prebuiltIndex();
        List<Boolean> answers = new ArrayList<>(input.queries().size());
        for (String query : input.queries()) {
            answers.add(filter.mightContain(MembershipProblem.keyOf(query)));
        }
        return new MembershipProblem.MembershipOutcome(answers, input.corpus().size(),
                input.queries().size(),
                "a lookup in a prebuilt Bloom filter; no false negatives, and a small rate of "
                        + "false positives that the declared accuracy accounts for");
    }
}
