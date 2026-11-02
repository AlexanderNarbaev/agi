package io.matrix.knowledge.acquisition;

import java.util.Locale;

/**
 * The formats the acquisition pipeline can read (RECON-W42.2).
 *
 * <p>The mission's §5.1 names TXT, Markdown, HTML, PDF, EPUB, DOCX, CSV and
 * the structured formats, and says: "Use appropriate established libraries
 * instead of developing PDF parsing, EPUB decoding, or document extraction
 * from scratch."
 *
 * <p>Each format is read by a library that already exists — the JDK for
 * plain text and for the ZIP containers an EPUB and a DOCX are, jsoup for
 * HTML, for the XHTML inside an EPUB and for the XML inside a DOCX, and
 * PDFBox for PDF. Nothing here parses a binary format by hand, and no new
 * dependency was added: the CONSTITUTION's seventh article pins the
 * stack.
 */
public enum DocumentFormat {

    /** Plain text. */
    TXT(true),
    /** Markdown: headings and structure are kept. */
    MARKDOWN(true),
    /** HTML: the visible text is kept, the markup is not. */
    HTML(true),
    /** PDF: the text layer is extracted with PDFBox. */
    PDF(false),
    /** EPUB: a ZIP container of XHTML documents. */
    EPUB(false),
    /** DOCX: a ZIP container of XML documents. */
    DOCX(false),
    /** CSV: a table of rows, with a header. */
    CSV(true),
    /** A knowledge package: the store's own JSON format. */
    JSON(false);

    private final boolean plainText;

    DocumentFormat(boolean plainText) {
        this.plainText = plainText;
    }

    /** Whether the format is plain text and can be read directly. */
    public boolean plainText() {
        return plainText;
    }

    /**
     * The format of a file, from its name.
     *
     * @param filename the file name
     * @return the format, or null when the extension is not one this
     *     pipeline reads
     */
    public static DocumentFormat of(String filename) {
        if (filename == null) {
            return null;
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return MARKDOWN;
        }
        if (lower.endsWith(".txt") || lower.endsWith(".text")) {
            return TXT;
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".xhtml")) {
            return HTML;
        }
        if (lower.endsWith(".pdf")) {
            return PDF;
        }
        if (lower.endsWith(".epub")) {
            return EPUB;
        }
        if (lower.endsWith(".docx")) {
            return DOCX;
        }
        if (lower.endsWith(".csv")) {
            return CSV;
        }
        if (lower.endsWith(".json")) {
            return JSON;
        }
        return null;
    }
}
