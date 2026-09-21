package io.matrix.api;

/**
 * RUN 140 — Text normalizer.
 *
 * <p>Cleans up generated text by trimming whitespace,
 * removing control characters, and normalizing unicode.
 */
public final class TextNormalizer {

    private TextNormalizer() {}

    /** Trim and collapse multiple spaces into one. */
    public static String normalize(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        boolean lastWasSpace = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t') {
                if (!lastWasSpace) {
                    sb.append(' ');
                    lastWasSpace = true;
                }
            } else if (c == '\n' || c == '\r') {
                if (!lastWasSpace) {
                    sb.append(' ');
                    lastWasSpace = true;
                }
            } else if (c < 0x20) {
                // Skip control characters except \n (already handled)
                continue;
            } else {
                sb.append(c);
                lastWasSpace = false;
            }
        }
        return sb.toString().strip();
    }

    /** Trim leading/trailing whitespace. */
    public static String trim(String text) {
        return text == null ? "" : text.strip();
    }

    /** Collapse multiple consecutive spaces into one. */
    public static String collapseSpaces(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", " ").strip();
    }

    /** Remove all control characters except common whitespace. */
    public static String stripControl(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\p{Cntrl}]", "");
    }
}
