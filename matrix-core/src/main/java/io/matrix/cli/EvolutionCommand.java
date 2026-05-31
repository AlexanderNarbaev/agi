package io.matrix.cli;

import io.matrix.minecraft.*;
import io.matrix.observability.MatrixMetrics;
import io.matrix.neuron.DecisionTree;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Command(name = "evolution", description = "Run Minecraft survival evolution experiment")
public class EvolutionCommand implements Runnable {

    @Option(names = {"-g", "--generations"}, defaultValue = "200", description = "Number of generations")
    int generations;

    @Option(names = {"-p", "--population"}, defaultValue = "50", description = "Population size")
    int population;

    @Option(names = {"-s", "--steps"}, defaultValue = "500", description = "Max steps per agent")
    int maxSteps;

    @Option(names = {"-w", "--world-size"}, defaultValue = "50", description = "World size (NxN)")
    int worldSize;

    @Option(names = {"--seed"}, defaultValue = "42", description = "Random seed")
    long seed;

    @Inject
    Logger log;

    @Inject
    MatrixMetrics metrics;

    @Override
    public void run() {
        log.infof("Evolution: %d generations × %d agents × %d steps, world=%dx%d",
                generations, population, maxSteps, worldSize, worldSize);

        Random rng = new Random(seed);
        List<NeuralBrain> brains = new ArrayList<>();
        for (int i = 0; i < population; i++) {
            brains.add(new NeuralBrain(new Random(rng.nextLong())));
        }

        List<Double> fitnesses = new ArrayList<>();
        double bestFitness = 0;
        SurvivalRunner.SurvivalResult bestResult = null;

        for (int gen = 0; gen < generations; gen++) {
            fitnesses.clear();
            double totalFitness = 0;
            double genBest = 0;
            SurvivalRunner.SurvivalResult genBestResult = null;

            for (int i = 0; i < brains.size(); i++) {
                BlockWorld world = new BlockWorld(worldSize, worldSize, new Random(rng.nextLong()));
                BlockAgent agent = new BlockAgent(
                        new BlockWorld.Position(worldSize / 2, worldSize * 2 / 3 - 2));

                metrics.survivalRun();
                SurvivalRunner runner = new SurvivalRunner(world, agent, brains.get(i),
                        maxSteps, metrics, new Random(rng.nextLong()));
                var result = runner.run();

                double fitness = result.score();
                fitnesses.add(fitness);
                totalFitness += fitness;
                if (fitness > genBest) {
                    genBest = fitness;
                    genBestResult = result;
                }
                if (fitness > bestFitness) {
                    bestFitness = fitness;
                    bestResult = result;
                }
            }

            metrics.evolutionGeneration();
            metrics.fitnessBest((long) genBest);
            metrics.fitnessAvg((long) (totalFitness / population));

            if (gen % 20 == 0 || gen == generations - 1) {
                log.infof("Gen %3d | best=%.1f avg=%.2f | %s",
                        gen, genBest, totalFitness / population,
                        genBestResult != null ? genBestResult.toString() : "");
            }

            List<NeuralBrain> nextGen = new ArrayList<>();
            for (int i = 0; i < population / 2; i++) {
                int p1 = tournamentSelect(fitnesses, rng);
                int p2 = tournamentSelect(fitnesses, rng);
                DecisionTree move = rng.nextBoolean()
                        ? brains.get(p1).moveTree() : brains.get(p2).moveTree();
                DecisionTree mine = rng.nextBoolean()
                        ? brains.get(p1).mineTree() : brains.get(p2).mineTree();
                DecisionTree craft = rng.nextBoolean()
                        ? brains.get(p1).craftTree() : brains.get(p2).craftTree();
                DecisionTree eat = rng.nextBoolean()
                        ? brains.get(p1).eatTree() : brains.get(p2).eatTree();
                nextGen.add(new NeuralBrain(move, mine, craft, eat, eat));
                nextGen.add(brains.get(p1));
            }
            while (nextGen.size() < population) {
                nextGen.add(new NeuralBrain(new Random(rng.nextLong())));
            }
            brains = nextGen;
        }

        log.infof("Evolution complete: bestFitness=%.1f blocks=%d crafted=%d tool=%s",
                bestFitness,
                bestResult != null ? bestResult.blocksMined() : 0,
                bestResult != null ? bestResult.itemsCrafted() : 0,
                bestResult != null ? bestResult.finalTool().toString() : "NONE");
    }

    private static int tournamentSelect(List<Double> fitnesses, Random rng) {
        int t1 = rng.nextInt(fitnesses.size());
        int t2 = rng.nextInt(fitnesses.size());
        return fitnesses.get(t1) >= fitnesses.get(t2) ? t1 : t2;
    }
}
