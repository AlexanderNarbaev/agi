package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.knowledge.acquisition.DocumentImporter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.4 — an imported concept gets an account of its own.
 *
 * <p>The mission's first practical objective says the system must "improve
 * its knowledge representation". RECON-W41 could not: the representations
 * were authored by hand in the packages, and a concept that came from a
 * document had claims and evidence but no authored account — so the system's
 * answer about it was the claim itself, which is not an explanation.
 */
class RepresentationComposerTest {

    private static final String LESSON = """
            Cellular respiration is the process by which cells break down glucose to release energy.

            Glycolysis is the first stage of cellular respiration. It splits one glucose molecule into two pyruvate molecules in the cytoplasm.

            The Krebs cycle produces NADH and FADH2 in the mitochondrial matrix.
            """;

    @Test
    @DisplayName("an imported concept gets representations at the levels the document supports")
    void anImportedConceptGetsRepresentationsAtTheLevelsTheDocumentSupports(@TempDir Path dir)
            throws Exception {
        Path file = dir.resolve("lesson.txt");
        Files.writeString(file, LESSON, StandardCharsets.UTF_8);

        try (KnowledgeStore store = new KnowledgeStore(dir.resolve("w.sqlite"))) {
            DocumentImporter.ImportResult result =
                    new DocumentImporter(store).importDocument("ws", file, null);

            assertThat(result.representationsCreated()).isGreaterThan(0);

            List<Representation> representations = store.exportWorkspace("ws").representations();
            assertThat(representations).isNotEmpty();
            // Every representation's concept exists.
            assertThat(representations).allSatisfy(representation ->
                    assertThat(store.concepts("ws")).anySatisfy(concept ->
                            assertThat(concept.id()).isEqualTo(representation.conceptId())));
        }
    }

    @Test
    @DisplayName("the account uses the document's own sentences")
    void theAccountUsesTheDocumentsOwnSentences(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("lesson.txt");
        Files.writeString(file, LESSON, StandardCharsets.UTF_8);

        try (KnowledgeStore store = new KnowledgeStore(dir.resolve("w.sqlite"))) {
            new DocumentImporter(store).importDocument("ws", file, null);

            for (Representation representation : store.exportWorkspace("ws").representations()) {
                assertThat(representation.statement()).isNotBlank();
                // Every included fact is a sentence of the document.
                for (String fact : representation.includedFacts()) {
                    assertThat(LESSON).contains(fact);
                }
            }
        }
    }

    @Test
    @DisplayName("a level the document does not support is not written")
    void aLevelTheDocumentDoesNotSupportIsNotWritten(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("simple.txt");
        Files.writeString(file, "Water is a transparent substance that freezes at zero degrees.",
                StandardCharsets.UTF_8);

        try (KnowledgeStore store = new KnowledgeStore(dir.resolve("w.sqlite"))) {
            new DocumentImporter(store).importDocument("ws", file, null);

            List<Representation> representations = store.exportWorkspace("ws").representations();
            // One simple claim: one introductory account, and nothing that
            // claims to be more.
            assertThat(representations).allSatisfy(representation ->
                    assertThat(representation.level()).isEqualTo(AbstractionLevel.INTRODUCTORY));
        }
    }

    @Test
    @DisplayName("the account says what it leaves out")
    void theAccountSaysWhatItLeavesOut(@TempDir Path dir) throws Exception {
        // A document with three sentences about ONE subject: the concept
        // gets three claims, and the introductory account has to say which
        // of them it left out.
        Path file = dir.resolve("water.txt");
        Files.writeString(file, """
                Water is a transparent substance.

                Water boils at one hundred degrees.

                Water freezes at zero degrees.
                """, StandardCharsets.UTF_8);

        try (KnowledgeStore store = new KnowledgeStore(dir.resolve("w.sqlite"))) {
            new DocumentImporter(store).importDocument("ws", file, null);

            List<Representation> representations = store.exportWorkspace("ws").representations();
            assertThat(representations).anySatisfy(representation ->
                    assertThat(representation.deliberatelyOmitted()).isNotEmpty());
        }
    }

    @Test
    @DisplayName("the account survives a restart")
    void theAccountSurvivesARestart(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("w.sqlite");
        Path file = dir.resolve("lesson.txt");
        Files.writeString(file, LESSON, StandardCharsets.UTF_8);

        int before;
        try (KnowledgeStore store = new KnowledgeStore(db)) {
            new DocumentImporter(store).importDocument("ws", file, null);
            before = store.exportWorkspace("ws").representations().size();
        }

        try (KnowledgeStore store = new KnowledgeStore(db)) {
            List<Representation> representations = store.exportWorkspace("ws").representations();
            assertThat(representations).hasSize(before);
            assertThat(representations).isNotEmpty();
        }
    }
}
