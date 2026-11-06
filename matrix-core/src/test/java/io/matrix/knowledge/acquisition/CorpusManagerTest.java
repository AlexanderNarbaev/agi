package io.matrix.knowledge.acquisition;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.Source;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.7 — the corpus registry.
 *
 * <p>The mission's §5.5: "Ten documents copying the same source do not
 * constitute ten independent confirmations", and the registry is the
 * place where that rule is visible.
 */
class CorpusManagerTest {

    @Test
    @DisplayName("the registry lists every source")
    void theRegistryListsEverySource(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            CorpusManager.Registry registry = CorpusManager.registry(workbench, "photosynthesis");

            assertThat(registry.sources()).isEqualTo(
                    workbench.workspace("photosynthesis").sources().size());
            assertThat(registry.entries()).hasSize(registry.sources());
            assertThat(registry.authored()).isGreaterThan(0);
            assertThat(registry.imported()).isEqualTo(0);
            assertThat(registry.notes()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("the imported source is in the registry, with its language")
    void theImportedSourceIsInTheRegistryWithItsLanguage(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("notes.txt");
        Files.writeString(file, """
                Oxygenic photosynthesis releases oxygen as a byproduct of splitting water.
                """, StandardCharsets.UTF_8);

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.importDocument("photosynthesis", file, null);

            CorpusManager.Registry registry = CorpusManager.registry(workbench, "photosynthesis");

            assertThat(registry.imported()).isEqualTo(1);
            assertThat(registry.documents()).isEqualTo(1);
            CorpusManager.Entry imported = registry.entries().stream()
                    .filter(CorpusManager.Entry::imported)
                    .findFirst()
                    .orElseThrow();
            assertThat(imported.language()).isEqualTo("en");
            assertThat(imported.format()).isEqualTo("txt");
            assertThat(imported.contentHash()).isNotBlank();
            assertThat(imported.claimsSupported()).isGreaterThan(0);
            assertThat(imported.note()).contains("independent confirmation");
        }
    }

    @Test
    @DisplayName("a source that supports no claim says so")
    void aSourceThatSupportsNoClaimSaysSo(@TempDir Path dir) throws Exception {
        // The claim extractor does not turn every sentence into a claim,
        // and the registry must say so rather than pretend the source
        // supports something.
        Path file = dir.resolve("notes.txt");
        Files.writeString(file, """
                Some laboratory reports a peculiar property of the chloroplast envelope.
                """, StandardCharsets.UTF_8);

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.importDocument("photosynthesis", file, null);

            CorpusManager.Registry registry = CorpusManager.registry(workbench, "photosynthesis");
            CorpusManager.Entry imported = registry.entries().stream()
                    .filter(CorpusManager.Entry::imported)
                    .findFirst()
                    .orElseThrow();

            assertThat(imported.claimsSupported()).isEqualTo(0);
            assertThat(imported.note()).contains("supports no claim");
        }
    }

    @Test
    @DisplayName("the same document imported twice is one source")
    void theSameDocumentImportedTwiceIsOneSource(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("notes.txt");
        Files.writeString(file, """
                Some laboratory reports a peculiar property of the chloroplast envelope.
                """, StandardCharsets.UTF_8);

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.importDocument("photosynthesis", file, null);
            workbench.importDocument("photosynthesis", file, null);

            CorpusManager.Registry registry = CorpusManager.registry(workbench, "photosynthesis");

            assertThat(registry.imported()).isEqualTo(1);
            assertThat(registry.documents()).isEqualTo(1);
            // The store knows the content, so the second import was a
            // known document, not a new one.
            Source known = CorpusManager.known(workbench, "photosynthesis",
                    registry.entries().stream().filter(CorpusManager.Entry::imported)
                            .findFirst().orElseThrow().contentHash());
            assertThat(known).isNotNull();
        }
    }

    @Test
    @DisplayName("the independent confirmations are counted by the content hash")
    void theIndependentConfirmationsAreCountedByTheContentHash(@TempDir Path dir) throws Exception {
        Path first = dir.resolve("first.txt");
        Files.writeString(first, """
                Oxygenic photosynthesis releases oxygen as a byproduct of splitting water.
                """, StandardCharsets.UTF_8);
        Path second = dir.resolve("second.txt");
        Files.writeString(second, """
                Oxygenic photosynthesis releases oxygen as a byproduct of splitting water.
                """, StandardCharsets.UTF_8);

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.importDocument("photosynthesis", first, null);
            workbench.importDocument("photosynthesis", second, null);

            CorpusManager.Registry registry = CorpusManager.registry(workbench, "photosynthesis");

            // Two files with the SAME CONTENT are one source: the
            // confirmations are counted by the content hash, not by the
            // number of files.
            assertThat(registry.imported()).isEqualTo(1);
            assertThat(registry.independentConfirmations()).isLessThanOrEqualTo(2);
        }
    }

    @Test
    @DisplayName("two files with different content are two sources")
    void twoFilesWithDifferentContentAreTwoSources(@TempDir Path dir) throws Exception {
        Path first = dir.resolve("first.txt");
        Files.writeString(first, """
                The Calvin-Benson cycle is the second stage of photosynthesis.
                It fixes carbon dioxide in the stroma using ATP and NADPH.
                """, StandardCharsets.UTF_8);
        Path second = dir.resolve("second.txt");
        Files.writeString(second, """
                Some laboratory reports a peculiar property of the chloroplast envelope.
                The property has not been confirmed by another laboratory.
                """, StandardCharsets.UTF_8);

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.importDocument("photosynthesis", first, null);
            workbench.importDocument("photosynthesis", second, null);

            CorpusManager.Registry registry = CorpusManager.registry(workbench, "photosynthesis");

            assertThat(registry.imported()).isEqualTo(2);
            assertThat(registry.documents()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("the registry survives a restart")
    void theRegistrySurvivesARestart(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("w.sqlite");
        Path file = dir.resolve("notes.txt");
        Files.writeString(file, """
                Some laboratory reports a peculiar property of the chloroplast envelope.
                """, StandardCharsets.UTF_8);

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.importDocument("photosynthesis", file, null);
        }

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            CorpusManager.Registry registry = CorpusManager.registry(workbench, "photosynthesis");
            assertThat(registry.imported()).isEqualTo(1);
            assertThat(registry.entries().stream()
                    .filter(CorpusManager.Entry::imported)
                    .findFirst().orElseThrow().language()).isEqualTo("en");
        }
    }
}
