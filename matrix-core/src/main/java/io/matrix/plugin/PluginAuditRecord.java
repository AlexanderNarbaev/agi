package io.matrix.plugin;

/**
 * W51 — one entry of the append-only plugin audit.
 *
 * @param pluginId the plugin the record is about
 * @param action   what was attempted
 * @param outcome  what was decided
 * @param reason   why
 * @param at       when it happened
 */
public record PluginAuditRecord(
        String pluginId,
        String action,
        String outcome,
        String reason,
        String at) {

    /** The outcome of an attempted action. */
    public static final String OUTCOME_APPLIED = "APPLIED";
    /** The outcome of an attempted action that was refused. */
    public static final String OUTCOME_REFUSED = "REFUSED";
}

