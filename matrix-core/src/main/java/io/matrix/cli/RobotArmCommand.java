package io.matrix.cli;

import io.matrix.pilot.PyBulletBridge;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "robot-arm",
        mixinStandardHelpOptions = true,
        description = "Train MPDT neuron to control a 2-DOF robot arm (Pilot #4)")
public class RobotArmCommand implements Callable<Integer> {

    @Option(names = {"-g", "--generations"},
            description = "Number of generations",
            defaultValue = "100")
    int generations;

    @Option(names = {"-p", "--population"},
            description = "Population size",
            defaultValue = "30")
    int population;

    @Option(names = {"-k", "--inputs"},
            description = "Neuron input count",
            defaultValue = "12")
    int k;

    @Option(names = {"-s", "--script"},
            description = "Path to robot_arm_sim.py",
            defaultValue = "scripts/robot_arm_sim.py")
    String scriptPath;

    @Override
    public Integer call() throws Exception {
        if (!Files.exists(Path.of(scriptPath))) {
            System.err.println("Script not found: " + scriptPath);
            return 1;
        }

        System.out.println("=== Pilot #4: Robot Arm Control ===");
        System.out.println("Generations: " + generations);
        System.out.println("Population: " + population);
        System.out.println("Neuron k: " + k);
        System.out.println();

        int exitCode = new ProcessBuilder(
                "python3", scriptPath,
                "--generations", String.valueOf(generations),
                "--population", String.valueOf(population))
                .inheritIO()
                .start()
                .waitFor();

        return exitCode;
    }
}
