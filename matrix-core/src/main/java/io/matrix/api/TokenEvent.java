package io.matrix.api;

/**
 * RUN 90 — TokenEvent for streaming generation.
 *
 * <p>Each event in the stream represents one generated token plus
 * metadata about whether it's an EOS marker or an error.
 */
public record TokenEvent(
        int tokenId,
        String text,
        boolean isEos,
        int stepIndex
) {
    /** Mark the end-of-stream (after EOS emitted). */
    public static TokenEvent eos(int stepIndex) {
        return new TokenEvent(-1, "", true, stepIndex);
    }

    /** Mark a stream error. */
    public static TokenEvent error(String message) {
        return new TokenEvent(-1, "ERROR:" + message, false, -1);
    }

    /** True if this is a regular content token. */
    public boolean isContent() {
        return !isEos && tokenId >= 0;
    }
}
