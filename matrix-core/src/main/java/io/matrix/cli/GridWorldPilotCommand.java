package io.matrix.cli;

import io.matrix.evolution.*;
import io.matrix.neuron.DecisionTree;
import io.matrix.observability.MatrixMetrics;
import io.matrix.simulation.*;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

@Command(name = "pilot-gridworld", description = "Run GridWorld pilot: train agent, export metrics and logic")
public class GridWorldPilotCommand implements Runnable {

    @Option(names = {"-g", "--generations"}, defaultValue = "200", description = "Generations")
    int generations;

    @Option(names = {"-p", "--population"}, defaultValue = "30", description = "Population size")
    int population;

    @Option(names = {"-k", "--inputs"}, defaultValue = "18", description = "Neuron inputs (k)")
    int k;

    @Option(names = {"--seed"}, defaultValue = "42", description = "Random seed")
    long seed;

    @Option(names = {"-o", "--output"}, defaultValue = "output/pilot-gridworld.json", description = "Output JSON path")
    String outputPath;

    @Option(names = {"-t", "--trials"}, defaultValue = "3", description = "Trials per fitness eval")
    int trials;

    @Option(names = {"--survival-threshold"}, defaultValue = "0", description = "Min energy for survival")
    int survivalThreshold;

    @Inject
    Logger log;

    @Inject
    MatrixMetrics metrics;

    @Override
    public void run() {
        log.infof("MATRIX Pilot #1 — GridWorld Agent Training");
        log.infof("  Generations: %d | Population: %d | k: %d | Seed: %d", generations, population, k, seed);
        log.infof("  World: 20×20, 15 walls, 10 resources, 200 max steps, %d trials", trials);

        Random rng = new Random(seed);
        FitnessFn fitnessFn = new FitnessFn(20, 20, 15, 10, 200, trials, rng);
        EvolutionLoop loop = new EvolutionLoop(generations, population, k, fitnessFn, metrics, rng);

        log.info("Starting evolution...");
        long startMs = System.currentTimeMillis();
        loop.run();
        long elapsedMs = System.currentTimeMillis() - startMs;

        var bestHistory = loop.bestFitnessHistory();
        var avgHistory = loop.avgFitnessHistory();

        // Collect per-generation data
        List<Map<String, Object>> genData = new ArrayList<>();
        for (int g = 0; g < bestHistory.size(); g++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("generation", g);
            entry.put("best", bestHistory.get(g));
            entry.put("avg", avgHistory.get(g));
            genData.add(entry);
        }

        long initialBest = bestHistory.get(0);
        long finalBest = bestHistory.get(bestHistory.size() - 1);
        long finalAvg = avgHistory.get(bestHistory.size() - 1);
        long improvement = finalBest - initialBest;

        // Extract best agent logic
        AgentBrain bestBrain = loop.bestBrain();
        Map<String, Object> bestLogic = new LinkedHashMap<>();
        bestLogic.put("north", describeTree(bestBrain.nNeuron()));
        bestLogic.put("south", describeTree(bestBrain.sNeuron()));
        bestLogic.put("west", describeTree(bestBrain.wNeuron()));
        bestLogic.put("east", describeTree(bestBrain.eNeuron()));

        // Run demo simulation with best agent
        Map<String, Object> demo = runDemo(bestBrain);

        // Build output
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("pilot", "GridWorld #1");
        output.put("version", "1.2.0");
        output.put("timestamp", java.time.Instant.now().toString());
        output.put("config", Map.of(
                "generations", generations,
                "population", population,
                "k", k,
                "seed", seed,
                "trials", trials,
                "worldWidth", 20,
                "worldHeight", 20,
                "walls", 15,
                "resources", 10,
                "maxSteps", 200
        ));
        output.put("results", Map.of(
                "initialBest", initialBest,
                "finalBest", finalBest,
                "finalAvg", finalAvg,
                "improvement", improvement,
                "elapsedMs", elapsedMs,
                "generationsPerSecond", generations * 1000.0 / Math.max(elapsedMs, 1)
        ));
        output.put("bestLogic", bestLogic);
        output.put("generations", genData);
        output.put("demo", demo);

        try {
            Path out = Path.of(outputPath);
            Files.createDirectories(out.getParent());
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(out.toFile(), output);
            log.infof("Results saved to: %s", out.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to write output", e);
        }

        log.infof("=== GridWorld Pilot Complete ===");
        log.infof("  Fitness: %d → %d (+%d)", initialBest, finalBest, improvement);
        log.infof("  Time: %.1fs (%.1f gen/s)", elapsedMs / 1000.0, generations * 1000.0 / elapsedMs);
        log.infof("  Demo — food: %d, collisions: %d, score: %d",
                demo.get("food"), demo.get("collisions"), demo.get("score"));

        double survivalPct = finalBest > 0 ? Math.min(100, finalBest * 100.0 / (200 * trials)) : 0;
        log.infof("  Survival estimate: %.1f%%", survivalPct);
    }

    private Map<String, Object> runDemo(AgentBrain brain) {
        Random demoRng = new Random(123);
        World world = new World(20, 20, 15, 10, demoRng);
        AgentBody body = new AgentBody(new Position(10, 10), 100);
        SimulationRunner runner = new SimulationRunner(world, body, brain, 200, demoRng);
        SimulationResult result = runner.run();

        Map<String, Object> demo = new LinkedHashMap<>();
        demo.put("steps", result.steps());
        demo.put("food", result.foodCollected());
        demo.put("collisions", result.wallCollisions());
        demo.put("initialEnergy", result.initialEnergy());
        demo.put("finalEnergy", result.finalEnergy());
        demo.put("score", result.rawScore());
        return demo;
    }

    private static Map<String, Object> describeTree(DecisionTree tree) {
        int leafCount = TreeWalker.countLeaves(tree);
        List<String> allPaths = new ArrayList<>();
        for (int i = 0; i < leafCount; i++) {
            allPaths.add(TreeWalker.pathBits(tree, i).toString());
        }
        Map<String, Object> desc = new LinkedHashMap<>();
        desc.put("leaves", leafCount);
        desc.put("splits", TreeWalker.countSplits(tree));
        desc.put("depth", tree.depth());
        desc.put("paths", allPaths);
        return desc;
    }

    /**
     * Reads tree paths as bit strings for interpretability.
     */
    public static List<String> readPaths(DecisionTree tree) {
        int leafCount = TreeWalker.countLeaves(tree);
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < leafCount; i++) {
            paths.add(TreeWalker.pathBits(tree, i).toString());
        }
        return paths;
    }
}
