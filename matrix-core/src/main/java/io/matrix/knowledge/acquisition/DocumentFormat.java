package io.matrix.knowledge.acquisition;

import java.util.Locale;

/**
 * The formats the acquisition pipeline can read (RECON-W42.2).
 *
 * <p>The mission's §5.1 names TXT, Markdown, HTML, PDF, EPUB and the
 * structured formats, and says: "Use appropriate established libraries
 * instead of developing PDF parsing, EPUB decoding, or document extraction
 * from scratch."
 *
 * <p>Each format is read by a library that already exists — the JDK for
 * plain text and for the ZIP container an EPUB is, jsoup for HTML and for
 * the XHTML inside an EPUB, and PDFBox for PDF. Nothing here parses a
 * binary format by hand.
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
        if (lower.endsWith(".json")) {
            return JSON;
        }
        return null;
    }
}
