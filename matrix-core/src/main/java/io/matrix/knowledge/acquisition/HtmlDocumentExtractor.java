package io.matrix.knowledge.acquisition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Reads HTML with jsoup (RECON-W42.2).
 *
 * <p>jsoup is the project's established HTML library, already on the
 * classpath. The mission's §13.2 lists malicious HTML among the attacks an
 * importer must survive; jsoup parses a document as a tree and never
 * executes it, and this extractor never follows a link — the visible text
 * is taken, and nothing is fetched.
 *
 * <p><b>What is dropped, and why.</b> The script and style elements, the
 * navigation and the advertising are dropped: they are not the document's
 * claims about its subject. The headings are kept, because they are the
 * document's own structure and a claim must be traceable to its section.
 */
public final class HtmlDocumentExtractor implements DocumentExtractor {

    @Override
    public DocumentFormat format() {
        return DocumentFormat.HTML;
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
        String html = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        Document document = Jsoup.parse(html, filename);

        // The document's own metadata, when it states it. A document that
        // does not state its author has an unknown author, not a blank one
        // that a reader may fill in.
        String author = document.selectFirst("meta[name=author]") == null
                ? SourceRecord.UNKNOWN
                : document.selectFirst("meta[name=author]").attr("content");
        String language = document.selectFirst("html[lang]") == null
                ? SourceRecord.UNKNOWN
                : document.selectFirst("html[lang]").attr("lang");
        source.author(author).language(language);
        source.processed("parsed the HTML with jsoup");

        document.select("script, style, nav, noscript, iframe").remove();

        List<ExtractedDocument.Segment> segments = new ArrayList<>();
        String heading = null;
        int order = 0;
        for (Element element : document.body().children()) {
            if (element.tagName().equals("h1") || element.tagName().equals("h2")
                    || element.tagName().equals("h3")) {
                heading = element.text().strip();
                continue;
            }
            String text = element.text().strip();
            if (text.isBlank()) {
                continue;
            }
            segments.add(new ExtractedDocument.Segment(
                    "seg-" + segments.size() + "-" + order,
                    text,
                    heading == null ? filename : filename + ": \"" + heading + "\"",
                    order++,
                    heading,
                    TextDocumentExtractor.guessLanguage(text)));
        }
        // A document whose body has no element children still has text.
        if (segments.isEmpty()) {
            String text = document.text().strip();
            if (!text.isBlank()) {
                segments.add(new ExtractedDocument.Segment("seg-0-0", text, filename, 0, null,
                        TextDocumentExtractor.guessLanguage(text)));
            }
        }

        String whole = String.join("\n\n", segments.stream()
                .map(ExtractedDocument.Segment::text).toList());
        source.extractedStructure("HTML with headings");
        source.extractionQuality(segments.isEmpty() ? 0.0 : 1.0);
        source.processed("kept " + segments.size() + " segment(s)");
        return new ExtractedDocument(source.build(), whole, segments);
    }

    /** The elements that carry the document's headings, for a caller that wants them. */
    public static Elements headings(Document document) {
        return document.select("h1, h2, h3");
    }
}
