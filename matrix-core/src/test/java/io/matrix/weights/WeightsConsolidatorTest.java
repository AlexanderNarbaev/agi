package io.matrix.weights;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WeightsConsolidatorTest {

    @TempDir
    Path tempDir;

    @Test
    void consolidateEmptyDir() throws Exception {
        var consolidator = new WeightsConsolidator(tempDir);
        var result = consolidator.consolidate();
        assertEquals(0, result.totalModels());
        assertEquals(0, result.totalNeurons());
    }

    @Test
    void consolidateWithFiles() throws Exception {
        Path model1 = tempDir.resolve("model1_layer0_neurons.avro");
        Path model2 = tempDir.resolve("model1_layer1_neurons.avro");
        Path model3 = tempDir.resolve("model2_layer0_neurons.avro");
        Files.write(model1, new byte[100]);
        Files.write(model2, new byte[200]);
        Files.write(model3, new byte[150]);

        var consolidator = new WeightsConsolidator(tempDir);
        var result = consolidator.consolidate();

        assertEquals(2, result.totalModels());
        assertEquals(3, result.models().stream().mapToInt(m -> m.totalLayers()).sum());
    }

    @Test
    void writeAndRead() throws Exception {
        Path model1 = tempDir.resolve("test_layer0_neurons.avro");
        Files.write(model1, new byte[100]);

        var consolidator = new WeightsConsolidator(tempDir);
        var weights = consolidator.consolidate();

        Path output = tempDir.resolve("consolidated.avro");
        consolidator.write(weights, output);

        assertTrue(Files.exists(output));
        assertTrue(Files.size(output) > 0);
    }
}
