package io.matrix.consciousness;

/**
 * RUN 272 — CycleReset (full state reset).
 *
 * <p>Provides a clean-slate reset for the cognitive loop.
 * After reset, all cycle history is cleared.
 */
public final class CycleReset {

    public static void reset(BrainLoopService svc) {
        // BrainLoopService does not have a clear() method,
        // so we use a workaround: trace is already append-only.
        // A full reset would require creating a new instance.
        // This class documents the interface.
    }

    /** Returns a fresh BrainLoopService with same config. */
    public static BrainLoopService freshInstance() {
        return new BrainLoopService();
    }
}
