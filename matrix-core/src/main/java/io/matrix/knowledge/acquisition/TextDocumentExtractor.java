package io.matrix.knowledge.acquisition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads plain text and Markdown (RECON-W42.2).
 *
 * <p>Markdown keeps its structure: a heading starts a new segment and the
 * heading names the segment, so that a claim extracted from a chapter can
 * be traced to the chapter rather than to a line number.
 *
 * <p>The text is normalized: the line endings are unified, the trailing
 * spaces are dropped and the runs of blank lines are collapsed. What is
 * <b>not</b> done is any change of the words themselves — the mission's §6
 * forbids silently changing meaning, and normalization that rewrites
 * words is normalization that can change meaning.
 */
public final class TextDocumentExtractor implements DocumentExtractor {

    private final DocumentFormat format;

    public TextDocumentExtractor(DocumentFormat format) {
        this.format = format;
    }

    @Override
    public DocumentFormat format() {
        return format;
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
        String raw = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        String normalized = normalize(raw);
        source.processed("read " + normalized.length() + " characters");
        source.extractedStructure(format == DocumentFormat.MARKDOWN
                ? "markdown with headings" : "plain text");

        List<ExtractedDocument.Segment> segments = segment(normalized, filename);
        source.extractionQuality(segments.isEmpty() ? 0.0 : 1.0);
        return new ExtractedDocument(source.build(), normalized, segments);
    }

    /** The normalization: the line endings and the blank lines, nothing else. */
    public static String normalize(String raw) {
        String unified = raw.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder out = new StringBuilder(unified.length());
        int blankRun = 0;
        for (String line : unified.split("\n", -1)) {
            String trimmed = line.stripTrailing();
            if (trimmed.isBlank()) {
                blankRun++;
                if (blankRun > 1) {
                    continue;
                }
            } else {
                blankRun = 0;
            }
            out.append(trimmed).append('\n');
        }
        return out.toString().strip();
    }

    /**
     * Splits the text into segments.
     *
     * <p>A heading starts a segment; a blank line separates paragraphs
     * inside one.
     */
    private List<ExtractedDocument.Segment> segment(String text, String filename) {
        List<ExtractedDocument.Segment> segments = new ArrayList<>();
        if (text.isBlank()) {
            return segments;
        }
        String heading = null;
        StringBuilder current = new StringBuilder();
        int order = 0;
        int paragraphStart = 1;

        List<String> lines = List.of(text.split("\n", -1));
        int lineNumber = 0;
        for (String line : lines) {
            lineNumber++;
            String markdownHeading = markdownHeading(line);
            if (markdownHeading != null) {
                if (!current.isEmpty()) {
                    segments.add(segmentOf(segments.size(), order++, heading, current.toString(),
                            paragraphStart, filename));
                    current.setLength(0);
                }
                heading = markdownHeading;
                paragraphStart = lineNumber;
                continue;
            }
            if (line.isBlank()) {
                if (!current.isEmpty()) {
                    segments.add(segmentOf(segments.size(), order++, heading, current.toString(),
                            paragraphStart, filename));
                    current.setLength(0);
                    paragraphStart = lineNumber + 1;
                }
                continue;
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line);
        }
        if (!current.isEmpty()) {
            segments.add(segmentOf(segments.size(), order++, heading, current.toString(),
                    paragraphStart, filename));
        }
        return segments;
    }

    /** The heading of a Markdown line, or null. */
    private String markdownHeading(String line) {
        if (format != DocumentFormat.MARKDOWN) {
            return null;
        }
        String trimmed = line.strip();
        if (!trimmed.startsWith("#")) {
            return null;
        }
        int level = 0;
        while (level < trimmed.length() && trimmed.charAt(level) == '#') {
            level++;
        }
        if (level > 6 || level >= trimmed.length()) {
            return null;
        }
        if (trimmed.charAt(level) != ' ') {
            return null;
        }
        return trimmed.substring(level + 1).strip();
    }

    private ExtractedDocument.Segment segmentOf(int index, int order, String heading,
                                                String text, int line, String filename) {
        String location = heading == null
                ? filename + ":" + line
                : filename + ": \"" + heading + "\" (line " + line + ")";
        return new ExtractedDocument.Segment(
                "seg-" + index + "-" + order, text.strip(), location, order, heading,
                guessLanguage(text));
    }

    /**
     * The language of a segment, guessed by its script.
     *
     * <p>A guess, and labelled as one: the language of a document is not
     * known until something says so, and Cyrillic text is Russian only by
     * convention.
     */
    public static String guessLanguage(String text) {
        int cyrillic = 0;
        int latin = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'а' && c <= 'я' || c >= 'ё' && c <= 'ё') {
                cyrillic++;
            } else if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') {
                latin++;
            }
        }
        if (cyrillic > latin) {
            return "ru";
        }
        if (latin > 0) {
            return "en";
        }
        return "unknown";
    }

    /** The lower-cased language name, for the store. */
    public static String normalizeLanguage(String language) {
        return language == null ? "unknown" : language.toLowerCase(Locale.ROOT);
    }
}
