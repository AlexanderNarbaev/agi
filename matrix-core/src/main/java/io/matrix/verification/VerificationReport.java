package io.matrix.verification;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reports property violations to logs and metrics.
 */
@ApplicationScoped
public class VerificationReport {

    private static final Logger log = LoggerFactory.getLogger(VerificationReport.class);

    private long totalViolations = 0;
    private long criticalViolations = 0;

    /**
     * Report a property violation.
     */
    public void reportViolation(PropertyViolation violation) {
        totalViolations++;
        
        if (violation.isCritical()) {
            criticalViolations++;
            log.error("CRITICAL property violation: {}", violation.toReport());
        } else {
            log.warn("Property violation: {}", violation.toReport());
        }
    }

    /**
     * Report all violations from a verification result.
     */
    public void reportResult(VerificationResult result) {
        if (result.isPassing()) {
            log.debug("Verification passed");
            return;
        }

        for (PropertyViolation violation : result.violations()) {
            reportViolation(violation);
        }

        log.error("Verification failed: {}", result.getSummary());
    }

    /**
     * Get total violation count.
     */
    public long getTotalViolations() {
        return totalViolations;
    }

    /**
     * Get critical violation count.
     */
    public long getCriticalViolations() {
        return criticalViolations;
    }

    /**
     * Reset counters.
     */
    public void reset() {
        totalViolations = 0;
        criticalViolations = 0;
    }
}
