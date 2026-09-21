package io.matrix.consensus;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Message type for the IBGP (Imperfect Byzantine Generals Problem) consensus protocol.
 *
 * <p>Supports the four-phase consensus flow:
 * <ol>
 * <li>{@link Type#PROPOSE} — leader proposes a value</li>
 * <li>{@link Type#VOTE} — nodes vote on the proposal</li>
 * <li>{@link Type#COMMIT} — quorum reached, commit the value</li>
 * <li>{@link Type#REJECT} — quorum not reached, reject</li>
 * </ol>
 *
 * <p>Designed for Pekko actor serialization. Implements {@link Serializable}
 * for wire-compatible transport across actor boundaries.
 *
 * <p>Ref: arXiv:2410.16237 — IBGP protocol messages
 */
public final class ByzantineMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Type {
        PROPOSE, VOTE, COMMIT, REJECT
    }

    private final UUID messageId;
    private final String senderId;
    private final Type type;
    private final String payload;
    private final int round;
    private final long timestamp;
    private final boolean agree;

    public ByzantineMessage(UUID messageId, String senderId, Type type,
                            String payload, int round, long timestamp,
                            boolean agree) {
        this.messageId = Objects.requireNonNull(messageId, "messageId");
        this.senderId = Objects.requireNonNull(senderId, "senderId");
        this.type = Objects.requireNonNull(type, "type");
        this.payload = payload;
        this.round = round;
        this.timestamp = timestamp;
        this.agree = agree;
    }

    public static ByzantineMessage propose(String senderId, String payload, int round) {
        return new ByzantineMessage(UUID.randomUUID(), senderId, Type.PROPOSE,
                payload, round, System.currentTimeMillis(), true);
    }

    public static ByzantineMessage vote(String senderId, String payload,
                                         int round, boolean agree) {
        return new ByzantineMessage(UUID.randomUUID(), senderId, Type.VOTE,
                payload, round, System.currentTimeMillis(), agree);
    }

    public static ByzantineMessage commit(String senderId, String payload, int round) {
        return new ByzantineMessage(UUID.randomUUID(), senderId, Type.COMMIT,
                payload, round, System.currentTimeMillis(), true);
    }

    public static ByzantineMessage reject(String senderId, String payload, int round) {
        return new ByzantineMessage(UUID.randomUUID(), senderId, Type.REJECT,
                payload, round, System.currentTimeMillis(), false);
    }

    public UUID messageId() { return messageId; }
    public String senderId() { return senderId; }
    public Type type() { return type; }
    public String payload() { return payload; }
    public int round() { return round; }
    public long timestamp() { return timestamp; }
    public boolean agree() { return agree; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ByzantineMessage other)) return false;
        return messageId.equals(other.messageId);
    }

    @Override
    public int hashCode() {
        return messageId.hashCode();
    }

    @Override
    public String toString() {
        return "ByzantineMessage{type=" + type + ", sender=" + senderId
                + ", round=" + round + ", agree=" + agree + "}";
    }
}
