package io.matrix.evolution;

import io.matrix.simulation.*;
import io.matrix.neuron.DecisionTree;

import java.util.Random;

/**
 * Evaluates fitness of a chromosome (which encodes 4 direction-neuron trees)
 * by running multiple simulation trials.
 */
public class FitnessFn {

    private final int worldWidth;
    private final int worldHeight;
    private final int wallCount;
    private final int resourceCount;
    private final int maxSteps;
    private final int trials;
    private final Random worldRng;

    public FitnessFn(int worldWidth, int worldHeight, int wallCount, int resourceCount,
                     int maxSteps, int trials, Random worldRng) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.wallCount = wallCount;
        this.resourceCount = resourceCount;
        this.maxSteps = maxSteps;
        this.trials = trials;
        this.worldRng = worldRng;
    }

    /**
     * Evaluates fitness by running {@code trials} simulations and averaging the raw score.
     */
    public long evaluate(Chromosome nCh, Chromosome sCh, Chromosome wCh, Chromosome eCh) {
        long totalScore = 0;
        for (int t = 0; t < trials; t++) {
            totalScore += runTrial(nCh, sCh, wCh, eCh);
        }
        return totalScore / trials;
    }

    private long runTrial(Chromosome nCh, Chromosome sCh, Chromosome wCh, Chromosome eCh) {
        World world = new World(worldWidth, worldHeight, wallCount, resourceCount, worldRng);
        AgentBrain brain = new AgentBrain(
                nCh.tree(), sCh.tree(), wCh.tree(), eCh.tree());
        AgentBody body = new AgentBody(new Position(worldWidth / 2, worldHeight / 2), 100);
        SimulationRunner runner = new SimulationRunner(world, body, brain, maxSteps, worldRng);
        SimulationResult result = runner.run();
        return result.rawScore();
    }
}
