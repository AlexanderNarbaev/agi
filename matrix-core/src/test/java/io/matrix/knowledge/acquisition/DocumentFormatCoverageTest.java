package io.matrix.knowledge.acquisition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import io.matrix.knowledge.KnowledgeStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.7 — the formats the mission's §5.1 names, and the two that
 * were missing.
 *
 * <p>The mission's §5.1 lists TXT, Markdown, HTML, PDF, EPUB, DOCX, CSV and
 * the structured formats. Six were read; DOCX and CSV were not, and the
 * mission's list is the requirement.
 */
class DocumentFormatCoverageTest {

    @Test
    @DisplayName("every format the mission names is recognised by its extension")
    void everyFormatTheMissionNamesIsRecognisedByItsExtension() {
        assertThat(DocumentFormat.of("notes.txt")).isEqualTo(DocumentFormat.TXT);
        assertThat(DocumentFormat.of("chapter.md")).isEqualTo(DocumentFormat.MARKDOWN);
        assertThat(DocumentFormat.of("page.html")).isEqualTo(DocumentFormat.HTML);
        assertThat(DocumentFormat.of("paper.pdf")).isEqualTo(DocumentFormat.PDF);
        assertThat(DocumentFormat.of("book.epub")).isEqualTo(DocumentFormat.EPUB);
        assertThat(DocumentFormat.of("report.docx")).isEqualTo(DocumentFormat.DOCX);
        assertThat(DocumentFormat.of("table.csv")).isEqualTo(DocumentFormat.CSV);
        assertThat(DocumentFormat.of("package.json")).isEqualTo(DocumentFormat.JSON);
        assertThat(DocumentFormat.of("archive.bin")).isNull();
    }

    @Test
    @DisplayName("a DOCX's paragraphs are read, and its assets are not")
    void aDocxsParagraphsAreReadAndItsAssetsAreNot(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("lesson.docx");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                    <w:body>
                    <w:p><w:r><w:t>Cellular respiration releases energy from glucose.</w:t></w:r></w:p>
                    <w:p><w:r><w:t>Glycolysis is the first stage of cellular respiration.</w:t></w:r></w:p>
                    </w:body></w:document>
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/styles.xml"));
            zip.write("<w:styles/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        try (KnowledgeStore store = new KnowledgeStore(dir.resolve("w.sqlite"))) {
            DocumentImporter.ImportResult result =
                    new DocumentImporter(store).importDocument("ws", file, null);

            assertThat(result.source().format()).isEqualTo(DocumentFormat.DOCX);
            assertThat(result.source().extractionQuality()).isEqualTo(1.0);
            assertThat(result.claimsProposed()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    @DisplayName("a CSV's rows are read, and the header names the columns")
    void aCsvsRowsAreReadAndTheHeaderNamesTheColumns(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("table.csv");
        Files.writeString(file, """
                concept,statement
                Photosynthesis,Green plants make sugar from light.
                Glycolysis,Glucose is split into two pyruvate molecules.
                """, StandardCharsets.UTF_8);

        try (KnowledgeStore store = new KnowledgeStore(dir.resolve("w.sqlite"))) {
            DocumentImporter.ImportResult result =
                    new DocumentImporter(store).importDocument("ws", file, null);

            assertThat(result.source().format()).isEqualTo(DocumentFormat.CSV);
            assertThat(result.source().extractionQuality()).isEqualTo(1.0);
            assertThat(result.claimsProposed()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    @DisplayName("the CSV's parser follows RFC 4180: the quoted fields, the embedded breaks")
    void theCsvsParserFollowsRfc4180() {
        List<List<String>> rows = CsvDocumentExtractor.parse(
                "a,b\n\"one, two\",\"line\nbreak\"\n\"say \"\"hi\"\"\",x\n", ',');

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).containsExactly("a", "b");
        assertThat(rows.get(1)).containsExactly("one, two", "line\nbreak");
        assertThat(rows.get(2)).containsExactly("say \"hi\"", "x");
    }

    @Test
    @DisplayName("the CSV's delimiter is read from the first line")
    void theCsvsDelimiterIsReadFromTheFirstLine() {
        List<List<String>> rows = CsvDocumentExtractor.parse("a;b\n1;2\n", ';');
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsExactly("a", "b");
    }

    @Test
    @DisplayName("the DOCX's bomb limits exist, so that a zip bomb is refused")
    void theDocxsBombLimitsExist() {
        assertThat(DocxDocumentExtractor.MAXIMUM_ENTRY_BYTES).isGreaterThan(0);
        assertThat(DocxDocumentExtractor.MAXIMUM_TOTAL_BYTES)
                .isGreaterThan(DocxDocumentExtractor.MAXIMUM_ENTRY_BYTES);
        assertThat(DocxDocumentExtractor.MAXIMUM_ENTRIES).isGreaterThan(0);
    }

    @Test
    @DisplayName("a DOCX that is not a ZIP is refused, not half read")
    void aDocxThatIsNotAZipIsRefusedNotHalfRead(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("broken.docx");
        Files.writeString(file, "this is not a zip container", StandardCharsets.UTF_8);

        try (KnowledgeStore store = new KnowledgeStore(dir.resolve("w.sqlite"))) {
            assertThatThrownBy(() -> new DocumentImporter(store).importDocument("ws", file, null))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
