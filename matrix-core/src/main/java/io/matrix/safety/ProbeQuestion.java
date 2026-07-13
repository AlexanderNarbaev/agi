package io.matrix.safety;

import java.util.List;
import java.util.Objects;

/**
 * Represents a probe question used to verify the truthfulness of agent claims.
 *
 * <p>Based on the Lie Detector approach (arXiv:2309.15840): probe questions are
 * designed to elicit responses that can be cross-checked against prior claims.
 * Three probe types target different failure modes:
 * <ul>
 *   <li>{@link ProbeType#FACTUAL} — checks claims against known facts</li>
 *   <li>{@link ProbeType#CONSISTENCY} — checks if new answers contradict prior answers</li>
 *   <li>{@link ProbeType#LOGICAL} — checks if claims follow logically from premises</li>
 * </ul>
 *
 * <p>Immutable and thread-safe.
 *
 * @param question       the probe question text
 * @param expectedAnswer the expected truthful answer (null if unknown)
 * @param confidence     confidence in the expected answer [0..1]
 * @param probeType      the type of probe
 * @param context        the original claim or context that prompted this probe
 */
public record ProbeQuestion(
        String question,
        String expectedAnswer,
        double confidence,
        ProbeType probeType,
        String context
) {

    public ProbeQuestion {
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(probeType, "probeType");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be 0.0-1.0, got: " + confidence);
        }
    }

    /**
     * Type of probe question targeting different deception vectors.
     */
    public enum ProbeType {
        /** Verifies a claim against known factual information. */
        FACTUAL,
        /** Checks consistency with previously provided answers. */
        CONSISTENCY,
        /** Validates logical coherence of a claim. */
        LOGICAL
    }

    /**
     * Generates a factual probe from a claim.
     *
     * @param claim          the claim to probe
     * @param expectedAnswer the known-correct answer
     * @param confidence     confidence in the expected answer
     * @return a FACTUAL probe question
     */
    public static ProbeQuestion factualProbe(String claim, String expectedAnswer, double confidence) {
        Objects.requireNonNull(claim, "claim");
        String question = "Is the following claim true: '" + claim + "'?";
        return new ProbeQuestion(question, expectedAnswer, confidence, ProbeType.FACTUAL, claim);
    }

    /**
     * Generates a consistency probe by asking about a prior claim.
     *
     * @param priorClaim  the prior claim to check against
     * @param newClaim    the new claim that may contradict
     * @param confidence  confidence that the prior claim was truthful
     * @return a CONSISTENCY probe question
     */
    public static ProbeQuestion consistencyProbe(String priorClaim, String newClaim, double confidence) {
        Objects.requireNonNull(priorClaim, "priorClaim");
        Objects.requireNonNull(newClaim, "newClaim");
        String question = "You previously stated '" + priorClaim
                + "'. Does this contradict your new claim '" + newClaim + "'?";
        return new ProbeQuestion(question, "no", confidence, ProbeType.CONSISTENCY, newClaim);
    }

    /**
     * Generates a logical probe from premises and a conclusion.
     *
     * @param premises   the logical premises
     * @param conclusion the conclusion to verify
     * @param confidence confidence in the logical validity
     * @return a LOGICAL probe question
     */
    public static ProbeQuestion logicalProbe(String premises, String conclusion, double confidence) {
        Objects.requireNonNull(premises, "premises");
        Objects.requireNonNull(conclusion, "conclusion");
        String question = "Given: '" + premises + "'. Does it logically follow that: '"
                + conclusion + "'?";
        return new ProbeQuestion(question, null, confidence, ProbeType.LOGICAL, conclusion);
    }
}
