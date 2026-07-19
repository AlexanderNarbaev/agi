package io.matrix.io;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Multiplexes several {@link Sensor}s into a single virtual stream that the
 * {@code AgentLoop} polls each tick. Sources are addressable by {@code sourceId}
 * for per-source inspection and gating.
 *
 * <p>Multiplex strategy: when multiple sensors have unread frames, the bus
 * returns the most-recent. Sensors are queried lazily; the bus never blocks.
 *
 * <p>Ref: L25 §2.
 */
public final class SensorBus {

    private final Map<String, Sensor> sensors = new LinkedHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong frameCounter =
            new java.util.concurrent.atomic.AtomicLong(0);

    /** Registers a sensor (later registration with same id replaces). */
    public SensorBus register(Sensor sensor) {
        if (sensor == null) throw new IllegalArgumentException("sensor must not be null");
        if (sensors.containsKey(sensor.sourceId())) {
            throw new IllegalArgumentException("duplicate sensor id: " + sensor.sourceId());
        }
        sensors.put(sensor.sourceId(), sensor);
        return this;
    }

    /** Removes and returns the sensor under {@code sourceId}, if any. */
    public Optional<Sensor> unregister(String sourceId) {
        return Optional.ofNullable(sensors.remove(sourceId));
    }

    /** Number of registered sensors. */
    public int size() { return sensors.size(); }

    /** All registered source ids in insertion order. */
    public java.util.List<String> sourceIds() {
        return java.util.List.copyOf(sensors.keySet());
    }

    /** Look up a specific sensor by id. */
    public Optional<Sensor> get(String sourceId) {
        return Optional.ofNullable(sensors.get(sourceId));
    }

    /** All registered sensors (live view). */
    public Collection<Sensor> all() {
        return sensors.values();
    }

    /**
     * Polls every sensor once. Returns a synthetic frame whose {@code sensorBits}
     * is the {@code long} that the legacy {@code AgentLoop.Sensor.read()} would
     * have produced: bit-OR of every registered sensor's last frame.
     *
     * <p>If the bus is empty, returns {@link SensorFrame#EMPTY}.
     */
    public SensorFrame pollAll() {
        if (sensors.isEmpty()) return SensorFrame.EMPTY;
        long composite = 0L;
        SensorFrame last = SensorFrame.EMPTY;
        for (Sensor s : sensors.values()) {
            SensorFrame frame;
            try {
                frame = s.peek();
                if (frame == null || frame == SensorFrame.EMPTY) {
                    frame = s.read();
                }
            } catch (RuntimeException re) {
                // Skip the broken sensor instead of crashing the agent loop.
                continue;
            }
            if (frame == null) continue;
            composite |= frame.sensorBits();
            last = frame;
        }
        frameCounter.incrementAndGet();
        if (last == SensorFrame.EMPTY) {
            return SensorFrame.bits(composite);
        }
        // Retain the first sensor's identity and metadata for traceability.
        long ts = Math.max(last.timestampMs(), System.currentTimeMillis() - 1);
        return new SensorFrame(last.sourceId(), composite, ts, last.sourceTag(), last.payload());
    }

    /** Convenience: returns only the bit field (drop-in for legacy Sensor.read). */
    public long readBits() {
        return pollAll().sensorBits();
    }
}
