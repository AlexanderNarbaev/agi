package io.matrix.memory;

/**
 * RUN 161 — MemoryHierarchyTier (M0/M1/M2 levels).
 *
 * <p>Three-tier memory hierarchy per SPEC-011:
 * <ul>
 *   <li><b>M0</b>: working memory — millisecond lifespan, in-process.
 *       Just current cycle's activations.</li>
 *   <li><b>M1</b>: episodic memory — second-to-minute lifespan, between
 *       cycles. TR-consolidated from M2.</li>
 *   <li><b>M2</b>: long-term memory — persistent across sessions.
 *       SQLite-backed or REM-consolidated.</li>
 * </ul>
 *
 * <p>Tier transitions are deterministic (TR/REM). No background
 * randomness.
 */
public enum MemoryHierarchyTier {
    /** Working memory — current cycle only. */
    M0,
    /** Episodic memory — between cognitive cycles. */
    M1,
    /** Long-term memory — persistent across sessions. */
    M2
}
