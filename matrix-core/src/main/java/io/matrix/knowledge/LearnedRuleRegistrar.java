package io.matrix.knowledge;

import java.util.List;
import java.util.Random;

/**
 * Registers what the system has LEARNED about the learner as knowledge
 * (owner directive §24, and the RECON-W40 backlog item F2).
 *
 * <p>The acceptance model produces a rule: which shape of answer tends to be
 * accepted. A rule that lives only in memory is a rule the user cannot read,
 * cannot check, and cannot build on. This puts it where the rest of the
 * knowledge lives — in the store, as a claim with a source and evidence —
 * so that it is subject to the same rules as everything else:
 *
 * <ul>
 *   <li>it is a claim, so it has a status, a scope and a confidence;</li>
 *   <li>it has a source, so its provenance is a thing that can be read; and
 *       the source is an OBSERVATION — the measurement — rather than an
 *       authority the system pretends to have;</li>
 *   <li>it has evidence pointing at it, because a claim that asserts itself
 *       is not knowledge;</li>
 *   <li>it can be superseded later, by the same mechanism as any other
 *       claim.</li>
 * </ul>
 *
 * <p>What the claim says is deliberately narrow. It does not say "the
 * learner understands photosynthesis"; it says what was measured, on how
 * many items, and by what procedure.
 */
public final class LearnedRuleRegistrar {

    /** The kinds of change this registrar can make. */
    public record Registration(
            Claim claim,
            Source source,
            Evidence evidence,
            String proposition,
            int measuredOn,
            double accuracy) {
    }

    private LearnedRuleRegistrar() {
    }

    /**
     * Turn an acceptance-model evaluation into a claim, a source and the
     * evidence that supports it.
     *
     * @param workspaceId the workspace the claim belongs to
     * @param conceptId the concept the claim is about
     * @param datasetId the dataset the measurement came from
     * @param evaluation the measurement
     */
    public static Registration registration(String workspaceId, String conceptId,
                                            String datasetId,
                                            AcceptanceModel.Evaluation evaluation) {
        String proposition = proposition(evaluation, datasetId);

        String locator = "urn:measurement:acceptance-model:" + datasetId;
        Source source = new Source(
                Ids.source(workspaceId, SourceKind.OBSERVATION, locator),
                workspaceId,
                SourceKind.OBSERVATION,
                "The acceptance model measured on the held-out items of " + datasetId,
                locator,
                60,
                95,
                excerpt(evaluation),
                java.time.Instant.now().toString());

        String now = java.time.Instant.now().toString();
        Claim claim = new Claim(
                Ids.claim(workspaceId, conceptId, proposition),
                workspaceId,
                conceptId,
                proposition,
                "The answers this learner gave to the items of " + datasetId,
                "The acceptance model, measured on the held-out items",
                ClaimStatus.SUPPORTED,
                confidence(evaluation),
                1,
                source.id(),
                now,
                now);

        Evidence evidence = new Evidence(
                Ids.evidence(workspaceId, claim.id(), source.id()),
                workspaceId,
                claim.id(),
                source.id(),
                EvidenceRelation.SUPPORTS,
                excerpt(evaluation),
                80,
                java.time.Instant.now().toString());

        return new Registration(claim, source, evidence, proposition,
                evaluation.measuredOn(), evaluation.heldOutAccuracy());
    }

    /**
     * The proposition of the claim.
     *
     * <p>It states the measurement, its sample, and the comparison. It does
     * not claim more than that.
     */
    private static String proposition(AcceptanceModel.Evaluation evaluation, String datasetId) {
        if (evaluation.measuredOn() < 5) {
            return "The acceptance model of the answers to " + datasetId + " has been trained but not "
                    + "yet measured: only " + evaluation.measuredOn() + " held-out item(s) have been "
                    + "answered, and a comparison on that many items cannot decide anything.";
        }
        return "On the held-out items of " + datasetId + ", the rule induced from the learner's own "
                + "answers was accepted on " + Math.round(evaluation.heldOutAccuracy() * 100)
                + "% of " + evaluation.measuredOn() + " items, and the deterministic grader was "
                + "accepted on " + Math.round(evaluation.deterministicAccuracy() * 100)
                + "% of the same items. The grader remains the production path; the rule is kept "
                + "because it names the shape of an answer that tends to be accepted, which a lookup "
                + "table cannot.";
    }

    private static String excerpt(AcceptanceModel.Evaluation evaluation) {
        return evaluation.conclusion();
    }

    private static double confidence(AcceptanceModel.Evaluation evaluation) {
        if (evaluation.measuredOn() < 5) {
            return 0.2;
        }
        return Math.min(0.95, 0.5 + evaluation.heldOutAccuracy() * 0.4);
    }
}
