package io.matrix.knowledge.acquisition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Reads the text layer of a PDF with PDFBox (RECON-W42.2).
 *
 * <p>The mission's §5.1 requires an established library rather than a
 * hand-written parser, and §5.2 asks for the page references to be kept.
 * PDFBox 3.0.3 is the PDF library this repository already pins (see
 * `matrix-audit/build.gradle`), so this is the project's own choice.
 *
 * <p><b>What a PDF can and cannot give.</b> A PDF's text layer is what the
 * author typed; a scanned page has none, and the extraction quality says
 * so. The extraction quality is the share of pages that yielded text — a
 * scan yields zero, and a document that yields nothing must not be
 * promoted into the knowledge store as if it had said something.
 */
public final class PdfDocumentExtractor implements DocumentExtractor {

    /** The pages a document may have before the extraction is refused. */
    public static final int MAXIMUM_PAGES = 2_000;

    @Override
    public DocumentFormat format() {
        return DocumentFormat.PDF;
    }

    @Override
    public ExtractedDocument extract(Path path, SourceRecord.Builder source) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return extract(input, path.getFileName().toString(), source);
        }
    }

    @Override
    public ExtractedDocument extract(InputStream input, String filename,
                                     SourceRecord.Builder source) throws IOException {
        try (PDDocument document = Loader.loadPDF(input.readAllBytes())) {
            int pages = document.getNumberOfPages();
            if (pages > MAXIMUM_PAGES) {
                throw new IOException("the PDF has " + pages + " pages, which is over the limit of "
                        + MAXIMUM_PAGES);
            }
            source.processed("opened a PDF of " + pages + " page(s) with PDFBox");

            // The document's own metadata, when it states it.
            String title = document.getDocumentInformation() == null ? null
                    : document.getDocumentInformation().getTitle();
            String author = document.getDocumentInformation() == null ? null
                    : document.getDocumentInformation().getAuthor();
            source.author(author);
            if (title != null && !title.isBlank()) {
                source.processed("the document's title is \"" + title.strip() + "\"");
            }

            List<ExtractedDocument.Segment> segments = new ArrayList<>();
            int pagesWithText = 0;
            for (int page = 1; page <= pages; page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document).strip();
                if (text.isBlank()) {
                    continue;
                }
                pagesWithText++;
                segments.add(new ExtractedDocument.Segment(
                        "seg-" + segments.size() + "-" + page,
                        text,
                        filename + ", page " + page,
                        segments.size(),
                        null,
                        TextDocumentExtractor.guessLanguage(text)));
                source.pageReference("page " + page);
            }

            String whole = String.join("\n\n", segments.stream()
                    .map(ExtractedDocument.Segment::text).toList());
            source.extractedStructure("PDF text layer, page by page");
            source.extractionQuality(pages == 0 ? 0.0 : (double) pagesWithText / pages);
            source.processed(pagesWithText + " of " + pages + " page(s) carried text");
            return new ExtractedDocument(source.build(), whole, segments);
        }
    }
}
