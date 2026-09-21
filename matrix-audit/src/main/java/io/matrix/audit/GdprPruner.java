package io.matrix.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.Set;

/**
 * WAVE T-06 — GDPR "Right to be Forgotten" Pruner.
 *
 * Honors GDPR Article 17: appends tombstone entries for events of a given user,
 * preserving the immutable hash chain. Downstream consumers filter by
 * tombstone flag when displaying PII.
 *
 * <p><b>CONSTITUTION compliance:</b> Pure structural operation, no LLM.</p>
 */
public final class GdprPruner {

    private static final String TOMBSTONE_USER_ID = "[REDACTED]";
    private static final String TOMBSTONE_ACTION = "[PURGED]";
    private static final String TOMBSTONE_TARGET = "[PURGED]";

    private final HashChainedLog log;

    public GdprPruner(HashChainedLog log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Purge all events for the given user_id.
     * @return number of events tombstoned
     */
    public int purgeUser(String userId) {
        Objects.requireNonNull(userId, "userId");
        if (userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be blank");
        }

        // Iterate in insertion order. Each tombstone chains to the previous entry
        // in the log (original or previous tombstone).
        List<AuditEvent> userEvents = new ArrayList<>();
        for (AuditEvent e : log.all()) {
            if (e.userId().equals(userId) && !e.tombstone()) {
                userEvents.add(e);
            }
        }

        int count = 0;
        for (AuditEvent original : userEvents) {
            AuditEvent.Builder tb = AuditEvent.builder()
                .eventId(original.eventId())
                .userId(TOMBSTONE_USER_ID)
                .action(TOMBSTONE_ACTION)
                .target(TOMBSTONE_TARGET)
                .statusCode(original.statusCode())
                .metadata("purged_at_" + java.time.Instant.now() + "_per_gdpr_art17")
                .tombstone(true);
            if (original.explainId() != null) {
                tb.explainId(original.explainId());
            }
            log.append(tb);
            count++;
        }

        // Final erasure meta-event
        log.append(
            AuditEvent.builder()
                .userId("system")
                .action("GDPR_ERASURE")
                .target("user_id=" + userId)
                .metadata("purged_count=" + count)
                .tombstone(false)
        );

        return count;
    }

    /** Count tombstones (excluding the erasure meta-event). */
    public long countTombstones() {
        return log.all().stream()
            .filter(e -> e.tombstone() && e.userId().equals(TOMBSTONE_USER_ID))
            .count();
    }

    /**
     * Check if all original events for a user have been tombstoned.
     */
    public boolean isUserFullyErased(String userId) {
        if (userId == null) return false;
        var userOriginals = log.all().stream()
            .filter(e -> e.userId().equals(userId)
                && !e.tombstone()
                && !e.action().equals("GDPR_ERASURE"))
            .toList();
        if (userOriginals.isEmpty()) return false;

        Set<String> tombstonedIds = log.all().stream()
            .filter(AuditEvent::tombstone)
            .map(AuditEvent::eventId)
            .collect(Collectors.toSet());

        return userOriginals.stream()
            .allMatch(e -> tombstonedIds.contains(e.eventId()));
    }
}
