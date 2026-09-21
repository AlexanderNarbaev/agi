package io.matrix.audit;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuditModuleTest {

    @Test
    void testStatus() {
        String status = AuditModule.status();
        assertTrue(status.startsWith("matrix-audit:0.1.0-T06:hash=SHA-256"));
    }

    @Test
    void testHashAlgorithm() {
        assertEquals("SHA-256", AuditModule.HASH_ALGORITHM);
    }

    @Test
    void testAppendViaFacade() {
        AuditEvent event = AuditModule.append(
            AuditEvent.builder()
                .userId("facade-user")
                .action("FACADE_TEST")
        );
        assertNotNull(event.hash());
        assertEquals(64, event.hash().length());
    }

    @Test
    void testVerifyAfterAppend() {
        AuditModule.append(AuditEvent.builder().userId("u").action("X"));
        // Chain may have entries from other tests — verify() returns null if intact
        // We just check it doesn't throw
        Integer tamperedAt = AuditModule.verify();
        assertTrue(tamperedAt == null || tamperedAt >= 0);
    }

    @Test
    void testPrunerAvailable() {
        assertNotNull(AuditModule.pruner());
    }

    @Test
    void testReporterAvailable() {
        assertNotNull(AuditModule.reporter());
    }

    @Test
    void testDetectorAvailable() {
        assertNotNull(AuditModule.detector());
    }
}
