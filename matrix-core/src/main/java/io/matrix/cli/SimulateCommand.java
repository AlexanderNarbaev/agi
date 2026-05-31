package io.matrix.cli;

import io.matrix.evolution.EvolutionLoop;
import io.matrix.evolution.FitnessFn;
import io.matrix.observability.MatrixMetrics;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Random;

@Command(name = "simulate", description = "Run GridWorld agent training via genetic algorithm")
public class SimulateCommand implements Runnable {

    @Option(names = {"-g", "--generations"}, defaultValue = "100", description = "Number of generations")
    int generations;

    @Option(names = {"-p", "--population"}, defaultValue = "20", description = "Population size")
    int population;

    @Option(names = {"-k", "--inputs"}, defaultValue = "18", description = "Neuron inputs (k)")
    int k;

    @Option(names = {"--seed"}, defaultValue = "42", description = "Random seed")
    long seed;

    @Inject
    Logger log;

    @Inject
    MatrixMetrics metrics;

    @Override
    public void run() {
        log.infof("GridWorld simulation: %d generations × %d agents × k=%d",
                generations, population, k);

        Random rng = new Random(seed);
        FitnessFn fitnessFn = new FitnessFn(20, 20, 15, 10, 200, 3, rng);
        EvolutionLoop loop = new EvolutionLoop(generations, population, k, fitnessFn, metrics, rng);

        loop.run();

        var bestHistory = loop.bestFitnessHistory();
        var avgHistory = loop.avgFitnessHistory();
        for (int g = 0; g < bestHistory.size(); g += Math.max(1, bestHistory.size() / 10)) {
            log.infof("  Gen %3d | best=%d avg=%d", g, bestHistory.get(g), avgHistory.get(g));
        }

        log.info("GridWorld simulation complete");
    }
}
