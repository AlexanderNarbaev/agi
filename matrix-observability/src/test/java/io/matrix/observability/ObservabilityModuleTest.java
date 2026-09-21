package io.matrix.observability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ObservabilityModuleTest {

    @Test
    void testStatus() {
        String status = ObservabilityModule.status();
        assertTrue(status.startsWith("matrix-observability:0.1.0-T09"));
    }

    @Test
    void testHasStandardMetrics() {
        assertNotNull(ObservabilityModule.metrics().get("matrix_analyze_requests_total"));
        assertNotNull(ObservabilityModule.metrics().get("matrix_analyze_duration_ms"));
        assertNotNull(ObservabilityModule.metrics().get("matrix_explain_lookups_total"));
        assertNotNull(ObservabilityModule.metrics().get("matrix_federation_nodes"));
        assertNotNull(ObservabilityModule.metrics().get("matrix_audit_chain_integrity"));
    }

    @Test
    void testHasHealthChecks() {
        assertTrue(ObservabilityModule.health().probeCount() >= 1);
    }

    @Test
    void testPorts() {
        assertEquals(9090, ObservabilityModule.DEFAULT_PROMETHEUS_PORT);
        assertEquals(3000, ObservabilityModule.DEFAULT_GRAFANA_PORT);
        assertEquals(16686, ObservabilityModule.DEFAULT_JAEGER_PORT);
    }
}
