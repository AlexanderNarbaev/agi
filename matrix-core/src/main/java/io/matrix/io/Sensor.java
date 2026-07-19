package io.matrix.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Polymorphic sensor interface extending the legacy {@code AgentLoop.Sensor.read()}
 * with semantic frames ({@link SensorFrame}). Implementations encode domain events
 * (chat, IoT, Minecraft-bot observations) into 64-bit MPDT observation words.
 *
 * <p>Ref: L25_InputOutput.md.
 *
 * <p>Stability: a {@code Sensor} is FROZEN-by-default — implementations should be
 * deterministic and side-effect-free aside from any explicitly-mutated source state.
 */
public interface Sensor {

    /** Stable identifier used in logs and the SensorBus registry. */
    String sourceId();

    /**
     * Reads the next observation. Implementations should block briefly if no event
     * is available (returning {@link SensorFrame#EMPTY} when nothing meaningful
     * arrived within the polling window). Must be thread-safe.
     */
    SensorFrame read();

    /** Latest unread frame — useful for non-blocking drivers. */
    default SensorFrame peek() {
        return SensorFrame.EMPTY;
    }

    /** Whether the sensor can produce any more frames (false closes the agent loop). */
    default boolean hasMore() { return true; }

    /** Sensor capability declarations — used by SensorBus to dispatch. */
    default List<String> capabilities() {
        return List.of(sourceId());
    }
}
