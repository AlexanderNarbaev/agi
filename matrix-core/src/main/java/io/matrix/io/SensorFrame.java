package io.matrix.io;

/**
 * A unified observation frame for an MPDT-agent tick.
 *
 * <p>Combines:
 * <ul>
 *   <li>{@code sensorBits} — at most 64 bits produced by {@code read()} (truncated to {@code long})</li>
 *   <li>{@code sourceId} — which physical/virtual sensor produced the read</li>
 *   <li>optional payload (string for chat, JSON for IoT, byte buffer for binary events)</li>
 * </ul>
 *
 * <p>This richer-than-{@code long} structure allows the agent loop to reason about
 * where a signal came from. Backwards-compat: {@link #asLong()} returns the bit field
 * alone so the existing {@code Sensor.read()} contract keeps working.
 *
 * <p>Ref: L25_InputOutput.md (proposed).
 */
public record SensorFrame(
        String sourceId,
        long sensorBits,
        long timestampMs,
        String sourceTag,
        Payload payload) {

    public enum Payload { BITS, CHAT_TEXT, IOT_JSON, MINECRAFT_EVENT, GENERIC }

    public static final SensorFrame EMPTY =
            new SensorFrame("none", 0L, System.currentTimeMillis(), "-", Payload.BITS);

    /** Construct a bits-only frame (matches legacy {@code Sensor.read()} contract). */
    public static SensorFrame bits(long bits) {
        return new SensorFrame("legacy", bits, System.currentTimeMillis(), "-", Payload.BITS);
    }

    /** Cast to plain {@code long} — keeps {@code Sensor} interface working. */
    public long asLong() { return sensorBits; }

    @Override
    public String toString() {
        return String.format("SensorFrame[%s bits=0x%016x ts=%d tag=%s payload=%s]",
                sourceId, sensorBits, timestampMs, sourceTag, payload);
    }
}
