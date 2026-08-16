package io.matrix.io;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * IoT event sensor — receives incoming JSON events from an in-process queue
 * (populated, for example, by an MQTT or HTTP bridge downstream).
 *
 * <p>Each {@link IotEvent} becomes a {@link SensorFrame} whose {@code sensorBits}
 * encodes the event type and severity. Adapter code is responsible for
 * translating JSON payloads into {@link IotEvent} and calling
 * {@link #enqueue(IotEvent)}.
 *
 * <p>Bit layout (LSB-first):
 * <ul>
 *   <li>bits 0..7: event type code byte (0–255)</li>
 *   <li>bits 8..11: severity bucket (0=info, 1=warn, 2=error, 3=critical)</li>
 *   <li>bits 12..23: source kind hash (12 LSBs)</li>
 *   <li>bits 24..63: source-id hash + monotonic event counter</li>
 * </ul>
 *
 * <p>Ref: L25 §3.2 (IoT adapter skeleton).
 */
public final class IoTSensor implements Sensor {

    public static final String SOURCE_ID = "iot";

    /** Pending event tagged with its raw payload. */
    public record IotEvent(String topic, int severity, int typeCode, String json) {
        public IotEvent {
            if (topic == null) throw new IllegalArgumentException("topic required");
            if (severity < 0 || severity > 3) {
                throw new IllegalArgumentException("severity must be 0..3, got " + severity);
            }
            if (typeCode < 0 || typeCode > 255) {
                throw new IllegalArgumentException("typeCode must be 0..255, got " + typeCode);
            }
            if (json == null) json = "";
        }
    }

    private final LinkedBlockingQueue<IotEvent> inbox = new LinkedBlockingQueue<>(1024);
    private final AtomicLong counter = new AtomicLong();
    private volatile SensorFrame pending = SensorFrame.EMPTY;

    public IoTSensor() {}

    /** Adapter hook: submit an inbound IoT event for the next agent tick to consume. */
    public void enqueue(IotEvent event) {
        Objects.requireNonNull(event, "event");
        inbox.offer(event);
    }

    @Override
    public String sourceId() { return SOURCE_ID; }

    @Override
    public SensorFrame peek() {
        if (pending != SensorFrame.EMPTY) return pending;
        IotEvent ev = inbox.poll();
        if (ev == null) return SensorFrame.EMPTY;
        pending = encode(ev, counter.incrementAndGet());
        return pending;
    }

    @Override
    public SensorFrame read() {
        if (pending != SensorFrame.EMPTY) {
            SensorFrame f = pending;
            pending = SensorFrame.EMPTY;
            return f;
        }
        IotEvent ev = inbox.poll();
        if (ev == null) return SensorFrame.EMPTY;
        return encode(ev, counter.incrementAndGet());
    }

    @Override
    public List<String> capabilities() {
        return List.of("iot.event", "iot.mqtt", "iot.http-bridge");
    }

    static SensorFrame encode(IotEvent ev, long seq) {
        long bits = 0L;
        bits |= (ev.typeCode() & 0xFFL);                  // bits 0..7
        bits |= ((ev.severity() & 0xFL) << 8);           // bits 8..11
        bits |= (Math.floorMod(ev.topic().hashCode(), 1 << 12) & 0xFFFL) << 12; // bits 12..23
        bits |= ((seq & 0xFFFFFL) << 24);                 // bits 24..43 monotonic

        String tag = ev.topic() + "#" + ev.typeCode();
        String shaTag = shortHash(ev.json().getBytes(StandardCharsets.UTF_8));
        return new SensorFrame(SOURCE_ID, bits,
                System.currentTimeMillis(), tag + "-" + shaTag,
                SensorFrame.Payload.IOT_JSON);
    }

    static String shortHash(byte[] data) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4; i++) sb.append(String.format("%02x", h[i]));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return "nohash";
        }
    }
}
