package io.matrix.pilot.scada;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ScadaSimulator}.
 */
class ScadaSimulatorTest {

    @Test
    void initialReadingsShouldBeInNormalRange() {
        ScadaSimulator sim = new ScadaSimulator(42);
        List<ScadaSimulator.SensorReading> readings = sim.tick();

        assertThat(readings).hasSize(4);
        for (var r : readings) {
            assertThat(r.timestamp()).isPositive();
            assertThat(r.isFaulty()).isFalse();
        }
    }

    @Test
    void temperatureShouldIncreaseOverTime() {
        ScadaSimulator sim = new ScadaSimulator(42);
        sim.tick(); // take one reading
        double first = sim.currentValues().get(ScadaSensor.TEMPERATURE);

        for (int i = 0; i < 50; i++) sim.tick();
        double later = sim.currentValues().get(ScadaSensor.TEMPERATURE);

        assertThat(later).isGreaterThan(first);
    }

    @Test
    void tickShouldIncrementCounter() {
        ScadaSimulator sim = new ScadaSimulator(42);
        assertThat(sim.tickCount()).isEqualTo(0);

        sim.tick();
        sim.tick();
        sim.tick();
        assertThat(sim.tickCount()).isEqualTo(3);
    }

    @Test
    void injectTemperatureFaultShouldSetCriticalState() {
        ScadaSimulator sim = new ScadaSimulator(42);
        sim.tick(); // get to normal
        assertThat(sim.state()).isEqualTo(ScadaSimulator.ProcessState.NORMAL);

        sim.injectTemperatureFault();
        assertThat(sim.state()).isEqualTo(ScadaSimulator.ProcessState.CRITICAL);

        // Next reading should reflect critical state
        List<ScadaSimulator.SensorReading> readings = sim.tick();
        assertThat(readings.stream().anyMatch(ScadaSimulator.SensorReading::isCritical)).isTrue();
    }

    @Test
    void injectValveFaultShouldCloseValve() {
        ScadaSimulator sim = new ScadaSimulator(42);
        sim.injectValveFault();
        assertThat(sim.state()).isEqualTo(ScadaSimulator.ProcessState.CRITICAL);

        // Need a tick for currentValues to reflect the fault
        sim.tick();
        double valve = sim.currentValues().get(ScadaSensor.VALVE);
        assertThat(valve).isEqualTo(0.0);
    }

    @Test
    void emergencyShutdownShouldResetAllValues() {
        ScadaSimulator sim = new ScadaSimulator(42);
        sim.injectTemperatureFault();
        sim.emergencyShutdown();

        assertThat(sim.state()).isEqualTo(ScadaSimulator.ProcessState.EMERGENCY_SHUTDOWN);
        assertThat(sim.currentValues().get(ScadaSensor.TEMPERATURE)).isEqualTo(20.0);
        assertThat(sim.currentValues().get(ScadaSensor.PRESSURE)).isEqualTo(1.0);
    }

    @Test
    void eventLogShouldRecordFaults() {
        ScadaSimulator sim = new ScadaSimulator(42);
        sim.injectTemperatureFault();
        sim.emergencyShutdown();

        assertThat(sim.eventLog()).hasSize(2);
        assertThat(sim.eventLog().get(0)).contains("FAULT");
        assertThat(sim.eventLog().get(1)).contains("SHUTDOWN");
    }

    @Test
    void deterministicSeedShouldProduceSameReadings() {
        ScadaSimulator s1 = new ScadaSimulator(12345);
        ScadaSimulator s2 = new ScadaSimulator(12345);

        s1.tick();
        s2.tick();

        assertThat(s1.currentValues()).isEqualTo(s2.currentValues());
    }

    @Test
    void sensorIsNormalShouldWork() {
        assertThat(ScadaSensor.TEMPERATURE.isNormal(200)).isTrue();
        assertThat(ScadaSensor.TEMPERATURE.isNormal(450)).isFalse();
        assertThat(ScadaSensor.PRESSURE.isCritical(160)).isTrue();
        assertThat(ScadaSensor.PRESSURE.isCritical(100)).isFalse();
    }
}
