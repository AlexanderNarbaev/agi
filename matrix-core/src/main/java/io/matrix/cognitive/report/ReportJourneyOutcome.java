package io.matrix.cognitive.report;

import java.util.List;

/**
 * P2 — what one arm of the journey produced, in the terms a counterfactual compares.
 *
 * <p><b>Why this record exists.</b> The directive asks the counterfactual to show
 * <i>what changed and what did not</i>. A list of what changed is half an answer: the
 * reader has to be told which parts of the journey were the same, because "the answer is
 * the same" and "the artifact is the same" are the facts that make a difference
 * attributable to the method rather than to anything else. This record holds every aspect
 * the comparison needs, on both sides.</p>
 *
 * <p><b>What it is not.</b> It is not the response and not the trace; it is the
 * measurement of what the journey left behind, read from the same sources the caller
 * would look at — the response, the file system, the writer's index and the trace.</p>
 */
public record ReportJourneyOutcome(
        String taskId,
        String methodId,
        boolean forced,
        String reason,
        String disposition,
        String artifactName,
        String artifactLocator,
        String sha256,
        String bytesSha256,
        List<String> indexStatuses,
        List<String> tracePhases,
        boolean effectVerified,
        String observationDetail,
        String epistemicDisposition,
        List<String> evidenceClaimIds,
        String premiseStatus,
        int conflictCount,
        int readingCount,
        List<String> limitations,
        List<String> refusalReasons) {

    public ReportJourneyOutcome {
        indexStatuses = indexStatuses == null ? List.of() : List.copyOf(indexStatuses);
        tracePhases = tracePhases == null ? List.of() : List.copyOf(tracePhases);
        evidenceClaimIds = evidenceClaimIds == null ? List.of() : List.copyOf(evidenceClaimIds);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
        refusalReasons = refusalReasons == null ? List.of() : List.copyOf(refusalReasons);
    }
}
