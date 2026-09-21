package io.matrix.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApiModuleTest {

    @Test
    void testLiveness() {
        String status = ApiModule.liveness();
        assertTrue(status.startsWith("matrix-api-gateway:"));
        assertTrue(status.endsWith(":live"));
    }

    @Test
    void testVersionNotEmpty() {
        assertFalse(ApiModule.VERSION.isEmpty());
        assertEquals("0.1.0-T01", ApiModule.VERSION);
    }
}
