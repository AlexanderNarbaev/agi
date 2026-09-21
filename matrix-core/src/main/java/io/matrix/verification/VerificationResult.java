package io.matrix.verification;

import java.util.List;

/**
 * Result of a verification run containing all detected violations.
 */
public record VerificationResult(
        List<PropertyViolation> violations
) {
    /**
     * Check if all properties passed.
     */
    public boolean isPassing() {
        return violations.isEmpty();
    }

    /**
     * Get only critical violations.
     */
    public List<PropertyViolation> getCriticalViolations() {
        return violations.stream()
                .filter(PropertyViolation::isCritical)
                .toList();
    }

    /**
     * Get summary string.
     */
    public String getSummary() {
        if (isPassing()) {
            return "All properties verified successfully";
        }
        long critical = getCriticalViolations().size();
        return String.format("%d violations detected (%d critical)", violations.size(), critical);
    }
}
