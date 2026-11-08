package io.matrix.plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * W51 — the append-only audit of the plugin lifecycle.
 *
 * <p>Thread safety: the audit is written from the registry, which may be called from more
 * than one thread, and the record order is the order of the writes. The clock is a parameter
 * of each write, never a call to the system clock inside this class, so a test can produce a
 * reproducible history.</p>
 */
public final class PluginAudit {

    private final List<PluginAuditRecord> records = new ArrayList<>();

    /**
     * Records an attempt.
     *
     * @param pluginId the plugin the record is about
     * @param action   what was attempted
     * @param outcome  what was decided
     * @param reason   why
     * @param at       when it happened
     */
    public synchronized void record(String pluginId, String action, String outcome, String reason,
                                     String at) {
        records.add(new PluginAuditRecord(pluginId, action, outcome, reason, at));
    }

    /** Every record, in the order it was written. */
    public synchronized List<PluginAuditRecord> records() {
        return List.copyOf(records);
    }

    /**
     * The records of one plugin.
     *
     * @param pluginId the plugin to filter by
     * @return the matching records
     */
    public synchronized List<PluginAuditRecord> recordsOf(String pluginId) {
        return records.stream().filter(r -> r.pluginId().equals(pluginId)).toList();
    }

    /** The number of records written. */
    public synchronized int size() {
        return records.size();
    }
}
