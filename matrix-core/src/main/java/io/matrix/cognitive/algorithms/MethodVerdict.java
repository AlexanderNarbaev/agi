package io.matrix.cognitive.algorithms;

/**
 * W53 — what the calls of a method did, in the stratum they were observed in.
 *
 * <p><b>Why a verdict is its own record and not a column of the cost.</b> The cost of a call
 * is a property of the method: the same method takes about the same time whatever it was
 * asked. The <i>verdict</i> of a call is a property of the method <b>and of the request it
 * answered</b> — a method that is right on one family and wrong on another is neither
 * reliable nor unreliable until the family is named. The research that forced this says so
 * directly: a rate pooled over families "answers a different question from 'how reliable is
 * this method for this request'", and a confidence interval over such a rate "would be a
 * precise statement about the wrong quantity"
 * ({@code docs-v2/research/W53-RESEARCH-D-RELIABILITY-CALIBRATION.md} §3.8).</p>
 *
 * <p><b>The stratum.</b> The key of a verdict is the method, the family of the request and
 * the version of the capability that ran. The verdicts recorded without a stratum are
 * <i>pooled</i>: they say how the method behaved over everything it was ever asked, and they
 * are therefore not evidence for any particular request.</p>
 *
 * @param methodId          the method that was run
 * @param family            the family of the request it answered
 * @param capabilityVersion the version of the capability that ran
 * @param calls             how many calls were observed
 * @param validated         how many asserted a correct and validated result
 * @param incorrect         how many asserted a wrong result
 * @param partial           how many asserted a partial result
 * @param qualified         how many asserted a supported but not independent result
 * @param unsupported       how many asserted an unsupported result
 * @param abstained         how many declined to assert
 * @param notEvidence       how many said nothing about the method's reasoning
 */
public record MethodVerdict(
        String methodId,
        String family,
        String capabilityVersion,
        int calls,
        int validated,
        int incorrect,
        int partial,
        int qualified,
        int unsupported,
        int abstained,
        int notEvidence) {

    public MethodVerdict {
        if (methodId == null || methodId.isBlank()) {
            throw new IllegalArgumentException("the method of a verdict must not be blank");
        }
        if (calls < 0 || validated < 0 || incorrect < 0 || partial < 0 || qualified < 0
                || unsupported < 0 || abstained < 0 || notEvidence < 0) {
            throw new IllegalArgumentException("the counters of a verdict must not be negative");
        }
    }


    /**
     * The calls that decided something.
     *
     * <p>A decided trial is a call in which the method asserted something about the world:
     * right, wrong, partial, supported-but-not-independent or unsupported. The calls that
     * abstained, that failed, that timed out and that were refused said nothing about the
     * method's reasoning, and they are not in the denominator of the estimate.</p>
     *
     * @return the number of decided trials
     */
    public int decisions() {
        return validated + incorrect + partial + qualified + unsupported;
    }

    /**
     * Whether these verdicts were recorded without a stratum.
     *
     * <p>A pooled verdict says how the method behaved over everything it was ever asked, and
     * it is therefore not evidence for any particular request.</p>
     *
     * @return true when the verdicts carry no stratum
     */
    public boolean pooled() {
        return (family == null || family.isBlank())
                && (capabilityVersion == null || capabilityVersion.isBlank());
    }

    /**
     * These verdicts with one more call of the given kind.
     *
     * @param outcome the outcome of the call
     * @return the verdicts including that call
     */
    public MethodVerdict with(MethodOutcome outcome) {
        if (outcome == null) {
            throw new IllegalArgumentException("the outcome must not be null");
        }
        return new MethodVerdict(methodId, family, capabilityVersion, calls + 1,
                validated + (outcome.validated() ? 1 : 0),
                incorrect + (outcome == MethodOutcome.INCORRECT ? 1 : 0),
                partial + (outcome == MethodOutcome.PARTIAL ? 1 : 0),
                qualified + (outcome == MethodOutcome.QUALIFIED ? 1 : 0),
                unsupported + (outcome == MethodOutcome.UNSUPPORTED ? 1 : 0),
                abstained + (outcome == MethodOutcome.ABSTAINED ? 1 : 0),
                notEvidence + (outcome.notEvidence() ? 1 : 0));
    }
}
