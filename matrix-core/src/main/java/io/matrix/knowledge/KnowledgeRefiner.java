package io.matrix.knowledge;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies a correction to what is known, without destroying what was known
 * before (owner directive §13, §20 KnowledgeRefiner).
 *
 * <p>The workflow the owner directive describes has ten steps. This class
 * performs the ones that change the store:
 *
 * <ol>
 *   <li>the earlier representation is PRESERVED — the superseded claim keeps
 *       its row, with the status SUPERSEDED;</li>
 *   <li>the new claim is created with its own scope, so a claim that was
 *       true under one condition and false under another can be corrected by
 *       adding the condition rather than by erasing the fact;</li>
 *   <li>the new claim carries its SOURCE, and the evidence link is what
 *       makes it SUPPORTED rather than DRAFT;</li>
 *   <li>the revision records what was added, which assumptions remain valid,
 *       which simplifications stopped applying, and which new cases the
 *       refined model explains.</li>
 * </ol>
 *
 * <p>The changes are returned rather than applied, so the caller can apply
 * them in one transaction with everything else the refinement involves.
 */
public final class KnowledgeRefiner {

    /**
     * A refinement: what is now believed, on what authority, and what
     * changed.
     */
    public record Refinement(
            String conceptId,
            String supersededClaimId,
            String proposition,
            String scope,
            String context,
            ClaimStatus status,
            double confidence,
            SourceKind sourceKind,
            String sourceTitle,
            String sourceLocator,
            String sourceExcerpt,
            int sourceAuthority,
            int sourceFreshness,
            RevisionKind revisionKind,
            String summary,
            List<String> addedFacts,
            List<String> stillValidAssumptions,
            List<String> noLongerValidAssumptions,
            List<String> newlyExplainedCases) {

        public Refinement {
            addedFacts = List.copyOf(addedFacts == null ? List.of() : addedFacts);
            stillValidAssumptions = List.copyOf(stillValidAssumptions == null ? List.of() : stillValidAssumptions);
            noLongerValidAssumptions = List.copyOf(noLongerValidAssumptions == null ? List.of() : noLongerValidAssumptions);
            newlyExplainedCases = List.copyOf(newlyExplainedCases == null ? List.of() : newlyExplainedCases);
        }
    }

    /**
     * Build the changes a refinement implies.
     *
     * @throws IllegalArgumentException when the refinement names a concept
     *     or a superseded claim that the workspace does not hold
     */
    public KnowledgeChanges refine(String workspaceId, KnowledgeSnapshot snapshot, Refinement request) {
        Concept concept = concept(snapshot, request.conceptId());
        String now = Instant.now().toString();

        Claim superseded = null;
        if (request.supersededClaimId() != null && !request.supersededClaimId().isBlank()) {
            superseded = claim(snapshot, request.supersededClaimId());
            if (!superseded.conceptId().equals(concept.id())) {
                throw new IllegalArgumentException(
                        "the superseded claim belongs to a different concept");
            }
        }

        int version = superseded == null ? 1 : superseded.version() + 1;
        String claimId = Ids.claim(workspaceId, concept.id(),
                request.proposition() + "#v" + version);

        Source source = new Source(
                Ids.source(workspaceId, request.sourceKind(),
                        request.sourceLocator() == null ? request.sourceTitle() : request.sourceLocator()),
                workspaceId, request.sourceKind(), request.sourceTitle(), request.sourceLocator(),
                request.sourceAuthority(), request.sourceFreshness(), request.sourceExcerpt(), now);

        Claim updated = new Claim(claimId, workspaceId, concept.id(), request.proposition(),
                request.scope(), request.context(), request.status(), request.confidence(), version,
                source.id(), now, now);

        Evidence evidence = new Evidence(Ids.evidence(workspaceId, claimId, source.id()),
                workspaceId, claimId, source.id(), EvidenceRelation.SUPPORTS,
                request.sourceExcerpt(), request.sourceAuthority(), now);

        List<Claim> claims = new ArrayList<>();
        claims.add(updated);

        // The earlier claim is kept, marked superseded, and its version is
        // advanced. It is never deleted.
        if (superseded != null) {
            claims.add(new Claim(superseded.id(), superseded.workspaceId(), superseded.conceptId(),
                    superseded.proposition(), superseded.scope(), superseded.context(),
                    ClaimStatus.SUPERSEDED, superseded.confidence(), superseded.version() + 1,
                    superseded.introducedBySourceId(), superseded.createdAt(), now));
        }

        List<String> stillValid = request.stillValidAssumptions();
        List<String> noLongerValid = request.noLongerValidAssumptions();
        if (superseded != null && stillValid.isEmpty() && noLongerValid.isEmpty()) {
            // A refinement that does not say what changed is not a record of
            // a change; the superseded claim's own scope is the obvious
            // candidate for the assumption that no longer holds.
            noLongerValid = superseded.scope() == null ? List.of() : List.of(superseded.scope());
        }

        KnowledgeRevision revision = new KnowledgeRevision(
                Ids.revision(workspaceId, concept.id(), request.revisionKind(), request.summary()),
                workspaceId, "concept", concept.id(), request.revisionKind(), request.summary(),
                request.addedFacts(), stillValid, noLongerValid, request.newlyExplainedCases(),
                List.of(evidence.id()), now);

        return new KnowledgeChanges(List.of(source), claims, List.of(evidence), List.of(revision));
    }

    private static Concept concept(KnowledgeSnapshot snapshot, String conceptId) {
        for (Concept concept : snapshot.concepts()) {
            if (concept.id().equals(conceptId)) {
                return concept;
            }
        }
        throw new IllegalArgumentException("The workspace holds no concept " + conceptId);
    }

    private static Claim claim(KnowledgeSnapshot snapshot, String claimId) {
        for (Claim claim : snapshot.claims()) {
            if (claim.id().equals(claimId)) {
                return claim;
            }
        }
        throw new IllegalArgumentException("The workspace holds no claim " + claimId);
    }
}
