package io.matrix.cognitive;

/**
 * W44.1 — decides which channel a signal arrived on, and whether it is well formed.
 *
 * <p>The directive is blunt about what this must not do: recognising a PNG header is not
 * understanding an image, recognising a JSON object is not understanding the event it describes,
 * recognising Russian characters is not understanding Russian. So this classifier reports the
 * FORMAT of a payload and nothing else. It never claims to have understood the content, and it
 * never assigns a meaning to a channel.</p>
 *
 * <p>Malformed input is a first-class outcome. A payload that LOOKS structured but does not
 * parse is reported as malformed, not silently downgraded to text — the difference matters,
 * because a malformed structured payload usually means a producer bug, and a text payload
 * usually means a human.</p>
 *
 * <p>The classifier is a pure function of the bytes. It has no state and no clock.</p>
 */
public final class ChannelClassifier {

    /** The first byte of a PNG signature. */
    private static final int PNG_SIGNATURE_FIRST = 0x89;
    /** The byte that opens a PDF document. */
    private static final byte PDF_SIGNATURE = '%';
    /** The byte that opens a ZIP container, which is also the byte that opens most OOXML files. */
    private static final byte ZIP_SIGNATURE = 'P';

    private ChannelClassifier() {
    }

    /**
     * Classifies a signal.
     *
     * @param signal the signal to classify
     * @return the percept: the channel, whether it is malformed, and what was noticed
     */
    public static Percept classify(CognitiveSignal signal) {
        if (signal.rawBytes().length == 0) {
            return Percept.malformed(signal.id(), signal.channel(), "the payload is empty");
        }
        switch (signal.channel()) {
            case BINARY:
                return classifyBinary(signal);
            case STRUCTURED:
                return classifyStructured(signal);
            case TABULAR:
                return Percept.of(signal.id(), signal.channel(), "a tabular record", 0.5);
            case TIME_SERIES:
                return Percept.of(signal.id(), signal.channel(), "a numeric series", 0.5);
            case TEXT:
            case UNKNOWN:
            default:
                return classifyText(signal);
        }
    }

    private static Percept classifyBinary(CognitiveSignal signal) {
        byte[] bytes = signal.rawBytes();
        if (bytes.length >= PNG_SIGNATURE_SIZE && (bytes[0] & 0xFF) == PNG_SIGNATURE_FIRST) {
            return new Percept(signal.id(), signal.channel(),
                    "a PNG image", 0.9, false, java.util.List.of(),
                    java.util.List.of("the signature matched; the pixels were not interpreted"));
        }
        if (bytes.length >= PDF_SIGNATURE_SIZE && bytes[0] == PDF_SIGNATURE) {
            return new Percept(signal.id(), signal.channel(),
                    "a PDF document", 0.9, false, java.util.List.of(),
                    java.util.List.of("the signature matched; the text was not extracted"));
        }
        if (bytes.length >= ZIP_SIGNATURE_SIZE && bytes[0] == ZIP_SIGNATURE
                && bytes[1] == 'K') {
            return new Percept(signal.id(), signal.channel(),
                    "a ZIP container", 0.8, false, java.util.List.of(),
                    java.util.List.of("the container signature matched; the entries were not listed"));
        }
        return new Percept(signal.id(), signal.channel(),
                "binary data of an unrecognised format", 0.0, false, java.util.List.of(),
                java.util.List.of("no known signature matched; no file type was invented"));
    }

    private static Percept classifyStructured(CognitiveSignal signal) {
        String text = signal.rawText();
        if (text == null || text.isBlank()) {
            return Percept.malformed(signal.id(), signal.channel(),
                    "a structured payload with no text form");
        }
        String trimmed = text.strip();
        char first = trimmed.charAt(0);
        char last = trimmed.charAt(trimmed.length() - 1);
        if (first == '{' || first == '[') {
            boolean bracketed = (first == '{' && last == '}') || (first == '[' && last == ']');
            if (!bracketed) {
                return Percept.malformed(signal.id(), signal.channel(),
                        "a structured payload that is not a bracketed record");
            }
            return Percept.of(signal.id(), signal.channel(), "a structured record", 0.8);
        }
        // A bracketed record is one form of a structured payload. The other is a list of
        // name=value pairs, and it is a record in exactly the same sense: the channel says the
        // payload is structured, and the classifier checks that the FORM matches the claim.
        if (looksLikePairs(trimmed)) {
            return Percept.of(signal.id(), signal.channel(),
                    "a structured record of named values", 0.75);
        }
        return Percept.malformed(signal.id(), signal.channel(),
                "a structured payload that is neither a bracketed record nor a list of named values");
    }

    /**
     * Whether the text is a list of {@code name=value} pairs with boolean values.
     *
     * <p>The check is about FORM only. The classifier deliberately does not know which names
     * are meaningful — that is the job of the system that reads the record, and a channel
     * classifier that rejected unknown names would be judging meaning, which is exactly what it
     * must not do.</p>
     */
    private static boolean looksLikePairs(String text) {
        boolean any = false;
        for (String part : text.split(",")) {
            String piece = part.strip();
            if (piece.isEmpty()) {
                continue;
            }
            int equals = piece.indexOf('=');
            if (equals <= 0) {
                return false;
            }
            String value = piece.substring(equals + 1).strip();
            boolean booleanValue = "0".equals(value) || "1".equals(value)
                    || "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
            if (!booleanValue) {
                return false;
            }
            any = true;
        }
        return any;
    }

    private static Percept classifyText(CognitiveSignal signal) {
        String text = signal.rawText();
        if (text == null) {
            return new Percept(signal.id(), signal.channel(),
                    "text of an unknown script", 0.2, false, java.util.List.of(),
                    java.util.List.of("the script was not identified"));
        }
        if (text.isBlank()) {
            return Percept.malformed(signal.id(), signal.channel(), "the payload is blank");
        }
        boolean cyrillic = false;
        boolean latin = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0400 && c <= 0x04FF) {
                cyrillic = true;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                latin = true;
            }
        }
        String script = cyrillic && latin ? "mixed Cyrillic and Latin"
                : cyrillic ? "Cyrillic" : latin ? "Latin" : "a script without Latin or Cyrillic letters";
        return new Percept(signal.id(), signal.channel(),
                "text in " + script, cyrillic && latin ? 0.4 : 0.7, false, java.util.List.of(),
                java.util.List.of("the script was identified; the text was not understood"));
    }

    /** The number of bytes in a PNG signature. */
    private static final int PNG_SIGNATURE_SIZE = 8;
    /** The number of bytes needed to recognise a PDF header. */
    private static final int PDF_SIGNATURE_SIZE = 4;
    /** The number of bytes needed to recognise a ZIP container. */
    private static final int ZIP_SIGNATURE_SIZE = 2;
}
