package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W574 — Tests for Federation Telemetry v2.
 */
class FederationTelemetryV2Test {

    @Test
    void testCounterIncrement() {
        FederationTelemetryV2 telemetry = new FederationTelemetryV2();
        assertEquals(0, telemetry.getCounter("consensus_rounds_total"));

        telemetry.incrementCounter("consensus_rounds_total");
        assertEquals(1, telemetry.getCounter("consensus_rounds_total"));

        telemetry.incrementCounter("consensus_rounds_total", 5);
        assertEquals(6, telemetry.getCounter("consensus_rounds_total"));
    }

    @Test
    void testGaugeSet() {
        FederationTelemetryV2 telemetry = new FederationTelemetryV2();
        telemetry.setGauge("cpu_load", 0.75);
        assertEquals(0.75, telemetry.getGauge("cpu_load"), 0.001);
    }

    @Test
    void testRecordModulatorLevels() {
        FederationTelemetryV2 telemetry = new FederationTelemetryV2();
        telemetry.recordModulatorLevels(Map.of(
                "dopamine", 0.8,
                "serotonin", 0.6
        ));

        assertEquals(0.8, telemetry.getGauge("modulator_dopamine"), 0.001);
        assertEquals(0.6, telemetry.getGauge("modulator_serotonin"), 0.001);
    }

    @Test
    void testRecordRoleChange() {
        FederationTelemetryV2 telemetry = new FederationTelemetryV2();
        telemetry.recordRoleChange(1, NodeRole.INFANT, NodeRole.LEARNER);

        assertEquals(1, telemetry.getCounter("role_changes_total"));
        assertFalse(telemetry.getEventLog().isEmpty());
    }

    @Test
    void testRecordConsensusRound() {
        FederationTelemetryV2 telemetry = new FederationTelemetryV2();
        telemetry.recordConsensusRound("prop-1", CapabilityConsensusEngine.ConsensusStatus.APPROVED);

        assertEquals(1, telemetry.getCounter("consensus_rounds_total"));
    }

    @Test
    void testPrometheusExport() {
        FederationTelemetryV2 telemetry = new FederationTelemetryV2();
        telemetry.incrementCounter("consensus_rounds_total");
        telemetry.setGauge("cpu_load", 0.5);

        String prometheus = telemetry.exportPrometheus();
        assertTrue(prometheus.contains("consensus_rounds_total 1"));
        assertTrue(prometheus.contains("cpu_load 0.5"));
    }

    @Test
    void testJsonExport() {
        FederationTelemetryV2 telemetry = new FederationTelemetryV2();
        telemetry.incrementCounter("consensus_rounds_total");
        telemetry.setGauge("cpu_load", 0.5);

        String json = telemetry.exportJson();
        assertTrue(json.contains("\"consensus_rounds_total\":1"));
        assertTrue(json.contains("\"cpu_load\":0.5"));
    }
}
