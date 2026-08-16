package io.matrix.verification;

import java.util.Map;

/**
 * Record of a property violation detected at runtime.
 */
public record PropertyViolation(
        String property,
        String description,
        Map<String, Object> context,
        long timestamp
) {
    /**
     * Format as human-readable report.
     */
    public String toReport() {
        return String.format("[%tT] Property '%s' violated: %s | Context: %s",
                timestamp, property, description, context);
    }

    /**
     * Check if this is a critical violation requiring immediate action.
     */
    public boolean isCritical() {
        return property.contains("frozen") || property.contains("safety");
    }
}
