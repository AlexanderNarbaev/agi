package io.matrix.neuron;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 436 — Trivial CSV parser/serializer (RFC 4180-quoting).
 * <p>Parses a CSV line into fields with proper quote escaping; serialises
 * a list of fields back into a CSV line. Pure function, no Random.
 * CONSTITUTION I-safe.
 *
 * <p>Limitations: doesn't support multi-line cells (cells with embedded
 * {@code \n}). For full RFC 4180 line-aware parsing use {@code commons-csv}.
 */
public final class Csv {

    private Csv() {}

    /** Parse a single CSV line into a list of fields. */
    public static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        boolean afterQuote = false;  // for escaped "" → " inside quotes
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        // Escaped quote
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                        afterQuote = true;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == ',') {
                    fields.add(cur.toString());
                    cur.setLength(0);
                    afterQuote = false;
                } else if (c == '"' && cur.length() == 0 && !afterQuote) {
                    inQuotes = true;
                } else {
                    cur.append(c);
                    afterQuote = false;
                }
            }
        }
        fields.add(cur.toString());
        return fields;
    }

    /** Serialise a list of fields into a CSV line. */
    public static String formatLine(List<String> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(',');
            String f = fields.get(i);
            boolean needsQuote = f.contains(",") || f.contains("\"") ||
                    f.contains("\n") || f.contains("\r");
            if (needsQuote) {
                sb.append('"');
                for (int j = 0; j < f.length(); j++) {
                    char c = f.charAt(j);
                    if (c == '"') sb.append('"');
                    sb.append(c);
                }
                sb.append('"');
            } else {
                sb.append(f);
            }
        }
        return sb.toString();
    }
}
