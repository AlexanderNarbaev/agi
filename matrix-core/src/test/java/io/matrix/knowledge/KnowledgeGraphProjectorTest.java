package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.7 — the knowledge graph as a read-only projection (D-003).
 *
 * <p>The graph store was a separate, hand-populated store: the lifecycle
 * manager added two entities to it, and the workspace's own concepts and
 * claims were not in it at all.
 */
class KnowledgeGraphProjectorTest {

    @Test
    @DisplayName("the projection holds the workspace's concepts, claims and sources")
    void theProjectionHoldsTheWorkspacesConceptsClaimsAndSources(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            KnowledgeSnapshot snapshot = workbench.workspace("photosynthesis");

            KnowledgeGraphStore graph = KnowledgeGraphProjector.project(snapshot);

            assertThat(graph.entityCount()).isEqualTo(snapshot.concepts().size()
                    + snapshot.claims().size() + snapshot.sources().size());
            assertThat(graph.getEntity("c-photosynthesis-4d4bb673")).isPresent();
            assertThat(graph.getEntity("c-photosynthesis-4d4bb673").orElseThrow().type())
                    .isEqualTo("concept");
            assertThat(graph.getEntity("s-publication-540a1d14")).isPresent();
        }
    }

    @Test
    @DisplayName("a claim states its concept, and is supported by its source")
    void aClaimStatesItsConceptAndIsSupportedByItsSource(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            KnowledgeGraphStore graph =
                    KnowledgeGraphProjector.project(workbench.workspace("photosynthesis"));

            assertThat(graph.getRelations("cl-a9724c3d")).anySatisfy(relation -> {
                assertThat(relation.predicate()).isEqualTo("states");
                assertThat(relation.toId()).isEqualTo("c-photosynthesis-4d4bb673");
            });
            assertThat(graph.getRelations("cl-a9724c3d")).anySatisfy(relation -> {
                assertThat(relation.predicate()).isEqualTo("supported by");
                assertThat(relation.toId()).startsWith("s-");
            });
        }
    }

    @Test
    @DisplayName("the projection holds no state of its own")
    void theProjectionHoldsNoStateOfItsOwn(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            KnowledgeSnapshot snapshot = workbench.workspace("photosynthesis");

            KnowledgeGraphStore first = KnowledgeGraphProjector.project(snapshot);
            KnowledgeGraphStore second = KnowledgeGraphProjector.project(snapshot);

            // Two projections of the same workspace are the same graph:
            // there is no second copy of the knowledge that could drift.
            assertThat(second.entityCount()).isEqualTo(first.entityCount());
            assertThat(second.relationCount()).isEqualTo(first.relationCount());
            assertThat(second.entityIds()).isEqualTo(first.entityIds());
        }
    }

    @Test
    @DisplayName("a change in the workspace shows in the next projection")
    void aChangeInTheWorkspaceShowsInTheNextProjection(@TempDir Path dir) throws Exception {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            int before = KnowledgeGraphProjector.project(workbench.workspace("photosynthesis"))
                    .entityCount();

            java.nio.file.Path lesson = dir.resolve("lesson.txt");
            java.nio.file.Files.writeString(lesson,
                    "The thylakoid membrane is the site of the light-dependent reactions.\n");
            workbench.importDocument("photosynthesis", lesson, null);

            int after = KnowledgeGraphProjector.project(workbench.workspace("photosynthesis"))
                    .entityCount();
            assertThat(after).isGreaterThan(before);
        }
    }

    @Test
    @DisplayName("the relation's weight is the claim's own authored confidence")
    void theRelationsWeightIsTheClaimsOwnAuthoredConfidence(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            KnowledgeSnapshot snapshot = workbench.workspace("photosynthesis");
            KnowledgeGraphStore graph = KnowledgeGraphProjector.project(snapshot);

            Claim claim = snapshot.claims().stream()
                    .filter(c -> c.id().equals("cl-a9724c3d"))
                    .findFirst()
                    .orElseThrow();
            double weight = graph.getRelations(claim.id()).stream()
                    .filter(relation -> relation.predicate().equals("states"))
                    .findFirst()
                    .orElseThrow()
                    .weight();

            assertThat(weight).isEqualTo(claim.confidence());
            assertThat(weight).isBetween(0.0, 1.0);
        }
    }

    @Test
    @DisplayName("the graph of a workspace with no knowledge is empty, and does not fail")
    void theGraphOfAWorkspaceWithNoKnowledgeIsEmpty(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            KnowledgeGraphStore graph =
                    KnowledgeGraphProjector.project(workbench.workspace("empty"));
            assertThat(graph.entityCount()).isEqualTo(0);
            assertThat(graph.relationCount()).isEqualTo(0);
        }
    }
}
