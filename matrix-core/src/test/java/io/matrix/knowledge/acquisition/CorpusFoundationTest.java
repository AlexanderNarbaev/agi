package io.matrix.knowledge.acquisition;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.knowledge.Claim;
import io.matrix.knowledge.ClaimStatus;
import io.matrix.knowledge.KnowledgeWorkbench;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The corpus foundation: the manifest, the import, the report and the
 * idempotence (RECON-W43.4).
 *
 * <p>The mission's §7 asks the import to preserve the structure, the
 * locations, the warnings, the hashes, the deduplication, the licensing and
 * the traceability from a claim back to a span of a source. These tests
 * check each of those against the corpus of this wave — a corpus that is
 * small, controlled, and every file of which carries its licence.
 */
class CorpusFoundationTest {

    private static final String WORKSPACE = "corpus";

    /** The corpus of the classpath, written to a directory the test can read. */
    private static Path corpus(Path directory) throws IOException {
        Path target = directory.resolve("corpus");
        Files.createDirectories(target);
        for (String name : List.of("corpus-manifest.json", "photosynthesis-notes.md",
                "water-boiling.txt", "photosynthesis-page.html")) {
            try (InputStream in = CorpusFoundationTest.class.getClassLoader()
                    .getResourceAsStream("corpus/" + name)) {
                if (in == null) {
                    throw new IOException("the corpus file is missing: " + name);
                }
                Files.copy(in, target.resolve(name));
            }
        }
        return target;
    }

    @Test
    @DisplayName("the manifest describes the corpus, and every file matches it")
    void theManifestDescribesTheCorpusAndEveryFileMatchesIt(@TempDir Path dir) throws Exception {
        Path corpus = corpus(dir);

        CorpusManifest.Report report = CorpusManifest.verify(corpus);

        assertThat(report.listed()).isEqualTo(3);
        assertThat(report.hashMatches()).isEqualTo(3);
        assertThat(report.unlisted()).isZero();
        assertThat(report.unlicensed()).isZero();
        assertThat(report.verified()).isTrue();
        assertThat(report.warnings()).isEmpty();
    }

    @Test
    @DisplayName("a file whose bytes differ from the manifest is reported, not imported")
    void aFileWhoseBytesDifferFromTheManifestIsReportedNotImported(@TempDir Path dir)
            throws Exception {
        Path corpus = corpus(dir);
        Files.writeString(corpus.resolve("water-boiling.txt"),
                "Water boils at 100 degrees Celsius. Edited without the manifest.\n");

        CorpusManifest.Report report = CorpusManifest.verify(corpus);

        assertThat(report.hashMatches()).isEqualTo(2);
        assertThat(report.verified()).isFalse();
        assertThat(report.warnings()).anyMatch(w -> w.contains("differ from the manifest"));
    }

    @Test
    @DisplayName("a file the manifest does not list has no licence, and that is reported")
    void aFileTheManifestDoesNotListHasNoLicenceAndThatIsReported(@TempDir Path dir)
            throws Exception {
        Path corpus = corpus(dir);
        Files.writeString(corpus.resolve("smuggled.txt"), "text nobody licensed\n");

        CorpusManifest.Report report = CorpusManifest.verify(corpus);

        assertThat(report.unlisted()).isEqualTo(1);
        assertThat(report.unlicensed()).isEqualTo(1);
        assertThat(report.verified()).isFalse();
        assertThat(report.warnings()).anyMatch(w -> w.contains("smuggled.txt"));
    }

