package io.matrix.audit;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * WAVE T-06 — Audit Event.
 *
 * Immutable record of a single auditable action. Once written to the
 * {@link HashChainedLog}, an event cannot be modified — only superseded
 * by GDPR pruner (which writes a "tombstone" entry).
 *
 * <p><b>CONSTITUTION compliance:</b> Fields are immutable (Java record).
 * No LLM is invoked; events are pure structural data.</p>
 */
public record AuditEvent(
    @JsonProperty("event_id") String eventId,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("prev_hash") String prevHash,
    @JsonProperty("hash") String hash,
    @JsonProperty("user_id") String userId,
    @JsonProperty("action") String action,
    @JsonProperty("target") String target,
    @JsonProperty("status_code") int statusCode,
    @JsonProperty("explain_id") String explainId,
    @JsonProperty("metadata") String metadata,
    @JsonProperty("tombstone") boolean tombstone
) {
    public AuditEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(prevHash, "prevHash");
        Objects.requireNonNull(hash, "hash");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(action, "action");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        private Instant timestamp = Instant.now();
        private String prevHash = "0".repeat(64);
        private String hash = "";
        private String userId = "";
        private String action = "";
        private String target = "";
        private int statusCode = 200;
        private String explainId = null;
        private String metadata = null;
        private boolean tombstone = false;

        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder action(String action) { this.action = action; return this; }
        public Builder target(String target) { this.target = target; return this; }
        public Builder statusCode(int statusCode) { this.statusCode = statusCode; return this; }
        public Builder explainId(String explainId) { this.explainId = explainId; return this; }
        public Builder metadata(String metadata) { this.metadata = metadata; return this; }
        public Builder tombstone(boolean tombstone) { this.tombstone = tombstone; return this; }
        public Builder prevHash(String prevHash) { this.prevHash = prevHash; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder hash(String hash) { this.hash = hash; return this; }
        public Builder eventId(String eventId) { this.eventId = eventId; return this; }

        public AuditEvent build() {
            return new AuditEvent(
                eventId, timestamp, prevHash, hash,
                userId, action, target, statusCode,
                explainId, metadata, tombstone
            );
        }
    }
}
