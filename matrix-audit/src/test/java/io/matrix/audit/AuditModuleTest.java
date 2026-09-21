package io.matrix.audit;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuditModuleTest {

    @Test
    void testStatus() {
        String status = AuditModule.status();
        assertTrue(status.startsWith("matrix-audit:0.1.0-T01:hash="));
        assertTrue(status.endsWith("SHA-256"));
    }

    @Test
    void testHashAlgorithm() {
        assertEquals("SHA-256", AuditModule.HASH_ALGORITHM);
    }
}
