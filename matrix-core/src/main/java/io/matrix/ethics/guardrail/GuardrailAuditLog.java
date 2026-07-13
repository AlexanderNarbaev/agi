package io.matrix.ethics.guardrail;

import java.time.Instant;
import java.util.List;

/**
 * Immutable audit log entry for a single guardrail decision.
 *
 * <p>Every guardrail evaluation — PASS, WARN, or BLOCK — is recorded
 * for EU AI Act compliance (Art. 12 — Record-Keeping).
 *
 * @param id          database-generated ID (null for new entries)
 * @param timestamp   when the evaluation occurred
 * @param userId      user who triggered the evaluation
 * @param endpoint    API endpoint path
 * @param inputText   truncated input text (max 4000 chars)
 * @param outputText  truncated output text (max 4000 chars, null if input was blocked)
 * @param guardName   name of the guard that produced the verdict
 * @param verdict     the final verdict (PASS, WARN, BLOCK)
 * @param reason      human-readable explanation
 * @param confidence  confidence score [0.0 .. 1.0]
 * @param patterns    list of detected patterns
 */
public record GuardrailAuditLog(
        Long id,
        Instant timestamp,
        String userId,
        String endpoint,
        String inputText,
        String outputText,
        String guardName,
        GuardrailVerdict verdict,
        String reason,
        double confidence,
        List<String> patterns
) {
    public GuardrailAuditLog {
        if (timestamp == null) timestamp = Instant.now();
        if (userId == null) userId = "unknown";
        if (endpoint == null) endpoint = "";
        if (guardName == null) guardName = "unknown";
        if (verdict == null) verdict = GuardrailVerdict.PASS;
        if (reason == null) reason = "";
        if (patterns == null) patterns = List.of();
    }

    /** Whether this entry represents a blocked request. */
    public boolean isBlocked() {
        return verdict == GuardrailVerdict.BLOCK;
    }

    /** Whether this entry represents a warning. */
    public boolean isWarning() {
        return verdict == GuardrailVerdict.WARN;
    }
}
