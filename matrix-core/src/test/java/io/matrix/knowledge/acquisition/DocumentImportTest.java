package io.matrix.knowledge.acquisition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.matrix.knowledge.ClaimStatus;
import io.matrix.knowledge.KnowledgeStore;
import io.matrix.knowledge.Source;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.2 — a document becomes knowledge the store can trace.
 *
 * <p>The mission's §5: the acquisition pipeline, the source structure, the
 * promotion layers, and the rule that ten documents copying one source are
 * not ten confirmations.
 */
class DocumentImportTest {

    private static final String LESSON = """
            Photosynthesis is the process by which green plants use light to convert carbon dioxide
            and water into sugar and oxygen.

            The light-dependent reactions happen in the thylakoid membrane. They produce ATP and NADPH.

            The Calvin cycle happens in the stroma. It uses ATP and NADPH to build sugar from carbon dioxide.
            """;

    @Test
    @DisplayName("a plain text document becomes claims with evidence behind them")
    void aPlainTextDocumentBecomesClaimsWithEvidenceBehindThem(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("lesson.txt");
        Files.writeString(file, LESSON, StandardCharsets.UTF_8);

        try (KnowledgeStore store = new KnowledgeStore(dir.resolve("w.sqlite"))) {
            DocumentImporter importer = new DocumentImporter(store);
            DocumentImporter.ImportResult result = importer.importDocument("ws", file, null);

            assertThat(result.claimsProposed()).isGreaterThanOrEqualTo(3);
            assertThat(result.source().format()).isEqualTo(DocumentFormat.TXT);
            assertThat(result.source().contentHash()).hasSize(64);

            // The claims are CANDIDATES, not facts: nothing has validated
            // them yet.
            assertThat(store.claims("ws")).isNotEmpty();
            assertThat(store.claims("ws")).allSatisfy(claim ->
                    assertThat(claim.status()).isEqualTo(ClaimStatus.CANDIDATE));

            // Every claim has evidence pointing at the source, with the
            // excerpt and the location.
            assertThat(store.evidence("ws")).isNotEmpty();
            assertThat(store.evidence("ws")).allSatisfy(evidence -> {
                assertThat(evidence.sourceId()).isEqualTo(result.source().sourceId());
                assertThat(evidence.excerpt()).isNotBlank();
            });

            // The source carries the content hash, which is what makes the
            // independence rule computable.
            List<Source> sources = store.sources("ws");
            assertThat(sources).hasSize(1);
            assertThat(sources.get(0).contentHash()).isEqualTo(result.source().contentHash());
        }
    }

    @Test
    @DisplayName("the same document under another name is the same source")
    void theSameDocumentUnderAnotherNameIsTheSameSource(@TempDir Path dir) throws IOException {
        Path first = dir.resolve("lesson.txt");
        Path second = dir.resolve("a-copy-of-the-lesson.txt");
        Files.writeString(first, LESSON, StandardCharsets.UTF_8);
        Files.writeString(second, LESSON, StandardCharsets.UTF_8);

        try (KnowledgeStore store = new KnowledgeStore(dir.resolve("w.sqlite"))) {
            DocumentImporter importer = new DocumentImporter(store);
            DocumentImporter.ImportResult a = importer.importDocument("ws", first, null);
            DocumentImporter.ImportResult b = importer.importDocument("ws", second, null);

            assertThat(a.source().sourceId()).isEqualTo(b.source().sourceId());
            assertThat(a.source().contentHash()).isEqualTo(b.source().contentHash());
            // One source, not two.
            assertThat(store.sources("ws")).hasSize(1);
        }
    }

    @Test
    @DisplayName("ten copies of one source are one source, not ten confirmations")
    void tenCopiesOfOneSourceAreOneSourceNotTenConfirmations(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("lesson.txt");
        Files.writeString(file, LESSON, StandardCharsets.UTF_8);

        try (KnowledgeStore store = new KnowledgeStore(dir.resolve("w.sqlite"))) {
            new DocumentImporter(store).importDocument("ws", file, null);

            List<Source> sources = store.sources("ws");
            assertThat(DocumentImporter.independentSources(sources)).isEqualTo(1);

            // And the promotion policy refuses to verify a claim on that
            // basis, while refusing to support it with no evidence at all.
            assertThat(PromotionPolicy.mayPromote(PromotionStage.CANDIDATE,
                    PromotionStage.SUPPORTED, 1, true, false)).isTrue();
            assertThat(PromotionPolicy.mayPromote(PromotionStage.SUPPORTED,
                    PromotionStage.VERIFIED, 1, true, false)).isFalse();
            assertThat(PromotionPolicy.refusalReason(PromotionStage.SUPPORTED,
                    PromotionStage.VERIFIED, 1, true)).contains("copies of one source");
            assertThat(PromotionPolicy.mayPromote(PromotionStage.CANDIDATE,
                    PromotionStage.SUPPORTED, 0, false, false)).isFalse();
        }
    }

