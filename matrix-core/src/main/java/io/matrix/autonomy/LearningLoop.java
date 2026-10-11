package io.matrix.autonomy;

import io.matrix.brain.BrainCycle;

/**
 * W35.1 — anti-corruption seam between the autonomy engine and an LLM-backed learning loop.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Relocating the LLM-serving classes out of {@code matrix-core} surfaced a coupling that a
 * package move alone cannot solve. {@link SelfImprovingEngine} holds a concrete
 * {@code BrainLearningLoop}, which constructs a Qwen ONNX model and therefore cannot live in
 * the core. But {@code SelfImprovingEngine} itself is constructed by
 * {@code OvernightRunner} in {@code matrix-brain-runtime}, and Runtime sits BELOW Gateway in
 * the dependency direction:
 *
 * <pre>Core  &larr;  Runtime  &larr;  Gateway</pre>
 *
 * <p>So moving {@code BrainLearningLoop} to the Gateway left the core importing upward from
 * the Runtime. The three ways out were to make Runtime depend on Gateway (forbidden), to
 * duplicate the engine (two engines that drift), or to invert the dependency with an
 * interface. This is the third option: the core states what it needs, and the Gateway
 * supplies it.
 *
 * <h2>Why it is deliberately small</h2>
 *
 * <p>The interface contains exactly the three methods {@link SelfImprovingEngine} calls —
 * {@link #talk}, {@link #learn}, {@link #close}. Anything larger would drag the LLM's surface
 * back toward the core and re-create the coupling under a different name. A seam that mirrors
 * the dependency it replaces is not a seam, it is a rename.
 *
 * <h2>What this buys</h2>
 *
 * <p>CONSTITUTION Article I is now satisfied in both directions at once: the deterministic core
 * contains no ONNX and no LLM-serving class, and the Runtime does not depend on the Gateway.
 * Neither constraint had to be relaxed to satisfy the other.
 */
public interface LearningLoop {

    /**
     * Exchange one message with the learning loop.
     *
     * @param message the utterance to answer
     * @return the cycle result, whose {@code reply()} the caller reads
     */
    BrainCycle.CycleResult talk(String message);

    /**
     * Run one learning pass.
     *
     * @return the number of facts newly learned, which may be zero
     */
    int learn();

    /** Release any model resources held by the loop. */
    void close();
}