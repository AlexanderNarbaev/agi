package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * W261 — Cognitive ReAct (Reasoning + Acting).
 *
 * <p>Inspired by ReAct (Yao et al. 2023). Interleave reasoning steps
 * (Thought) with actions (Act) and observations.
 *
 * <p>Loop:
 * - Thought: reason about current state
 * - Action: select next action
 * - Observation: observe outcome
 * - Repeat until done
 *
 * <p>CONSTITUTION VI compliance: ReAct cognitive reasoning,
 * not phenomenal consciousness claim.
 */
public final class CognitiveReAct {

    private CognitiveReAct() {}

    /** A single ReAct step. */
    public record ReActStep(
        String thought,
        String action,
        String observation,
        CognitiveGenesisProfile profile
    ) {}

    /** ReAct loop result. */
    public record ReActResult(
        List<ReActStep> steps,
        String finalAnswer,
        int iterations
    ) {}

    /** Functional interface for action executor. */
    @FunctionalInterface
    public interface ActionExecutor {
        String execute(String action, CognitiveGenesisProfile state);
    }

    /** Functional interface for thought generation. */
    @FunctionalInterface
    public interface ThoughtGenerator {
        String generate(CognitiveGenesisProfile state, int iteration, String lastObservation);
    }

    /** Functional interface for termination check. */
    @FunctionalInterface
    public interface TerminationCheck {
        boolean shouldStop(int iteration, String lastObservation);
    }

    /**
     * Run ReAct loop on a cognitive profile.
     */
    public static ReActResult run(CognitiveGenesisProfile initial,
                                    int maxIterations,
                                    ThoughtGenerator thoughtGen,
                                    ActionExecutor actionExec,
                                    TerminationCheck termination) {
        if (initial == null || maxIterations < 1) {
            return new ReActResult(new ArrayList<>(), "", 0);
        }
        List<ReActStep> steps = new ArrayList<>();
        CognitiveGenesisProfile current = initial;
        String lastObservation = "";
        int iterations = 0;
        for (int i = 0; i < maxIterations; i++) {
            iterations++;
            String thought = (thoughtGen != null) ?
                thoughtGen.generate(current, i, lastObservation) :
                "default thought " + i;
            String action = "act(" + i + ")";
            String observation = (actionExec != null) ?
                actionExec.execute(action, current) :
                "default observation " + i;
            steps.add(new ReActStep(thought, action, observation, current));
            lastObservation = observation;
            // Update profile based on observation (simple heuristic)
            current = evolveProfile(current, observation);
            // Check termination
            if (termination != null && termination.shouldStop(i, observation)) {
                break;
            }
        }
        return new ReActResult(steps, lastObservation, iterations);
    }

    private static CognitiveGenesisProfile evolveProfile(CognitiveGenesisProfile p, String observation) {
        // Simple evolution: adjust phi based on observation length
        double adjustment = Math.min(0.1, observation.length() / 1000.0);
        double newPhi = Math.max(0.0, Math.min(1.0, p.phiBinary() + adjustment));
        return new CognitiveGenesisProfile(
            newPhi, p.phiF(), p.phiR(), p.phiLinGauss(),
            p.interAgentPhi(), p.stabilityPhi(), p.crossLevelPhi(),
            p.kolmogorovK(),
            p.analogicalSimilarity(), p.conceptualExclusion(),
            p.nkEdgeOfChaosK(), p.memristorConductance(), p.lSystemComplexityRatio()
        );
    }
}
