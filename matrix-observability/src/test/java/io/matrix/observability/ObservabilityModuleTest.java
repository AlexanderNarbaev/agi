package io.matrix.observability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ObservabilityModuleTest {

    @Test
    void testStatus() {
        String status = ObservabilityModule.status();
        assertTrue(status.contains("matrix-observability:0.1.0-T01"));
        assertTrue(status.contains(":prom=9090:"));
        assertTrue(status.contains(":grafana=3000:"));
        assertTrue(status.contains(":jaeger=16686"));
    }

    @Test
    void testPorts() {
        assertEquals(9090, ObservabilityModule.DEFAULT_PROMETHEUS_PORT);
        assertEquals(3000, ObservabilityModule.DEFAULT_GRAFANA_PORT);
        assertEquals(16686, ObservabilityModule.DEFAULT_JAEGER_PORT);
    }
}
