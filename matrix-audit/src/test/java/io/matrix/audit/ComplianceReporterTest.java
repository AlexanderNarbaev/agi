package io.matrix.audit;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import static org.junit.jupiter.api.Assertions.*;

class ComplianceReporterTest {

    @Test
    void testGenerateBasicReport() {
        HashChainedLog log = new HashChainedLog();
        log.append(AuditEvent.builder().userId("alice").action("LOGIN"));
        log.append(AuditEvent.builder().userId("alice").action("ANALYZE").statusCode(200));
        log.append(AuditEvent.builder().userId("bob").action("ANALYZE").statusCode(500));

        ComplianceReporter reporter = new ComplianceReporter(log);
        ComplianceReporter.ComplianceReport report = reporter.generate(
            ComplianceReporter.Framework.GDPR,
            Instant.now().minus(1, ChronoUnit.DAYS),
            Instant.now().plus(1, ChronoUnit.DAYS)
        );

        assertEquals("GDPR", report.framework());
        assertEquals(3, report.totalEvents());
        assertEquals(1, report.failedActions());
        assertEquals(2, report.uniqueUsers());
        assertTrue(report.chainIntact());
        assertNotNull(report.chainHeadHash());
        assertNotNull(report.chainTailHash());
    }

    @Test
    void testReportIncludesUserActivity() {
        HashChainedLog log = new HashChainedLog();
        log.append(AuditEvent.builder().userId("alice").action("A"));
        log.append(AuditEvent.builder().userId("alice").action("B"));
        log.append(AuditEvent.builder().userId("bob").action("C"));

        ComplianceReporter reporter = new ComplianceReporter(log);
        ComplianceReporter.ComplianceReport report = reporter.generateLastDays(
            ComplianceReporter.Framework.SOX, 30
        );

        assertEquals(3, report.totalEvents());
        assertEquals(2, report.uniqueUsers());
        // Sorted by event count desc
        assertEquals("alice", report.userActivity().get(0).userId());
        assertEquals(2, report.userActivity().get(0).eventCount());
    }

    @Test
    void testEmptyLogReport() {
        HashChainedLog log = new HashChainedLog();
        ComplianceReporter reporter = new ComplianceReporter(log);
        ComplianceReporter.ComplianceReport report = reporter.generateLastDays(
            ComplianceReporter.Framework.HIPAA, 30
        );

        assertEquals(0, report.totalEvents());
        assertEquals(0, report.uniqueUsers());
        assertTrue(report.chainIntact());
    }

    @Test
    void testRenderJsonContainsKeyFields() {
        HashChainedLog log = new HashChainedLog();
        log.append(AuditEvent.builder().userId("alice").action("A"));

        ComplianceReporter reporter = new ComplianceReporter(log);
        ComplianceReporter.ComplianceReport report = reporter.generateLastDays(
            ComplianceReporter.Framework.ISO27001, 7
        );
        String json = reporter.renderJson(report);

        assertTrue(json.contains("\"framework\""));
        assertTrue(json.contains("\"ISO27001\""));
        assertTrue(json.contains("\"total_events\""));
        assertTrue(json.contains("\"chain_intact\""));
        assertTrue(json.contains("\"report_hash\""));
    }

    @Test
    void testReportHashIsStable() {
        HashChainedLog log = new HashChainedLog();
        log.append(AuditEvent.builder().userId("u").action("A"));

        ComplianceReporter reporter = new ComplianceReporter(log);
        ComplianceReporter.ComplianceReport r1 = reporter.generateLastDays(
            ComplianceReporter.Framework.GDPR, 30
        );
        ComplianceReporter.ComplianceReport r2 = reporter.generateLastDays(
            ComplianceReporter.Framework.GDPR, 30
        );

        // Different generatedAt but same data => same reportHash
        assertEquals(r1.reportHash(), r2.reportHash());
    }

    @Test
    void testAllFrameworksWork() {
        HashChainedLog log = new HashChainedLog();
        log.append(AuditEvent.builder().userId("u").action("A"));
        ComplianceReporter reporter = new ComplianceReporter(log);

        for (ComplianceReporter.Framework fw : ComplianceReporter.Framework.values()) {
            ComplianceReporter.ComplianceReport report = reporter.generateLastDays(fw, 7);
            assertEquals(fw.name(), report.framework());
            assertEquals(fw.fullName, report.frameworkFullName());
        }
    }
}
