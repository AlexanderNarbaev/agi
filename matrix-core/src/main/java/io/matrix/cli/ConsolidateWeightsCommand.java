package io.matrix.cli;

import io.matrix.weights.WeightsConsolidator;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * CLI command to consolidate all pretrained model weights into a single
 * unified Avro file.
 *
 * <p>Usage: {@code matrix consolidate-weights [--dir /path/to/pretrained]}
 */
@CommandLine.Command(name = "consolidate-weights",
        description = "Consolidate all pretrained model weights into a single unified file")
public class ConsolidateWeightsCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"--dir", "-d"},
            description = "Path to pretrained models directory",
            defaultValue = "models/pretrained")
    private String dir;

    @CommandLine.Option(names = {"--output", "-o"},
            description = "Output file path",
            defaultValue = "models/pretrained/consolidated_weights.avro")
    private String output;

    @Override
    public Integer call() throws Exception {
        var consolidator = new WeightsConsolidator(Path.of(dir));
        var weights = consolidator.consolidate();
        consolidator.write(weights, Path.of(output));

        System.out.println("Consolidated weights written to: " + output);
        System.out.println("  Models: " + weights.totalModels());
        System.out.println("  Total neurons: " + weights.totalNeurons());
        System.out.println("  Total layers: " + weights.models().stream()
                .mapToInt(WeightsConsolidator.UnifiedWeights::totalLayers).sum());
        return 0;
    }
}
