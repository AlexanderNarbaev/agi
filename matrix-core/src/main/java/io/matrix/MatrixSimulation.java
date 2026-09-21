package io.matrix;

import io.matrix.evolution.EvolutionLoop;
import io.matrix.evolution.FitnessFn;
import io.matrix.neuron.DecisionTree;
import io.matrix.simulation.*;

import java.util.Random;

/**
 * CLI entry point for Phase 0 — GridWorld agent training via genetic algorithm.
 */
public final class MatrixSimulation {

    private static final int WORLD_WIDTH = 20;
    private static final int WORLD_HEIGHT = 20;
    private static final int WALL_COUNT = 15;
    private static final int RESOURCE_COUNT = 10;
    private static final int MAX_STEPS = 200;
    private static final int TRIALS = 3;
    private static final int GENERATIONS = 30;
    private static final int POPULATION_SIZE = 10;
    private static final int K = 18;

    public static void main(String[] args) {
        System.out.println("MATRIX Phase 0 — GridWorld Agent Training");
        System.out.println("=========================================");

        Random worldRng = new Random(42);
        FitnessFn fitnessFn = new FitnessFn(WORLD_WIDTH, WORLD_HEIGHT, WALL_COUNT,
                RESOURCE_COUNT, MAX_STEPS, TRIALS, worldRng);

        EvolutionLoop loop = new EvolutionLoop(GENERATIONS, POPULATION_SIZE, K,
                fitnessFn, new Random(42));

        System.out.println("World: " + WORLD_WIDTH + "x" + WORLD_HEIGHT
                + ", walls=" + WALL_COUNT + ", resources=" + RESOURCE_COUNT);
        System.out.println("Max steps: " + MAX_STEPS + ", trials: " + TRIALS);
        System.out.println("Generations: " + GENERATIONS + ", population: " + POPULATION_SIZE);
        System.out.println("Neuron inputs (k): " + K);
        System.out.println();

        loop.run();

        var bestHistory = loop.bestFitnessHistory();
        var avgHistory = loop.avgFitnessHistory();

        System.out.println(" Generation │ Best Fitness │ Avg Fitness");
        System.out.println("────────────┼───────────────┼─────────────");
        for (int g = 0; g < bestHistory.size(); g++) {
            System.out.printf(" %10d │ %13d │ %11d%n", g, bestHistory.get(g), avgHistory.get(g));
        }
        System.out.println();

        AgentBrain brain = loop.bestBrain();
        System.out.println("Best brain after evolution:");
        System.out.println("  North neuron: " + summarize(brain.nNeuron()));
        System.out.println("  South neuron: " + summarize(brain.sNeuron()));
        System.out.println("  West  neuron: " + summarize(brain.wNeuron()));
        System.out.println("  East  neuron: " + summarize(brain.eNeuron()));
        System.out.println();

        System.out.println("Running demo simulation...");
        World demoWorld = new World(WORLD_WIDTH, WORLD_HEIGHT, WALL_COUNT, RESOURCE_COUNT,
                new Random(123));
        AgentBody demoBody = new AgentBody(new Position(10, 10), 100);
        SimulationRunner demoRunner = new SimulationRunner(demoWorld, demoBody, brain,
                MAX_STEPS, new Random(456));
        SimulationResult demoResult = demoRunner.run();

        System.out.println("  Steps: " + demoResult.steps());
        System.out.println("  Food collected: " + demoResult.foodCollected());
        System.out.println("  Wall collisions: " + demoResult.wallCollisions());
        System.out.println("  Final energy: " + demoResult.finalEnergy());
        System.out.println("  Raw score: " + demoResult.rawScore());
    }

    private static String summarize(DecisionTree tree) {
        int leaves = io.matrix.evolution.TreeWalker.countLeaves(tree);
        int splits = io.matrix.evolution.TreeWalker.countSplits(tree);
        return "leaves=" + leaves + ", splits=" + splits + ", depth=" + tree.depth();
    }
}
