package io.matrix.cognitive.report;

import io.matrix.knowledge.Answer;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.ClaimConflictDetector;
import io.matrix.knowledge.PremiseAssessment;
import io.matrix.knowledge.PremiseSemantics;
import io.matrix.knowledge.Source;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * P2 — the report's text, assembled only from what the workspace answered.
 *
 * <p>The sentences in the report are the workspace's claims, with their identifiers and
 * their statuses. Nothing is added that the citations do not carry, and a question the
 * workspace could not answer is written as a refusal to answer.</p>
 *
 * <p><b>Why the assembly lives here.</b> The text was the route's private method, and a
 * method that runs the journey has to be able to produce it: the reference method writes
 * the report, and the replay method serves the one that was written before. The text is
 * therefore a shared part of the journey rather than a part of the route.</p>
 */
public final class ReportBuilder {

    private ReportBuilder() {
    }

    /**
     * The report for one answer.
     *
     * @param workspaceId the workspace the question was asked over
     * @param question    the question
     * @param answer      the workspace's answer
     * @return the report's markdown
     */
    public static String markdown(String workspaceId, String question, Answer answer) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Evidence report\n\n");
        markdown.append("**Question.** ").append(question).append("\n\n");
        markdown.append("**Workspace.** ").append(workspaceId)
                .append(" · **Sources.** ").append(answer.sources().size())
                .append(" · **Claims.** ").append(answer.claims().size()).append("\n\n");
        markdown.append("## Answer\n\n");
        if (answer.answered()) {
            markdown.append(answer.statement()).append("\n\n");
        } else {
            markdown.append("The workspace could not answer this question: ")
                    .append(answer.abstentionReason()).append("\n\n");
            markdown.append("**UNGROUNDED** — no answer is asserted.\n\n");
        }
        markdown.append("## Supported claims\n\n");
        if (answer.claims().isEmpty()) {
            markdown.append("_None: the workspace holds no established claim for this question._\n\n");
        }
        Map<String, String> sourceById = new LinkedHashMap<>();
        answer.sources().forEach(source -> sourceById.put(source.id(), source.title()));
        for (Claim claim : answer.claims()) {
            markdown.append("- `").append(claim.id()).append("` ").append(claim.proposition())
                    .append(" — ").append(claim.status());
            String sourceTitle = sourceById.get(claim.introducedBySourceId());
            if (sourceTitle != null) {
                markdown.append(" — ").append(sourceTitle);
            }
            markdown.append("\n");
        }
        markdown.append("\n## Sources\n\n");
        for (Source source : answer.sources()) {
            markdown.append("- ").append(source.title()).append(" (").append(source.id())
                    .append(")\n");
        }
        markdown.append("\n## Limitations\n\n");
        for (String limitation : answer.limitations()) {
            markdown.append("- ").append(limitation).append("\n");
        }
        if (!answer.unmatchedTerms().isEmpty()) {
            markdown.append("\n## Not covered by the workspace\n\n");
            for (String term : answer.unmatchedTerms()) {
                markdown.append("- `").append(term).append("`\n");
            }
        }
        PremiseSemantics.Disposition disposition = answer.premise();
        if (disposition != null && disposition.status() != PremiseSemantics.Status.NO_PREMISE) {
            markdown.append("\n## The premise of the question\n\n");
            markdown.append("- **Status.** ").append(disposition.status().name())
                    .append(" (").append(disposition.kind().name()).append(").\n");
            markdown.append("- **The premise.** \"").append(disposition.premise()).append("\"\n");
            markdown.append("- **Why.** ").append(disposition.reason()).append("\n");
            for (PremiseAssessment.EvidenceRef reference : disposition.evidence()) {
                if (!"CONTRADICTS".equals(reference.relation())) {
                    continue;
                }
                markdown.append("- **The claim that denies it.** `")
                        .append(reference.claimId()).append("` — \"").append(reference.proposition())
                        .append("\" (source ").append(reference.sourceId());
                if (reference.sourceTitle() != null) {
                    markdown.append(", \"").append(reference.sourceTitle()).append("\"");
                }
                markdown.append(")\n");
            }
        }
        if (disposition != null && !disposition.conflicts().isEmpty()) {
            markdown.append("\n## Conflicts in the evidence\n\n");
            markdown.append("The corpus holds claims that cannot both hold. Both sides are given "
                    + "with their provenance, and the system does not decide between them.\n\n");
            for (ClaimConflictDetector.Conflict conflict : disposition.conflicts()) {
                markdown.append("- ").append(conflict.reason()).append("\n");
                for (ClaimConflictDetector.Side side : conflict.sides()) {
                    markdown.append("  - `").append(side.claimId()).append("` — \"")
                            .append(side.proposition()).append("\" (source ")
                            .append(side.sourceId());
                    if (side.sourceTitle() != null) {
                        markdown.append(", \"").append(side.sourceTitle()).append("\"");
                    }
                    markdown.append("): ").append(side.detail()).append("\n");
                }
            }
        }
        if (disposition != null && !disposition.readings().isEmpty()) {
            markdown.append("\n## The readings of the question\n\n");
            markdown.append("The question admits more than one reading. The readings are given "
                    + "with their provenance, and none of them is chosen.\n\n");
            for (PremiseSemantics.Reading reading : disposition.readings()) {
                markdown.append("- \"").append(reading.label()).append("\"\n");
                for (PremiseAssessment.EvidenceRef reference : reading.evidence()) {
                    markdown.append("  - `").append(reference.claimId()).append("` (source ")
                            .append(reference.sourceId()).append(")\n");
                }
            }
        }
        markdown.append("\n## Epistemic status\n\n");
        markdown.append("- The claims above are the workspace's own, with their ids and their "
                + "statuses; this report asserts nothing the citations do not carry.\n");
        markdown.append("- A report is a record, not evidence: it does not support the "
                + "propositions it repeats.\n");
        if (!answer.answered()) {
            markdown.append("- The question is **not answered**; the absence of an answer is "
                    + "the epistemic result, not a defect of the file action.\n");
        }
        return markdown.toString();
    }
}