    @Test
    @DisplayName("a Markdown document keeps its headings as locations")
    void aMarkdownDocumentKeepsItsHeadingsAsLocations(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("book.md");
        Files.writeString(file, """
                # The light reactions

                Photosynthesis is the process by which green plants use light to make sugar.

                # The dark reactions

                The Calvin cycle happens in the stroma of the chloroplast.
                """, StandardCharsets.UTF_8);

        try (KnowledgeStore store = new KnowledgeStore(dir.resolve("w.sqlite"))) {
            DocumentImporter.ImportResult result =
                    new DocumentImporter(store).importDocument("ws", file, null);

            assertThat(result.source().format()).isEqualTo(DocumentFormat.MARKDOWN);
            assertThat(result.segments()).isGreaterThanOrEqualTo(2);
            assertThat(result.source().extractedStructure()).contains("headings");
        }
    }

    @Test
    @DisplayName("an HTML document yields its text and not its scripts")
    void anHtmlDocumentYieldsItsTextAndNotItsScripts(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("page.html");
        Files.writeString(file, """
                <html lang="en"><head><title>A page</title>
                <meta name="author" content="A. Reader">
                <script>alert('not content');</script></head>
                <body><h1>Photosynthesis</h1>
                <p>Photosynthesis is the process by which green plants make sugar from light.</p>
                <script>alert('also not content');</script>
                </body></html>
                """, StandardCharsets.UTF_8);

        try (KnowledgeStore store = new KnowledgeStore(dir.resolve("w.sqlite"))) {
            DocumentImporter.ImportResult result =
                    new DocumentImporter(store).importDocument("ws", file, null);

            assertThat(result.source().format()).isEqualTo(DocumentFormat.HTML);
            // The document states its author and its language, so they are
            // recorded - and not invented.
            assertThat(result.source().author()).isEqualTo("A. Reader");
            assertThat(result.source().language()).isEqualTo("en");
            assertThat(result.claimsProposed()).isGreaterThanOrEqualTo(1);
            // The scripts are gone.
            for (var claim : store.claims("ws")) {
                assertThat(claim.proposition()).doesNotContain("alert(");
            }
        }
    }

    @Test
    @DisplayName("a document that does not state its author has an unknown author")
    void aDocumentThatDoesNotStateItsAuthorHasAnUnknownAuthor(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("page.html");
        Files.writeString(file, "<html><body><p>Photosynthesis is the process by which green "
                + "plants make sugar from light and water.</p></body></html>", StandardCharsets.UTF_8);

        try (KnowledgeStore store = new KnowledgeStore(dir.resolve("w.sqlite"))) {
            DocumentImporter.ImportResult result =
                    new DocumentImporter(store).importDocument("ws", file, null);

            assertThat(result.source().author()).isEqualTo(SourceRecord.UNKNOWN);
            assertThat(result.source().publicationDate()).isEqualTo(SourceRecord.UNKNOWN);
        }
    }

    @Test
    @DisplayName("an EPUB's content documents are read and its assets are not")
    void anEpubsContentDocumentsAreReadAndItsAssetsAreNot(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("book.epub");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("OEBPS/chapter1.xhtml"));
            zip.write("""
                    <html lang="en"><body><h1>Photosynthesis</h1>
                    <p>Photosynthesis is the process by which green plants make sugar from light.</p>
                    </body></html>
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("OEBPS/style.css"));
            zip.write("body { color: red }".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        try (KnowledgeStore store = new KnowledgeStore(dir.resolve("w.sqlite"))) {
            DocumentImporter.ImportResult result =
                    new DocumentImporter(store).importDocument("ws", file, null);

            assertThat(result.source().format()).isEqualTo(DocumentFormat.EPUB);
            assertThat(result.claimsProposed()).isGreaterThanOrEqualTo(1);
            assertThat(result.source().extractionQuality()).isEqualTo(1.0);
        }
    }

    @Test
    @DisplayName("the document size limit is the one the mission names")
    void theDocumentSizeLimitIsTheOneTheMissionNames() {
        assertThat(DocumentImporter.MAXIMUM_DOCUMENT_BYTES).isEqualTo(32L * 1024 * 1024);
        // And the EPUB's expansion limits exist, so that a zip bomb is
        // refused rather than expanded.
        assertThat(EpubDocumentExtractor.MAXIMUM_ENTRY_BYTES).isGreaterThan(0);
        assertThat(EpubDocumentExtractor.MAXIMUM_TOTAL_BYTES)
                .isGreaterThan(EpubDocumentExtractor.MAXIMUM_ENTRY_BYTES);
        assertThat(EpubDocumentExtractor.MAXIMUM_ENTRIES).isGreaterThan(0);
    }

    @Test
    @DisplayName("a format the pipeline does not read is refused by name")
    void aFormatThePipelineDoesNotReadIsRefusedByName(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("data.bin");
        Files.writeString(file, "not a document", StandardCharsets.UTF_8);

        try (KnowledgeStore store = new KnowledgeStore(dir.resolve("w.sqlite"))) {
            assertThatThrownBy(() -> new DocumentImporter(store).importDocument("ws", file, null))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("not one this pipeline reads");
        }
    }
}
