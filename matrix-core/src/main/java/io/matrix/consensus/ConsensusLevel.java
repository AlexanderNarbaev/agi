package io.matrix.consensus;

/**
 * Consensus decision levels based on impact scope.
 *
 * <p>Ref: L2_Iteraction_protocol.md §6.4
 */
public enum ConsensusLevel {
    LEVEL_0(0, "Single FNL", "LobeMediator alone"),
    LEVEL_1(1, "Single cluster", "ClusterMediator + notify InstanceMediator"),
    LEVEL_2(2, "Single instance", "InstanceMediator + user override possible"),
    LEVEL_3(3, "All instances", "Global Mediator Council, 2/3 threshold");

    private final int level;
    private final String scope;
    private final String authority;

    ConsensusLevel(int level, String scope, String authority) {
        this.level = level;
        this.scope = scope;
        this.authority = authority;
    }

    public int level() { return level; }
    public String scope() { return scope; }
    public String authority() { return authority; }
}
