package io.matrix.knowledge.acquisition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.matrix.knowledge.Claim;
import io.matrix.knowledge.ClaimStatus;
import io.matrix.knowledge.Concept;
import io.matrix.knowledge.Evidence;
import io.matrix.knowledge.EvidenceRelation;
import io.matrix.knowledge.Ids;
import io.matrix.knowledge.KnowledgeChanges;
import io.matrix.knowledge.KnowledgeStore;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.Source;
import io.matrix.knowledge.SourceKind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.7 — the import is atomic.
 *
 * <p>The mission's §14 asks for an atomic import. The knowledge writes
 * were in one transaction, but the concepts were created by a write of
 * their own, before that transaction began — so a failure in between left
 * the workspace holding concepts with no claims.
 */
class AtomicImportTest {

    @Test
    @DisplayName("a successful import writes the concepts with the claims")
    void aSuccessfulImportWritesTheConceptsWithTheClaims(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("lesson.txt");
        Files.writeString(file, """
                The Krebs cycle is the second stage of cellular respiration.

                It happens in the mitochondrial matrix and produces NADH and FADH2.
                """, StandardCharsets.UTF_8);

        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            int conceptsBefore = workbench.workspace("photosynthesis").concepts().size();
            int claimsBefore = workbench.workspace("photosynthesis").claims().size();

            workbench.importDocument("photosynthesis", file, "lesson.txt", null);

            assertThat(workbench.workspace("photosynthesis").concepts().size())
                    .isGreaterThan(conceptsBefore);
            assertThat(workbench.workspace("photosynthesis").claims().size())
                    .isGreaterThan(claimsBefore);
        }
    }

    @Test
    @DisplayName("a refused change leaves the concept it brought with it unwritten")
    void aRefusedChangeLeavesTheConceptItBroughtWithItUnwritten(@TempDir Path dir) {
        try (KnowledgeStore store = new KnowledgeStore(dir.resolve("w.sqlite"))) {
            Concept concept = new Concept("c-test", "ws", "The Krebs Cycle", null, null,
                    "2026-01-01T00:00:00Z");
            Claim claim = new Claim("cl-test", "ws", "c-test",
                    "The Krebs cycle is the second stage of cellular respiration.",
                    "The Krebs Cycle", "p1", ClaimStatus.CANDIDATE, 0.0, 1, "s-missing",
                    "document extraction", "not validated", null, null,
                    "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z");
            Evidence evidence = new Evidence(Ids.digest("e-test"), "ws", "cl-test", "s-missing",
                    EvidenceRelation.SUPPORTS, "x", 1, "2026-01-01T00:00:00Z");

            // The evidence names a source the workspace does not hold, so
            // the store refuses the change. The concept came with it, and
            // the concept must not be left behind: the import is atomic.
            assertThatThrownBy(() -> store.applyChanges("ws",
                    new KnowledgeChanges(List.of(), List.of(claim), List.of(evidence),
                            List.of(), List.of()),
                    List.of(concept)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknown source");

            assertThat(store.concepts("ws")).isEmpty();
            assertThat(store.evidence("ws")).isEmpty();
        }
    }

    @Test
    @DisplayName("the concept's id is a pure function of the workspace and the name")
    void theConceptsIdIsAPureFunctionOfTheWorkspaceAndTheName() {
        String first = DocumentImporter.conceptId("ws", "The Krebs Cycle");
        String second = DocumentImporter.conceptId("ws", "the krebs cycle");
        assertThat(first).isEqualTo(second);
        assertThat(first).isNotEqualTo(DocumentImporter.conceptId("other", "The Krebs Cycle"));
    }
}
