package io.matrix.brain.runtime.research;

import java.util.List;

/**
 * EPISTEMIC-INTEGRITY P3 — the RESEARCH-ONLY record of one interaction's verified outcome.
 *
 * <p><b>What this is.</b> The learning label the production path uses is
 * {@code confidence >= 0.7 AND accepted} — a quantity computed from the retrieval
 * overlap, i.e. a measurement of the PIPELINE, not of whether the task was right. This
 * record is the alternative the directive asks for: an outcome signal whose verdict is
 * obtained by an <em>independent</em> check, and whose own record keeps the four
 * provenances apart.</p>
 *
 * <p><b>The four provenances, kept apart on the record itself.</b></p>
 * <ul>
 *   <li>{@link Provenance#PREDICTION} — what the SYSTEM said: the reply, the confidence,
 *       the acceptance, the modulator trace. This is a claim, never a verdict.</li>
 *   <li>{@link Provenance#VALIDATION} — what an INDEPENDENT checker said about that
 *       claim: the verdict, the evidence it rests on, and the cost of obtaining it.</li>
 *   <li>{@link Provenance#LEARNING} — what the learning path did with the observation:
 *       the provenance role it carries and whether the P0.4 admission gate let it into
 *       the learning window.</li>
 *   <li>{@link Provenance#EVALUATION} — what the HARNESS predicted. It is carried so a
 *       reader can see the prediction, and it is NEVER an input to the validation: the
 *       record asserts that, and the experiment asserts it again.</li>
 * </ul>
 *
 * <p><b>Why the evaluation provenance is carried at all.</b> A harness's expectation is
 * the evaluation's gold. Mixing it into the label is the laundering the directive forbids.
 * Keeping it on the record, with {@link #expectationUsedInOutcome()} false, makes the
 * separation checkable rather than promised.</p>
 *
 * <p><b>Research-only.</b> Nothing in the production learning path reads this class, and
 * nothing here writes to the production store, the production log or the quarantine
 * ledger. The observation's {@link #feature()} is the PRODUCTION feature
 * ({@code EpisodeFeatureExtractor}: the input and the reply text only) — the label's
 * source is never part of it.</p>
 */
public record OutcomeSignal(
        String observationId,
        String family,
        String input,
        String reply,
        double confidence,
        boolean accepted,
        Provenance predictionProvenance,
        Provenance validationProvenance,
        Provenance learningProvenance,
        Provenance evaluationProvenance,
        Outcome outcome,
        String validator,
        String evidence,
        String learningDisposition,
        long acquisitionNanos,
        long validationNanos,
        String evaluationExpectation,
        boolean expectationUsedInOutcome) {

    /** What a fact about an interaction is a statement OF. */
    public enum Provenance {
        /** What the system claimed. */
        PREDICTION,
        /** What an independent checker verified. */
        VALIDATION,
        /** What the learning path did with the observation. */
        LEARNING,
        /** What the harness predicted — never an input to the validation. */
        EVALUATION
    }

    /**
     * The verified outcome of an interaction.
     *
     * <p><b>UNKNOWN is a first-class state.</b> Absence of a verification is not a
     * negative verdict: an observation the checker cannot judge is UNKNOWN, and it must
     * not be counted as a failure of the system — the same discipline the epistemic work
     * demands of the asking path (unsupported is not contradicted).</p>
     */
    public enum Outcome {
        /** The reply is faithful to the machine's own knowledge store. */
        GROUNDED,
        /** The store holds nothing that backs the reply. */
        NOT_GROUNDED,
        /** The runtime declined to answer. */
        REFUSED,
        /** No independent verdict is possible. */
        UNKNOWN
    }

    public OutcomeSignal {
        if (predictionProvenance != Provenance.PREDICTION
                || validationProvenance != Provenance.VALIDATION
                || learningProvenance != Provenance.LEARNING
                || evaluationProvenance != Provenance.EVALUATION) {
            throw new IllegalArgumentException(
                "the four provenances must be kept apart on the record");
        }
        if (expectationUsedInOutcome) {
            throw new IllegalArgumentException(
                "the harness's expectation must never be an input to the outcome");
        }
    }

    /** Whether the outcome is one a classifier may learn from (UNKNOWN is not). */
    public boolean labelled() {
        return outcome == Outcome.GROUNDED || outcome == Outcome.NOT_GROUNDED;
    }

    /** The binary target of the validated outcome: GROUNDED is true. */
    public boolean positive() {
        return outcome == Outcome.GROUNDED;
    }

    /**
     * The PRODUCTION label of the observation: {@code confidence >= 0.7 AND accepted}.
     *
     * <p>It is carried here so the experiment can measure how well the production label
     * is recovered from the same feature that carries the validated outcome. That number
     * is the measurement of the self-fulfilling label, and it is published whether it
     * flatters the change or not.</p>
     */
    public boolean productionPositive() {
        return confidence >= 0.7 && accepted;
    }

    /**
     * The production feature of the observation: the input and the reply text only.
     *
     * <p>The production encoder ({@code EpisodeFeatureExtractor.encode}) hashes
     * {@code input + " " + reply}. The confidence and the acceptance flag are NOT in the
     * feature, so the production label is not an input of the feature — but the reply is,
     * and the reply is what the retrieval produced, which is why the experiment measures
     * the self-fulfillingness of every label explicitly instead of assuming it away.</p>
     */
    public long[] feature(int dim) {
        return new io.matrix.brain.runtime.EpisodeFeatureExtractor(dim)
                .encode(input + " " + reply);
    }

    /** The terms of the reply that carry content, for a verifier's coverage rule. */
    public List<String> replyContentTerms() {
        return OutcomeTerms.contentTerms(reply);
    }
}
