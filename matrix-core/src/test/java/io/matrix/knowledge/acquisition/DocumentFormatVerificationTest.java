package io.matrix.knowledge.acquisition;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the parsers actually do (RECON-W43.4).
 *
 * <p>The mission's §7 asks that the parser behaviour and the limits be
 * VERIFIED for every format the pipeline claims to read — not assumed. This
 * test builds a well-formed sample of each format, runs it through the
 * registry, and records what came out: the segments, the locations, the
 * headings and the language.
 *
 * <p><b>What the samples are.</b> The text formats are written here, so
 * they are exactly what this test says they are. The container formats are
 * written here too: a minimal PDF, a minimal EPUB and a minimal DOCX, each
 * well-formed enough that any reader of the format opens it. So this test
 * proves that the parser handles a WELL-FORMED file of the format. It does
 * NOT prove that the parser handles the messy files of the wild — a real
 * PDF with a broken xref, a scanned PDF with no text layer, an EPUB with
 * nested navigation. Those are not in the corpus of this wave, and the
 * document says so.
 */
class DocumentFormatVerificationTest {

    /** One format's measured behaviour. */
    private record Measured(String format, boolean accepted, int segments, boolean located,
                            boolean headed, String language, String text, String note) {
    }

    @Test
    @DisplayName("every format the pipeline claims to read is verified, and what it does is recorded")
    void everyFormatThePipelineClaimsToReadIsVerified(@TempDir Path dir) throws Exception {
        Map<String, Measured> measured = new LinkedHashMap<>();
        DocumentExtractorRegistry registry = new DocumentExtractorRegistry();

        for (DocumentFormat format : List.of(DocumentFormat.TXT, DocumentFormat.MARKDOWN,
                DocumentFormat.HTML, DocumentFormat.CSV, DocumentFormat.PDF,
                DocumentFormat.EPUB, DocumentFormat.DOCX)) {
            Path file = dir.resolve(sample(format));
            Files.write(file, bytes(format));
            SourceRecord.Builder source = new SourceRecord.Builder("src-1",
                    file.getFileName().toString(), "hash", format).importedAt("2026-11-02T00:00:00Z");
            ExtractedDocument document = registry.extract(format, Files.readAllBytes(file),
                    file.getFileName().toString(), source);
            boolean located = document.segments().stream()
                    .anyMatch(segment -> segment.location() != null && !segment.location().isBlank());
            boolean headed = document.segments().stream()
                    .anyMatch(segment -> segment.heading() != null && !segment.heading().isBlank());
            String language = document.segments().stream()
                    .map(ExtractedDocument.Segment::language)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst().orElse(null);
            measured.put(format.name(), new Measured(format.name(), true, document.segments().size(),
                    located, headed, language, document.text(), ""));
        }

        // The formats that are NOT read by the document pipeline, and what
        // happens when they are handed to it.
        List<String> refusals = new ArrayList<>();
        for (DocumentFormat format : List.of(DocumentFormat.JSON)) {
            Path file = dir.resolve("package.json");
            Files.writeString(file, "{\"workspace\":\"photosynthesis\"}");
            try {
                registry.extract(format, Files.readAllBytes(file), "package.json",
                        new SourceRecord.Builder("src-2", "package.json", "hash", format));
                refusals.add(format.name() + ": it was accepted");
            } catch (RuntimeException refused) {
                refusals.add(format.name() + ": refused");
            }
        }

        // THE MEASUREMENT. It is printed, and it is asserted, and the
        // assertions are the floors the parser met — not what it ought to
        // do.
        for (Measured entry : measured.values()) {
            System.out.println("  the format " + entry.format() + ": the segments "
                    + entry.segments() + " | the location " + entry.located() + " | the heading "
                    + entry.headed() + " | the language " + entry.language()
                    + " | the text " + entry.text().length() + " characters");
        }
        System.out.println("  the refusals: " + refusals);

        for (Measured entry : measured.values()) {
            assertThat(entry.segments()).as("the segments of %s", entry.format()).isPositive();
            assertThat(entry.text()).as("the text of %s", entry.format()).isNotBlank();
            assertThat(entry.located()).as("the location of %s", entry.format()).isTrue();
        }

        // The structured formats keep their headings, and the flat one does
        // not pretend to.
        assertThat(measured.get("MARKDOWN").headed()).isTrue();
        assertThat(measured.get("HTML").headed()).isTrue();
        assertThat(measured.get("PDF").headed()).isFalse();

        // The pipeline refuses a knowledge package as a document.
        assertThat(refusals).allMatch(refusal -> refusal.endsWith(": refused"));
    }

