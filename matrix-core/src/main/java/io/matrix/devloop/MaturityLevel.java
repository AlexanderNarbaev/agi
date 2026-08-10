package io.matrix.devloop;

/**
 * Maturity level for developmental loop (SPEC-000).
 *
 * <p>MA-0: Sandbox only — no external actions, no memory writes.
 * MA-1: Local actions — file I/O, tool use, no network.
 * MA-2: Network actions — API calls, federation, still no self-modify.
 * MA-3: Self-modification — can propose χ-acts (schema changes) under gates.
 * MA-4: Autonomous — full self-modification with constitutional triggers.
 *
 * <p>Per CONSTITUTION II.2-3: training is always outside runtime.
 * MA levels control what the system can DO, not what it can learn.
 */
public enum MaturityLevel {
    MA_0_SANDBOX(0, "sandbox"),
    MA_1_LOCAL(1, "local"),
    MA_2_NETWORK(2, "network"),
    MA_3_SELF_MODIFY(3, "self-modify"),
    MA_4_AUTONOMOUS(4, "autonomous");

    private final int level;
    private final String name;

    MaturityLevel(int level, String name) {
        this.level = level;
        this.name = name;
    }

    public int level() { return level; }
    public String displayName() { return name; }

    public boolean canAct(MaturityLevel required) {
        return this.level >= required.level;
    }

    public MaturityLevel next() {
        return switch (this) {
            case MA_0_SANDBOX -> MA_1_LOCAL;
            case MA_1_LOCAL -> MA_2_NETWORK;
            case MA_2_NETWORK -> MA_3_SELF_MODIFY;
            case MA_3_SELF_MODIFY -> MA_4_AUTONOMOUS;
            case MA_4_AUTONOMOUS -> this; // ceiling
        };
    }

    public MaturityLevel previous() {
        return switch (this) {
            case MA_0_SANDBOX -> this; // floor
            case MA_1_LOCAL -> MA_0_SANDBOX;
            case MA_2_NETWORK -> MA_1_LOCAL;
            case MA_3_SELF_MODIFY -> MA_2_NETWORK;
            case MA_4_AUTONOMOUS -> MA_3_SELF_MODIFY;
        };
    }
}
