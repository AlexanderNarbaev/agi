package io.matrix.neuron;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * DESIGN-46 — Tabular Q-Learning (Watkins 1989).
 * Model-free off-policy TD(0) learning. Q(s,a) ← Q(s,a) + α[r + γ·max_a' Q(s',a') − Q(s,a)].
 * Pure function (CONSTITUTION I) — seeded exploration.
 */
public final class QLearning {

    private QLearning() {}

    public record StepResult(
            double[][] newQ,
            int action,
            double reward
    ) {}

    /**
     * Single Q-Learning update. Pure function: same Q + (s,a,r,s') → same newQ.
     */
    public static StepResult update(double[][] q, int state, int action,
                                   double reward, int nextState,
                                   double alpha, double gamma) {
        if (q == null) throw new IllegalArgumentException("null");
        if (alpha < 0 || alpha > 1) {
            throw new IllegalArgumentException("α ∈ [0,1]");
        }
        if (gamma < 0 || gamma > 1) {
            throw new IllegalArgumentException("γ ∈ [0,1]");
        }
        double maxNext = 0;
        if (q[nextState] != null) {
            for (double v : q[nextState]) maxNext = Math.max(maxNext, v);
        }
        double oldQ = q[state][action];
        double newQ = oldQ + alpha * (reward + gamma * maxNext - oldQ);
        double[][] result = copyQ(q);
        result[state][action] = newQ;
        return new StepResult(result, action, reward);
    }

    /** Epsilon-greedy action selection. */
    public static int selectAction(double[][] q, int state, double epsilon,
                                   long seed) {
        if (q == null) throw new IllegalArgumentException("null");
        if (q[state] == null || q[state].length == 0) return 0;
        Random rng = new Random(seed);
        if (rng.nextDouble() < epsilon) {
            return rng.nextInt(q[state].length);
        }
        // Greedy
        int best = 0;
        double bestV = q[state][0];
        for (int a = 1; a < q[state].length; a++) {
            if (q[state][a] > bestV) {
                bestV = q[state][a];
                best = a;
            }
        }
        return best;
    }

    /** Train Q-table for nEpisodes with env simulator. */
    public static double[][] train(int nStates, int nActions, int nEpisodes,
                                   double alpha, double gamma, double epsilon,
                                   long seed,
                                   StepFn stepFn) {
        if (stepFn == null) throw new IllegalArgumentException("null");
        double[][] q = new double[nStates][nActions];
        Random rng = new Random(seed);
        for (int ep = 0; ep < nEpisodes; ep++) {
            int state = rng.nextInt(nStates);
            for (int t = 0; t < 100; t++) {  // max steps per episode
                int action = selectAction(q, state, epsilon, rng.nextLong());
                var step = stepFn.step(state, action);
                var result = update(q, state, action, step.reward(),
                        step.nextState(), alpha, gamma);
                q = result.newQ();
                state = step.nextState();
            }
        }
        return q;
    }

    public record EnvStep(int nextState, double reward) {}

    @FunctionalInterface
    public interface StepFn {
        EnvStep step(int state, int action);
    }

    private static double[][] copyQ(double[][] q) {
        double[][] copy = new double[q.length][];
        for (int i = 0; i < q.length; i++) {
            copy[i] = q[i].clone();
        }
        return copy;
    }
}
