package io.matrix.brain.runtime.graph;

import java.util.Objects;
import java.util.Random;

/**
 * Deterministic randomness for cognitive nodes, satisfying CONSTITUTION Article I.
 *
 * <p>Article I (stratified stochasticity) permits seeded randomness in the cognitive layer and
 * requires it to be reproducible. The seed is derived from the triple
 * {@code (GlobalSeed, NodeId, StepCount)} so that:</p>
 *
 * <ul>
 *   <li>the same triple always yields the same stream — a trajectory can be replayed exactly;</li>
 *   <li>two different nodes at the same step get different streams — they do not correlate;</li>
 *   <li>the same node at different steps gets different streams — exploration over time.</li>
 * </ul>
 *
 * <p>Derivation mixes the node name into the seed with a 64-bit FNV-1a hash before combining, so
 * a node named "a" and one named "b" cannot collide through truncation of {@link String#hashCode}.</p>
 *
 * <p><b>Caveat, stated because it is load-bearing.</b> Seeding makes a trajectory reproducible
 * only if the graph's <em>topology</em> is also a function of the seed and step. In a connectome
 * whose structure changes during sleep, {@code f(GlobalSeed, NodeId, StepCount)} does not by
 * itself determine the computation, and two runs will diverge after the first prune. That is why
 * {@link AuditRecord} exists — it records what was drawn, so a replay can be compared rather than
 * assumed.</p>
 */
public final class SeededRandom {

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private SeededRandom() {
    }

    /** 64-bit FNV-1a over a string. */
    public static long hash(String value) {
        Objects.requireNonNull(value, "value");
        long h = FNV_OFFSET;
        for (int i = 0; i < value.length(); i++) {
            h ^= value.charAt(i);
            h *= FNV_PRIME;
        }
        return h;
    }

    /**
     * Derives the seed for one node at one step.
     *
     * @param globalSeed the connectome-wide seed
     * @param nodeId     the node drawing randomness
     * @param stepCount  the step index within the episode
     * @return a deterministic derived seed
     */
    public static long derive(long globalSeed, String nodeId, long stepCount) {
        long nodeHash = hash(nodeId);
        // mix so that (seed, node, step) tuples do not alias across dimensions
        long mixed = globalSeed;
        mixed = mixed * 0x9E3779B97F4A7C15L + nodeHash;
        mixed ^= stepCount * 0xBF58476D1CE4E5B9L;
        return mix64(mixed);
    }

    /** SplitMix64 finaliser — good avalanche, so neighbouring seeds give unrelated streams. */
    private static long mix64(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Creates a {@link Random} stream for one node at one step. */
    public static Random stream(long globalSeed, NodeId nodeId, long stepCount) {
        return new Random(derive(globalSeed, nodeId.value(), stepCount));
    }

    /**
     * One entry in the audit trail required by Article I: which node, which step, which seed,
     * what was drawn, and what came of it.
     *
     * @param nodeId  the drawing node
     * @param step    the step index
     * @param seed    the derived seed actually used
     * @param value   the value drawn, in [0, 1)
     * @param outcome what the draw decided, in the caller's own words
     */
    public record AuditRecord(String nodeId, long step, long seed, double value, String outcome) {
        public AuditRecord {
            Objects.requireNonNull(nodeId, "nodeId");
            Objects.requireNonNull(outcome, "outcome");
            if (value < 0.0 || value >= 1.0) {
                throw new IllegalArgumentException("value must be in [0,1), got " + value);
            }
        }
    }
}
