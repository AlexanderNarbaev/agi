package io.matrix.neuron;

import java.util.Random;

/**
 * DESIGN-52 — Tabular SARSA (Rummery 1994).
 * On-policy TD(0) variant. Q(s,a) ← Q(s,a) + α[r + γ·Q(s',a') − Q(s,a)]
 * where a' is the action actually taken by the current policy.
 * Pure function (CONSTITUTION I).
 */
public final class SARSA {

    private SARSA() {}

    /** Pure-function update with chosen next action. */
    public static double[][] update(double[][] q, int state, int action,
                                    double reward, int nextState,
                                    int nextAction, double alpha, double gamma) {
        if (q == null) throw new IllegalArgumentException("null");
        if (alpha < 0 || alpha > 1) {
            throw new IllegalArgumentException("α ∈ [0,1]");
        }
        if (gamma < 0 || gamma > 1) {
            throw new IllegalArgumentException("γ ∈ [0,1]");
        }
        double oldQ = q[state][action];
        double nextQ = (q[nextState] != null
                && nextAction < q[nextState].length)
                ? q[nextState][nextAction] : 0;
        double newQ = oldQ + alpha * (reward + gamma * nextQ - oldQ);
        double[][] result = new double[q.length][];
        for (int i = 0; i < q.length; i++) result[i] = q[i].clone();
        result[state][action] = newQ;
        return result;
    }
}
