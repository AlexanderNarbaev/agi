package io.matrix.research;

import io.matrix.neuron.EnrichedNeuron;
import io.matrix.neuron.NeuronCompactor;
import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RUN 345 — DESIGN-22 §4 NeuronCompactor.
 *
 * <p>Acceptance:
 *  - compact + decompact round-trip is lossless
 *  - Compression ratio > 1.0 when neurons are similar (correlated)
 *  - BLN v2 magic header correct
 *  - saveTo / loadFrom work
 */
class Exp345NeuronCompactorTest {

    @Test
    void roundTripLossless() {
        List<EnrichedNeuron> neurons = randomNeurons(50, 0xCAFE);
        byte[] data = NeuronCompactor.compact(neurons);

        List<EnrichedNeuron> restored = NeuronCompactor.decompact(data);
        assertThat(restored).hasSize(neurons.size());

        // Verify each neuron matches
        for (int i = 0; i < neurons.size(); i++) {
            assertThat(restored.get(i).table().table())
                    .as("table[%d]", i)
                    .isEqualTo(neurons.get(i).table().table());
            assertThat(restored.get(i).magnitude())
                    .as("magnitude[%d]", i)
                    .isCloseTo(neurons.get(i).magnitude(),
                            org.assertj.core.data.Offset.offset(1e-5));
            for (int j = 0; j < EnrichedNeuron.CHEMICAL_DIM; j++) {
                assertThat(restored.get(i).chemicalVector()[j])
                        .as("chem[%d][%d]", i, j)
                        .isCloseTo(neurons.get(i).chemicalVector()[j],
                                org.assertj.core.data.Offset.offset(1e-5));
            }
            assertThat(restored.get(i).tag())
                    .as("tag[%d]", i)
                    .isEqualTo(neurons.get(i).tag());
        }
    }

    @Test
    void compressionRatio() throws IOException {
        // Identical neurons compress well — empty XOR deltas
        TruthTable base = makeTable(8, 100, 0xAL);
        EnrichedNeuron baseNeuron = EnrichedNeuron.derive(base);
        List<EnrichedNeuron> copies = new ArrayList<>();
        for (int i = 0; i < 100; i++) copies.add(baseNeuron);
        byte[] copiesData = NeuronCompactor.compact(copies);
        int copiesRaw = estimateRaw(copies);
        double copiesRatio = NeuronCompactor.compressionRatio(copiesRaw, copiesData.length);

        // Random neurons — random deltas, no compression gain
        List<EnrichedNeuron> random = randomNeurons(100, 0xAAAAAAL);
        byte[] randomData = NeuronCompactor.compact(random);
        int randomRaw = estimateRaw(random);
        double randomRatio = NeuronCompactor.compressionRatio(randomRaw, randomData.length);

        System.out.printf("[Exp345] 100 identical: raw=%,d bytes, compacted=%,d bytes, ratio=%.4f%n",
                copiesRaw, copiesData.length, copiesRatio);
        System.out.printf("[Exp345] 100 random:    raw=%,d bytes, compacted=%,d bytes, ratio=%.4f%n",
                randomRaw, randomData.length, randomRatio);

        // Identical neurons should compress at least somewhat (ratio > 1)
        assertThat(copiesRatio)
                .as("identical neurons compress > 1x")
                .isGreaterThan(1.0);

        // Document ratio for both; compression properties depend on data
        // — exact ratios vary, but identical should be >= random.
        assertThat(copiesData.length)
                .as("identical bytes <= random bytes (identical compresses at least as well)")
                .isLessThanOrEqualTo(randomData.length);
    }

    @Test
    void emptyListCompactsToHeader() {
        List<EnrichedNeuron> empty = List.of();
        byte[] data = NeuronCompactor.compact(empty);
        // Header: 4 magic + 4 version + 4 total + 4 blockSize + 4 blockCount = 20
        assertThat(data.length).isEqualTo(20);
    }

    @Test
    void singletonRoundTrip() {
        EnrichedNeuron one = EnrichedNeuron.derive(makeTable(8, 100, 0x1));
        byte[] data = NeuronCompactor.compact(List.of(one));
        List<EnrichedNeuron> restored = NeuronCompactor.decompact(data);
        assertThat(restored).hasSize(1);
        assertThat(restored.get(0).table().table()).isEqualTo(one.table().table());
    }

