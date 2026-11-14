package io.matrix.cognitive.distributed;

import io.matrix.cognitive.RemoteReportAssessor;
import io.matrix.cognitive.algorithms.MethodOutcome;
import io.matrix.contract.bridge.CognitiveTaskResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W53 — what the peer's answer is, as a verdict on the method that produced it.
 *
 * <p><b>What these tests hold.</b> The mapping is a table of meanings, and a table of
 * meanings is wrong in a way that only shows up in the numbers: a verdict that is counted
 * when it should not be inflates a peer's reliability, and one that is not counted when it
 * should be hides a peer that answers differently every time. The tests therefore hold two
 * lines at once: the meaning of each disposition, and the line between a decided trial and
 * something that is merely not evidence.</p>
 */
class RemoteVerdictsTest {

    @Test
    @DisplayName("a supported answer is a validated result, and a contradicted one is wrong")
    void theDispositionsOfACompletedExecution() {
        assertEquals(MethodOutcome.VALIDATED, RemoteVerdicts.of(
                CognitiveTaskResponse.Status.COMPLETED,
                RemoteReportAssessor.Disposition.SUPPORTED),
                "chain A's own evidence supports the report and the report is independent");
        assertEquals(MethodOutcome.INCORRECT, RemoteVerdicts.of(
                CognitiveTaskResponse.Status.COMPLETED,
                RemoteReportAssessor.Disposition.REJECTED),
                "chain A's own evidence contradicts the report");
        assertEquals(MethodOutcome.QUALIFIED, RemoteVerdicts.of(
                CognitiveTaskResponse.Status.COMPLETED,
                RemoteReportAssessor.Disposition.QUALIFIED),
                "the report is supported but not independent: it is a decided trial that is "
                        + "not a validated one");
        assertEquals(MethodOutcome.UNSUPPORTED, RemoteVerdicts.of(
                CognitiveTaskResponse.Status.COMPLETED,
                RemoteReportAssessor.Disposition.INVALID),
                "the answer does not answer the task");
    }

    @Test
    @DisplayName("what settles nothing is not a decided trial")
    void anUnsettledAnswerIsNotADecidedTrial() {
        MethodOutcome unresolved = RemoteVerdicts.of(
                CognitiveTaskResponse.Status.COMPLETED,
                RemoteReportAssessor.Disposition.UNRESOLVED);
        assertEquals(MethodOutcome.UNVERIFIED, unresolved,
                "nothing settled the proposition: the peer's answer was neither confirmed "
                        + "nor contradicted");
        assertFalse(unresolved.decides(),
                "an unsettled answer must not enter the denominator of a reliability estimate");
        assertTrue(unresolved.notEvidence(),
                "an unsettled answer says nothing about the peer's reasoning");

        assertEquals(MethodOutcome.UNVERIFIED, RemoteVerdicts.of(
                        CognitiveTaskResponse.Status.COMPLETED,
                        RemoteReportAssessor.Disposition.ABSTAINED),
                "chain A's own abstention is not the peer's abstention");
    }

    @Test
    @DisplayName("an execution that did not finish is counted apart from the decisions")
    void theStatusesOfAnExecutionThatDidNotFinish() {
        assertEquals(MethodOutcome.REFUSED, RemoteVerdicts.of(
                        CognitiveTaskResponse.Status.REFUSED, null),
                "the peer refused the request: that is a refusal, and it is not a trial");
        assertEquals(MethodOutcome.REFUSED, RemoteVerdicts.of(
                        CognitiveTaskResponse.Status.UNSUPPORTED, null),
                "the capability was not served here: that is a refusal too, and the peer's "
                        + "protocol has one status for the two facts");
        assertEquals(MethodOutcome.TECHNICAL_FAILURE, RemoteVerdicts.of(
                        CognitiveTaskResponse.Status.FAILED, null),
                "the execution failed");
        assertEquals(MethodOutcome.TIMEOUT, RemoteVerdicts.of(
                        CognitiveTaskResponse.Status.TIMED_OUT, null),
                "the execution ran out of its budget");
        assertEquals(MethodOutcome.CANCELLED, RemoteVerdicts.of(
                        CognitiveTaskResponse.Status.CANCELLED, null),
                "the task was withdrawn");
        assertEquals(MethodOutcome.PARTIAL, RemoteVerdicts.of(
                        CognitiveTaskResponse.Status.PARTIAL, null),
                "the peer answered only part of the task");
        assertEquals(MethodOutcome.UNVERIFIED, RemoteVerdicts.of(
                        CognitiveTaskResponse.Status.IN_PROGRESS, null),
                "the peer is still working: there is nothing to judge yet");
    }

    @Test
    @DisplayName("nothing at all is not evidence, and never a decision")
    void nothingAtAllIsNotEvidence() {
        MethodOutcome nothing = RemoteVerdicts.of(null, null);
        assertEquals(MethodOutcome.UNVERIFIED, nothing,
                "an execution that reported nothing leaves no verdict to record");
        assertFalse(nothing.decides(),
                "nothing must not be counted as a trial");
        assertTrue(nothing.notEvidence(),
                "nothing is not evidence about the peer's reasoning");
    }

    @Test
    @DisplayName("only the validated trial is the one the numerator counts")
    void onlyTheValidatedTrialIsInTheNumerator() {
        for (RemoteReportAssessor.Disposition disposition : RemoteReportAssessor.Disposition.values()) {
            MethodOutcome outcome = RemoteVerdicts.of(
                    CognitiveTaskResponse.Status.COMPLETED, disposition);
            assertEquals(disposition == RemoteReportAssessor.Disposition.SUPPORTED,
                    outcome.validated(),
                    "only a supported report is a validated result: " + disposition);
        }
    }
}
