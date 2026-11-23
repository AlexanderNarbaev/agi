package io.matrix.knowledge;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** The applicable evidence for current reasoning or a named historical source event. */
public final class SourceRevisionView {

    private SourceRevisionView() {
    }

    public static KnowledgeSnapshot project(KnowledgeSnapshot archive, String historicalEventId) {
        List<SourceRevisionSelection> events = archive.sourceRevisionSelections();
        if (events.isEmpty() && historicalEventId == null) {
            return archive;
        }
        SourceRevisionSelection.validateChainAndSources(archive.workspaceId(), events, archive.sources());
        Set<String> predecessors = events.stream().map(SourceRevisionSelection::previousEventId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, SourceRevisionSelection> selected = new LinkedHashMap<>();
        for (SourceRevisionSelection event : events) {
            if (!predecessors.contains(event.eventId())) {
                selected.put(event.documentKey(), event);
            }
        }
        if (historicalEventId != null) {
            SourceRevisionSelection requested = events.stream()
                    .filter(event -> historicalEventId.equals(event.eventId())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown workspace-local source revision event"));
            selected.put(requested.documentKey(), requested);
        }
        Set<String> active = archive.sources().stream().map(Source::id).collect(Collectors.toSet());
        events.forEach(event -> active.remove(event.sourceId()));
        selected.values().forEach(event -> active.add(event.sourceId()));
        return restrict(archive, active, List.copyOf(selected.values()));
    }

    static KnowledgeSnapshot restrict(KnowledgeSnapshot archive, Set<String> allowed,
            List<SourceRevisionSelection> selected) {
        List<Source> sources = archive.sources().stream()
                .filter(source -> allowed.contains(source.id())).toList();
        List<Evidence> availableEvidence = archive.evidence().stream()
                .filter(row -> allowed.contains(row.sourceId())).toList();
        Set<String> independentlySupported = availableEvidence.stream()
                .filter(row -> row.relation() == EvidenceRelation.SUPPORTS)
                .map(Evidence::claimId).collect(Collectors.toSet());
        Set<Statement> activeStatements = archive.claims().stream()
                .filter(claim -> claim.introducedBySourceId() != null
                        && allowed.contains(claim.introducedBySourceId())
                        && !EpistemicPolicy.isWithdrawn(claim.status()))
                .map(Statement::of).collect(Collectors.toSet());
        List<Claim> claims = archive.claims().stream()
                .filter(claim -> {
                    String introducedBy = claim.introducedBySourceId();
                    return introducedBy == null || allowed.contains(introducedBy)
                            || independentlySupported.contains(claim.id())
                            && !activeStatements.contains(Statement.of(claim));
                }).toList();
        Set<String> claimIds = claims.stream().map(Claim::id).collect(Collectors.toSet());
        Set<String> conceptIds = claims.stream().map(Claim::conceptId).collect(Collectors.toSet());
        Set<String> affectedConcepts = archive.claims().stream()
                .filter(claim -> !claimIds.contains(claim.id()))
                .map(Claim::conceptId).collect(Collectors.toSet());
        return new KnowledgeSnapshot(archive.workspaceId(), archive.workspaceName(),
                archive.concepts().stream().filter(concept -> conceptIds.contains(concept.id())).toList(),
                sources, claims,
                availableEvidence.stream().filter(row -> claimIds.contains(row.claimId())).toList(),
                archive.representations().stream().filter(row -> conceptIds.contains(row.conceptId())
                        && !affectedConcepts.contains(row.conceptId())).toList(),
                archive.prerequisites(), archive.revisions(), archive.derivations().stream()
                        .filter(row -> claimIds.contains(row.conclusionClaimId())
                                && claimIds.containsAll(row.premiseClaimIds())).toList(),
                archive.contradictions().stream()
                        .filter(row -> claimIds.contains(row.claimAId()) && claimIds.contains(row.claimBId())).toList(),
                archive.goals(), selected, archive.exportedAt());
    }

    private record Statement(String proposition, String scope) {

        static Statement of(Claim claim) {
            return new Statement(claim.proposition(), claim.scope());
        }
    }
}
