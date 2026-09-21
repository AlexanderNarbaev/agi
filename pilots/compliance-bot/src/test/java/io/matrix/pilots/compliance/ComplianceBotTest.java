package io.matrix.pilots.compliance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ComplianceBotTest {

    private final ComplianceBot bot = new ComplianceBot();

    @Test
    void testGdprDetectsPII() {
        String doc = "Customer SSN: 123-45-6789. Please retain for 7 years.";
        var report = bot.check("Test Doc", doc, ComplianceBot.Regulation.GDPR);
        assertFalse(report.passed());
        assertTrue(report.totalViolations() > 0);
    }

    @Test
    void testGdprCleanDocument() {
        String doc = "We respect user privacy and honor deletion requests.";
        var report = bot.check("Test Doc", doc, ComplianceBot.Regulation.GDPR);
        assertTrue(report.passed());
    }

    @Test
    void testSoxRequiresControlLanguage() {
        String doc = "We make money by selling things.";
        var report = bot.check("Test Doc", doc, ComplianceBot.Regulation.SOX);
        assertFalse(report.findings().isEmpty());
        assertEquals(ComplianceBot.Severity.WARNING,
            report.findings().get(0).severity());
    }

    @Test
    void testSoxCleanDocument() {
        String doc = "Internal audit controls are documented and approved quarterly.";
        var report = bot.check("Test Doc", doc, ComplianceBot.Regulation.SOX);
        assertTrue(report.passed());
    }

    @Test
    void testHipaaCriticalOnPII() {
        String doc = "Patient: 123-45-6789. Diagnosis: diabetes.";
        var report = bot.check("Test Doc", doc, ComplianceBot.Regulation.HIPAA);
        assertFalse(report.passed());
        assertEquals(ComplianceBot.Severity.CRITICAL,
            report.findings().get(0).severity());
    }

    @Test
    void testReportHasHash() {
        var report = bot.check("X", "No PII", ComplianceBot.Regulation.GDPR);
        assertNotNull(report.reportHash());
        assertEquals(16, report.reportHash().length());
    }
}
