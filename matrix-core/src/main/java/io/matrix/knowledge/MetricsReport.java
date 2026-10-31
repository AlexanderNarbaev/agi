package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The metrics of a learning workspace (owner directive §51-52).
 *
 * <p>The directive is explicit about what a metrics report must and must not
 * be:
 *
 * <ul>
 *   <li>it must carry the DATASET VERSION, the SOFTWARE VERSION, the TEST
 *       PROCEDURE and the SAMPLE SIZE;</li>
 *   <li>it must NOT be a single opaque intelligence score;</li>
 *   <li>it must NOT hide the metrics that were not measured.</li>
 * </ul>
 *
 * <p>So this report is a set of separate numbers, each with the sample it
 * was measured on, and it ends with an explicit list of what is NOT
 * measured. A metric without a sample size is not a measurement, and a
 * report that omits the unmeasured is a report that hides them.
 */
public record MetricsReport(
        String workspaceId,
        String softwareVersion,
        String softwareVersionSource,
        String datasetId,
        String datasetVersion,
        String testProcedure,
        List<DimensionMetric> dimensions,
        CoverageMetric coverage,
        AskingMetric asking,
        List<String> unmeasured,
        List<String> notes) {

    /** One capability dimension, with the sample it was measured on. */
    public record DimensionMetric(
            String conceptId,
            String dimension,
            String stage,
            double score,
            int evidenceCount,
            int variantCount,
            int heldOutCount,
            boolean measured,
            String note) {
    }

    /** What the workspace knows, and how much of it is backed by evidence. */
    public record CoverageMetric(
            int concepts,
            int claims,
            int establishedClaims,
            int claimsWithEvidence,
            int contradictedClaims,
            int sources,
            int evidenceLinks,
            int representations,
            int revisions,
            int prerequisites,
            double evidenceBackedShare) {
    }

    /** How the workspace behaves when it is asked. */
    public record AskingMetric(
            int questionsAsked,
            int questionsAnswered,
            int questionsRefused,
            int distinctQuestions,
            int recurringGaps,
            double refusalRate) {
    }

    public MetricsReport {
        dimensions = List.copyOf(dimensions == null ? List.of() : dimensions);
        unmeasured = List.copyOf(unmeasured == null ? List.of() : unmeasured);
        notes = List.copyOf(notes == null ? List.of() : notes);
    }

    /** The report as a map, for the HTTP layer. */
    public Map<String, Object> asMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("workspaceId", workspaceId);
        out.put("softwareVersion", softwareVersion);
        out.put("softwareVersionSource", softwareVersionSource);
        out.put("datasetId", datasetId);
        out.put("datasetVersion", datasetVersion);
        out.put("testProcedure", testProcedure);
        out.put("dimensions", dimensions);
        out.put("coverage", coverage);
        out.put("asking", asking);
        out.put("unmeasured", unmeasured);
        out.put("notes", notes);
        return out;
    }

    /**
     * The metrics that are NOT measured, named.
     *
     * <p>These are listed rather than omitted. Each one is something a
     * reader might reasonably assume is covered by the numbers above, and
     * the honest thing is to say that it is not.
     */
    public static List<String> unmeasuredMetrics() {
        List<String> out = new ArrayList<>();
        out.add("Transfer to a human learner: the teach-back is machine-to-machine, and no human "
                + "has been taught by this system.");
        out.add("Long-term retention: the outcomes are the ones recorded so far. Nothing here "
                + "measures what the learner remembers a month later.");
        out.add("Generalisation beyond the assessed dataset: the held-out items are held out from "
                + "the training, not from the domain.");
        out.add("Causal attribution: a score says what was assessed, not why it changed.");
        out.add("The quality of the sources beyond their recorded authority and freshness: the "
                + "excerpt is what the source said, not a verification that the source is right.");
        out.add("Efficiency: no latency or cost measurement is reported here.");
        return out;
    }
}