    @Test
    @DisplayName("the import produces a report with the licence, the segments and the trace")
    void theImportProducesAReportWithTheLicenceTheSegmentsAndTheTrace(@TempDir Path dir)
            throws Exception {
        Path corpus = corpus(dir);

        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            CorpusImport.Report report = CorpusImport.run(workbench, WORKSPACE, corpus);

            assertThat(report.filesImported()).isEqualTo(3);
            assertThat(report.licensed()).isEqualTo(3);
            assertThat(report.segments()).isPositive();
            assertThat(report.claimsAdded()).isPositive();
            assertThat(report.claimsTraced()).isEqualTo(report.claimsAdded());

            for (CorpusImport.FileReport file : report.files()) {
                assertThat(file.licence()).as("the licence of %s", file.file()).isNotBlank();
                assertThat(file.hashMatches()).as("the hash of %s", file.file()).isTrue();
                assertThat(file.sourceId()).as("the source of %s", file.file()).isNotBlank();
                assertThat(file.segments()).as("the segments of %s", file.file()).isPositive();
                for (CorpusImport.ClaimTrace trace : file.claims()) {
                    assertThat(trace.span()).as("the span of %s", trace.claimId()).isNotBlank();
                    assertThat(trace.sourceId()).isEqualTo(file.sourceId());
                }
            }

            // The imported claims enter as CANDIDATE. The import does not
            // promote anything, and nothing it read is a VERIFIED claim.
            List<Claim> imported = workbench.storeForValidation().claims(WORKSPACE).stream()
                    .filter(claim -> claim.introducedBySourceId() != null)
                    .toList();
            assertThat(imported).isNotEmpty();
            assertThat(imported).allMatch(claim -> claim.status() == ClaimStatus.CANDIDATE);
        }
    }

    @Test
    @DisplayName("the same corpus imported twice changes nothing the second time")
    void theSameCorpusImportedTwiceChangesNothingTheSecondTime(@TempDir Path dir)
            throws Exception {
        Path corpus = corpus(dir);

        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            CorpusImport.Report first = CorpusImport.run(workbench, WORKSPACE, corpus);
            int claimsAfterFirst = workbench.storeForValidation().claims(WORKSPACE).size();
            int sourcesAfterFirst = workbench.storeForValidation().sources(WORKSPACE).size();

            CorpusImport.Report second = CorpusImport.run(workbench, WORKSPACE, corpus);
            int claimsAfterSecond = workbench.storeForValidation().claims(WORKSPACE).size();
            int sourcesAfterSecond = workbench.storeForValidation().sources(WORKSPACE).size();

            // The source id is derived from the workspace and the content
            // hash, so the second import writes the SAME source and the
            // SAME claims. The counts do not move.
            assertThat(second.filesImported()).isEqualTo(first.filesImported());
            assertThat(claimsAfterSecond).isEqualTo(claimsAfterFirst);
            assertThat(sourcesAfterSecond).isEqualTo(sourcesAfterFirst);

            // And the report says so, rather than pretending it imported
            // something new.
            assertThat(second.files().stream().map(CorpusImport.FileReport::sourceId).toList())
                    .isEqualTo(first.files().stream().map(CorpusImport.FileReport::sourceId).toList());
        }
    }

    @Test
    @DisplayName("the corpus holds no book and nothing is fitted to it")
    void theCorpusHoldsNoBookAndNothingIsFittedToIt(@TempDir Path dir) throws Exception {
        Path corpus = corpus(dir);

        // The corpus is small and every file says what it is.
        List<Path> files = CorpusManifest.files(corpus);
        assertThat(files).hasSize(3);
        long bytes = 0;
        for (Path file : files) {
            bytes += Files.size(file);
        }
        assertThat(bytes).isLessThan(64 * 1024L);

        // Nothing in the corpus is trained on: the pipeline reads, extracts
        // and validates, and the only thing it writes is the knowledge
        // change. There is no weight, no model and no checkpoint anywhere
        // in it.
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            CorpusImport.run(workbench, WORKSPACE, corpus);
            assertThat(workbench.storeForValidation().claims(WORKSPACE)).isNotEmpty();
        }
        assertThat(files).allMatch(file -> file.getFileName().toString().endsWith(".md")
                || file.getFileName().toString().endsWith(".txt")
                || file.getFileName().toString().endsWith(".html"));
        assertThat(files.stream().map(path -> path.getFileName().toString()).sorted()
                .toList()).isEqualTo(List.of("photosynthesis-notes.md", "photosynthesis-page.html",
                        "water-boiling.txt").stream().sorted(Comparator.naturalOrder()).toList());
    }
}
