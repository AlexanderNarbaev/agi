package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W40 — the bundled knowledge package.
 *
 * <p>The package is the first thing a user of the application will actually
 * load, so its integrity is checked here: every claim that asserts a status
 * carries evidence, every reference resolves, the refinement chain is
 * connected, and the misconceptions are actually marked as refuted rather
 * than quietly filed as ordinary facts.
 */
class KnowledgeImporterTest {

    private final KnowledgeImporter importer = new KnowledgeImporter();

    @Test
    @DisplayName("the bundled photosynthesis package imports and satisfies the store's rules")
    void theBundledPackageImports() {
        KnowledgeSnapshot snapshot = importer.importResource("knowledge/photosynthesis.pack.json");

        assertThat(snapshot.workspaceId()).isEqualTo("photosynthesis");
        assertThat(snapshot.concepts()).hasSize(8);
        assertThat(snapshot.sources()).isNotEmpty();
        assertThat(snapshot.claims()).isNotEmpty();
        assertThat(snapshot.representations()).isNotEmpty();

        // The store's own validation must accept it.
        KnowledgeStore.validate(snapshot);
    }

    @Test
    @DisplayName("every established claim carries evidence and every source is a real locator")
    void everyEstablishedClaimCarriesEvidence() {
        KnowledgeSnapshot snapshot = importer.importResource("knowledge/photosynthesis.pack.json");

        var evidenceByClaim = snapshot.evidence().stream()
                .collect(java.util.stream.Collectors.groupingBy(Evidence::claimId));

        for (Claim claim : snapshot.claims()) {
            if (claim.status() == ClaimStatus.DRAFT) {
                continue;
            }
            assertThat(evidenceByClaim.get(claim.id()))
                    .as("claim %s is %s and must carry evidence", claim.id(), claim.status())
                    .isNotEmpty();
        }

        for (Source source : snapshot.sources()) {
            assertThat(source.locator()).as("source %s", source.id()).isNotBlank();
            assertThat(source.locator()).contains("http");
            assertThat(source.excerpt()).as("source %s", source.id()).isNotBlank();
            assertThat(source.authority()).isBetween(0, 100);
        }
    }

    @Test
    @DisplayName("the misconception claims are refuted with contradicting evidence")
    void theMisconceptionsAreRefuted() {
        KnowledgeSnapshot snapshot = importer.importResource("knowledge/photosynthesis.pack.json");

        List<Claim> refuted = snapshot.claims().stream()
                .filter(c -> c.status() == ClaimStatus.REFUTED)
                .toList();
        assertThat(refuted).as("the package must carry refuted misconceptions").isNotEmpty();

        var contradicting = snapshot.evidence().stream()
                .filter(e -> e.relation() == EvidenceRelation.CONTRADICTS)
                .map(Evidence::claimId)
                .toList();
        for (Claim claim : refuted) {
            assertThat(contradicting).as("refuted claim %s", claim.id()).contains(claim.id());
        }
    }

    @Test
    @DisplayName("the refinement chain is connected and ordered from introductory to mechanistic")
    void theRefinementChainIsConnected() {
        KnowledgeSnapshot snapshot = importer.importResource("knowledge/photosynthesis.pack.json");

        List<Representation> photosynthesis = snapshot.representations().stream()
                .filter(r -> r.conceptId().equals(conceptId(snapshot, "Photosynthesis")))
                .toList();
        assertThat(photosynthesis).hasSize(4);

        var byLevel = photosynthesis.stream()
                .collect(java.util.stream.Collectors.toMap(Representation::level, Representation::id));
        assertThat(byLevel.keySet()).containsExactlyInAnyOrder(
                AbstractionLevel.INTRODUCTORY, AbstractionLevel.INTERMEDIATE,
                AbstractionLevel.ADVANCED, AbstractionLevel.MECHANISTIC);

        // Each level refines the one below it, so the introductory account is
        // preserved rather than erased.
        var byId = photosynthesis.stream()
                .collect(java.util.stream.Collectors.toMap(Representation::id, Representation::id));
        Representation intro = photosynthesis.stream()
                .filter(r -> r.level() == AbstractionLevel.INTRODUCTORY).findFirst().orElseThrow();
        Representation intermediate = photosynthesis.stream()
                .filter(r -> r.level() == AbstractionLevel.INTERMEDIATE).findFirst().orElseThrow();
        Representation advanced = photosynthesis.stream()
                .filter(r -> r.level() == AbstractionLevel.ADVANCED).findFirst().orElseThrow();
        Representation mechanistic = photosynthesis.stream()
                .filter(r -> r.level() == AbstractionLevel.MECHANISTIC).findFirst().orElseThrow();

        assertThat(intro.refinesRepresentationId()).isNull();
        assertThat(intermediate.refinesRepresentationId()).isEqualTo(intro.id());
        assertThat(advanced.refinesRepresentationId()).isEqualTo(intermediate.id());
        assertThat(mechanistic.refinesRepresentationId()).isEqualTo(advanced.id());
        assertThat(byId).hasSize(4);
    }

