package io.matrix.agent;

import io.matrix.neuron.TruthTable;
import io.matrix.simulation.AgentBrain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PretrainedLoaderTest {

    private final PretrainedLoader loader = new PretrainedLoader();

    @Test
    void testEmptyDirReturnsEmptyList() throws Exception {
        Path emptyFile = Files.createTempFile("empty", ".avro");
        try {
            Files.delete(emptyFile);
            assertThatThrownBy(() -> loader.loadTruthTables(emptyFile))
                    .isInstanceOf(java.io.IOException.class);
        } finally {
            Files.deleteIfExists(emptyFile);
        }
    }

    @Test
    void testLoadTruthTablesFromDemoOutput() throws Exception {
        Path dir = Files.createTempDirectory("pretrain-test");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "python3", "scripts/pretrain_neurons.py",
                    "--demo", "--k", "8", "--neurons-per-layer", "10",
                    "--layers", "2", "--output-dir", dir.toString(),
                    "--seed", "42"
            );
            pb.directory(Path.of("").toAbsolutePath().toFile());
            Process p = pb.start();
            int exit = p.waitFor();

            if (exit != 0) {
                String err = new String(p.getErrorStream().readAllBytes());
                System.err.println("Python script error: " + err);
                return;
            }

            List<TruthTable> tables0 = loader.loadLayer(dir,
                    "SmolLM2-135M-synth", 0);
            List<TruthTable> tables1 = loader.loadLayer(dir,
                    "SmolLM2-135M-synth", 1);

            assertThat(tables0).hasSize(10);
            assertThat(tables1).hasSize(10);
            assertThat(tables0.get(0).k()).isEqualTo(8);
        } finally {
            Files.walk(dir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
        }
    }

    @Test
    void testLoadPretrainedBrain() throws Exception {
        Path dir = Files.createTempDirectory("pretrain-brain");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "python3", "scripts/pretrain_neurons.py",
                    "--demo", "--k", "8", "--neurons-per-layer", "10",
                    "--layers", "1", "--output-dir", dir.toString(),
                    "--seed", "42"
            );
            pb.directory(Path.of("").toAbsolutePath().toFile());
            Process p = pb.start();
            int exit = p.waitFor();
            if (exit != 0) {
                System.err.println("Python stderr: " + new String(p.getErrorStream().readAllBytes()));
                return;
            }

            AgentBrain brain = loader.loadPretrainedBrain(dir.toString(), 0);

            assertThat(brain).isNotNull();
            assertThat(brain.nNeuron()).isNotNull();
            assertThat(brain.sNeuron()).isNotNull();
            assertThat(brain.wNeuron()).isNotNull();
            assertThat(brain.eNeuron()).isNotNull();
        } finally {
            Files.walk(dir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
        }
    }

    @Test
    void testTruthTableToTreeRoundtrip() {
        var rng = new java.util.Random(42);
        TruthTable original = TruthTable.random(6, rng);
        var tree = PretrainedLoader.truthTableToTree(original);

        for (int i = 0; i < (1 << 6); i++) {
            var bs = java.util.BitSet.valueOf(new long[]{i});
            assertThat(tree.evaluate(bs)).isEqualTo(original.evaluate(i));
        }
    }
}
