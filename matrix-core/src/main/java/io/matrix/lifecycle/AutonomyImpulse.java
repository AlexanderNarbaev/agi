package io.matrix.lifecycle;

/**
 * Four autonomy impulses (H-J / DESIGN-08 §6): curiosity, consolidation,
 * integrity-check, share-digest. Each impulse is a unit of background
 * work the lifecycle can fire when budget permits; all four are
 * budget-gated by {@link io.matrix.budgeter.ConjugateBudgeter} and
 * cannot bypass the {@link io.matrix.ethics.EthicalFilter} (FROZEN gate,
 * CONSTITUTION IV).
 */
public enum AutonomyImpulse {
    /** Active exploration of under-sampled observation regions. */
    CURIOSITY,
    /** Memory consolidation: replay + digest (H-I / DESIGN-19). */
    CONSOLIDATION,
    /** Integrity check: hashed-checkpoint vs current. */
    INTEGRITY_CHECK,
    /** Share-digest: gossip M3→M4 (DESIGN-08 §5). */
    SHARE_DIGEST
}