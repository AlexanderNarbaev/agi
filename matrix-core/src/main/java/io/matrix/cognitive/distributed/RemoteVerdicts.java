package io.matrix.cognitive.distributed;

import io.matrix.cognitive.RemoteReportAssessor;
import io.matrix.cognitive.algorithms.MethodOutcome;
import io.matrix.contract.bridge.CognitiveTaskResponse;

/**
 * W53 — what the peer's answer is, as a verdict on the method that produced it.
 *
 * <p><b>Why this mapping exists at all.</b> The mandate asks for the reliability of a method to
 * be measured from <i>decided trials</i> — a call whose answer something verified — and not from a
 * completion rate. The remote path is the one place in the system where a verifier exists:
 * chain A assesses the peer's report against chain A's own evidence, and that assessment ends
 * in a disposition. The dispositions are the verdicts.</p>
 *
 * <p><b>What the resulting quantity is, and what it is not.</b> A verdict here is chain A's
 * reading of the peer's answer, so the rate it produces answers: <i>"how often does a report
 * from this capability become a validated proposition in chain A's own epistemic model?"</i>
 * That is a quantity about the peer <b>and</b> about chain A's evidence — a stricter chain A
 * produces more rejections from the same peer — and it is not a measure of the peer's
 * reasoning alone. The research that forced this taxonomy says the same about every rate it
 * warns against: a rate is confounded by the strictness of the verifier that produced it
 * ({@code docs-v2/research/W53-RESEARCH-D-RELIABILITY-CALIBRATION.md} §3.8). The estimate
 * therefore names the stratum it was measured in, and the decision that reads it names the
 * basis it is used on.</p>
 *
 * <p><b>Why the dispositions are not all decided trials.</b> Chain A's own abstention is not the
 * peer's abstention, and "nothing settles the proposition" is not a wrong answer: both are
 * <i>unverified</i> rather than decided, so they never enter the denominator.</p>
 *
 * <p><b>What a refusal is here, and what the peer does not tell us.</b> The peer reports a
 * refusal for two different facts: a request refused by policy — a right, a budget or a
 * constraint — and the brain cycle declining the input. The protocol has one status for the
 * two, so this table cannot tell them apart, and it records both as a refusal: the taxonomy's
 * refusal covers "a capability was missing, or a right was not granted", and a call that was
 * refused is what the peer's own row says happened. The distinction between the two facts is
 * in the peer's row and in the reason the peer sent; it is not in the verdict, and this table
 * does not pretend otherwise.</p>
 *
 * <p><b>Why the mapping is a class and not a chain of conditions in the coordinator.</b> It is a
 * table of meanings, and a table of meanings deserves its own name, its own tests and its own
 * place in the trace.</p>
 */
public final class RemoteVerdicts {

    private RemoteVerdicts() {
    }

    /**
     * The verdict of one remote execution.
     *
     * @param status       what the peer reported about the execution
     * @param disposition  what chain A concluded about the peer's answer
     * @return the kind of outcome the call had
     */
    public static MethodOutcome of(CognitiveTaskResponse.Status status,
                                   RemoteReportAssessor.Disposition disposition) {
        if (status == null) {
            return MethodOutcome.UNVERIFIED;
        }
        return switch (status) {
            case COMPLETED -> completed(disposition);
            case PARTIAL -> MethodOutcome.PARTIAL;
            case REFUSED, UNSUPPORTED -> MethodOutcome.REFUSED;
            case FAILED -> MethodOutcome.TECHNICAL_FAILURE;
            case TIMED_OUT -> MethodOutcome.TIMEOUT;
            case CANCELLED -> MethodOutcome.CANCELLED;
            case IN_PROGRESS -> MethodOutcome.UNVERIFIED;
        };
    }

    /**
     * The verdict of an execution the peer reported as completed.
     *
     * @param disposition what chain A concluded about the peer's answer
     * @return the kind of outcome the call had
     */
    private static MethodOutcome completed(RemoteReportAssessor.Disposition disposition) {
        if (disposition == null) {
            return MethodOutcome.UNVERIFIED;
        }
        return switch (disposition) {
            case SUPPORTED -> MethodOutcome.VALIDATED;
            case QUALIFIED -> MethodOutcome.QUALIFIED;
            case REJECTED -> MethodOutcome.INCORRECT;
            case INVALID -> MethodOutcome.UNSUPPORTED;
            case UNRESOLVED, ABSTAINED -> MethodOutcome.UNVERIFIED;
        };
    }
}