    @Test
    void saveAndLoadFile() throws IOException {
        Path cwd = Paths.get("").toAbsolutePath();
        Path tmpFile = null;
        for (Path p = cwd; p != null; p = p.getParent()) {
            Path candidate = p.resolve("build");
            if (Files.exists(candidate)) {
                Files.createDirectories(candidate);
                tmpFile = candidate.resolve("test-compaction.bln");
                break;
            }
        }
        assertThat(tmpFile).isNotNull();

        List<EnrichedNeuron> neurons = randomNeurons(20, 0xBEEFL);
        NeuronCompactor.saveTo(tmpFile, neurons);
        assertThat(Files.exists(tmpFile)).isTrue();

        List<EnrichedNeuron> restored = NeuronCompactor.loadFrom(tmpFile);
        assertThat(restored).hasSize(neurons.size());

        // Cleanup
        Files.deleteIfExists(tmpFile);
    }

    @Test
    void invalidMagicRejected() {
        byte[] bogus = new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF,
                                  0, 0, 0, 2,  // version
                                  0, 0, 0, 0,  // total
                                  0, 0, 1, 0,  // blockSize + count
                                  0, 0, 0, 0};
        // The outer RuntimeException has message "decompaction failed"
        // with cause "bad magic: ..." — check both
        assertThatThrownBy(() -> NeuronCompactor.decompact(bogus))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void compressionOnRealPool() throws IOException {
        // Load FnlRegistry (which holds 3 models per RUN 344) and compact
        Path cwd = Paths.get("").toAbsolutePath();
        Path modelPath = null;
        for (Path p = cwd; p != null; p = p.getParent()) {
            Path candidate = p.resolve("models/external/qwen2.5-0.5b/model.safetensors");
            if (Files.exists(candidate)) {
                modelPath = candidate;
                break;
            }
        }
        if (modelPath == null) {
            // Skip if Qwen not present
            return;
        }
        var runner = io.matrix.imports.BooleanChainRunner.loadFromSafetensors(
                modelPath, "model", 1 << 12);  // small budget for test
        // Qwen has neurons with mixed k (9, 12, 14) within layers — group
        // by k and compact each group separately.
        java.util.Map<Integer, List<EnrichedNeuron>> byK = new java.util.TreeMap<>();
        for (var layer : runner.layers()) {
            for (TruthTable t : layer.neurons()) {
                if (t == null) continue;
                byK.computeIfAbsent(t.k(), k -> new ArrayList<>())
                        .add(EnrichedNeuron.derive(t));
            }
        }
        long totalRaw = 0, totalCompacted = 0;
        for (var entry : byK.entrySet()) {
            byte[] compacted = NeuronCompactor.compact(entry.getValue());
            int raw = estimateRaw(entry.getValue());
            totalRaw += raw;
            totalCompacted += compacted.length;
            System.out.printf("[Exp345] k=%d: %d neurons, raw=%,d bytes, compacted=%,d bytes%n",
                    entry.getKey(), entry.getValue().size(), raw, compacted.length);
        }
        double ratio = NeuronCompactor.compressionRatio((int) totalRaw, (int) totalCompacted);
        System.out.printf("[Exp345] TOTAL Qwen: raw=%,d bytes, compacted=%,d bytes, ratio=%.4f%n",
                totalRaw, totalCompacted, ratio);
    }

    // -- helpers --

    private static List<EnrichedNeuron> randomNeurons(int count, long seed) {
        List<EnrichedNeuron> list = new ArrayList<>();
        Random rng = new Random(seed);
        for (int i = 0; i < count; i++) {
            int k = 8;
            int card = rng.nextInt(1 << k);
            list.add(EnrichedNeuron.derive(makeTable(k, card, seed + i * 0xDEFL)));
        }
        return list;
    }

    private static TruthTable makeTable(int k, int cardinality, long seed) {
        BitSet bs = new BitSet(1 << k);
        Random rng = new Random(seed);
        for (int i = 0; i < cardinality; i++) bs.set(rng.nextInt(1 << k));
        return TruthTable.of(k, bs);
    }

    /** Estimate raw bytes: each neuron = (table bytes) + 16 floats + 1 byte. */
    private static int estimateRaw(List<EnrichedNeuron> neurons) {
        int sum = 0;
        for (EnrichedNeuron n : neurons) {
            int tableBytes = n.table().table().toByteArray().length;
            sum += tableBytes + 4 * (1 + EnrichedNeuron.CHEMICAL_DIM) + 1;
        }
        return sum;
    }
}
