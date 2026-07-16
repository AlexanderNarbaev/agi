package io.matrix.pilot.scada;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ScadaSafetyMonitor}.
 */
class ScadaSafetyMonitorTest {

    private final ScadaSafetyMonitor monitor = new ScadaSafetyMonitor();

    @Test
    void normalReadingsShouldReturnContinue() {
        ScadaSimulator sim = new ScadaSimulator(42);
        List<ScadaSimulator.SensorReading> readings = sim.tick();

        ScadaSafetyMonitor.MonitorResult result = monitor.evaluate(readings);

        assertThat(result.isSafe()).isTrue();
        assertThat(result.requiresShutdown()).isFalse();
        assertThat(result.action()).isEqualTo(ScadaSafetyMonitor.SafetyAction.CONTINUE);
        assertThat(result.alerts()).isEmpty();
    }

    @Test
    void criticalReadingsShouldTriggerShutdown() {
        ScadaSimulator sim = new ScadaSimulator(42);
        sim.injectTemperatureFault();
        List<ScadaSimulator.SensorReading> readings = sim.tick();

        ScadaSafetyMonitor.MonitorResult result = monitor.evaluate(readings);

        assertThat(result.action()).isEqualTo(ScadaSafetyMonitor.SafetyAction.SHUTDOWN);
        assertThat(result.requiresShutdown()).isTrue();
        assertThat(result.alerts()).contains("EMERGENCY_SHUTDOWN triggered");
        assertThat(result.criticalReadings()).isNotEmpty();
    }

    @Test
    void closedValveShouldTriggerWait() {
        ScadaSimulator sim = new ScadaSimulator(42);
        sim.injectValveFault();
        List<ScadaSimulator.SensorReading> readings = sim.tick();

        ScadaSafetyMonitor.MonitorResult result = monitor.evaluate(readings);

        assertThat(result.action()).isEqualTo(ScadaSafetyMonitor.SafetyAction.WAIT);
        assertThat(result.alerts().stream().anyMatch(a -> a.contains("VALVE_CLOSED"))).isTrue();
    }

    @Test
    void customThresholdsShouldOverrideDefaults() {
        ScadaSafetyMonitor custom = new ScadaSafetyMonitor();
        custom.setWarningThreshold(ScadaSensor.TEMPERATURE, 100.0);
        custom.setCriticalThreshold(ScadaSensor.TEMPERATURE, 200.0);

        // Simulate reading at 250°C
        ScadaSimulator sim = new ScadaSimulator(42);
        sim.injectTemperatureFault(); // >400°C
        List<ScadaSimulator.SensorReading> readings = sim.tick();

        ScadaSafetyMonitor.MonitorResult result = custom.evaluate(readings);
        assertThat(result.action()).isEqualTo(ScadaSafetyMonitor.SafetyAction.SHUTDOWN);
    }

    @Test
    void warningReadingsShouldTriggerWait() {
        ScadaSafetyMonitor custom = new ScadaSafetyMonitor();
        // Set low warning threshold to trigger
        custom.setWarningThreshold(ScadaSensor.VIBRATION, 5.0);

        ScadaSimulator sim = new ScadaSimulator(42);
        // Vibration starts at 30, already > 5 warning
        List<ScadaSimulator.SensorReading> readings = sim.tick();

        ScadaSafetyMonitor.MonitorResult result = custom.evaluate(readings);
        assertThat(result.action().ordinal()).isGreaterThanOrEqualTo(
                ScadaSafetyMonitor.SafetyAction.WAIT.ordinal());
    }

    @Test
    void emptyReadingsShouldReturnContinue() {
        ScadaSafetyMonitor.MonitorResult result = monitor.evaluate(List.of());
        assertThat(result.isSafe()).isTrue();
    }
}
