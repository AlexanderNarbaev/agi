package io.matrix.cauldron;

import io.matrix.evolution.EvolutionLoop;
import io.matrix.evolution.FitnessFn;
import io.matrix.noosphere.FnlPackage;
import io.matrix.simulation.AgentBrain;
import io.matrix.simulation.SimulationResult;
import io.matrix.simulation.SimulationRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Cauldron Protocol — automatic generation of new FNLs via genetic algorithm.
 *
 * <p>When the system encounters a new task type, Cauldron runs an evolution
 * loop to create an optimized FNL. The result is packaged for Noosphere
 * publication.
 *
 * <p>Ref: L5_Cauldren.md §2, L8_Roadmap.md §3.5
 */
public class CauldronProtocol {

    public enum CauldronState { IDLE, EVOLVING, COMPLETED, FAILED }

    public record CauldronResult(
            CauldronState state,
            AgentBrain bestBrain,
            long bestFitness,
            int generations,
            String summary
    ) {
        public static CauldronResult completed(AgentBrain brain, long fitness, int gens) {
            return new CauldronResult(CauldronState.COMPLETED, brain, fitness, gens,
                    "FNL evolved: fitness=" + fitness + ", generations=" + gens);
        }

        public static CauldronResult failed(String reason) {
            return new CauldronResult(CauldronState.FAILED, null, 0, 0, reason);
        }
    }

    private static final int DEFAULT_GENERATIONS = 30;
    private static final int DEFAULT_POPULATION = 10;
    private static final int DEFAULT_K = 18;

    private final Random rng;
    private final List<String> cauldronLog = new ArrayList<>();
    private CauldronState state = CauldronState.IDLE;

    public CauldronProtocol(Random rng) {
        this.rng = rng;
    }

    /**
     * Runs the Cauldron to evolve a new FNL for a given task specification.
     *
     * @param worldWidth  simulation world width
     * @param worldHeight simulation world height
     * @param wallCount   number of walls
     * @param resourceCount number of resources
     * @param maxSteps    max simulation steps per trial
     * @param trials      number of trials per fitness evaluation
     * @param generations number of GA generations
     * @param populationSize GA population size
     * @param k           neuron input bits
     * @return the Cauldron result
     */
    public CauldronResult evolve(int worldWidth, int worldHeight,
                                  int wallCount, int resourceCount,
                                  int maxSteps, int trials,
                                  int generations, int populationSize, int k) {
        state = CauldronState.EVOLVING;
        cauldronLog.add("CAULDRON:START w=" + worldWidth + "x" + worldHeight
                + " gen=" + generations + " pop=" + populationSize);

        try {
            var fitnessFn = new FitnessFn(worldWidth, worldHeight, wallCount,
                    resourceCount, maxSteps, trials, new Random(rng.nextLong()));
            var loop = new EvolutionLoop(generations, populationSize, k,
                    fitnessFn, new Random(rng.nextLong()));
            loop.run();

            long bestFitness = loop.bestFitnessHistory().stream()
                    .mapToLong(Long::longValue).max().orElse(0);
            AgentBrain brain = loop.bestBrain();

            state = CauldronState.COMPLETED;
            cauldronLog.add("CAULDRON:DONE fitness=" + bestFitness);

            return CauldronResult.completed(brain, bestFitness, generations);
        } catch (Exception e) {
            state = CauldronState.FAILED;
            cauldronLog.add("CAULDRON:FAILED " + e.getMessage());
            return CauldronResult.failed(e.getMessage());
        }
    }

    /**
     * Quick Cauldron run with defaults for a 10x10 environment.
     */
    public CauldronResult evolveForTask(String taskName) {
        int complexity = taskComplexity(taskName);
        int worldSize = 10 + complexity * 5;
        int walls = 3 + complexity * 3;
        int resources = 2 + complexity * 2;
        int gens = DEFAULT_GENERATIONS + complexity * 10;

        return evolve(worldSize, worldSize, walls, resources,
                50 + complexity * 25, 2 + complexity, gens,
                DEFAULT_POPULATION, DEFAULT_K);
    }

    /**
     * Creates an FnlPackage from a Cauldron result.
     */
    public FnlPackage packageResult(CauldronResult result, String taskName,
                                      String type, String authorInstanceId) {
        if (result.state() != CauldronState.COMPLETED) return null;

        return FnlPackage.builder()
                .name(taskName.replace(" ", "_") + "_FNL")
                .type(type)
                .version("1.0.0")
                .authorInstanceId(authorInstanceId)
                .accuracy(result.bestFitness() / 1000.0)
                .generation(result.generations())
                .description("Evolved FNL for task: " + taskName)
                .tags("cauldron", type.toLowerCase(), taskName.toLowerCase())
                .certified(false)
                .build();
    }

    public CauldronState state() { return state; }

    public List<String> cauldronLog() { return List.copyOf(cauldronLog); }

    private int taskComplexity(String taskName) {
        String lower = taskName.toLowerCase();
        if (lower.contains("complex") || lower.contains("difficult")) return 3;
        if (lower.contains("medium")) return 2;
        return 1;
    }
}
