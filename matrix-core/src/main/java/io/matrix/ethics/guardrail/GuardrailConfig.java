package io.matrix.ethics.guardrail;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for the EU AI Act guardrail system.
 *
 * <p>Each guard layer can be independently enabled/disabled and tuned.
 * Configuration is injected via Quarkus {@code application.properties}
 * under the {@code matrix.guardrail} prefix.
 *
 * <p>Ref: EU AI Act Art. 9 — Risk Management System
 */
@ConfigMapping(prefix = "matrix.guardrail")
public interface GuardrailConfig {

    /** Input filter guard configuration. */
    InputFilter inputFilter();

    /** Output validation guard configuration. */
    OutputValidation outputValidation();

    /** Audit log configuration. */
    AuditLog auditLog();

    interface InputFilter {
        /** Enable input filtering guard. */
        @WithDefault("true")
        boolean enabled();

        /** Maximum input length in characters. */
        @WithDefault("32000")
        int maxLength();

        /** Enable prompt injection detection. */
        @WithDefault("true")
        boolean promptInjectionDetection();

        /** Enable malicious pattern detection. */
        @WithDefault("true")
        boolean maliciousPatternDetection();

        /** Rate limit: max requests per user per minute. */
        @WithDefault("60")
        int rateLimitPerMinute();

        /** Rate limit: max requests per user per hour. */
        @WithDefault("1000")
        int rateLimitPerHour();

        /** Confidence threshold for blocking (0.0-1.0). */
        @WithDefault("0.7")
        double blockThreshold();

        /** Confidence threshold for warning (0.0-1.0). */
        @WithDefault("0.4")
        double warnThreshold();
    }

    interface OutputValidation {
        /** Enable output validation guard. */
        @WithDefault("true")
        boolean enabled();

        /** Maximum output length in characters. */
        @WithDefault("64000")
        int maxLength();

        /** Enable bias detection. */
        @WithDefault("true")
        boolean biasDetection();

        /** Enable hallucination indicator checks. */
        @WithDefault("true")
        boolean hallucinationCheck();

        /** Enable factual consistency checks. */
        @WithDefault("true")
        boolean factualConsistency();

        /** Confidence threshold for blocking (0.0-1.0). */
        @WithDefault("0.7")
        double blockThreshold();

        /** Confidence threshold for warning (0.0-1.0). */
        @WithDefault("0.4")
        double warnThreshold();
    }

    interface AuditLog {
        /** Enable audit logging. */
        @WithDefault("true")
        boolean enabled();

        /** Retention period in days. */
        @WithDefault("365")
        int retentionDays();

        /** Log PASS verdicts (false = only WARN/BLOCK). */
        @WithDefault("false")
        boolean logPassVerdicts();
    }
}
