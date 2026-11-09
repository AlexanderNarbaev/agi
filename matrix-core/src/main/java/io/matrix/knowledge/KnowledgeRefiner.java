package io.matrix.knowledge;

import java.time.Clock;
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
     * The clock the refined records are stamped with.
     *
     * <p>The stamp is not decoration: it becomes the {@code createdAt} of the
     * refined representation, and {@code Representations.activeAt} picks the
     * active account of a level by comparing those stamps - so the refinement
     * decides WHICH account is the previous one it refines. A wall clock read
     * there would decide what the system believes, so the instant is injected
     * (Article I) and the default is the system clock.
     */
    private final Clock clock;

    /** The refiner that stamps its records with the system clock. */
    public KnowledgeRefiner() {
        this(Clock.systemUTC());
    }

    /**
     * The refiner that stamps its records with a chosen clock.
     *
     * @param clock the instant the refinement is stamped with; the system
     *     clock when null
     */
    public KnowledgeRefiner(Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * A refinement: what is now believed, on what authority, and what
     * changed.
     *
     * @param refinedSourceId W51 §6. The identifier of the source the
     *     refinement was READ FROM — the document whose text carries the
     *     new claim. It is the identity of the refinement's own source
     *     record, and it is what keeps two refinements of two documents
     *     from sharing one source record.
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
            List<String> newlyExplainedCases,
            String refinedRepresentation,
            AbstractionLevel refinedRepresentationLevel,
            String refinedSourceId) {

        public Refinement {
            addedFacts = List.copyOf(addedFacts == null ? List.of() : addedFacts);
            stillValidAssumptions = List.copyOf(stillValidAssumptions == null ? List.of() : stillValidAssumptions);
            noLongerValidAssumptions = List.copyOf(noLongerValidAssumptions == null ? List.of() : noLongerValidAssumptions);
            newlyExplainedCases = List.copyOf(newlyExplainedCases == null ? List.of() : newlyExplainedCases);
        }

        /**
         * A refinement that does not name the source it was read from.
         *
         * <p>The identity of the refinement's source is then derived from
         * its locator alone, which is the W50 behaviour this wave removed;
         * a caller that can name the source it read from should.
         */
        public Refinement(
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
                List<String> newlyExplainedCases,
                String refinedRepresentation,
                AbstractionLevel refinedRepresentationLevel) {
            this(conceptId, supersededClaimId, proposition, scope, context, status, confidence,
                    sourceKind, sourceTitle, sourceLocator, sourceExcerpt, sourceAuthority,
                    sourceFreshness, revisionKind, summary, addedFacts, stillValidAssumptions,
                    noLongerValidAssumptions, newlyExplainedCases, refinedRepresentation,
                    refinedRepresentationLevel, null);
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
        String now = clock.instant().toString();

        Claim superseded = null;
        if (request.supersededClaimId() != null && !request.supersededClaimId().isBlank()) {
            superseded = claim(snapshot, request.supersededClaimId());
            if (!superseded.conceptId().equals(concept.id())) {
                throw new IllegalArgumentException(
                        "the superseded claim belongs to a different concept");
            }
            // A claim with nothing behind it cannot be superseded: the
            // superseded status is itself an established one, and the store
            // refuses an established claim with no evidence. The check is
            // here so that the refusal says why.
            if (!hasEvidence(snapshot, superseded.id())) {
                throw new IllegalArgumentException(
                        "the superseded claim carries no evidence, and a claim with no evidence "
                                + "cannot be superseded");
            }
        }

        int version = superseded == null ? 1 : superseded.version() + 1;
        String claimId = Ids.claim(workspaceId, concept.id(),
                request.proposition() + "#v" + version);

        Source source = new Source(
                // W51 §6 — THE IDENTITY OF THE REFINEMENT'S SOURCE.
                //
                // It used to be derived from the locator, and the locator
                // of every imported document is the constant "the whole
                // document" — so every refinement in a workspace derived
                // the SAME source identifier, and the second revision
                // overwrote the first revision's record in place. The W50
                // evaluation measured the consequence: the claims that
                // pointed at that record followed it to a document they
                // were not read from.
                //
                // The identity is now derived from the source the
                // refinement was read from and the proposition it carries.
                request.refinedSourceId() != null
                        ? Ids.refinementSource(workspaceId, request.sourceKind(),
                                request.refinedSourceId(), request.proposition())
                        : Ids.source(workspaceId, request.sourceKind(),
                                request.sourceLocator() == null ? request.sourceTitle()
                                        : request.sourceLocator()),
                workspaceId, request.sourceKind(), request.sourceTitle(), request.sourceLocator(),
                request.sourceAuthority(), request.sourceFreshness(), request.sourceExcerpt(), now,
                // The hash of the material the correction was read from.
                Ids.digest(request.sourceExcerpt() == null ? "" : request.sourceExcerpt()),
                null);

        Claim updated = new Claim(claimId, workspaceId, concept.id(), request.proposition(),
                request.scope(), request.context(), request.status(), request.confidence(), version,
                source.id(),
                "transcribed from the excerpt of the correcting source",
                request.status() == ClaimStatus.VERIFIED
                        ? "checked against the excerpt of the correcting source"
                        : "not independently verified: read from the correcting source",
                KnowledgeStore.warrantOfKind(request.sourceKind().name()),
                DerivationStatus.NOT_DERIVED,
                now, now);

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
                    superseded.introducedBySourceId(),
                    superseded.extractionMethod(), superseded.validationMethod(),
                    superseded.warrant(), superseded.derivationStatus(),
                    superseded.createdAt(), now));
        }

        List<String> stillValid = request.stillValidAssumptions();
        List<String> noLongerValid = request.noLongerValidAssumptions();
        if (superseded != null && stillValid.isEmpty() && noLongerValid.isEmpty()) {
            // A refinement that does not say what changed is not a record of
            // a change; the superseded claim's own scope is the obvious
            // candidate for the assumption that no longer holds.
            noLongerValid = superseded.scope() == null ? List.of() : List.of(superseded.scope());
        }

        // The refined ACCOUNT: when the correction changes what the
        // workspace says, the explanation the reader sees must change too.
        // The earlier representation is kept and the new one refines it, so
        // the chain shows how the account evolved.
        List<Representation> representations = new ArrayList<>();
        if (request.refinedRepresentation() != null && !request.refinedRepresentation().isBlank()) {
            List<Representation> available = snapshot.representations().stream()
                    .filter(r -> r.conceptId().equals(concept.id()))
                    .toList();
            AbstractionLevel level = request.refinedRepresentationLevel() == null
                    ? AbstractionLevel.INTRODUCTORY : request.refinedRepresentationLevel();
            // The account that is refined is the ACTIVE one at that level, so
            // the correction lands on what the reader is actually given.
            Representation previous = Representations.activeAt(available, level);
            if (previous == null) {
                previous = Representations.activeAt(available, AbstractionLevel.INTRODUCTORY);
                if (previous != null) {
                    level = previous.level();
                }
            }
            String representationId = Ids.representation(workspaceId, concept.id(), level,
                    request.refinedRepresentation());
            representations.add(new Representation(representationId, workspaceId, concept.id(), level,
                    previous == null ? "a reader" : previous.audience(),
                    request.refinedRepresentation(),
                    request.scope(),
                    previous == null ? List.of() : previous.assumptions(),
                    request.addedFacts(),
                    previous == null ? List.of() : previous.deliberatelyOmitted(),
                    previous == null ? List.of() : previous.validityConditions(),
                    previous == null ? List.of() : previous.prerequisiteConceptIds(),
                    previous == null ? null : previous.id(),
                    request.confidence(),
                    now));
        }

        KnowledgeRevision revision = new KnowledgeRevision(
                Ids.revision(workspaceId, concept.id(), request.revisionKind(), request.summary()),
                workspaceId, "concept", concept.id(), request.revisionKind(), request.summary(),
                request.addedFacts(), stillValid, noLongerValid, request.newlyExplainedCases(),
                List.of(evidence.id()), now);

        return new KnowledgeChanges(List.of(source), claims, List.of(evidence), representations,
                List.of(revision));
    }

    private static boolean hasEvidence(KnowledgeSnapshot snapshot, String claimId) {
        return snapshot.evidence().stream().anyMatch(e -> e.claimId().equals(claimId));
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
