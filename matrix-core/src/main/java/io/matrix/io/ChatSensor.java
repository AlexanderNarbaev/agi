package io.matrix.io;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reads chat messages from an in-memory producer queue and converts each to a
 * 64-bit observation suitable for the MPDT agent loop.
 *
 * <p>Bit encoding (LSB-first):
 * <ol>
 *   <li>Bits 0..7: lowercase ASCII of first character of the message (or 0 if blank).</li>
 *   <li>Bits 8..12: length bucket (0=0–3, 1=4–15, 2=16–63, 3=64–255, 4=256+).</li>
 *   <li>Bits 13..17: hashCode-bucket of message (5 LSBs of non-negative hash).</li>
 *   <li>Bit 18: question mark detected.</li>
 *   <li>Bit 19: imperative/exclamation detected.</li>
 *   <li>Bits 20..63: reserved / monotonic counter (low 32 bits of cycle id).</li>
 * </ol>
 *
 * <p>Producers can call {@link #enqueue(String)} from any thread; consumers
 * (the {@code AgentLoop}) call {@link #read()} once per tick.
 *
 * <p>Ref: L25 §3.1 (chat input adapter).
 */
public final class ChatSensor implements Sensor {

    public static final String SOURCE_ID = "chat";

    private final LinkedBlockingQueue<String> inbox = new LinkedBlockingQueue<>(256);
    private final AtomicLong sequence = new AtomicLong();
    private volatile SensorFrame pending = SensorFrame.EMPTY;

    public ChatSensor() {}

    /** Producer API: submit a new chat message. */
    public void enqueue(String message) {
        if (message == null || message.isBlank()) return;
        inbox.offer(message);
    }

    /** Clears all pending messages (used after a session reset). */
    public void clear() {
        inbox.clear();
        pending = SensorFrame.EMPTY;
    }

    @Override
    public String sourceId() { return SOURCE_ID; }

    @Override
    public SensorFrame peek() {
        if (pending != SensorFrame.EMPTY) return pending;
        String msg = inbox.poll();
        if (msg == null) return SensorFrame.EMPTY;
        pending = encode(msg, sequence.incrementAndGet());
        return pending;
    }

    @Override
    public SensorFrame read() {
        if (pending != SensorFrame.EMPTY) {
            SensorFrame f = pending;
            pending = SensorFrame.EMPTY;
            return f;
        }
        String msg = inbox.poll();
        if (msg == null) return SensorFrame.EMPTY;
        return encode(msg, sequence.incrementAndGet());
    }

    @Override
    public java.util.List<String> capabilities() {
        return java.util.List.of("chat.text", "chat.intent");
    }

    /** Encodes a single message into a SensorFrame. */
    static SensorFrame encode(String message, long seq) {
        Objects.requireNonNull(message, "message");
        String lower = message.toLowerCase(Locale.ROOT);
        long bits = 0L;

        char first = lower.charAt(0);
        bits |= ((first & 0xFF) & 0xFFL);                   // bits 0..7
        bits |= (lengthBucket(lower.length()) & 0x1FL) << 8; // bits 8..12
        long hashBucket = (message.hashCode() & 0x1FL);     // 5 LSBs of non-negative hash
        bits |= hashBucket << 13;
        bits |= (lower.indexOf('?') >= 0 ? 1L : 0L) << 18;
        bits |= (lower.indexOf('!') >= 0 ? 1L : 0L) << 19;
        bits |= (seq & 0xFFFFFFFFL) << 20;                  // bits 20..51 monotonic counter
        // bits 52..63: zero, reserved for system use

        return new SensorFrame(SOURCE_ID, bits,
                System.currentTimeMillis(), hashTag(message),
                SensorFrame.Payload.CHAT_TEXT);
    }

    static long lengthBucket(int len) {
        if (len <= 3) return 0;
        if (len <= 15) return 1;
        if (len <= 63) return 2;
        if (len <= 255) return 3;
        return 4;
    }

    /** Deterministic tag (first 8 hex chars of sha-256 of bytes — truncated for log brevity). */
    static String hashTag(String message) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4; i++) sb.append(String.format("%02x", hash[i]));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return String.valueOf(message.hashCode());
        }
    }

    /** Best-effort peek with timeout (used in tests). */
    public SensorFrame readBlocking(long millis) {
        try {
            String msg = inbox.poll(millis, TimeUnit.MILLISECONDS);
            return msg == null ? SensorFrame.EMPTY : encode(msg, sequence.incrementAndGet());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SensorFrame.EMPTY;
        }
    }
}
