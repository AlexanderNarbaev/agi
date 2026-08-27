package io.matrix.signals;

import java.time.Instant;
import java.util.Objects;

/**
 * A single sensor observation in the perception pipeline (SPEC-004,
 * DESIGN-16, W-F).
 *
 * <p>Wraps a modality-keyed payload with the metadata needed to trace
 * provenance and respect the k-anonymity gate:
 * <ul>
 *   <li>{@code timestamp} — wall-clock arrival time of the observation.
 *       Never read inside decision paths (determinism rule); recorded for
 *       audit only.</li>
 *   <li>{@code modality} — mirrors the {@link SignalModule#modality()}
 *       string ("text", "image", "audio", "sensor", "actuator").</li>
 *   <li>{@code payload} — opaque modality-specific data; the encoder for
 *       the matching modality is responsible for converting it into a
 *       64-bit signal vector.</li>
 *   <li>{@code kAnonymous} — when {@code true}, downstream consumers must
 *       not surface this packet in any context where fewer than
 *       {@code k} similar packets are available in the same window.</li>
 * </ul>
 *
 * <p>Immutable: all fields are {@code final}, the record is the unit of
 * perception and is safe to share across threads.
 */
public record SensorPacket(Instant timestamp,
                            String modality,
                            Object payload,
                            boolean kAnonymous) {

    public SensorPacket {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(modality, "modality");
        if (modality.isBlank()) {
            throw new IllegalArgumentException("modality must not be blank");
        }
        // payload may be null (sensor reports absence); that is fine.
    }

    /** Convenience factory for the common non-anonymous case. */
    public static SensorPacket of(String modality, Object payload) {
        return new SensorPacket(Instant.now(), modality, payload, false);
    }

    /** Convenience factory for a k-anonymous packet. */
    public static SensorPacket anonymous(String modality, Object payload) {
        return new SensorPacket(Instant.now(), modality, payload, true);
    }

    /** A packet whose payload has already been encoded to a signal vector. */
    public Encoded encodeWith(SignalModule module) {
        Objects.requireNonNull(module, "module");
        if (!module.modality().equals(modality)) {
            throw new IllegalArgumentException(
                    "modality mismatch: packet=" + modality
                            + " module=" + module.modality());
        }
        long[] signal = module.encode(payload);
        return new Encoded(timestamp, modality, signal, kAnonymous);
    }

    /**
     * The encoded form of a {@link SensorPacket}: signal vector + same
     * metadata. Use this downstream of the perception pipeline; the
     * original payload is discarded to avoid leaking sensitive content.
     */
    public record Encoded(Instant timestamp,
                          String modality,
                          long[] signal,
                          boolean kAnonymous) {
        public Encoded {
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(modality, "modality");
            Objects.requireNonNull(signal, "signal");
            if (signal.length == 0) {
                throw new IllegalArgumentException("signal must be non-empty");
            }
            signal = signal.clone();
        }
        /** Defensive copy on read to keep the record immutable. */
        @Override public long[] signal() { return signal.clone(); }
    }
}