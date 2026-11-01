package io.matrix.knowledge.acquisition;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Document;

/**
 * Reads an EPUB with the JDK's ZIP reader and jsoup (RECON-W42.2).
 *
 * <p>The mission's §5.1 says not to develop EPUB decoding from scratch.
 * An EPUB is a ZIP container of XHTML documents: the container is read with
 * `java.util.zip`, which is the JDK's own implementation, and the XHTML
 * inside it with jsoup, the project's established HTML library. Nothing
 * here implements the ZIP or the HTML format.
 *
 * <p><b>The bomb.</b> The mission's §13.2 names zip bombs among the attacks
 * an importer must survive. A ZIP entry declares its uncompressed size, and
 * a bomb declares a small compressed entry that expands to gigabytes. The
 * declared size is therefore checked before the entry is read, and the
 * total read is bounded as well — so a bomb is refused rather than
 * exhausting the heap.
 */
public final class EpubDocumentExtractor implements DocumentExtractor {

    /** The bytes one document may expand to. */
    public static final long MAXIMUM_ENTRY_BYTES = 16L * 1024 * 1024;

    /** The bytes a whole EPUB may expand to. */
    public static final long MAXIMUM_TOTAL_BYTES = 64L * 1024 * 1024;

    /** The entries one EPUB may contain. */
    public static final int MAXIMUM_ENTRIES = 2_000;

    @Override
    public DocumentFormat format() {
        return DocumentFormat.EPUB;
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
        Map<String, byte[]> documents = new LinkedHashMap<>();
        long total = 0L;
        int entries = 0;

        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAXIMUM_ENTRIES) {
                    throw new IOException("the EPUB has more than " + MAXIMUM_ENTRIES
                            + " entries, which is over the limit");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (!isContentDocument(name)) {
                    continue;
                }
                // The bomb check: the declared size is checked BEFORE the
                // entry is read, so a bomb is refused without being
                // expanded.
                long declared = entry.getSize();
                if (declared > MAXIMUM_ENTRY_BYTES) {
                    throw new IOException("the EPUB entry \"" + name + "\" declares "
                            + declared + " bytes, which is over the limit of "
                            + MAXIMUM_ENTRY_BYTES);
                }
                byte[] content = zip.readNBytes((int) MAXIMUM_ENTRY_BYTES + 1);
                if (content.length > MAXIMUM_ENTRY_BYTES) {
                    throw new IOException("the EPUB entry \"" + name + "\" expands past the limit of "
                            + MAXIMUM_ENTRY_BYTES + " bytes");
                }
                total += content.length;
                if (total > MAXIMUM_TOTAL_BYTES) {
                    throw new IOException("the EPUB expands past the limit of "
                            + MAXIMUM_TOTAL_BYTES + " bytes");
                }
                documents.put(name, content);
            }
        }
        source.processed("read " + documents.size() + " content document(s) from the EPUB");

        String title = null;
        String author = null;
        String language = SourceRecord.UNKNOWN;
        List<ExtractedDocument.Segment> segments = new ArrayList<>();
        int order = 0;

        for (Map.Entry<String, byte[]> document : documents.entrySet()) {
            String name = document.getKey();
            Document parsed = Jsoup.parse(new String(document.getValue(), StandardCharsets.UTF_8),
                    name);
            parsed.select("script, style, nav, noscript").remove();

            if (title == null) {
                Element titleElement = parsed.selectFirst("title");
                if (titleElement != null && !titleElement.text().isBlank()) {
                    title = titleElement.text().strip();
                }
            }
            if (author == null) {
                Element authorElement = parsed.selectFirst("[epub|type~=author], [role=author], "
                        + "meta[name=author]");
                if (authorElement != null && !authorElement.text().isBlank()) {
                    author = authorElement.text().strip();
                }
            }
            if (SourceRecord.UNKNOWN.equals(language)) {
                Element langElement = parsed.selectFirst("html[lang]");
                if (langElement != null && !langElement.attr("lang").isBlank()) {
                    language = langElement.attr("lang");
                }
            }

            String heading = null;
            for (org.jsoup.nodes.Element element : parsed.body().children()) {
                if (element.tagName().matches("h[1-3]")) {
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
                        name + (heading == null ? "" : ": \"" + heading + "\""),
                        order++,
                        heading,
                        TextDocumentExtractor.guessLanguage(text)));
            }
        }
        if (title != null) {
            source.processed("the book's title is \"" + title + "\"");
        }
        source.author(author).language(language);

        String whole = String.join("\n\n", segments.stream()
                .map(ExtractedDocument.Segment::text).toList());
        source.extractedStructure("EPUB content documents, in reading order");
        source.extractionQuality(documents.isEmpty() ? 0.0 : 1.0);
        source.processed("kept " + segments.size() + " segment(s)");
        return new ExtractedDocument(source.build(), whole, segments);
    }

    /** Whether the entry is a content document rather than an asset. */
    private boolean isContentDocument(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm");
    }

    /** Reads an EPUB's bytes into a stream, for a caller that has them already. */
    public static InputStream stream(byte[] content) {
        return new ByteArrayInputStream(content);
    }
}
