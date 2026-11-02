package io.matrix.knowledge.acquisition;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Reads a DOCX with the JDK's ZIP reader and jsoup (RECON-W42.7).
 *
 * <p>The mission's §5.1 names DOCX among the formats the acquisition
 * pipeline must read, and says: "Use appropriate established libraries
 * instead of developing PDF parsing, EPUB decoding, or document extraction
 * from scratch." A DOCX is a ZIP container of XML documents: the container
 * is read with `java.util.zip`, the JDK's own implementation, and the XML
 * with jsoup's XML parser — the project's established library, already
 * used for the HTML and for the XHTML inside an EPUB. Nothing here
 * implements the ZIP or the XML format, and no new dependency was added:
 * the CONSTITUTION's seventh article pins the stack.
 *
 * <p><b>What is read.</b> The body of the document — the paragraphs of
 * `word/document.xml` — and the headers, when they carry text. A
 * paragraph is a segment, so that a claim extracted from the document can
 * be traced to the paragraph it came from.
 *
 * <p><b>The bomb.</b> The mission's §13.2 names zip bombs among the
 * attacks an importer must survive. The declared size of every entry is
 * checked before it is read, and the total read is bounded — so a bomb is
 * refused rather than exhausting the heap.
 */
public final class DocxDocumentExtractor implements DocumentExtractor {

    /** The bytes one entry may expand to. */
    public static final long MAXIMUM_ENTRY_BYTES = 16L * 1024 * 1024;

    /** The bytes a whole document may expand to. */
    public static final long MAXIMUM_TOTAL_BYTES = 64L * 1024 * 1024;

    /** The entries one document may contain. */
    public static final int MAXIMUM_ENTRIES = 2_000;

    /** The XML element that holds a run of text. */
    private static final String TEXT_ELEMENT = "w:t";

    @Override
    public DocumentFormat format() {
        return DocumentFormat.DOCX;
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
        List<String> paragraphs = new ArrayList<>();
        long total = 0;
        int entries = 0;
        boolean sawBody = false;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAXIMUM_ENTRIES) {
                    throw new IOException("the document holds more than " + MAXIMUM_ENTRIES
                            + " entries, and it is refused");
                }
                if (entry.isDirectory() || !isText(entry.getName())) {
                    continue;
                }
                long declared = entry.getSize();
                if (declared > MAXIMUM_ENTRY_BYTES) {
                    throw new IOException("the entry " + entry.getName() + " declares "
                            + declared + " bytes, and it is refused");
                }
                if (entry.getName().equals("word/document.xml")) {
                    sawBody = true;
                }
                byte[] content = readBounded(zip, entry.getName());
                total += content.length;
                if (total > MAXIMUM_TOTAL_BYTES) {
                    throw new IOException("the document expands past "
                            + MAXIMUM_TOTAL_BYTES + " bytes, and it is refused");
                }
                paragraphs.addAll(paragraphsOf(new String(content, StandardCharsets.UTF_8)));
            }
        }

        // THE CORRUPTED CONTAINER.
        //
        // A ZIP reader hands back nothing for a file that is not a ZIP, so
        // a broken document was imported as an EMPTY one - silently, with
        // a clean result. The mission's §13.2 names the corrupted packages
        // among the attacks an importer must survive, and a document that
        // cannot be read is refused, not imported as empty.
        if (!sawBody) {
            throw new IOException("the document holds no word/document.xml, so it is not a "
                    + "document this pipeline can read, and it is refused");
        }

        String text = String.join("\n\n", paragraphs);
        source.processed("read " + text.length() + " characters");
        source.extractedStructure("docx paragraphs");
        source.extractionQuality(paragraphs.isEmpty() ? 0.0 : 1.0);

        List<ExtractedDocument.Segment> segments = new ArrayList<>();
        int order = 0;
        for (String paragraph : paragraphs) {
            if (paragraph.isBlank()) {
                continue;
            }
            segments.add(new ExtractedDocument.Segment(
                    "seg-" + order,
                    paragraph,
                    "word/document.xml paragraph " + (order + 1),
                    order,
                    null,
                    null));
            order++;
        }
        return new ExtractedDocument(source.build(), text, segments);
    }

    /** Whether the entry holds the document's text. */
    private static boolean isText(String name) {
        if (name == null) {
            return false;
        }
        return name.equals("word/document.xml")
                || (name.startsWith("word/header") && name.endsWith(".xml"));
    }

    /** The paragraphs of one XML part. */
    private static List<String> paragraphsOf(String xml) {
        List<String> paragraphs = new ArrayList<>();
        if (xml == null || xml.isBlank()) {
            return paragraphs;
        }
        Document document = Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser());
        for (Element paragraph : document.getElementsByTag("w:p")) {
            StringBuilder text = new StringBuilder();
            for (Element run : paragraph.getElementsByTag(TEXT_ELEMENT)) {
                text.append(run.text());
            }
            String value = text.toString().strip();
            if (!value.isEmpty()) {
                paragraphs.add(value);
            }
        }
        if (paragraphs.isEmpty()) {
            // A document without paragraphs — a table, say — still has text.
            Elements texts = document.getElementsByTag(TEXT_ELEMENT);
            for (Element run : texts) {
                String value = run.text().strip();
                if (!value.isEmpty()) {
                    paragraphs.add(value);
                }
            }
        }
        return paragraphs;
    }

    /** Reads an entry, refusing to expand past the limit. */
    private static byte[] readBounded(InputStream input, String name) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long read = 0;
        int count;
        while ((count = input.read(buffer)) > 0) {
            read += count;
            if (read > MAXIMUM_ENTRY_BYTES) {
                throw new IOException("the entry " + name + " expands past "
                        + MAXIMUM_ENTRY_BYTES + " bytes, and it is refused");
            }
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }
}
