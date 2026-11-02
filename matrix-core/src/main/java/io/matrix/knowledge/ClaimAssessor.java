package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * The assessor: does this claim still hold? (RECON-W43.0)
 *
 * <p>This is the engine of the mission's §5 scenario. It reads the evidence
 * that is linked to a claim and produces a {@link ClaimAssessment} — the
 * reasoning result, with its evidence, its contradictions, its scope, its
 * unresolved questions, and, when the evidence supports one, a proposed
 * narrower formulation.
 *
 * <p><b>What it does not do.</b> It does not decide whether the claim is
 * true. It decides what the recorded evidence says about the claim, and it
 * says which parts it could not decide. The evidence is a set of links
 * between claims and sources; this class reads them and nothing else.
 *
 * <p><b>Where the condition of a counterexample lives.</b> An observation
 * that contradicts a claim is stored as a {@link Source} of kind
 * {@link SourceKind#OBSERVATION}, and the condition under which it was made
 * is stored in that source's {@code locator} — for an observation, the
 * locator IS where and under what it was seen. The reading of the
 * counterexample therefore depends on what the observer recorded, and when
 * the observer recorded nothing, the assessor says so instead of guessing.
 */
public final class ClaimAssessor {

    private ClaimAssessor() {
    }

    /**
     * Assesses a claim against the evidence linked to it.
     *
     * @param workspaceId the workspace
     * @param claim the claim
     * @param evidence the evidence of that workspace
     * @param sources the sources of that workspace
     * @return the assessment
     */
    public static ClaimAssessment assess(String workspaceId, Claim claim,
                                          List<Evidence> evidence, List<Source> sources) {
        List<ClaimAssessment.EvidenceRef> supporting = new ArrayList<>();
        List<ClaimAssessment.EvidenceRef> contradicting = new ArrayList<>();
        List<ClaimAssessment.EvidenceRef> qualifying = new ArrayList<>();
        List<ClaimAssessment.EvidenceRef> exemplifying = new ArrayList<>();
        List<ClaimAssessment.EvidenceRef> insufficient = new ArrayList<>();
        List<Evidence> contextual = new ArrayList<>();

        for (Evidence e : evidence) {
            if (!claim.id().equals(e.claimId())) {
                continue;
            }
            ClaimAssessment.EvidenceRef ref = ref(e, sources);
            switch (e.relation()) {
                case SUPPORTS -> supporting.add(ref);
                case CONTRADICTS -> contradicting.add(ref);
                case QUALIFIES -> qualifying.add(ref);
                case EXEMPLIFIES -> exemplifying.add(ref);
                case FAILS_TO_SUPPORT, INSUFFICIENT -> insufficient.add(ref);
                case CONTEXTUAL -> contextual.add(e);
            }
        }

        ClaimStatus standing = standing(supporting, contradicting, exemplifying, insufficient);
        String standingReason = standingReason(standing, supporting, contradicting,
                exemplifying, insufficient, contextual);

        CounterexampleReading reading = null;
        String readingReason = null;
        List<String> alternatives = List.of();
        List<String> unresolved = new ArrayList<>();

        if (!contradicting.isEmpty()) {
            Evidence counterexample = evidence.stream()
                    .filter(e -> claim.id().equals(e.claimId())
                            && e.relation() == EvidenceRelation.CONTRADICTS)
                    .findFirst().orElseThrow();
            Source source = sourceOf(counterexample.sourceId(), sources);
            String condition = conditionOf(source);
            String claimScope = claim.scope() == null ? "" : claim.scope().strip();

            if (condition == null || condition.isBlank()) {
                reading = CounterexampleReading.INCONCLUSIVE;
                readingReason = "the counterexample records no condition, so it cannot be read "
                        + "against the claim's scope; the claim may be false, or the "
                        + "counterexample may be unreliable, or the condition may simply not have "
                        + "been recorded";
                alternatives = CounterexampleReading.unresolvedExplanations();
                unresolved.add("the conditions under which the counterexample contradicts");
            } else if (claimScope.isBlank()) {
                reading = CounterexampleReading.REFUTES;
                readingReason = "the claim is stated without qualification and the observation "
                        + "contradicts it under the recorded condition (" + condition + ")";
            } else {
                reading = CounterexampleReading.REVEALS_MISSING_CONDITION;
                readingReason = "the contradiction holds under the recorded condition (" + condition
                        + "), and the claim's own scope (" + claimScope + ") does not state it, so "
                        + "the claim is incomplete rather than false";
            }
            unresolved.add("whether the reading itself is the right one");
        }

        if (insufficient.isEmpty() && supporting.isEmpty() && contradicting.isEmpty()
                && exemplifying.isEmpty() && standing == ClaimStatus.UNKNOWN) {
            unresolved.add("no evidence has been recorded for this claim");
        }

        String proposal = null;
        String proposalReason = null;
        if (reading == CounterexampleReading.REVEALS_MISSING_CONDITION) {
            Evidence counterexample = evidence.stream()
                    .filter(e -> claim.id().equals(e.claimId())
                            && e.relation() == EvidenceRelation.CONTRADICTS)
                    .findFirst().orElseThrow();
            String condition = conditionOf(sourceOf(counterexample.sourceId(), sources));
            proposal = claim.proposition() + " (except where " + condition + ")";
            proposalReason = "the condition is quoted verbatim from the counterexample's "
                    + "observation; the system assembled the sentence and did not decide the "
                    + "wording — that is the user's to accept or reject";
        } else if (!contradicting.isEmpty()) {
            proposalReason = "the recorded evidence does not support a narrower formulation: "
                    + (reading == CounterexampleReading.INCONCLUSIVE
                            ? "the counterexample states no condition"
                            : "the claim is contradicted as stated, and narrowing it would not be "
                                    + "a refinement");
        }

        return new ClaimAssessment(
                workspaceId,
                claim.id(),
                claim.proposition(),
                claim.scope(),
                standing,
                standingReason,
                reading,
                readingReason,
                alternatives,
                supporting,
                contradicting,
                qualifying,
                exemplifying,
                insufficient,
                unresolved,
                proposal,
                proposalReason,
                assessedAt());
    }

    /** The epistemic standing implied by the evidence relations. */
    private static ClaimStatus standing(List<ClaimAssessment.EvidenceRef> supporting,
                                        List<ClaimAssessment.EvidenceRef> contradicting,
                                        List<ClaimAssessment.EvidenceRef> exemplifying,
                                        List<ClaimAssessment.EvidenceRef> insufficient) {
        if (!contradicting.isEmpty() && !supporting.isEmpty()) {
            return ClaimStatus.CONTESTED;
        }
        if (!contradicting.isEmpty()) {
            return ClaimStatus.REFUTED;
        }
        if (!supporting.isEmpty()) {
            return ClaimStatus.SUPPORTED;
        }
        if (!exemplifying.isEmpty()) {
            return ClaimStatus.SUPPORTED;
        }
        if (!insufficient.isEmpty()) {
            return ClaimStatus.INSUFFICIENT_EVIDENCE;
        }
        return ClaimStatus.UNKNOWN;
    }

    /** Why the standing is what it is — what the evidence does and does not establish. */
    private static String standingReason(ClaimStatus standing,
                                         List<ClaimAssessment.EvidenceRef> supporting,
                                         List<ClaimAssessment.EvidenceRef> contradicting,
                                         List<ClaimAssessment.EvidenceRef> exemplifying,
                                         List<ClaimAssessment.EvidenceRef> insufficient,
                                         List<Evidence> contextual) {
        return switch (standing) {
            case CONTESTED -> "the evidence both supports and contradicts the claim; the sources "
                    + "disagree and both accounts are kept";
            case REFUTED -> "no evidence supports the claim and " + contradicting.size()
                    + " contradicts it";
            case SUPPORTED -> {
                if (supporting.isEmpty()) {
                    yield "supported by " + exemplifying.size() + " example(s) only; an example "
                            + "is consistent with the claim and does not establish it in general";
                }
                if (exemplifying.isEmpty()) {
                    yield "supported by " + supporting.size() + " source(s)";
                }
                yield "supported by " + supporting.size() + " source(s) and " + exemplifying.size()
                        + " example(s); an example is consistent with the claim and does not "
                        + "establish it in general";
            }
            case INSUFFICIENT_EVIDENCE -> "the evidence was sought and does not decide; the "
                    + "absence of support is not evidence of absence";
            case UNKNOWN -> contextual.isEmpty()
                    ? "no evidence has been recorded for this claim"
                    : "nothing in the recorded evidence bears on the claim";
            default -> standing.name();
        };
    }

    /**
     * The timestamp of the assessment.
     *
     * <p>It records WHEN the reasoning ran and nothing reads it: the
     * conclusion depends on the evidence, not on the clock.
     */
    @io.matrix.auditor.ArticleIPermitted(value = "the timestamp of the assessment; it records "
            + "WHEN the reasoning ran, and no decision reads it")
    private static String assessedAt() {
        return java.time.Instant.now().toString();
    }

    /** The condition of an observation, which is where it was observed. */
    private static String conditionOf(Source source) {
        if (source == null || source.kind() != SourceKind.OBSERVATION) {
            return null;
        }
        return source.locator();
    }

    /** The source of an evidence row. */
    private static Source sourceOf(String sourceId, List<Source> sources) {
        for (Source s : sources) {
            if (s.id().equals(sourceId)) {
                return s;
            }
        }
        return null;
    }

    /** An evidence row with the source it rests on. */
    private static ClaimAssessment.EvidenceRef ref(Evidence e, List<Source> sources) {
        Source source = sourceOf(e.sourceId(), sources);
        return new ClaimAssessment.EvidenceRef(
                e.id(),
                e.relation() == null ? null : e.relation().name(),
                e.sourceId(),
                source == null ? null : source.title(),
                source == null ? null : source.kind().name(),
                e.excerpt(),
                e.recordedAt());
    }
}
