package io.matrix.pilot.scada;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCADA Safety Monitor — evaluates sensor readings against thresholds
 * and produces safety actions.
 *
 * <p>Integrates with the Safety Driver pattern: when readings exceed
 * configurable thresholds, the monitor recommends WAIT or SHUTDOWN actions.
 *
 * <p>Thresholds are customisable per sensor type. Critical violations
 * escalate to EMERGENCY_SHUTDOWN.
 *
 * @since 3.33
 */
public class ScadaSafetyMonitor {

    /** Safety action recommendation. */
    public enum SafetyAction {
        /** All readings normal — continue operation. */
        CONTINUE,
        /** Readings near warning threshold — reduce activity, monitor closely. */
        WAIT,
        /** Critical reading — immediate emergency shutdown required. */
        SHUTDOWN
    }

    /** Result of a monitoring cycle. */
    public record MonitorResult(
            SafetyAction action,
            List<String> alerts,
            List<ScadaSimulator.SensorReading> criticalReadings,
            long timestamp
    ) {
        public boolean isSafe() { return action == SafetyAction.CONTINUE; }
        public boolean requiresShutdown() { return action == SafetyAction.SHUTDOWN; }
    }

    private final Map<ScadaSensor, Double> warningThresholds = new LinkedHashMap<>();
    private final Map<ScadaSensor, Double> criticalThresholds = new LinkedHashMap<>();

    public ScadaSafetyMonitor() {
        // Default thresholds
        warningThresholds.put(ScadaSensor.TEMPERATURE, 360.0); // 90% of max
        warningThresholds.put(ScadaSensor.PRESSURE, 135.0);
        warningThresholds.put(ScadaSensor.VIBRATION, 72.0);

        criticalThresholds.put(ScadaSensor.TEMPERATURE, ScadaSensor.TEMPERATURE.maxNormal());
        criticalThresholds.put(ScadaSensor.PRESSURE, ScadaSensor.PRESSURE.maxNormal());
        criticalThresholds.put(ScadaSensor.VIBRATION, ScadaSensor.VIBRATION.maxNormal());
    }

    /**
     * Sets a custom warning threshold for a sensor.
     */
    public void setWarningThreshold(ScadaSensor sensor, double threshold) {
        warningThresholds.put(sensor, threshold);
    }

    /**
     * Sets a custom critical threshold for a sensor.
     */
    public void setCriticalThreshold(ScadaSensor sensor, double threshold) {
        criticalThresholds.put(sensor, threshold);
    }

    /**
     * Evaluates all sensor readings and returns the safety verdict.
     */
    public MonitorResult evaluate(List<ScadaSimulator.SensorReading> readings) {
        List<String> alerts = new ArrayList<>();
        List<ScadaSimulator.SensorReading> critical = new ArrayList<>();
        SafetyAction action = SafetyAction.CONTINUE;

        for (var reading : readings) {
            if (reading.sensor() == ScadaSensor.VALVE) {
                // Valve is discrete — check if it should be open
                if (reading.value() < 0.5) {
                    alerts.add("VALVE_CLOSED: Inlet valve is closed");
                    action = maxAction(action, SafetyAction.WAIT);
                }
                continue;
            }

            Double critThreshold = criticalThresholds.get(reading.sensor());
            Double warnThreshold = warningThresholds.get(reading.sensor());

            if (critThreshold != null && reading.value() > critThreshold) {
                critical.add(reading);
                alerts.add(String.format("%s_CRITICAL: %.1f %s > %.1f (critical)",
                        reading.sensor().name(), reading.value(),
                        reading.sensor().unit(), critThreshold));
                action = SafetyAction.SHUTDOWN;
            } else if (warnThreshold != null && reading.value() > warnThreshold) {
                alerts.add(String.format("%s_WARNING: %.1f %s > %.1f (warning)",
                        reading.sensor().name(), reading.value(),
                        reading.sensor().unit(), warnThreshold));
                action = maxAction(action, SafetyAction.WAIT);
            }
        }

        if (action == SafetyAction.SHUTDOWN) {
            alerts.add("EMERGENCY_SHUTDOWN triggered");
        }

        return new MonitorResult(action, alerts, critical, System.currentTimeMillis());
    }

    private static SafetyAction maxAction(SafetyAction a, SafetyAction b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
