package io.matrix.knowledge;

import io.matrix.contract.Provenance;
import io.matrix.knowledge.acquisition.DocumentImporter;
import java.util.Objects;
import java.util.Optional;

/** Resolves a canonical claim's source-specific observation in a selected evidence view. */
record SourceObservation(Claim claim, Source source, Evidence support, Provenance.Span span) {

    static Optional<SourceObservation> resolve(KnowledgeSnapshot view, Claim canonical, String sourceId) {
        if (sourceId == null) {
            return Optional.empty();
        }
        Source source = view.sources().stream()
                .filter(row -> sourceId.equals(row.id()) && view.workspaceId().equals(row.workspaceId()))
                .findFirst().orElse(null);
        if (source == null || source.contentHash() == null
                || !sourceId.equals(DocumentImporter.sourceId(view.workspaceId(), source.contentHash()))) {
            return Optional.empty();
        }
        String observationId = DocumentImporter.claimId(
                view.workspaceId(), source.contentHash(), canonical.proposition());
        Claim observation = view.claims().stream()
                .filter(row -> observationId.equals(row.id())
                        && view.workspaceId().equals(row.workspaceId())
                        && sourceId.equals(row.introducedBySourceId())
                        && canonical.proposition().equals(row.proposition())
                        && Objects.equals(canonical.scope(), row.scope()))
                .findFirst().orElse(null);
        if (observation == null || !supports(view, canonical.id(), sourceId, canonical.proposition())) {
            return Optional.empty();
        }
        Provenance.Span span = boundedSpan(observation.context());
        if (span == null) {
            return Optional.empty();
        }
        return view.evidence().stream()
                .filter(row -> isSupport(view, row, observationId, sourceId, observation.proposition()))
                .findFirst().map(row -> new SourceObservation(observation, source, row, span));
    }

    private static Provenance.Span boundedSpan(String context) {
        try {
            Provenance.Span span = ClaimSpan.parse(context);
            return span != null && span.bounded() ? span : null;
        } catch (IllegalArgumentException malformedSpan) {
            return null;
        }
    }

    private static boolean supports(KnowledgeSnapshot view, String claimId, String sourceId, String proposition) {
        return view.evidence().stream().anyMatch(row -> isSupport(view, row, claimId, sourceId, proposition));
    }

    private static boolean isSupport(KnowledgeSnapshot view, Evidence row, String claimId, String sourceId,
            String proposition) {
        return view.workspaceId().equals(row.workspaceId())
                && claimId.equals(row.claimId()) && sourceId.equals(row.sourceId())
                && row.relation() == EvidenceRelation.SUPPORTS
                && proposition.equals(row.excerpt());
    }
}
