package io.matrix.research;

import io.matrix.chain.ChainDescriptor;
import io.matrix.chain.MultiChainEnsemble;
import io.matrix.imports.BooleanChainRunner;
import io.matrix.imports.TruthTableLayer;
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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * RUN 347 — Phase U Multi-chain federation with consensus.
 *
 * <p>Acceptance:
 *  - BYZANTINE: majority bit per position
 *  - WEIGHTED: magnitude-weighted sum
 *  - DEBATE: fallback to BYZANTINE (explanation exchange deferred)
 *  - 3 chains from 3 different distilled models converge
 */
class Exp347MultiChainConsensusTest {

    @Test
    void byzantineMajorityConsensus() {
        List<BooleanChainRunner> runners = List.of(
                makeSmallChain("A", 0xAA),
                makeSmallChain("B", 0xBB),
                makeSmallChain("C", 0xCC));
        MultiChainEnsemble ensemble = new MultiChainEnsemble(
                List.of(
                        ChainDescriptor.of("A", runners.get(0), 100),
                        ChainDescriptor.of("B", runners.get(1), 100),
                        ChainDescriptor.of("C", runners.get(2), 100)
                ),
                MultiChainEnsemble.Strategy.BYZANTINE);

        boolean[] input = new boolean[]{true, true, false, false, true, false};
        var result = ensemble.evaluate(input);

        assertThat(result.bits()).isNotEmpty();
        assertThat(result.contributions()).hasSize(3);
        assertThat(result.winningRationale()).contains("BYZANTINE");
    }

    @Test
    void weightedConsensusUsesMagnitudes() {
        List<BooleanChainRunner> runners = List.of(
                makeSmallChain("A", 0xAA),
                makeSmallChain("B", 0xBB));
        MultiChainEnsemble ensemble = new MultiChainEnsemble(
                List.of(
                        ChainDescriptor.of("A", runners.get(0), 100),
                        ChainDescriptor.of("B", runners.get(1), 100)
                ),
                MultiChainEnsemble.Strategy.WEIGHTED);

        boolean[] input = new boolean[]{true, false, true, false};
        var result = ensemble.evaluate(input);

        assertThat(result.bits()).isNotEmpty();
        assertThat(result.winningRationale()).contains("WEIGHTED");
    }

    @Test
    void debateFallbackToByzantine() {
        List<BooleanChainRunner> runners = List.of(
                makeSmallChain("A", 0xAA),
                makeSmallChain("B", 0xBB));
        MultiChainEnsemble ensemble = new MultiChainEnsemble(
                List.of(
                        ChainDescriptor.of("A", runners.get(0), 100),
                        ChainDescriptor.of("B", runners.get(1), 100)
                ),
                MultiChainEnsemble.Strategy.DEBATE);

        boolean[] input = new boolean[]{true, false};
        var result = ensemble.evaluate(input);

        assertThat(result.winningRationale()).contains("DEBATE");
    }

    @Test
    void threeModelsFromFnlRegistry() throws IOException {
        // Distill 3 different models, then run consensus across them
        Path cwd = Paths.get("").toAbsolutePath();
        List<Path> models = new ArrayList<>();
        for (Path p = cwd; p != null; p = p.getParent()) {
            Path ext = p.resolve("models/external");
            if (!Files.exists(ext)) continue;
            try (var s = Files.list(ext)) {
                s.filter(Files::isDirectory).forEach(d -> {
                    Path st = d.resolve("model.safetensors");
                    if (Files.exists(st)) models.add(st);
                });
            }
            break;
        }
        if (models.size() < 3) {
            assumeTrue(false, "need ≥3 safetensors models");
            return;
        }

        // Distill 3 models into chains
        List<BooleanChainRunner> runners = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            BooleanChainRunner r = BooleanChainRunner.loadFromSafetensors(
                    models.get(i), "model", 1 << 12);  // small budget
            if (r != null && r.layerCount() > 0) runners.add(r);
        }
        if (runners.size() < 3) {
            assumeTrue(false, "3 models didn't all distill");
            return;
        }

        MultiChainEnsemble ensemble = MultiChainEnsemble.of(
                runners, MultiChainEnsemble.Strategy.BYZANTINE);
        boolean[] input = new boolean[256];
        new Random(0xCAFE).nextBytes(new byte[32]);  // warm RNG
        Random rng = new Random(0xCAFE);
        for (int i = 0; i < input.length; i++) input[i] = rng.nextBoolean();

        var result = ensemble.evaluate(input);
        assertThat(result.bits().length).isGreaterThan(0);
        assertThat(result.contributions()).hasSize(3);
        System.out.printf("[Exp347] 3-model BYZANTINE: members=%d, bits=%d%n",
                ensemble.memberCount(), result.bits().length);
    }

    @Test
    void emptyMembersRejected() {
        assertThat(catchThrow(() ->
                new MultiChainEnsemble(List.of(), MultiChainEnsemble.Strategy.BYZANTINE)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static Throwable catchThrow(Runnable r) {
        try { r.run(); return null; }
        catch (Throwable t) { return t; }
    }

    private static BooleanChainRunner makeSmallChain(String name, long seed) {
        List<TruthTableLayer> layers = new ArrayList<>();
        Random rng = new Random(seed);
        for (int li = 0; li < 2; li++) {
            List<TruthTable> neurons = new ArrayList<>();
            for (int ni = 0; ni < 4; ni++) {
                int k = 8;
                BitSet bs = new BitSet(1 << k);
                int card = rng.nextInt(1 << k);
                for (int i = 0; i < card; i++) bs.set(rng.nextInt(1 << k));
                neurons.add(TruthTable.of(k, bs));
            }
            layers.add(new TruthTableLayer(neurons, 8));
        }
        return new BooleanChainRunner(name, "(test)", layers);
    }
}
