package io.matrix.ethics.guardrail;

/**
 * Test configuration implementations for guardrail tests.
 * Provides sensible defaults that can be overridden per test.
 */
final class TestConfig {

    private TestConfig() {}

    // ── InputFilter Config ──

    static GuardrailConfig.InputFilter inputFilter() {
        return inputFilter(60, 1000);
    }

    static GuardrailConfig.InputFilter inputFilter(int rateLimitPerMinute, int rateLimitPerHour) {
        return new GuardrailConfig.InputFilter() {
            @Override public boolean enabled() { return true; }
            @Override public int maxLength() { return 32000; }
            @Override public boolean promptInjectionDetection() { return true; }
            @Override public boolean maliciousPatternDetection() { return true; }
            @Override public int rateLimitPerMinute() { return rateLimitPerMinute; }
            @Override public int rateLimitPerHour() { return rateLimitPerHour; }
            @Override public double blockThreshold() { return 0.7; }
            @Override public double warnThreshold() { return 0.4; }
        };
    }

    static GuardrailConfig.InputFilter disabledInputFilter() {
        return new GuardrailConfig.InputFilter() {
            @Override public boolean enabled() { return false; }
            @Override public int maxLength() { return 32000; }
            @Override public boolean promptInjectionDetection() { return true; }
            @Override public boolean maliciousPatternDetection() { return true; }
            @Override public int rateLimitPerMinute() { return 60; }
            @Override public int rateLimitPerHour() { return 1000; }
            @Override public double blockThreshold() { return 0.7; }
            @Override public double warnThreshold() { return 0.4; }
        };
    }

    // ── OutputValidation Config ──

    static GuardrailConfig.OutputValidation outputValidation() {
        return new GuardrailConfig.OutputValidation() {
            @Override public boolean enabled() { return true; }
            @Override public int maxLength() { return 64000; }
            @Override public boolean biasDetection() { return true; }
            @Override public boolean hallucinationCheck() { return true; }
            @Override public boolean factualConsistency() { return true; }
            @Override public double blockThreshold() { return 0.7; }
            @Override public double warnThreshold() { return 0.4; }
        };
    }

    static GuardrailConfig.OutputValidation disabledOutputValidation() {
        return new GuardrailConfig.OutputValidation() {
            @Override public boolean enabled() { return false; }
            @Override public int maxLength() { return 64000; }
            @Override public boolean biasDetection() { return true; }
            @Override public boolean hallucinationCheck() { return true; }
            @Override public boolean factualConsistency() { return true; }
            @Override public double blockThreshold() { return 0.7; }
            @Override public double warnThreshold() { return 0.4; }
        };
    }

    // ── AuditLog Config ──

    static GuardrailConfig.AuditLog auditLog() {
        return new GuardrailConfig.AuditLog() {
            @Override public boolean enabled() { return true; }
            @Override public int retentionDays() { return 365; }
            @Override public boolean logPassVerdicts() { return false; }
        };
    }

    // ── Full Config ──

    static GuardrailConfig guardrailConfig() {
        return new GuardrailConfig() {
            @Override public InputFilter inputFilter() { return TestConfig.inputFilter(); }
            @Override public OutputValidation outputValidation() { return TestConfig.outputValidation(); }
            @Override public AuditLog auditLog() { return TestConfig.auditLog(); }
        };
    }
}
