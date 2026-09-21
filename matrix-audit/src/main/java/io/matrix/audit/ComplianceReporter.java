package io.matrix.audit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * WAVE T-06 — Compliance Report Generator.
 *
 * Produces structured compliance reports for SOX, HIPAA, and GDPR audits.
 * Reports include:
 * - Total event count + chain integrity proof
 * - Per-user activity breakdown
 * - Failed actions (4xx/5xx status codes)
 * - Tombstone count (GDPR erasures)
 * - Hash chain head + tail
 *
 * <p><b>Output formats:</b> Plain JSON (T-06 default), PDF (T-06.5 with PDFBox).</p>
 *
 * <p><b>CONSTITUTION compliance:</b> Reports are derived from the
 * immutable chain — no LLM, no interpretation, just aggregation.</p>
 */
public final class ComplianceReporter {

    public enum Framework {
        SOX("Sarbanes-Oxley"),
        HIPAA("Health Insurance Portability and Accountability Act"),
        GDPR("General Data Protection Regulation"),
        ISO27001("ISO/IEC 27001 Information Security Management");

        public final String fullName;
        Framework(String fullName) { this.fullName = fullName; }
    }

    public record ComplianceReport(
        String framework,
        String frameworkFullName,
        Instant generatedAt,
        Instant periodStart,
        Instant periodEnd,
        int totalEvents,
        int tombstonedEvents,
        boolean chainIntact,
        Integer firstTamperedIndex,
        String chainHeadHash,
        String chainTailHash,
        int failedActions,
        int uniqueUsers,
        List<UserActivity> userActivity,
        String reportHash
    ) {}

    public record UserActivity(
        String userId,
        int eventCount,
        Instant firstSeen,
        Instant lastSeen
    ) {}

    private final HashChainedLog log;

    public ComplianceReporter(HashChainedLog log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Generate a compliance report for the given framework covering all events
     * in the log (or a time-bounded subset).
     */
    public ComplianceReport generate(Framework framework, Instant periodStart, Instant periodEnd) {
        Objects.requireNonNull(framework, "framework");
        Objects.requireNonNull(periodStart, "periodStart");
        Objects.requireNonNull(periodEnd, "periodEnd");

        List<AuditEvent> all = log.all();
        List<AuditEvent> inPeriod = all.stream()
            .filter(e -> !e.timestamp().isBefore(periodStart) && !e.timestamp().isAfter(periodEnd))
            .toList();

        Integer tamperedIndex = log.verify();
        boolean chainIntact = (tamperedIndex == null);

        int total = inPeriod.size();
        int tombstoned = (int) inPeriod.stream().filter(AuditEvent::tombstone).count();
        int failed = (int) inPeriod.stream().filter(e -> e.statusCode() >= 400).count();

        // User activity aggregation
        java.util.Map<String, int[]> stats = new java.util.HashMap<>();  // [count, firstMs, lastMs]
        for (AuditEvent e : inPeriod) {
            stats.computeIfAbsent(e.userId(), k -> new int[]{0, Integer.MAX_VALUE, 0});
            int[] s = stats.get(e.userId());
            s[0]++;
            long ms = e.timestamp().toEpochMilli();
            if (ms < s[1]) s[1] = (int) ms;
            if (ms > s[2]) s[2] = (int) ms;
        }

        List<UserActivity> activities = stats.entrySet().stream()
            .map(entry -> new UserActivity(
                entry.getKey(),
                entry.getValue()[0],
                Instant.ofEpochMilli(entry.getValue()[1]),
                Instant.ofEpochMilli(entry.getValue()[2])
            ))
            .sorted((a, b) -> Integer.compare(b.eventCount(), a.eventCount()))
            .toList();

        String headHash = all.isEmpty() ? null : all.get(0).hash();
        String tailHash = all.isEmpty() ? null : all.get(all.size() - 1).hash();

        ComplianceReport report = new ComplianceReport(
            framework.name(),
            framework.fullName,
            Instant.now(),
            periodStart,
            periodEnd,
            total,
            tombstoned,
            chainIntact,
            tamperedIndex,
            headHash,
            tailHash,
            failed,
            activities.size(),
            activities,
            null  // computed below
        );

        // Compute report hash for tamper-evidence
        String reportInput = report.framework() + "|" + report.totalEvents() +
            "|" + report.chainHeadHash() + "|" + report.chainTailHash();
        String reportHash = HashChainedLog.sha256(reportInput);

        return new ComplianceReport(
            report.framework(), report.frameworkFullName(),
            report.generatedAt(), report.periodStart(), report.periodEnd(),
            report.totalEvents(), report.tombstonedEvents(),
            report.chainIntact(), report.firstTamperedIndex(),
            report.chainHeadHash(), report.chainTailHash(),
            report.failedActions(), report.uniqueUsers(),
            report.userActivity(), reportHash
        );
    }

    /** Convenience: generate report for the last N days. */
    public ComplianceReport generateLastDays(Framework framework, int days) {
        Instant now = Instant.now();
        Instant start = now.minus(days, ChronoUnit.DAYS);
        return generate(framework, start, now);
    }

    /** Render the report as a JSON string (T-06). */
    public String renderJson(ComplianceReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"framework\": \"").append(report.framework()).append("\",\n");
        sb.append("  \"framework_full_name\": \"").append(report.frameworkFullName()).append("\",\n");
        sb.append("  \"generated_at\": \"").append(report.generatedAt()).append("\",\n");
        sb.append("  \"period_start\": \"").append(report.periodStart()).append("\",\n");
        sb.append("  \"period_end\": \"").append(report.periodEnd()).append("\",\n");
        sb.append("  \"total_events\": ").append(report.totalEvents()).append(",\n");
        sb.append("  \"tombstoned_events\": ").append(report.tombstonedEvents()).append(",\n");
        sb.append("  \"chain_intact\": ").append(report.chainIntact()).append(",\n");
        if (report.firstTamperedIndex() != null) {
            sb.append("  \"first_tampered_index\": ").append(report.firstTamperedIndex()).append(",\n");
        }
        sb.append("  \"chain_head_hash\": \"").append(report.chainHeadHash()).append("\",\n");
        sb.append("  \"chain_tail_hash\": \"").append(report.chainTailHash()).append("\",\n");
        sb.append("  \"failed_actions\": ").append(report.failedActions()).append(",\n");
        sb.append("  \"unique_users\": ").append(report.uniqueUsers()).append(",\n");
        sb.append("  \"report_hash\": \"").append(report.reportHash()).append("\"\n");
        sb.append("}");
        return sb.toString();
    }
}
