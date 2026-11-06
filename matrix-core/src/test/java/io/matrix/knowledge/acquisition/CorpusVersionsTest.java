package io.matrix.knowledge.acquisition;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.knowledge.KnowledgeStore;
import io.matrix.knowledge.KnowledgeWorkbench;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.7 — the corpus registry groups the versions of a title.
 *
 * <p>The registry could report that two sources share a title, and could
 * not say how many versions of that title the workspace holds — so a
 * workspace could not answer "is this the second edition?".
 */
class CorpusVersionsTest {

    @Test
    @DisplayName("two documents with the same title are two versions")
    void twoDocumentsWithTheSameTitleAreTwoVersions(@TempDir Path dir) throws Exception {
        Path first = dir.resolve("first.txt");
        Files.writeString(first, """
                The Calvin cycle fixes carbon dioxide into sugar.
                """, StandardCharsets.UTF_8);
        Path second = dir.resolve("second.txt");
        Files.writeString(second, """
                The Calvin cycle fixes carbon dioxide into sugar, and it runs in the stroma.
                """, StandardCharsets.UTF_8);

        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            // The title is the FILE's name, so both documents are filed
            // under one title when they are given the same name.
            workbench.importDocument("photosynthesis", first, "calvin.txt", null);
            workbench.importDocument("photosynthesis", second, "calvin.txt", null);

            CorpusManager.Registry registry = CorpusManager.registry(workbench, "photosynthesis");
            CorpusManager.TitleGroup group = groupOf(registry, "calvin");

            assertThat(group).isNotNull();
            assertThat(group.versionCount()).isEqualTo(2);
            assertThat(group.versions()).extracting(CorpusManager.Entry::contentHash)
                    .doesNotHaveDuplicates();
            assertThat(group.note()).contains("2 versions");
        }
    }

    @Test
    @DisplayName("the same document imported twice is one version")
    void theSameDocumentImportedTwiceIsOneVersion(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("calvin.txt");
        Files.writeString(file, """
                The Calvin cycle fixes carbon dioxide into sugar.
                """, StandardCharsets.UTF_8);

        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.importDocument("photosynthesis", file, "calvin.txt", null);
            workbench.importDocument("photosynthesis", file, "calvin.txt", null);

            CorpusManager.Registry registry = CorpusManager.registry(workbench, "photosynthesis");
            CorpusManager.TitleGroup group = groupOf(registry, "calvin");

            assertThat(group).isNotNull();
            assertThat(group.versionCount()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("a version without an authored publication date is not given one")
    void aVersionWithoutAnAuthoredPublicationDateIsNotGivenOne(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("calvin.txt");
        Files.writeString(file, """
                The Calvin cycle fixes carbon dioxide into sugar.
                """, StandardCharsets.UTF_8);

        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.importDocument("photosynthesis", file, "calvin.txt", null);

            CorpusManager.Registry registry = CorpusManager.registry(workbench, "photosynthesis");
            CorpusManager.TitleGroup group = groupOf(registry, "calvin");

            // The registry does not guess a date, and the moment the
            // document was imported is not a publication date.
            assertThat(group.latestPublishedAt()).isEqualTo("unknown");
            assertThat(group.latestSourceId()).isNull();
        }
    }

    @Test
    @DisplayName("the registry says that it does not guess the dates")
    void theRegistrySaysThatItDoesNotGuessTheDates(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            CorpusManager.Registry registry = CorpusManager.registry(workbench, "photosynthesis");

            assertThat(registry.notes()).anySatisfy(note -> {
                assertThat(note).contains("versions of a title");
                assertThat(note).contains("does not guess a date");
            });
            assertThat(registry.titles()).isNotEmpty();
        }
    }

    /** The group of the title that starts with the given text. */
    private CorpusManager.TitleGroup groupOf(CorpusManager.Registry registry, String prefix) {
        for (CorpusManager.TitleGroup group : registry.titles()) {
            if (group.title() != null && group.title().toLowerCase(java.util.Locale.ROOT)
                    .startsWith(prefix)) {
                return group;
            }
        }
        return null;
    }
}
