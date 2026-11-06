package io.matrix.knowledge;

/**
 * The support score of an answer: what was actually measured, decomposed,
 * and what it does NOT mean (owner directive §6.5).
 *
 * <p>RECON-W40 exposed a field called {@code confidence}. The audit of
 * RECON-W41 established what it was: the mean of the matched claims' own
 * {@code confidence} field - values authored by hand in the knowledge
 * package - multiplied by the IDF retrieval coverage. No calibration data
 * existed anywhere in the repository, and a number called "confidence",
 * presented to two decimal places, reads as a probability of factual
 * correctness. It was not one.
 *
 * <p>The mission's rule is explicit: "If a heuristic score is useful, call it
 * a support score and disclose its meaning." This record does that. It
 * carries:
 *
 * <ul>
 *   <li>the <b>value</b> - the same heuristic product, unchanged, so that
 *       nothing that depended on it breaks;</li>
 *   <li>the <b>inputs</b> that produced it, separately: the retrieval
 *       coverage, the mean authority and freshness of the sources, how many
 *       claims and sources were used;</li>
 *   <li>the <b>scope match</b> - whether the question's terms fall inside
 *       the scope the claims declare;</li>
 *   <li>the <b>contradiction status</b> - and, honestly, whether a
 *       contradiction check has been performed at all;</li>
 *   <li>the <b>calibration status</b> - and the honest answer is that no
 *       calibration set exists;</li>
 *   <li>the <b>meaning</b>, in one sentence a user can read.</li>
 * </ul>
 *
 * <p>The point of the decomposition is not to make the number better. It is
 * to make the number honest: a reader can now see exactly what was counted
 * and what was not, and cannot mistake it for a probability.
 *
 * @see Answer
 */
public record SupportScore(
        double value,
        double evidenceCoverage,
        double meanSourceAuthority,
        double meanSourceFreshness,
        int claimCount,
        int sourceCount,
        ScopeMatch scopeMatch,
        ContradictionStatus contradiction,
        String calibrationStatus,
        String meaning) {

    /** How well the question falls inside the scope the claims declare. */
    public enum ScopeMatch {
        /** The question's terms fall inside the declared scope. */
        MATCHES,
        /** A scope is declared and none of the question's terms fall in it. */
        OUTSIDE,
        /** No scope is declared, so nothing can be said. */
        UNDECLARED
    }

    /** Whether a contradiction check was carried out, and what it found. */
    public enum ContradictionStatus {
        /** No contradicting claim was found. */
        NONE_DETECTED,
        /** The check has not been performed yet; it is not that none exist. */
        NOT_COMPUTED
    }

    /** The sentence every answer carries, so the number cannot be misread. */
    public static final String MEANING =
            "A support score: how much of the question the workspace's claims cover, weighted by "
                    + "the authority and freshness of the sources behind them. It is NOT a "
                    + "probability that the answer is factually correct, and it has not been "
                    + "calibrated against any held-out set.";

    /** The honest calibration status, until a calibration set exists. */
    public static final String UNCALIBRATED =
            "not calibrated: the repository holds no held-out set against which this score could "
                    + "be compared with observed correctness";

    public SupportScore {
        if (scopeMatch == null) {
            scopeMatch = ScopeMatch.UNDECLARED;
        }
        if (contradiction == null) {
            contradiction = ContradictionStatus.NOT_COMPUTED;
        }
        if (meaning == null || meaning.isBlank()) {
            meaning = MEANING;
        }
        if (calibrationStatus == null || calibrationStatus.isBlank()) {
            calibrationStatus = UNCALIBRATED;
        }
    }

    /** The score of an abstention: nothing was found, so nothing is claimed. */
    public static SupportScore none() {
        return new SupportScore(0.0, 0.0, 0.0, 0.0, 0, 0,
                ScopeMatch.UNDECLARED, ContradictionStatus.NOT_COMPUTED,
                UNCALIBRATED,
                "No answer was given, so there is no support to report.");
    }
}
