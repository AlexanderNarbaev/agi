package io.matrix.brain.runtime;

import io.matrix.safety.SafetyMonitor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TRUE-W8 — Audit wiring with real SafetyMonitor.
 *
 * <p>Wraps the matrix-core {@link SafetyMonitor} (FROZEN modulators:
 * ETHICAL_FILTER / SAFETY_MONITOR / LIE_DETECTOR / CONSISTENCY_CHECKER).
 * Every gateway call records an audit entry observable via snapshot.</p>
 */
public final class RealAuditService {

    private final SafetyMonitor safety;
    private long entryCount = 0;

    public RealAuditService(SafetyMonitor safety) {
        this.safety = safety;
    }

    /** Record one audit entry; returns alert level (0=none, 1=info, 2=warn, 3=critical). */
    public int record(String claim, String topic, boolean polarity, double confidence) {
        entryCount++;
        var report = safety.evaluate(claim, List.of(), List.of(), topic, polarity,
            confidence, List.of());
        if (report.alerts() == null || report.alerts().isEmpty()) return 0;
        int worst = 0;
        for (var alert : report.alerts()) {
            int level = switch (alert.level().name()) {
                case "INFO" -> 1;
                case "WARNING" -> 2;
                case "CRITICAL" -> 3;
                default -> 0;
            };
            if (level > worst) worst = level;
        }
        return worst;
    }

    public long entryCount() { return entryCount; }

    public SafetyMonitor safety() { return safety; }

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("engine", "SafetyMonitor.evaluate");
        m.put("entries", entryCount);
        m.put("alerts_history", safety.alertHistory().size());
        return m;
    }
}