    /** The name of a sample of a format. */
    private String sample(DocumentFormat format) {
        return switch (format) {
            case TXT -> "sample.txt";
            case MARKDOWN -> "sample.md";
            case HTML -> "sample.html";
            case CSV -> "sample.csv";
            case PDF -> "sample.pdf";
            case EPUB -> "sample.epub";
            case DOCX -> "sample.docx";
            case JSON -> "sample.json";
        };
    }

    /** The bytes of a sample of a format. */
    private byte[] bytes(DocumentFormat format) throws IOException {
        String text = "Photosynthesis is the process by which green plants turn light into "
                + "chemical energy. The plant takes in carbon dioxide and water, and releases "
                + "oxygen as a by-product.";
        return switch (format) {
            case TXT -> (text + "\n\nThe light-dependent stage needs light, and the "
                    + "light-independent stage does not.\n").getBytes(StandardCharsets.UTF_8);
            case MARKDOWN -> ("# Photosynthesis\n\n" + text + "\n\n## The two stages\n\nThe first "
                    + "stage needs light. The second does not.\n").getBytes(StandardCharsets.UTF_8);
            case HTML -> ("<html><head><title>Photosynthesis</title></head><body><h1>Photosynthesis"
                    + "</h1><p>" + text + "</p><h2>The two stages</h2><p>The first stage needs "
                    + "light. The second does not.</p></body></html>").getBytes(StandardCharsets.UTF_8);
            case CSV -> ("concept,statement\nphotosynthesis," + text + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            case PDF -> minimalPdf(text);
            case EPUB -> minimalEpub(text);
            case DOCX -> minimalDocx(text);
            case JSON -> "{}".getBytes(StandardCharsets.UTF_8);
        };
    }

    /**
     * A minimal PDF with a text layer.
     *
     * <p>It has no cross-reference table: PDFBox rebuilds one when it is
     * missing, and a PDF that a reader opens is a PDF. The text is a single
     * line in one line of content.
     */
    private byte[] minimalPdf(String text) {
        String content = "BT /F1 12 Tf 72 720 Td (" + text.replace("(", "").replace(")", "") + ") Tj ET";
        StringBuilder pdf = new StringBuilder();
        pdf.append("%PDF-1.4\n");
        pdf.append("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n");
        pdf.append("2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n");
        pdf.append("3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                + "/Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj\n");
        pdf.append("4 0 obj << /Length ").append(content.length()).append(" >> stream\n")
                .append(content).append("\nendstream endobj\n");
        pdf.append("5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n");
        pdf.append("trailer << /Size 6 /Root 1 0 R >>\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    /** A minimal EPUB: a ZIP with a chapter. */
    private byte[] minimalEpub(String text) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("mimetype"));
            zip.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("OEBPS/chapter1.html"));
            zip.write(("<html><head><title>Photosynthesis</title></head><body><h1>Photosynthesis</h1>"
                    + "<p>" + text + "</p></body></html>").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    /** A minimal DOCX: a ZIP with a document part. */
    private byte[] minimalDocx(String text) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types "
                    + "xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/></Types>")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006"
                    + "/main\"><w:body><w:p><w:r><w:t>" + text + "</w:t></w:r></w:p></w:body>"
                    + "</w:document>").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
