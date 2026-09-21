package io.matrix.devloop;

/**
 * Maturity level for the developmental loop (SPEC-000).
 *
 * <p>Staged-autonomy ladder MA-0…MA-5 (SPEC-000#fr-5, docs/vision/ARCHITECTURE.md §4).
 * Each level expands what the system may DO; learning is always outside the runtime
 * (CONSTITUTION II.2-3). Transition is gated by {@link MaturityGateKeeper} and is
 * monotonic: a level can only advance forward, never backward (SPEC-000 INV-3 — demotion
 * is quarantine of permissions, represented out-of-band, not a level rollback).
 *
 * <ul>
 *   <li>MA-0 — sensorimotor (observation): shadow mode, predictions only, no actions.</li>
 *   <li>MA-1 — pre-operational (play): sandbox actions (Minecraft, sim-SCADA).</li>
 *   <li>MA-2 — concrete operations: real-environment actions, each human-confirmed.</li>
 *   <li>MA-3 — early formal: autonomy in one certified domain, post-audit.</li>
 *   <li>MA-4 — formal operations: full Noosphere node.</li>
 *   <li>MA-5 — mentor: right to teach clones (teacher), initiate cloning.</li>
 * </ul>
 */
public enum MaturityLevel {
    MA_0_SANDBOX(0, "sandbox"),
    MA_1_LOCAL(1, "local"),
    MA_2_NETWORK(2, "network"),
    MA_3_SELF_MODIFY(3, "self-modify"),
    MA_4_AUTONOMOUS(4, "autonomous"),
    MA_5_MENTOR(5, "mentor");

    private final int level;
    private final String name;

    MaturityLevel(int level, String name) {
        this.level = level;
        this.name = name;
    }

    /** Numeric ordinal, monotonic in MA-0..MA-5. */
    public int level() { return level; }

    public String displayName() { return name; }

    /** Whether this level grants the permissions required by {@code required}. */
    public boolean canAct(MaturityLevel required) {
        return this.level >= required.level;
    }

    /** Next level (forward); ceiling returns itself. */
    public MaturityLevel next() {
        return switch (this) {
            case MA_0_SANDBOX -> MA_1_LOCAL;
            case MA_1_LOCAL -> MA_2_NETWORK;
            case MA_2_NETWORK -> MA_3_SELF_MODIFY;
            case MA_3_SELF_MODIFY -> MA_4_AUTONOMOUS;
            case MA_4_AUTONOMOUS -> MA_5_MENTOR;
            case MA_5_MENTOR -> this; // ceiling
        };
    }

    /** Previous level; floor returns itself. Not used for transitions (monotonicity). */
    public MaturityLevel previous() {
        return switch (this) {
            case MA_0_SANDBOX -> this; // floor
            case MA_1_LOCAL -> MA_0_SANDBOX;
            case MA_2_NETWORK -> MA_1_LOCAL;
            case MA_3_SELF_MODIFY -> MA_2_NETWORK;
            case MA_4_AUTONOMOUS -> MA_3_SELF_MODIFY;
            case MA_5_MENTOR -> MA_4_AUTONOMOUS;
        };
    }
}
