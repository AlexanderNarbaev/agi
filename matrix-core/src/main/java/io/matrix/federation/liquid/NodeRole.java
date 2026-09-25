package io.matrix.federation.liquid;

import java.util.Objects;

/**
 * W569 — Liquid Node Roles for Federation.
 *
 * <p>Dynamic role assignment based on capability levels (L0-L7).
 * Nodes automatically promote/demote based on:
 * <ul>
 *   <li>Uptime & Stability (SLA metrics)</li>
 *   <li>Consensus Accuracy (history of correct votes)</li>
 *   <li>Knowledge Contribution (unique data provided)</li>
 * </ul>
 *
 * <h2>Role Hierarchy</h2>
 * <pre>
 *   Guardian (L7) ──── Veto power on ethics, highest weight
 *       ↑
 *   Specialist (L6) ── High weight in domain expertise
 *       ↑
 *   Adult (L5) ─────── Full voting rights
 *       ↑
 *   Learner (L3-L4) ── Local learning, limited voting
 *       ↑
 *   Infant (L0-L2) ─── Listen-only, no voting
 * </pre>
 *
 * <h2>Transition Rules</h2>
 * <p>Promotion requires meeting ALL thresholds for the target role.
 * Demotion triggers when ANY metric falls below the current role's minimum.
 */
public enum NodeRole {

    /** L0-L2: Listen-only, no voting, learning phase. */
    INFANT(0, 2, 0.0, 0.0, 0, 0.0),

    /** L3-L4: Local learning, limited voting weight. */
    LEARNER(3, 4, 0.3, 0.5, 10, 0.1),

    /** L5: Full voting rights, standard weight. */
    ADULT(5, 5, 0.6, 0.7, 50, 0.3),

    /** L6: High weight in domain expertise. */
    SPECIALIST(6, 6, 0.8, 0.85, 100, 0.5),

    /** L7: Veto power on ethics, highest weight. */
    GUARDIAN(7, 7, 0.9, 0.95, 200, 0.8);

    /** Minimum capability level for this role. */
    public final int minLevel;

    /** Maximum capability level for this role. */
    public final int maxLevel;

    /** Minimum uptime ratio [0, 1] to maintain this role. */
    public final double minUptime;

    /** Minimum consensus accuracy [0, 1] to maintain this role. */
    public final double minAccuracy;

    /** Minimum knowledge contributions to maintain this role. */
    public final int minContributions;

    /** Voting weight multiplier for this role. */
    public final double votingWeight;

    NodeRole(int minLevel, int maxLevel, double minUptime, double minAccuracy,
             int minContributions, double votingWeight) {
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.minUptime = minUptime;
        this.minAccuracy = minAccuracy;
        this.minContributions = minContributions;
        this.votingWeight = votingWeight;
    }

    /**
     * Check if a node qualifies for promotion to this role.
     *
     * @param capabilityLevel node's capability level (0-7)
     * @param uptimeRatio     uptime ratio [0, 1]
     * @param accuracy        consensus accuracy [0, 1]
     * @param contributions   knowledge contributions count
     * @return true if qualifies
     */
    public boolean qualifiesFor(int capabilityLevel, double uptimeRatio,
                                double accuracy, int contributions) {
        return capabilityLevel >= minLevel
                && capabilityLevel <= maxLevel
                && uptimeRatio >= minUptime
                && accuracy >= minAccuracy
                && contributions >= minContributions;
    }

    /**
     * Check if a node should be demoted from this role.
     *
     * @param uptimeRatio   uptime ratio [0, 1]
     * @param accuracy      consensus accuracy [0, 1]
     * @param contributions knowledge contributions count
     * @return true if should be demoted
     */
    public boolean shouldDemote(double uptimeRatio, double accuracy, int contributions) {
        return uptimeRatio < minUptime * 0.8  // 20% tolerance
                || accuracy < minAccuracy * 0.8
                || contributions < minContributions * 0.5;
    }

    /**
     * Get the appropriate role for a node based on its metrics.
     *
     * @param capabilityLevel node's capability level (0-7)
     * @param uptimeRatio     uptime ratio [0, 1]
     * @param accuracy        consensus accuracy [0, 1]
     * @param contributions   knowledge contributions count
     * @return the highest role the node qualifies for
     */
    public static NodeRole evaluate(int capabilityLevel, double uptimeRatio,
                                    double accuracy, int contributions) {
        // Check from highest to lowest
        for (int i = values().length - 1; i >= 0; i--) {
            NodeRole role = values()[i];
            if (role.qualifiesFor(capabilityLevel, uptimeRatio, accuracy, contributions)) {
                return role;
            }
        }
        return INFANT; // Default fallback
    }

    /**
     * Get the role by name (case-insensitive).
     */
    public static NodeRole fromName(String name) {
        if (name == null) return INFANT;
        for (NodeRole role : values()) {
            if (role.name().equalsIgnoreCase(name)) return role;
        }
        return INFANT;
    }

    /**
     * Check if this role can vote in consensus.
     */
    public boolean canVote() {
        return this.ordinal() >= ADULT.ordinal();
    }

    /**
     * Check if this role has veto power (Guardian only).
     */
    public boolean hasVetoPower() {
        return this == GUARDIAN;
    }

    /**
     * Check if this role can propose changes.
     */
    public boolean canPropose() {
        return this.ordinal() >= LEARNER.ordinal();
    }

    /**
     * Check if this role can modify the modulator registry.
     */
    public boolean canModifyRegistry() {
        return this.ordinal() >= ADULT.ordinal();
    }

    /**
     * Get the next role in the hierarchy (for promotion).
     *
     * @return the next higher role, or null if already at max
     */
    public NodeRole next() {
        int nextOrdinal = ordinal() + 1;
        NodeRole[] values = values();
        return nextOrdinal < values.length ? values[nextOrdinal] : null;
    }

    /**
     * Get the previous role in the hierarchy (for demotion).
     *
     * @return the next lower role, or null if already at min
     */
    public NodeRole previous() {
        int prevOrdinal = ordinal() - 1;
        return prevOrdinal >= 0 ? values()[prevOrdinal] : null;
    }

    @Override
    public String toString() {
        return String.format("%s(L%d-L%d, weight=%.1f)",
                name(), minLevel, maxLevel, votingWeight);
    }
}
