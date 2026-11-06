package io.matrix.knowledge.acquisition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a CSV as a table of rows (RECON-W42.7).
 *
 * <p>The mission's §5.1 names CSV among the formats the acquisition
 * pipeline must read. A CSV is a structured source: the header names the
 * columns, and every row is a record. Each row becomes a segment, so that
 * a claim extracted from the table can be traced to the row it came from
 * — the mission's §5.2 asks for the structure of the source, not for a
 * flat wall of text.
 *
 * <p><b>The parser.</b> RFC 4180 is a public specification and the parser
 * here follows it: the fields may be quoted, a quoted field may contain
 * the separator and the line break, and a doubled quote inside a quoted
 * field is one quote. The delimiter is the comma, and a semicolon or a
 * tab is accepted as well when the first line holds one of them.
 *
 * <p><b>What is not done.</b> The values are not interpreted: a number
 * stays the text it was, and a date stays the text it was. The mission's
 * §6 forbids silently changing meaning, and a CSV that guessed its types
 * would change them.
 */
public final class CsvDocumentExtractor implements DocumentExtractor {

    /** The bytes one document may hold. */
    public static final long MAXIMUM_BYTES = 32L * 1024 * 1024;

    /** The rows one document may hold. */
    public static final int MAXIMUM_ROWS = 200_000;

    /** The columns one row may hold. */
    public static final int MAXIMUM_COLUMNS = 1_024;

    @Override
    public DocumentFormat format() {
        return DocumentFormat.CSV;
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
        byte[] bytes = input.readNBytes((int) Math.min(MAXIMUM_BYTES + 1, Integer.MAX_VALUE));
        if (bytes.length > MAXIMUM_BYTES) {
            throw new IOException("the file holds more than " + MAXIMUM_BYTES + " bytes, "
                    + "and it is refused");
        }
        String raw = new String(bytes, StandardCharsets.UTF_8);
        char delimiter = delimiterOf(raw);

        List<List<String>> rows = parse(raw, delimiter);
        if (rows.size() > MAXIMUM_ROWS) {
            throw new IOException("the file holds more than " + MAXIMUM_ROWS + " rows, "
                    + "and it is refused");
        }

        List<String> header = rows.isEmpty() ? List.of() : rows.get(0);
        List<ExtractedDocument.Segment> segments = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int order = 0;
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.size() > MAXIMUM_COLUMNS) {
                throw new IOException("a row holds more than " + MAXIMUM_COLUMNS
                        + " columns, and it is refused");
            }
            String rendered = render(header, row);
            if (rendered.isBlank()) {
                continue;
            }
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(rendered);
            segments.add(new ExtractedDocument.Segment(
                    "seg-" + order,
                    rendered,
                    "row " + (order + 1),
                    order,
                    header.isEmpty() ? null : String.join(", ", header),
                    null));
            order++;
        }

        source.processed("read " + text.length() + " characters");
        source.extractedStructure(header.isEmpty() ? "csv rows" : "csv table");
        source.extractionQuality(segments.isEmpty() ? 0.0 : 1.0);
        return new ExtractedDocument(source.build(), text.toString(), segments);
    }

    /** The delimiter of the file: the comma, unless the first line says otherwise. */
    private static char delimiterOf(String raw) {
        int newline = raw.indexOf('\n');
        String first = newline < 0 ? raw : raw.substring(0, newline);
        int semicolons = 0;
        int tabs = 0;
        int commas = 0;
        for (int i = 0; i < first.length(); i++) {
            char ch = first.charAt(i);
            if (ch == '"') {
                continue;
            }
            if (ch == ';') {
                semicolons++;
            } else if (ch == '\t') {
                tabs++;
            } else if (ch == ',') {
                commas++;
            }
        }
        if (semicolons > commas && semicolons > tabs) {
            return ';';
        }
        if (tabs > commas && tabs > semicolons) {
            return '\t';
        }
        return ',';
    }

    /**
     * The rows of the file.
     *
     * @param raw the text
     * @param delimiter the delimiter
     * @return the rows
     */
    static List<List<String>> parse(String raw, char delimiter) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean fieldWasQuoted = false;
        int i = 0;
        int length = raw.length();
        while (i < length) {
            char ch = raw.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < length && raw.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                        continue;
                    }
                    quoted = false;
                    i++;
                    continue;
                }
                field.append(ch);
                i++;
                continue;
            }
            if (ch == '"' && field.isEmpty() && !fieldWasQuoted) {
                quoted = true;
                fieldWasQuoted = true;
                i++;
                continue;
            }
            if (ch == delimiter) {
                row.add(field.toString());
                field.setLength(0);
                fieldWasQuoted = false;
                i++;
                continue;
            }
            if (ch == '\r') {
                i++;
                continue;
            }
            if (ch == '\n') {
                row.add(field.toString());
                field.setLength(0);
                fieldWasQuoted = false;
                rows.add(row);
                row = new ArrayList<>();
                i++;
                continue;
            }
            field.append(ch);
            i++;
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    /**
     * The row as text: the header's names and the row's values, so that
     * the segment says what the values are OF.
     */
    private static String render(List<String> header, List<String> row) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) {
                out.append("; ");
            }
            String name = i < header.size() ? header.get(i).strip() : "";
            if (!name.isEmpty()) {
                out.append(name).append(": ");
            }
            out.append(row.get(i).strip());
        }
        return out.toString().strip();
    }
}