    @Test
    @DisplayName("the introductory representation declares what it omits")
    void theIntroductoryRepresentationDeclaresItsOmissions() {
        KnowledgeSnapshot snapshot = importer.importResource("knowledge/photosynthesis.pack.json");
        Representation intro = snapshot.representations().stream()
                .filter(r -> r.level() == AbstractionLevel.INTRODUCTORY
                        && r.conceptId().equals(conceptId(snapshot, "Photosynthesis")))
                .findFirst().orElseThrow();

        assertThat(intro.deliberatelyOmitted()).isNotEmpty();
        assertThat(intro.validityConditions()).isNotEmpty();
        assertThat(intro.assumptions()).isNotEmpty();
    }

    @Test
    @DisplayName("the package records a revision that explains what changed")
    void thePackageRecordsItsRevision() {
        KnowledgeSnapshot snapshot = importer.importResource("knowledge/photosynthesis.pack.json");

        assertThat(snapshot.revisions()).hasSize(1);
        KnowledgeRevision revision = snapshot.revisions().get(0);
        assertThat(revision.kind()).isEqualTo(RevisionKind.REFINEMENT);
        assertThat(revision.addedFacts()).isNotEmpty();
        assertThat(revision.stillValidAssumptions()).isNotEmpty();
        assertThat(revision.noLongerValidAssumptions()).isNotEmpty();
        assertThat(revision.newlyExplainedCases()).isNotEmpty();
        assertThat(revision.evidenceIds()).isNotEmpty();
    }

    @Test
    @DisplayName("the imported package survives a restart of the store")
    void theImportedPackageSurvivesARestart(@TempDir Path dir) {
        Path db = dir.resolve("knowledge.sqlite");
        KnowledgeSnapshot snapshot = importer.importResource("knowledge/photosynthesis.pack.json");

        try (KnowledgeStore store = new KnowledgeStore(db)) {
            store.importWorkspace(snapshot);
        }

        try (KnowledgeStore reopened = new KnowledgeStore(db)) {
            KnowledgeSnapshot restored = reopened.exportWorkspace("photosynthesis");
            assertThat(restored.concepts()).hasSize(snapshot.concepts().size());
            assertThat(restored.claims()).hasSize(snapshot.claims().size());
            assertThat(restored.representations()).hasSize(snapshot.representations().size());
            assertThat(restored.revisions()).hasSize(snapshot.revisions().size());
            assertThat(restored.evidence()).hasSize(snapshot.evidence().size());
        }
    }

    @Test
    @DisplayName("a package that points at something that is not there is refused")
    void aDanglingReferenceIsRefused() {
        String json = """
                {
                  "workspace": {"id": "broken", "name": "Broken"},
                  "sources": [],
                  "concepts": [{"key": "a", "name": "A", "definition": "d", "domain": "x"}],
                  "claims": [{
                    "key": "c", "concept": "missing", "proposition": "p", "status": "DRAFT"
                  }],
                  "representations": [], "prerequisites": [], "revisions": []
                }
                """;
        assertThatThrownBy(() -> importer.importPack(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown concept");
    }

    @Test
    @DisplayName("a package that is not valid JSON is refused with a readable message")
    void malformedJsonIsRefused() {
        assertThatThrownBy(() -> importer.importPack("{not json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not valid JSON");
    }

    private static String conceptId(KnowledgeSnapshot snapshot, String name) {
        return snapshot.concepts().stream()
                .filter(c -> c.name().equals(name))
                .findFirst().orElseThrow(() -> new AssertionError("no concept named " + name))
                .id();
    }
}
