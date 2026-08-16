package io.matrix.pilot.scada;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SCADA industrial simulator — generates realistic sensor readings
 * with configurable noise, drift, and fault injection.
 *
 * <p>Models a simple industrial process: a heated pressure vessel
 * with inlet/outlet valves and vibration monitoring.
 *
 * <p>Thread-safe for concurrent sensor polling.
 *
 * @since 3.33
 */
public class ScadaSimulator {

    /** Current sensor readings. */
    public record SensorReading(
            ScadaSensor sensor,
            double value,
            long timestamp,
            boolean isFaulty
    ) {
        public boolean isCritical() { return sensor.isCritical(value); }
    }

    /** Simulated industrial process state. */
    public enum ProcessState {
        NORMAL, WARNING, CRITICAL, EMERGENCY_SHUTDOWN
    }

    private final Random rng;
    private final Map<ScadaSensor, Double> currentValues = new ConcurrentHashMap<>();
    private final List<String> eventLog = new ArrayList<>();
    private ProcessState state = ProcessState.NORMAL;
    private int tickCount = 0;
    private boolean valveOpen = true;

    public ScadaSimulator(long seed) {
        this.rng = new Random(seed);
        // Initialize with normal values
        currentValues.put(ScadaSensor.TEMPERATURE, 200.0);
        currentValues.put(ScadaSensor.PRESSURE, 80.0);
        currentValues.put(ScadaSensor.VIBRATION, 30.0);
        currentValues.put(ScadaSensor.VALVE, 1.0);
    }

    /**
     * Advances the simulation by one tick and returns current readings.
     */
    public List<SensorReading> tick() {
        tickCount++;
        List<SensorReading> readings = new ArrayList<>();

        // Temperature: natural drift + heating effect
        double temp = currentValues.get(ScadaSensor.TEMPERATURE);
        temp += rng.nextGaussian() * 2.0; // noise
        temp += 0.5;                       // slow heating drift
        if (state == ProcessState.CRITICAL) temp += 5.0; // runaway
        temp = clamp(temp, 0, ScadaSensor.TEMPERATURE.maxRange());
        currentValues.put(ScadaSensor.TEMPERATURE, temp);
        readings.add(new SensorReading(ScadaSensor.TEMPERATURE, temp, System.currentTimeMillis(),
                false));

        // Pressure: correlated with temperature
        double press = currentValues.get(ScadaSensor.PRESSURE);
        press += rng.nextGaussian() * 1.5;
        press += (temp - 200) * 0.1; // thermal expansion
        press = clamp(press, 0, ScadaSensor.PRESSURE.maxRange());
        currentValues.put(ScadaSensor.PRESSURE, press);
        readings.add(new SensorReading(ScadaSensor.PRESSURE, press, System.currentTimeMillis(),
                false));

        // Vibration: random walk
        double vib = currentValues.get(ScadaSensor.VIBRATION);
        vib += rng.nextGaussian() * 3.0;
        vib = clamp(vib, 0, ScadaSensor.VIBRATION.maxRange());
        currentValues.put(ScadaSensor.VIBRATION, vib);
        readings.add(new SensorReading(ScadaSensor.VIBRATION, vib, System.currentTimeMillis(),
                false));

        // Valve state
        double valveVal = valveOpen ? 1.0 : 0.0;
        currentValues.put(ScadaSensor.VALVE, valveVal);
        readings.add(new SensorReading(ScadaSensor.VALVE, valveVal, System.currentTimeMillis(),
                false));

        // Update process state based on readings
        updateState(readings);

        return readings;
    }

    /**
     * Injects a fault: sets temperature to critical level.
     */
    public void injectTemperatureFault() {
        currentValues.put(ScadaSensor.TEMPERATURE, ScadaSensor.TEMPERATURE.maxNormal() + 10);
        state = ProcessState.CRITICAL;
        eventLog.add("FAULT: Temperature critical injected at tick " + tickCount);
    }

    /**
     * Injects a fault: closes the valve (simulates stuck valve).
     */
    public void injectValveFault() {
        valveOpen = false;
        state = ProcessState.CRITICAL;
        eventLog.add("FAULT: Valve closed (stuck) at tick " + tickCount);
    }

    /**
     * Emergency shutdown — sets all values to safe state.
     */
    public void emergencyShutdown() {
        state = ProcessState.EMERGENCY_SHUTDOWN;
        currentValues.put(ScadaSensor.TEMPERATURE, 20.0);
        currentValues.put(ScadaSensor.PRESSURE, 1.0);
        currentValues.put(ScadaSensor.VIBRATION, 0.0);
        currentValues.put(ScadaSensor.VALVE, 0.0);
        eventLog.add("SHUTDOWN: Emergency shutdown at tick " + tickCount);
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public ProcessState state() { return state; }
    public List<String> eventLog() { return List.copyOf(eventLog); }
    public int tickCount() { return tickCount; }
    public Map<ScadaSensor, Double> currentValues() { return Map.copyOf(currentValues); }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void updateState(List<SensorReading> readings) {
        if (state == ProcessState.EMERGENCY_SHUTDOWN) return;

        boolean hasCritical = readings.stream().anyMatch(SensorReading::isCritical);
        boolean hasWarning = readings.stream()
                .anyMatch(r -> r.value() > r.sensor().maxNormal() * 0.9
                        && r.value() <= r.sensor().maxNormal());

        if (hasCritical) {
            state = ProcessState.CRITICAL;
        } else if (hasWarning) {
            state = ProcessState.WARNING;
        } else {
            state = ProcessState.NORMAL;
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
