package io.matrix.audit;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class AnomalyDetectorTest {

    @Test
    void testNoAnomaliesOnEmptyLog() {
        HashChainedLog log = new HashChainedLog();
        AnomalyDetector detector = new AnomalyDetector(log);
        assertEquals(0, detector.scan().size());
    }

    @Test
    void testNoAnomaliesOnNormalActivity() {
        HashChainedLog log = new HashChainedLog();
        log.append(AuditEvent.builder().userId("alice").action("A"));
        log.append(AuditEvent.builder().userId("bob").action("B"));
        AnomalyDetector detector = new AnomalyDetector(log);
        assertEquals(0, detector.scan().size());
    }

    @Test
    void testHighFailureRateDetected() {
        HashChainedLog log = new HashChainedLog();
        for (int i = 0; i < 100; i++) {
            log.append(AuditEvent.builder()
                .userId("alice")
                .action("A")
                .statusCode(401));
        }
        AnomalyDetector detector = new AnomalyDetector(log);
        var anomalies = detector.scan();
        assertTrue(anomalies.stream()
            .anyMatch(a -> a.type().equals("HIGH_FAILURE_RATE")));
    }

    @Test
    void testGdprAbuseDetected() {
        HashChainedLog log = new HashChainedLog();
        for (int i = 0; i < 5; i++) {
            log.append(AuditEvent.builder()
                .userId("system")
                .action("GDPR_ERASURE")
                .target("user-" + i));
        }
        AnomalyDetector detector = new AnomalyDetector(log);
        var anomalies = detector.scan();
        assertTrue(anomalies.stream()
            .anyMatch(a -> a.type().equals("GDPR_ERASURE_ABUSE")));
    }

    @Test
    void testExplainIdReuseDetected() {
        HashChainedLog log = new HashChainedLog();
        for (int i = 0; i < 10; i++) {
            log.append(AuditEvent.builder()
                .userId("alice")
                .action("A")
                .explainId("expl_reused_123"));
        }
        AnomalyDetector detector = new AnomalyDetector(log);
        var anomalies = detector.scan();
        assertTrue(anomalies.stream()
            .anyMatch(a -> a.type().equals("EXPLAIN_ID_REUSE")));
    }

    @Test
    void testBurstActivityDetected() {
        HashChainedLog log = new HashChainedLog();
        for (int i = 0; i < 200; i++) {
            log.append(AuditEvent.builder().userId("alice").action("A"));
        }
        AnomalyDetector detector = new AnomalyDetector(log);
        var anomalies = detector.scan();
        assertTrue(anomalies.stream()
            .anyMatch(a -> a.type().equals("BURST_ACTIVITY")));
    }

    @Test
    void testSeverityEnum() {
        assertEquals(3, AnomalyDetector.Severity.values().length);
        assertNotNull(AnomalyDetector.Severity.valueOf("INFO"));
        assertNotNull(AnomalyDetector.Severity.valueOf("WARN"));
        assertNotNull(AnomalyDetector.Severity.valueOf("ALERT"));
    }

    @Test
    void testCustomThresholds() {
        HashChainedLog log = new HashChainedLog();
        log.append(AuditEvent.builder().userId("alice").action("A"));

        AnomalyDetector detector = new AnomalyDetector(
            log, 50, Duration.ofSeconds(1), 5, 1);
        // Single event shouldn't trigger burst
        assertFalse(detector.scan().stream()
            .anyMatch(a -> a.type().equals("BURST_ACTIVITY")));
    }
}
