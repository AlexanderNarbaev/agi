package io.matrix.consciousness;

/**
 * RUN 303 — CycleAdmission (admission control).
 *
 * <p>Decides whether to admit a cycle based on current
 * system load (arousal, saturation).
 */
public final class CycleAdmission {

    private final double maxArousal;
    private final int maxCyclesPerWindow;
    private int windowCycles = 0;

    public CycleAdmission(double maxArousal, int maxCyclesPerWindow) {
        this.maxArousal = maxArousal;
        this.maxCyclesPerWindow = maxCyclesPerWindow;
    }

    /** Returns true if cycle should be admitted. */
    public boolean admit(double currentArousal) {
        if (currentArousal >= maxArousal) return false;
        if (windowCycles >= maxCyclesPerWindow) return false;
        windowCycles++;
        return true;
    }

    public void resetWindow() { windowCycles = 0; }
    public int windowCycles() { return windowCycles; }
}
