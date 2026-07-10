package io.matrix.reasoning;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

/**
 * Boolean Reasoning Chain (BRC) — multi-step logical reasoning in boolean space.
 *
 * <p>Processes input through a chain of reasoning steps, each applying
 * a NeuronLayer to transform the boolean vector. Supports convergence
 * detection and early stopping.
 *
 * <p>Architecture:
 * <pre>
 *   Input → Step 0 → Step 1 → ... → Step N → Output
 *            ↓          ↓              ↓
 *         [check]    [check]        [check]
 *         converge   converge       converge
 * </pre>
 *
 * <p>Key properties:
 * <ul>
 *   <li>Deterministic: same input → same output</li>
 *   <li>Interpretable: each step is a readable boolean transformation</li>
 *   <li>Convergent: stops when output stabilizes</li>
 *   <li>Composable: steps can be added/removed</li>
 * </ul>
 */
public final class BrcChain {

    private final List<BrcStep> steps;
    private final int maxSteps;
    private final boolean earlyStopping;

    /**
     * Creates a BRC chain.
     *
     * @param steps reasoning steps
     * @param maxSteps maximum number of steps (0 = unlimited)
     * @param earlyStopping whether to stop on convergence
     */
    public BrcChain(List<BrcStep> steps, int maxSteps, boolean earlyStopping) {
        this.steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        this.maxSteps = Math.max(0, maxSteps);
        this.earlyStopping = earlyStopping;
    }

    /**
     * Evaluates the chain on the given input.
     *
     * @param input boolean input vector
     * @param vectorWidth width of the boolean vector
     * @return final BrcState after processing
     */
    public BrcState evaluate(BitSet input, int vectorWidth) {
        BrcState state = new BrcState(input, vectorWidth);
        int stepCount = 0;

        for (BrcStep step : steps) {
            if (maxSteps > 0 && stepCount >= maxSteps) {
                break;
            }

            state = step.apply(state);
            stepCount++;

            if (earlyStopping && state.isConverged()) {
                break;
            }
        }

        return state;
    }

    /**
     * Evaluates the chain with detailed step-by-step results.
     *
     * @param input boolean input vector
     * @param vectorWidth width of the boolean vector
     * @return list of states after each step
     */
    public List<BrcState> evaluateDetailed(BitSet input, int vectorWidth) {
        List<BrcState> results = new ArrayList<>();
        BrcState state = new BrcState(input, vectorWidth);
        results.add(state);
        int stepCount = 0;

        for (BrcStep step : steps) {
            if (maxSteps > 0 && stepCount >= maxSteps) {
                break;
            }

            state = step.apply(state);
            results.add(state);
            stepCount++;

            if (earlyStopping && state.isConverged()) {
                break;
            }
        }

        return results;
    }

    /**
     * Returns the number of steps in the chain.
     */
    public int stepCount() {
        return steps.size();
    }

    /**
     * Returns the maximum steps.
     */
    public int maxSteps() {
        return maxSteps;
    }

    /**
     * Returns whether early stopping is enabled.
     */
    public boolean isEarlyStopping() {
        return earlyStopping;
    }

    /**
     * Returns the steps.
     */
    public List<BrcStep> steps() {
        return steps;
    }

    /**
     * Builder for BrcChain.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<BrcStep> steps = new ArrayList<>();
        private int maxSteps = 0;
        private boolean earlyStopping = true;

        public Builder addStep(BrcStep step) {
            steps.add(Objects.requireNonNull(step, "step"));
            return this;
        }

        public Builder maxSteps(int maxSteps) {
            this.maxSteps = Math.max(0, maxSteps);
            return this;
        }

        public Builder earlyStopping(boolean earlyStopping) {
            this.earlyStopping = earlyStopping;
            return this;
        }

        public BrcChain build() {
            if (steps.isEmpty()) {
                throw new IllegalStateException("At least one step is required");
            }
            return new BrcChain(steps, maxSteps, earlyStopping);
        }
    }

    @Override
    public String toString() {
        return "BrcChain{steps=%d, maxSteps=%d, earlyStopping=%s}".formatted(
            steps.size(), maxSteps, earlyStopping
        );
    }
}
