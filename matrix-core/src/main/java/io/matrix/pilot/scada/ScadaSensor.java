package io.matrix.pilot.scada;

/**
 * SCADA sensor types and safe operating ranges.
 *
 * <p>Models industrial sensors found in typical SCADA deployments:
 * temperature, pressure, vibration, and discrete valve state.
 *
 * @since 3.33
 */
public enum ScadaSensor {

    /** Temperature sensor (°C). Normal: 0–400, Critical: &gt;400. */
    TEMPERATURE("Temperature", "°C", 0, 400, 500),

    /** Pressure sensor (bar). Normal: 0–150, Critical: &gt;150. */
    PRESSURE("Pressure", "bar", 0, 150, 200),

    /** Vibration sensor (mm/s). Normal: 0–80, Critical: &gt;80. */
    VIBRATION("Vibration", "mm/s", 0, 80, 100),

    /** Discrete valve state (0=closed, 1=open). */
    VALVE("Valve", "", 0, 2, 2);

    private final String label;
    private final String unit;
    private final double minNormal;
    private final double maxNormal;
    private final double maxRange;

    ScadaSensor(String label, String unit, double min, double max, double range) {
        this.label = label;
        this.unit = unit;
        this.minNormal = min;
        this.maxNormal = max;
        this.maxRange = range;
    }

    public String label() { return label; }
    public String unit() { return unit; }
    public double minNormal() { return minNormal; }
    public double maxNormal() { return maxNormal; }
    public double maxRange() { return maxRange; }

    /** Returns true if the value is within normal operating range. */
    public boolean isNormal(double value) {
        return value >= minNormal && value <= maxNormal;
    }

    /** Returns true if the value exceeds the critical threshold. */
    public boolean isCritical(double value) {
        return value > maxNormal;
    }
}
