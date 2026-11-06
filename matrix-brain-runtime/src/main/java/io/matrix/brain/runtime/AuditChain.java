package io.matrix.brain.runtime;

import io.matrix.audit.AuditEvent;
import io.matrix.audit.HashChainedLog;

import java.util.ArrayList;
import java.util.List;

/**
 * The runtime's audit chain — a face of the canonical engine.
 *
 * <p>W42.9 / D-012: there were THREE implementations of a hash-chained audit log in this
 * repository — {@code io.matrix.audit.HashChainedLog} (canonical), the gateway's bounded
 * mirror {@code io.matrix.api.HashChainedAuditBuffer}, and this class. This one is now
 * the runtime's view of the canonical engine: it keeps no hash logic of its own, so the
 * chain format and the verification are the canonical ones (CONSTITUTION Article VIII —
 * every decision is auditable, and the audit is tamper-evident by construction).</p>
 *
 * <p>The chain is in memory and grows with the number of recorded decisions. The
 * gateway's own buffer is bounded; this one is not, and that is stated rather than
 * hidden.</p>
 */
public final class AuditChain {

    public record Entry(
        long index,
        long timestampMillis,
        String action,
        String details,
        String prevHash,
        String hash
    ) {}

    private HashChainedLog log = new HashChainedLog();

    /** Record one decision and return the entry that was written. */
    public synchronized Entry append(String action, String details) {
        AuditEvent event = log.append(AuditEvent.builder()
            .userId("")
            .action(action)
            .target(details)
            .statusCode(0));
        return toEntry(event, log.size());
    }

    /** The entry at the one-based index, or null when there is none. */
    public synchronized Entry get(long idx) {
        List<AuditEvent> all = log.all();
        if (idx < 1 || idx > all.size()) return null;
        return toEntry(all.get((int) idx - 1), (int) idx);
    }

    public synchronized int size() {
        return log.size();
    }

    public synchronized List<Entry> all() {
        List<AuditEvent> events = log.all();
        List<Entry> entries = new ArrayList<>(events.size());
        for (int i = 0; i < events.size(); i++) {
            entries.add(toEntry(events.get(i), i + 1));
        }
        return entries;
    }

    /** Verify the chain: true when every entry still hashes to its recorded value. */
    public synchronized boolean verify() {
        return log.verify() == null;
    }

    /** Test helper: rewrite the details of the entry at the one-based index. */
    public synchronized void tamper(long idx, String newDetails) {
        List<AuditEvent> events = new ArrayList<>(log.all());
        if (idx < 1 || idx > events.size()) throw new IllegalArgumentException();
        AuditEvent old = events.get((int) idx - 1);
        events.set((int) idx - 1, new AuditEvent(
            old.eventId(), old.timestamp(), old.prevHash(), old.hash(),
            old.userId(), old.action(), newDetails, old.statusCode(),
            old.explainId(), old.metadata(), old.tombstone()));
        log = new HashChainedLog(events);
    }

    private static Entry toEntry(AuditEvent event, int index) {
        return new Entry(index, event.timestamp().toEpochMilli(), event.action(),
            event.target(), event.prevHash(), event.hash());
    }
}
