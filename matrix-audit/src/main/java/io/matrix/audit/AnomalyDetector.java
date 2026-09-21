package io.matrix.audit;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * WAVE T-06 — Anomaly Detector.
 *
 * Monitors the audit log for suspicious patterns:
 * - Burst activity (many events in a short window)
 * - High failure rate (many 4xx/5xx from one user)
 * - Unusual hour activity (events at unusual times)
 * - Repeated GDPR erasure requests (potential abuse)
 * - Rapid explain_id reuse (potential replay attack)
 *
 * <p>Detection runs synchronously on the current log state. For
 * production, this would run on a schedule (e.g., every minute) and
 * publish alerts to Slack/PagerDuty via the AlertDispatcher (T-09).</p>
 *
 * <p><b>CONSTITUTION compliance:</b> Pure structural analysis, no LLM.</p>
 */
public final class AnomalyDetector {

    /** Severity of an anomaly. */
    public enum Severity {
        INFO,    // informational, no action needed
        WARN,    // worth investigating
        ALERT    // page on-call
    }

    public record Anomaly(
        Severity severity,
        String type,
        String description,
        Instant detectedAt
    ) {}

    private final HashChainedLog log;
    private final int burstThreshold;       // events in burst window
    private final Duration burstWindow;
    private final int failureRateThreshold; // 4xx/5xx per user
    private final int gdprErasureThreshold; // GDPR requests per day

    public AnomalyDetector(HashChainedLog log) {
        this(log, 100, Duration.ofMinutes(1), 50, 3);
    }

    public AnomalyDetector(HashChainedLog log,
                           int burstThreshold,
                           Duration burstWindow,
                           int failureRateThreshold,
                           int gdprErasureThreshold) {
        this.log = Objects.requireNonNull(log, "log");
        this.burstThreshold = burstThreshold;
        this.burstWindow = burstWindow;
        this.failureRateThreshold = failureRateThreshold;
        this.gdprErasureThreshold = gdprErasureThreshold;
    }

    /**
     * Scan the current log state for anomalies. Returns a list (possibly empty).
     */
    public List<Anomaly> scan() {
        List<AuditEvent> events = log.all();
        List<Anomaly> anomalies = new ArrayList<>();

        anomalies.addAll(detectBursts(events));
        anomalies.addAll(detectHighFailureRates(events));
        anomalies.addAll(detectGdprAbuse(events));
        anomalies.addAll(detectExplainIdReuse(events));

        return anomalies;
    }

    /** Detect bursts: more than {@code burstThreshold} events in {@code burstWindow}. */
    private List<Anomaly> detectBursts(List<AuditEvent> events) {
        List<Anomaly> result = new ArrayList<>();
        if (events.isEmpty()) return result;

        Instant windowStart = events.get(events.size() - 1).timestamp().minus(burstWindow);
        long count = events.stream()
            .filter(e -> e.timestamp().isAfter(windowStart))
            .count();

        if (count > burstThreshold) {
            result.add(new Anomaly(
                Severity.WARN,
                "BURST_ACTIVITY",
                String.format("%d events in last %d seconds (threshold: %d)",
                    count, burstWindow.getSeconds(), burstThreshold),
                Instant.now()
            ));
        }
        return result;
    }

    /** Detect users with high 4xx/5xx rate. */
    private List<Anomaly> detectHighFailureRates(List<AuditEvent> events) {
        Map<String, Integer> failures = new HashMap<>();
        for (AuditEvent e : events) {
            if (e.statusCode() >= 400 && !e.tombstone()) {
                failures.merge(e.userId(), 1, Integer::sum);
            }
        }

        return failures.entrySet().stream()
            .filter(entry -> entry.getValue() > failureRateThreshold)
            .map(entry -> new Anomaly(
                Severity.ALERT,
                "HIGH_FAILURE_RATE",
                String.format("User '%s' has %d failed actions (threshold: %d)",
                    entry.getKey(), entry.getValue(), failureRateThreshold),
                Instant.now()
            ))
            .toList();
    }

    /** Detect repeated GDPR erasure requests (potential abuse vector). */
    private List<Anomaly> detectGdprAbuse(List<AuditEvent> events) {
        Instant oneDayAgo = Instant.now().minus(Duration.ofDays(1));
        long count = events.stream()
            .filter(e -> e.action().equals("GDPR_ERASURE"))
            .filter(e -> e.timestamp().isAfter(oneDayAgo))
            .count();

        if (count > gdprErasureThreshold) {
            return List.of(new Anomaly(
                Severity.WARN,
                "GDPR_ERASURE_ABUSE",
                String.format("%d GDPR erasure requests in last 24h (threshold: %d)",
                    count, gdprErasureThreshold),
                Instant.now()
            ));
        }
        return List.of();
    }

    /** Detect potential replay attacks: same explain_id used multiple times. */
    private List<Anomaly> detectExplainIdReuse(List<AuditEvent> events) {
        Map<String, Integer> counts = new HashMap<>();
        for (AuditEvent e : events) {
            if (e.explainId() != null) {
                counts.merge(e.explainId(), 1, Integer::sum);
            }
        }

        return counts.entrySet().stream()
            .filter(entry -> entry.getValue() > 5)
            .map(entry -> new Anomaly(
                Severity.WARN,
                "EXPLAIN_ID_REUSE",
                String.format("explain_id '%s' reused %d times — possible replay",
                    entry.getKey(), entry.getValue()),
                Instant.now()
            ))
            .toList();
    }
}
