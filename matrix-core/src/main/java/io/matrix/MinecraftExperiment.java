package io.matrix;

import io.matrix.minecraft.*;
import io.matrix.neuron.DecisionTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * MATRIX Survival Experiment — эволюция нейросети в Minecraft-песочнице.
 *
 * <p>Генетический алгоритм обучает NeuralBrain выживать в BlockWorld.
 * Фитнес = добытые блоки + созданные предметы + выживание + уровень инструмента.
 */
public final class MinecraftExperiment {

    private static final int WORLD_SIZE = 40;
    private static final int MAX_STEPS = 200;
    private static final int GENERATIONS = 30;
    private static final int POPULATION_SIZE = 20;

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("  MATRIX Minecraft Survival Experiment");
        System.out.println("=".repeat(60));
        System.out.println("  World: " + WORLD_SIZE + "x" + WORLD_SIZE);
        System.out.println("  Max steps: " + MAX_STEPS);
        System.out.println("  Generations: " + GENERATIONS);
        System.out.println("  Population: " + POPULATION_SIZE);
        System.out.println();

        Random rng = new Random(42);
        List<NeuralBrain> population = new ArrayList<>();
        List<Double> fitnesses = new ArrayList<>();

        for (int i = 0; i < POPULATION_SIZE; i++) {
            population.add(new NeuralBrain(new Random(rng.nextLong())));
        }

        double bestFitness = 0;
        NeuralBrain bestBrain = null;
        SurvivalRunner.SurvivalResult bestResult = null;

        for (int gen = 0; gen < GENERATIONS; gen++) {
            fitnesses.clear();
            double totalFitness = 0;
            double genBest = 0;
            SurvivalRunner.SurvivalResult genBestResult = null;

            for (int i = 0; i < population.size(); i++) {
                BlockWorld world = new BlockWorld(WORLD_SIZE, WORLD_SIZE,
                        new Random(rng.nextLong()));
                BlockAgent agent = new BlockAgent(
                        new BlockWorld.Position(WORLD_SIZE / 2, WORLD_SIZE * 2 / 3 - 2));

                SurvivalRunner runner = new SurvivalRunner(world, agent,
                        population.get(i), MAX_STEPS, new Random(rng.nextLong()));
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
                    bestBrain = population.get(i);
                    bestResult = result;
                }
            }

            double avgFitness = totalFitness / population.size();

            System.out.printf("  Gen %2d | best=%.1f avg=%.1f | %s%n",
                    gen, genBest, avgFitness,
                    genBestResult != null ? genBestResult.toString() : "");

            List<NeuralBrain> nextGen = new ArrayList<>();
            for (int i = 0; i < POPULATION_SIZE / 2; i++) {
                int p1 = tournamentSelect(fitnesses, rng);
                int p2 = tournamentSelect(fitnesses, rng);

                DecisionTree move = rng.nextBoolean()
                        ? population.get(p1).moveTree() : population.get(p2).moveTree();
                DecisionTree mine = rng.nextBoolean()
                        ? population.get(p1).mineTree() : population.get(p2).mineTree();
                DecisionTree craft = rng.nextBoolean()
                        ? population.get(p1).craftTree() : population.get(p2).craftTree();
                DecisionTree eat = rng.nextBoolean()
                        ? population.get(p1).eatTree() : population.get(p2).eatTree();

                nextGen.add(new NeuralBrain(move, mine, craft, eat, eat));
                nextGen.add(population.get(p1));
            }

            while (nextGen.size() < POPULATION_SIZE) {
                nextGen.add(new NeuralBrain(new Random(rng.nextLong())));
            }

            population = nextGen;
        }

        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("  Best Result After Evolution");
        System.out.println("=".repeat(60));
        System.out.println("  Fitness: " + bestFitness);
        if (bestResult != null) {
            System.out.println("  Survival: " + bestResult);
            System.out.println("  Blocks mined: " + bestResult.blocksMined());
            System.out.println("  Items crafted: " + bestResult.itemsCrafted());
            System.out.println("  Final tool: " + bestResult.finalTool());
        }
        System.out.println();
        System.out.println("  EXPERIMENT COMPLETE");
    }

    private static int tournamentSelect(List<Double> fitnesses, Random rng) {
        int t1 = rng.nextInt(fitnesses.size());
        int t2 = rng.nextInt(fitnesses.size());
        return fitnesses.get(t1) >= fitnesses.get(t2) ? t1 : t2;
    }
}
