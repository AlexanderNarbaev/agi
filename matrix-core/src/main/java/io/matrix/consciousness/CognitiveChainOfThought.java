package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * W260 — Cognitive Chain-of-Thought (CoT).
 *
 * <p>Inspired by Chain-of-Thought prompting (Wei et al. 2022). Make
 * cognitive processing explicit through a sequence of intermediate
 * "thought" steps.
 *
 * <p>Each step:
 * - Takes previous profile + step input
 * - Produces intermediate profile (thought)
 * - Builds up reasoning chain
 *
 * <p>Use cases:
 * - Multi-step cognitive reasoning
 * - Explainable cognitive decisions
 * - Decompose complex cognitive tasks
 *
 * <p>CONSTITUTION VI compliance: chain-of-thought cognitive reasoning,
 * not phenomenal consciousness claim.
 */
public final class CognitiveChainOfThought {

    private CognitiveChainOfThought() {}

    /** A single thought step. */
    public record ThoughtStep(
        int stepIndex,
        CognitiveGenesisProfile input,
        CognitiveGenesisProfile output,
        String description
    ) {}

    /** Result of chain-of-thought reasoning. */
    public record CoTResult(
        List<ThoughtStep> steps,
        CognitiveGenesisProfile finalOutput
    ) {}

    /**
     * Run a chain of thought on the input profile.
     *
     * @param input starting profile
     * @param numSteps number of reasoning steps
     * @param stepFunction applied at each step (returns intermediate profile)
     * @return CoT result with all steps and final output
     */
    public static CoTResult reason(CognitiveGenesisProfile input,
                                     int numSteps,
                                     StepFunction stepFunction) {
        if (input == null || numSteps < 1) {
            return new CoTResult(new ArrayList<>(), input);
        }
        List<ThoughtStep> steps = new ArrayList<>();
        CognitiveGenesisProfile current = input;
        for (int i = 0; i < numSteps; i++) {
            CognitiveGenesisProfile next;
            String desc;
            if (stepFunction != null) {
                next = stepFunction.apply(current, i);
                desc = stepFunction.describe(i);
            } else {
                next = defaultStep(current, i);
                desc = "default step " + i;
            }
            if (next == null) break;
            steps.add(new ThoughtStep(i, current, next, desc));
            current = next;
        }
        return new CoTResult(steps, current);
    }

    /**
     * Functional interface for step application.
     */
    @FunctionalInterface
    public interface StepFunction {
        CognitiveGenesisProfile apply(CognitiveGenesisProfile input, int stepIndex);
        default String describe(int stepIndex) { return "step " + stepIndex; }
    }

    /**
     * Default step: incremental phi adjustment.
     */
    private static CognitiveGenesisProfile defaultStep(CognitiveGenesisProfile input, int stepIndex) {
        // Each step increases phi slightly
        double newPhi = Math.min(1.0, input.phiBinary() + 0.01);
        return new CognitiveGenesisProfile(
            newPhi, newPhi, newPhi, newPhi,
            input.interAgentPhi(), input.stabilityPhi(), input.crossLevelPhi(),
            input.kolmogorovK(),
            input.analogicalSimilarity(), input.conceptualExclusion(),
            input.nkEdgeOfChaosK(), input.memristorConductance(), input.lSystemComplexityRatio()
        );
    }
}
