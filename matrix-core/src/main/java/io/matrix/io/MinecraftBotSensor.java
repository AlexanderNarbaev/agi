package io.matrix.io;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minecraft bot sensor — observes events from a headless or remote-controlled
 * Minecraft client/bot and produces observation frames suitable for the MPDT
 * agent loop.
 *
 * <p>This is a skeleton adapter that integrates with a {@link BotClient}
 * (the actual network bridge). The skeleton is fully testable without a
 * Minecraft server by queueing {@link BotEvent}s directly.
 *
 * <p>Bit encoding (LSB-first):
 * <ul>
 *   <li>bits 0..3: event kind (0=moved, 1=block_changed, 2=damage_taken, 3=chat_msg, 4=item_picked, 5=entity_seen, 6=objective_update)</li>
 *   <li>bits 4..7: bot health bucket (0=full..4=critical)</li>
 *   <li>bits 8..15: x coord mod 256</li>
 *   <li>bits 16..23: y coord mod 256</li>
 *   <li>bits 24..31: z coord mod 256</li>
 *   <li>bits 32..63: monotonic event counter (LSB-first)</li>
 * </ul>
 *
 * <p>Ref: L25 §3.3 (Minecraft bot adapter skeleton).
 */
public final class MinecraftBotSensor implements Sensor {

    public static final String SOURCE_ID = "minecraft-bot";

    /** Minimal contract for the actual remote bot bridge. */
    public interface BotClient {
        /** Returns true if the bot is connected. */
        boolean isConnected();
        /** Reconnect after a transient failure; returns true on success. */
        boolean reconnect();

        /** Helper: always-connected client (suitable for synthetic tests). */
        static BotClient alwaysConnected() {
            return new BotClient() {
                @Override public boolean isConnected() { return true; }
                @Override public boolean reconnect() { return true; }
            };
        }

        /** Helper: never-connected client (forces DISCONNECTED frame). */
        static BotClient neverConnected() {
            return new BotClient() {
                @Override public boolean isConnected() { return false; }
                @Override public boolean reconnect() { return false; }
            };
        }
    }

    /** An observed bot event. */
    public record BotEvent(
            Kind kind,
            int x, int y, int z,
            double healthRatio,        // 0..1
            String payload) {

        public enum Kind {
            MOVED(0), BLOCK_CHANGED(1), DAMAGE_TAKEN(2), CHAT(3),
            ITEM_PICKED(4), ENTITY_SEEN(5), OBJECTIVE_UPDATE(6);

            public final int code;
            Kind(int code) { this.code = code; }
        }

        public BotEvent {
            Objects.requireNonNull(kind, "kind");
            if (healthRatio < 0 || healthRatio > 1) {
                throw new IllegalArgumentException("healthRatio in [0,1]");
            }
            if (payload == null) payload = "";
        }
    }

    private final LinkedBlockingQueue<BotEvent> inbox = new LinkedBlockingQueue<>(512);
    private final AtomicLong counter = new AtomicLong();
    private final BotClient client;
    private volatile SensorFrame pending = SensorFrame.EMPTY;

    public MinecraftBotSensor(BotClient client) {
        this.client = client;
    }

    /** Adapter hook: enqueue an observed event. */
    public void enqueue(BotEvent event) {
        Objects.requireNonNull(event, "event");
        inbox.offer(event);
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    @Override
    public String sourceId() { return SOURCE_ID; }

    @Override
    public SensorFrame peek() {
        if (pending != SensorFrame.EMPTY) return pending;
        if (client != null && !client.isConnected()) {
            // Stub: emit a single reconnect-required frame that the agent loop can react to.
            long seq = counter.incrementAndGet();
            pending = encode(BotEvent.Kind.OBJECTIVE_UPDATE.code, 0, 0, 0, 1.0, "DISCONNECTED", seq);
            return pending;
        }
        BotEvent ev = inbox.poll();
        if (ev == null) return SensorFrame.EMPTY;
        long seq = counter.incrementAndGet();
        pending = encode(ev.kind().code, ev.x(), ev.y(), ev.z(), ev.healthRatio(), ev.payload(), seq);
        return pending;
    }

    @Override
    public SensorFrame read() {
        SensorFrame f = peek();
        if (f != SensorFrame.EMPTY && pending != SensorFrame.EMPTY) {
            pending = SensorFrame.EMPTY;
            return f;
        }
        return f;
    }

    @Override
    public List<String> capabilities() {
        return List.of("minecraft.bot.movement", "minecraft.bot.blocks", "minecraft.bot.chat");
    }

    static SensorFrame encode(int kindCode, int x, int y, int z,
                                double health, String payload, long seq) {
        long bits = 0L;
        bits |= (kindCode & 0xFL);                      // bits 0..3
        bits |= (healthBucket(health) & 0xFL) << 4;       // bits 4..7
        bits |= (Math.floorMod(x, 256) & 0xFFL) << 8;    // bits 8..15
        bits |= (Math.floorMod(y, 256) & 0xFFL) << 16;   // bits 16..23
        bits |= (Math.floorMod(z, 256) & 0xFFL) << 24;   // bits 24..31
        bits |= (seq & 0xFFFFFFFFL) << 32;               // bits 32..63
        String tag = "evt" + kindCode + "@" + x + "," + y + "," + z;
        return new SensorFrame(SOURCE_ID, bits,
                System.currentTimeMillis(), tag,
                SensorFrame.Payload.MINECRAFT_EVENT);
    }

    /** Maps {@code 0..1} health to a 4-bit bucket: 0 full → 4 critical. */
    static int healthBucket(double h) {
        if (h >= 0.95) return 0;
        if (h >= 0.70) return 1;
        if (h >= 0.40) return 2;
        if (h >= 0.20) return 3;
        return 4;
    }
}
