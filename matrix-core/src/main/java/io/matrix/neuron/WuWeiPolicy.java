package io.matrix.neuron;

import java.util.Random;

/**
 * RUN 472 — WuWeiPolicy: Taoist no-op action primitive (DESIGN-60).
 *
 * <p>Implements "wu wei" (无为, "effortless action" / "non-action") as an
 * engineering discipline for action selection. When surprise is below
 * threshold, the policy outputs a no-op — letting the system idle.
 *
 * <h2>Why this matters</h2>
 * <p>Active inference (Friston 2010) prescribes actions that minimize
 * expected free energy. But constant action-selection wastes resources.
 * Wu-wei adds a meta-policy: if the world is already in a low-surprise
 * state (F below threshold τ), the optimal action is the null action
 * (idling). This is computationally cheap, conserves energy, and matches
 * the biological observation that many brain regions are tonically
 * active at baseline but only engage action-selection circuits when
 * prediction error exceeds threshold (Friston et al. 2017).
 *
 * <h2>Novel combination (per W60+ research)</h2>
 * Combines:
 * <ul>
 *   <li>FreeEnergyLoss (Fr) as surprise signal</li>
 *   <li>Taoist non-action (wu wei) as meta-policy</li>
 *   <li>Threshold-based gating inspired by GW theory ignition threshold</li>
 * </ul>
 *
 * <h2>CONSTITUTION I</h2>
 * Pure function. No RNG, no wall-clock.
 */
public final class WuWeiPolicy {

    private WuWeiPolicy() {}

    /**
     * One decision: act or don't act.
     *
     * @param freeEnergy     current variational free energy (surprise)
     * @param threshold      action threshold (default 0.5)
     * @param numActions      number of candidate actions (0 = only no-op)
     * @return Decision with action index and whether to act
     */
    public static Decision decide(double freeEnergy, double threshold, int numActions) {
        if (threshold <= 0) {
            throw new IllegalArgumentException("threshold must be > 0");
        }
        if (numActions < 0) {
            throw new IllegalArgumentException("numActions must be ≥ 0");
        }
        // Wu wei: when surprise is below threshold, do nothing
        boolean shouldAct = freeEnergy >= threshold;
        // Action index: 0 means no-op, 1..numActions means act
        int actionIdx;
        if (!shouldAct || numActions == 0) {
            actionIdx = 0; // no-op
        } else {
            // Choose an action based on which would minimize free energy.
            // For simplicity, use a hash of free energy modulo numActions.
            // In real implementation, would enumerate candidate actions
            // and select the one minimizing expected free energy.
            actionIdx = 1 + (int) (Math.abs(freeEnergy * 100) % numActions);
        }
        return new Decision(actionIdx, shouldAct, freeEnergy, threshold);
    }

    /**
     * Sample-based decision: try a few candidate actions and pick the one
     * with lowest expected free energy.
     *
     * @param currentFE      current free energy
     * @param candidates     list of candidate actions (e.g. token IDs, directions)
     * @param fePerAction    expected free energy for each candidate (precomputed)
     * @param threshold      action threshold
     * @return Decision with the best candidate or 0 (no-op)
     */
    public static Decision selectBestAction(double currentFE, int[] candidates,
                                              double[] fePerAction, double threshold) {
        if (candidates == null || fePerAction == null) {
            throw new IllegalArgumentException("null candidates/fePerAction");
        }
        if (candidates.length != fePerAction.length) {
            throw new IllegalArgumentException("length mismatch");
        }
        // Wu wei: if current surprise is below threshold, no-op
        if (currentFE < threshold) {
            return new Decision(0, false, currentFE, threshold);
        }
        // Otherwise, pick candidate with lowest expected free energy
        int bestIdx = 0;
        double bestFE = Double.POSITIVE_INFINITY;
        for (int i = 0; i < candidates.length; i++) {
            if (fePerAction[i] < bestFE) {
                bestFE = fePerAction[i];
                bestIdx = i;
            }
        }
        return new Decision(candidates[bestIdx], true, currentFE, threshold);
    }

    /**
     * Decision returned by WuWeiPolicy.
     */
    public record Decision(
            int actionIndex,
            boolean shouldAct,
            double freeEnergy,
            double threshold) {
        public boolean isNoOp() {
            return actionIndex == 0;
        }

        public double excessSurprise() {
            return Math.max(0.0, freeEnergy - threshold);
        }
    }
}
