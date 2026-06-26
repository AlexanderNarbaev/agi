package io.matrix.cli;

import io.matrix.cauldron.CauldronProtocol;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Random;
import java.util.concurrent.Callable;

@Command(
        name = "cauldron",
        mixinStandardHelpOptions = true,
        description = "Pilot #5: Cauldron — autonomous FNL generation via genetic algorithm")
public class CauldronDemoCommand implements Callable<Integer> {

    @Option(names = {"-s", "--seed"}, description = "Random seed", defaultValue = "42")
    long seed;

    @Option(names = {"-t", "--task"}, description = "Task name to evolve for", defaultValue = "navigation")
    String taskName;

    @Option(names = {"-p", "--population"}, description = "Subpopulation size", defaultValue = "30")
    int population;

    @Option(names = {"-g", "--generations"}, description = "Number of generations", defaultValue = "50")
    int generations;

    @Option(names = {"--repeat"}, description = "Number of FNLs to generate", defaultValue = "3")
    int repeat;

    @Override
    public Integer call() {
        var rng = new Random(seed);

        System.out.println("=== Pilot #5: Cauldron — Autonomous FNL Generation ===");
        System.out.println("Task: " + taskName);
        System.out.println("Generating " + repeat + " FNL packages...");
        System.out.println();

        for (int i = 1; i <= repeat; i++) {
            CauldronProtocol cauldron = new CauldronProtocol(rng);
            var result = cauldron.evolveForTask(taskName + "-v" + i);

            if (result.state() == CauldronProtocol.CauldronState.COMPLETED) {
                var pkg = cauldron.packageResult(result, taskName + "-v" + i,
                        taskName.toUpperCase(), "demo-instance");
                System.out.printf("FNL #%d: %s | fitness=%d | gens=%d | accuracy=%.2f%n",
                        i, pkg.name(), result.bestFitness(), result.generations(),
                        pkg.accuracy());
            } else {
                System.out.printf("FNL #%d: FAILED (state=%s)%n", i, result.state());
            }
        }

        System.out.println("\nPilot #5 complete. FNLs auto-generated without manual intervention.");
        return 0;
    }
}
