package io.matrix.cognitive;

import java.util.Arrays;

/**
 * W44.1 — a raw input signal, before any interpretation.
 *
 * <p>The directive that governs this class is precise: <b>a signal's format and a signal's meaning
 * must remain separate</b>. Recognising a PNG header is not understanding an image; recognising a
 * JSON object is not understanding the event it describes. So this record carries ONLY what was
 * received: the bytes, the channel the bytes arrived on, and where they came from. Everything
 * about what they mean belongs to {@link Percept}, which is produced later and can disagree with
 * this signal.</p>
 *
 * <p>The raw payload is kept in both a textual and a binary form because the two are not
 * convertible without loss: a JSON document is bytes AND text, and a PNG is bytes that only
 * happen to have a textual prefix. {@code rawText} is {@code null} when the channel is binary;
 * {@code rawBytes} is never {@code null}.</p>
 *
 * <p>Sequence numbers are assigned by the caller in arrival order and are what makes the event
 * stream replayable: given the same signals in the same order, the same perception is produced
 * (CONSTITUTION Article I — no wall-clock and no randomness in a decision path).</p>
 */
public record CognitiveSignal(
        String id,
        Channel channel,
        String rawText,
        byte[] rawBytes,
        String provenance,
        long sequence) {

    /** The channels MATRIX can currently receive on. */
    public enum Channel {
        /** Free text in a human language. */
        TEXT,
        /** A structured record (JSON-like key/value pairs). */
        STRUCTURED,
        /** A binary protocol frame. */
        BINARY,
        /** A tabular stream of records. */
        TABULAR,
        /** A numeric time series. */
        TIME_SERIES,
        /** Anything that does not match a known channel. */
        UNKNOWN
    }

    public CognitiveSignal {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("signal id must not be blank");
        }
        if (channel == null) {
            throw new IllegalArgumentException("signal channel must not be null");
        }
        if (rawBytes == null) {
            rawBytes = new byte[0];
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("signal sequence must not be negative");
        }
    }

    /** A text signal. */
    public static CognitiveSignal text(String id, String text, String provenance, long sequence) {
        return new CognitiveSignal(id, Channel.TEXT, text,
                text == null ? new byte[0] : text.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                provenance, sequence);
    }

    /** A binary signal. */
    public static CognitiveSignal binary(String id, byte[] bytes, String provenance, long sequence) {
        return new CognitiveSignal(id, Channel.BINARY, null, bytes, provenance, sequence);
    }

    /** A structured signal whose text form is already a serialised record. */
    public static CognitiveSignal structured(String id, String text, String provenance, long sequence) {
        return new CognitiveSignal(id, Channel.STRUCTURED, text,
                text == null ? new byte[0] : text.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                provenance, sequence);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CognitiveSignal other)) {
            return false;
        }
        return sequence == other.sequence
                && id.equals(other.id)
                && channel == other.channel
                && java.util.Objects.equals(rawText, other.rawText)
                && Arrays.equals(rawBytes, other.rawBytes)
                && java.util.Objects.equals(provenance, other.provenance);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, channel, rawText, Arrays.hashCode(rawBytes), provenance, sequence);
    }

    @Override
    public String toString() {
        return "CognitiveSignal[" + id + " channel=" + channel + " seq=" + sequence
                + " bytes=" + rawBytes.length + "]";
    }
}
