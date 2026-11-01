package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hands the knowledge of one workspace to another (owner directive §17:
 * "transferring validated knowledge to another learner"; W40.7, the
 * distributed learning path).
 *
 * <p>The store's export and import are a BACKUP and a RESTORE: they put the
 * same workspace back, with the same identifiers. That is not a transfer. A
 * transfer gives the knowledge to a DIFFERENT learner, and the identifiers
 * have to change, because the identifiers are scoped to the workspace that
 * holds them: a claim's id is derived from the workspace, its concept and its
 * proposition, so two workspaces holding the same claim would otherwise hold
 * the same row.
 *
 * <p>What is transferred:
 *
 * <ul>
 *   <li>the concepts, with their new identifiers;</li>
 *   <li>the sources, with their new identifiers;</li>
 *   <li>the claims, with their new identifiers and re-pointed at the new
 *       concept and the new source;</li>
 *   <li>the evidence links, with their new identifiers and both ends
 *       re-pointed;</li>
 *   <li>the representations, re-pointed at the new concept and at the new
 *       claim they refine;</li>
 *   <li>the prerequisites and the revisions, re-pointed the same way.</li>
 * </ul>
 *
 * <p>What is NOT transferred: the learner's own history. The assessment
 * outcomes and the record of the questions that were asked belong to the
 * learner who produced them, not to the knowledge. A learner who receives
 * this knowledge has not yet demonstrated that they know it, and their
 * learning state must not say that they have.
 *
 * <p>The transfer is therefore a statement about the KNOWLEDGE only, and the
 * receiving workspace's learning state starts empty. That is the honest
 * reading of "validated knowledge": the knowledge was validated; the new
 * learner has not yet been assessed on it.
 */
public final class KnowledgeTransfer {

    /** What a transfer produced. */
    public record TransferResult(
            String fromWorkspace,
            String toWorkspace,
            int concepts,
            int claims,
            int sources,
            int evidenceLinks,
            int representations,
            int revisions,
            int prerequisites,
            List<String> notes) {

        public TransferResult {
            notes = List.copyOf(notes == null ? List.of() : notes);
        }
    }

    private KnowledgeTransfer() {
    }

    /**
     * Transfer a workspace's knowledge to another workspace.
     *
     * @param store the store to read from and write to
     * @param fromWorkspace the workspace whose knowledge is transferred
     * @param toWorkspace the workspace that receives it
     */
    public static TransferResult transfer(KnowledgeStore store, String fromWorkspace,
                                          String toWorkspace) {
        if (fromWorkspace.equals(toWorkspace)) {
            throw new IllegalArgumentException(
                    "a transfer needs two workspaces; use the export for a backup of one");
        }
        KnowledgeSnapshot source = store.exportWorkspace(fromWorkspace);
        if (source.concepts().isEmpty()) {
            throw new IllegalArgumentException(
                    "the workspace " + fromWorkspace + " holds no knowledge to transfer");
        }
        KnowledgeSnapshot moved = rekey(source, toWorkspace);
        store.importWorkspace(moved);

        List<String> notes = new ArrayList<>();
        notes.add("The identifiers were regenerated for the receiving workspace: a claim's id is "
                + "derived from the workspace that holds it, so the same claim in two workspaces "
                + "must not share a row.");
        notes.add("The learner's own history was NOT transferred: the assessment outcomes and the "
                + "record of the questions that were asked belong to the learner who produced them. "
                + "The receiving workspace's learning state starts empty, because knowledge that was "
                + "transferred has not been demonstrated by the new learner.");
        notes.add("The source records were transferred with their locators, their excerpts and their "
                + "authority and freshness, so the receiving workspace can show where each claim "
                + "came from.");

        return new TransferResult(fromWorkspace, toWorkspace, moved.concepts().size(),
                moved.claims().size(), moved.sources().size(), moved.evidence().size(),
                moved.representations().size(), moved.revisions().size(),
                moved.prerequisites().size(), notes);
    }

