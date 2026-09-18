package io.matrix.federation.telemetry;

import java.util.Map;

/**
 * W381 — Prometheus-format exporter for FederationTelemetry.
 * 
 * Renders telemetry snapshot in Prometheus text format for scraping.
 */
public final class PrometheusExporter {
    
    /** Prometheus content type. */
    public static final String CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8";
    
    private PrometheusExporter() {}
    
    /**
     * Escape Prometheus label values per spec:
     * - Backslash → \
     * - Newline → \n
     * - Double quote → \"
     */
    private static String escapeLabel(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\"", "\\\"");
    }
    
    /**
     * Render telemetry snapshot as Prometheus metrics.
     */
    public static String render(FederationTelemetry telemetry) {
        FederationTelemetry.TelemetrySnapshot snap = telemetry.snapshot();
        StringBuilder sb = new StringBuilder();
        
        // Total events counter
        sb.append("# HELP federation_total_events Total federation events recorded\n");
        sb.append("# TYPE federation_total_events counter\n");
        sb.append("federation_total_events ").append(snap.totalEvents()).append("\n\n");
        
        // Registry mutations counter
        sb.append("# HELP federation_registry_mutations Registry mutations\n");
        sb.append("# TYPE federation_registry_mutations counter\n");
        sb.append("federation_registry_mutations ").append(snap.registryMutations()).append("\n\n");
        
        // Event counts by type
        sb.append("# HELP federation_events_by_type Events by type\n");
        sb.append("# TYPE federation_events_by_type counter\n");
        for (Map.Entry<String, Integer> entry : snap.eventCountsByType().entrySet()) {
            sb.append("federation_events_by_type{type=\"").append(escapeLabel(entry.getKey())).append("\"} ")
              .append(entry.getValue()).append("\n");
        }
        sb.append("\n");
        
        // Proposals by status
        sb.append("# HELP federation_proposals_by_status Proposals by status\n");
        sb.append("# TYPE federation_proposals_by_status counter\n");
        for (Map.Entry<String, Integer> entry : snap.proposalCountsByStatus().entrySet()) {
            sb.append("federation_proposals_by_status{status=\"").append(escapeLabel(entry.getKey())).append("\"} ")
              .append(entry.getValue()).append("\n");
        }
        sb.append("\n");
        
        // Votes by decision
        sb.append("# HELP federation_votes_by_decision Votes by decision\n");
        sb.append("# TYPE federation_votes_by_decision counter\n");
        for (Map.Entry<String, Integer> entry : snap.voteCountsByDecision().entrySet()) {
            sb.append("federation_votes_by_decision{decision=\"").append(escapeLabel(entry.getKey())).append("\"} ")
              .append(entry.getValue()).append("\n");
        }
        
        return sb.toString();
    }
}
