package io.matrix.cognitive.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * P2 — the difference between two arms of the journey, including the parts that did NOT
 * differ.
 *
 * <p><b>Why the unchanged parts are in the record.</b> The directive asks the
 * counterfactual to show what changed and what did not. A list of the differences alone
 * cannot answer that: the reader would have to assume that everything not mentioned was
 * the same, and the assumption is exactly what a counterfactual must not make. Every
 * aspect the journey has is therefore compared, and each carries the verdict —
 * <b>identical</b> or <b>different</b> — together with the two values.</p>
 *
 * <p><b>What the comparison is for.</b> Attribution. When two arms differ in the method
 * and in nothing else that the caller can see, the difference in what they observed is
 * the method's doing; when they differ in the artifact as well, the method changed the
 * outcome and the counterfactual has said so.</p>
 */
public record ReportJourneyDiff(List<Aspect> aspects) {

    public ReportJourneyDiff {
        aspects = aspects == null ? List.of() : List.copyOf(aspects);
    }

    /**
     * One compared aspect.
     *
     * @param aspect        what was compared
     * @param reference     the value in the arm the evidence chose
     * @param counterfactual the value in the arm whose method was forced
     * @param identical     whether the two values are the same
     */
    public record Aspect(String aspect, String reference, String counterfactual,
            boolean identical) {

        public Aspect {
            reference = reference == null ? "" : reference;
            counterfactual = counterfactual == null ? "" : counterfactual;
        }
    }

    /**
     * Compares two arms of the journey.
     *
     * @param reference     the arm the evidence chose
     * @param counterfactual the arm whose method was forced
     * @return the diff, with every aspect marked
     */
    public static ReportJourneyDiff between(ReportJourneyOutcome reference,
            ReportJourneyOutcome counterfactual) {
        List<Aspect> aspects = new ArrayList<>();
        aspects.add(aspect("the action's identity", reference.taskId(), counterfactual.taskId()));
        aspects.add(aspect("the method that ran", reference.methodId(), counterfactual.methodId()));
        aspects.add(aspect("whether the method was forced", reference.forced(),
                counterfactual.forced()));
        aspects.add(aspect("the reason the method was chosen", reference.reason(),
                counterfactual.reason()));
        aspects.add(aspect("the disposition of the action", reference.disposition(),
                counterfactual.disposition()));
        aspects.add(aspect("the artifact's name", reference.artifactName(),
                counterfactual.artifactName()));
        aspects.add(aspect("the artifact's locator", reference.artifactLocator(),
                counterfactual.artifactLocator()));
        aspects.add(aspect("the sha256 the response reported", reference.sha256(),
                counterfactual.sha256()));
        aspects.add(aspect("the sha256 measured on the artifact's bytes", reference.bytesSha256(),
                counterfactual.bytesSha256()));
        aspects.add(aspect("the index's records for the identity", reference.indexStatuses(),
                counterfactual.indexStatuses()));
        aspects.add(aspect("the trace's steps", reference.tracePhases(),
                counterfactual.tracePhases()));
        aspects.add(aspect("whether the effect was verified", reference.effectVerified(),
                counterfactual.effectVerified()));
        aspects.add(aspect("what the independent reader saw", reference.observationDetail(),
                counterfactual.observationDetail()));
        aspects.add(aspect("the epistemic disposition of the answer",
                reference.epistemicDisposition(), counterfactual.epistemicDisposition()));
        aspects.add(aspect("the claims the answer cited", reference.evidenceClaimIds(),
                counterfactual.evidenceClaimIds()));
        aspects.add(aspect("the status of the question's premise", reference.premiseStatus(),
                counterfactual.premiseStatus()));
        aspects.add(aspect("the conflicts the answer carried", reference.conflictCount(),
                counterfactual.conflictCount()));
        aspects.add(aspect("the readings the answer carried", reference.readingCount(),
                counterfactual.readingCount()));
        aspects.add(aspect("the limitations the caller was told", reference.limitations(),
                counterfactual.limitations()));
        aspects.add(aspect("the refusal reasons the caller was told", reference.refusalReasons(),
                counterfactual.refusalReasons()));
        return new ReportJourneyDiff(aspects);
    }

    private static Aspect aspect(String name, Object reference, Object counterfactual) {
        String left = String.valueOf(reference);
        String right = String.valueOf(counterfactual);
        return new Aspect(name, left, right, Objects.equals(left, right));
    }

    /** The aspects that differ. */
    public List<Aspect> differences() {
        return aspects.stream().filter(aspect -> !aspect.identical()).toList();
    }

    /** The aspects that are the same. */
    public List<Aspect> unities() {
        return aspects.stream().filter(Aspect::identical).toList();
    }

    /** Whether every compared aspect is the same. */
    public boolean identicalEverywhere() {
        return differences().isEmpty();
    }
}