    /**
     * Give a snapshot the identifiers of another workspace.
     *
     * <p>The order matters: the concepts and the sources first, because the
     * claims refer to them; then the claims, because the evidence and the
     * representations refer to them.
     */
    static KnowledgeSnapshot rekey(KnowledgeSnapshot source, String toWorkspace) {
        Map<String, String> conceptIds = new HashMap<>();
        List<Concept> concepts = new ArrayList<>();
        for (Concept concept : source.concepts()) {
            String id = Ids.concept(toWorkspace, concept.name());
            conceptIds.put(concept.id(), id);
            concepts.add(new Concept(id, toWorkspace, concept.name(), concept.definition(),
                    concept.domain(), concept.createdAt()));
        }

        Map<String, String> sourceIds = new HashMap<>();
        List<Source> sources = new ArrayList<>();
        for (Source src : source.sources()) {
            String id = Ids.source(toWorkspace, src.kind(), src.locator());
            sourceIds.put(src.id(), id);
            sources.add(new Source(id, toWorkspace, src.kind(), src.title(), src.locator(),
                    src.authority(), src.freshness(), src.excerpt(), src.addedAt(),
                    src.contentHash(), src.publishedAt()));
        }

        Map<String, String> claimIds = new HashMap<>();
        List<Claim> claims = new ArrayList<>();
        for (Claim claim : source.claims()) {
            String id = Ids.claim(toWorkspace, conceptIds.get(claim.conceptId()),
                    claim.proposition());
            claimIds.put(claim.id(), id);
            String introducedBy = claim.introducedBySourceId();
            claims.add(new Claim(id, toWorkspace, conceptIds.get(claim.conceptId()),
                    claim.proposition(), claim.scope(), claim.context(), claim.status(),
                    claim.confidence(), claim.version(),
                    introducedBy == null ? null : sourceIds.getOrDefault(introducedBy, introducedBy),
                    claim.extractionMethod(), claim.validationMethod(),
                    claim.warrant(), claim.derivationStatus(),
                    claim.createdAt(), claim.updatedAt()));
        }

        List<Evidence> evidence = new ArrayList<>();
        for (Evidence ev : source.evidence()) {
            String claimId = claimIds.get(ev.claimId());
            String srcId = sourceIds.get(ev.sourceId());
            if (claimId == null || srcId == null) {
                continue;
            }
            evidence.add(new Evidence(
                    Ids.evidence(toWorkspace, claimId, srcId), toWorkspace, claimId, srcId,
                    ev.relation(), ev.excerpt(), ev.quality(), ev.recordedAt()));
        }

        // The representations are re-keyed in two passes, because one can
        // refine another: the first pass gives every representation its new
        // identifier, the second points each refinement at the right one.
        Map<String, String> representationIds = new HashMap<>();
        for (Representation rep : source.representations()) {
            String conceptId = conceptIds.get(rep.conceptId());
            if (conceptId == null) {
                continue;
            }
            representationIds.put(rep.id(), Ids.representation(toWorkspace, conceptId,
                    rep.level(), rep.statement()));
        }
        List<Representation> representations = new ArrayList<>();
        for (Representation rep : source.representations()) {
            String conceptId = conceptIds.get(rep.conceptId());
            if (conceptId == null) {
                continue;
            }
            List<String> prerequisiteConceptIds = new ArrayList<>();
            for (String prerequisiteId : rep.prerequisiteConceptIds()) {
                String moved = conceptIds.get(prerequisiteId);
                if (moved != null) {
                    prerequisiteConceptIds.add(moved);
                }
            }
            String refines = rep.refinesRepresentationId();
            representations.add(new Representation(
                    representationIds.get(rep.id()), toWorkspace, conceptId, rep.level(),
                    rep.audience(), rep.statement(), rep.scope(), rep.assumptions(),
                    rep.includedFacts(), rep.deliberatelyOmitted(), rep.validityConditions(),
                    prerequisiteConceptIds,
                    refines == null ? null : representationIds.getOrDefault(refines, refines),
                    rep.confidence(), rep.createdAt()));
        }

        List<Prerequisite> prerequisites = new ArrayList<>();
        for (Prerequisite prerequisite : source.prerequisites()) {
            String conceptId = conceptIds.get(prerequisite.conceptId());
            String requiresId = conceptIds.get(prerequisite.requiresConceptId());
            if (conceptId == null || requiresId == null) {
                continue;
            }
            prerequisites.add(new Prerequisite(toWorkspace, conceptId, requiresId,
                    prerequisite.required(), prerequisite.note()));
        }

        List<KnowledgeRevision> revisions = new ArrayList<>();
        for (KnowledgeRevision revision : source.revisions()) {
            String targetId = claimIds.get(revision.targetId());
            if (targetId == null) {
                continue;
            }
            List<String> evidenceIds = new ArrayList<>();
            for (String evidenceId : revision.evidenceIds()) {
                for (Evidence ev : evidence) {
                    if (ev.id().equals(evidenceId)
                            || (source.evidence().stream().anyMatch(old ->
                                    old.id().equals(evidenceId)
                                            && old.claimId().equals(revision.targetId())
                                            && ev.claimId().equals(targetId)))) {
                        evidenceIds.add(ev.id());
                        break;
                    }
                }
            }
            revisions.add(new KnowledgeRevision(
                    Ids.revision(toWorkspace, targetId, revision.kind(), revision.summary()),
                    toWorkspace, revision.targetType(), targetId, revision.kind(),
                    revision.summary(), revision.addedFacts(), revision.stillValidAssumptions(),
                    revision.noLongerValidAssumptions(), revision.newlyExplainedCases(),
                    evidenceIds, revision.recordedAt()));
        }

        return new KnowledgeSnapshot(toWorkspace, source.workspaceName(), concepts, sources, claims,
                evidence, representations, prerequisites, revisions, source.exportedAt());
    }
}
