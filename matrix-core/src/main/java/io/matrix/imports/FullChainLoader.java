package io.matrix.imports;

import io.matrix.neuron.TruthTable;
import io.matrix.bir.TtForm;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wave I: layer-agnostic, full 24-block boolean chain. Reads ALL
 * transformer-block tensors from a safetensors file and builds one
 * {@link TruthTableLayer} per block. Bounded only by JVM heap (64 GB
 * on the host machine), so all 24 blocks can be loaded.
 *
 * <p>Static factory {@link #loadAll(Path, int, String)} groups tensors
 * by layer index and emits a {@link BooleanChainRunner} with the full
 * transformer. The projection budget is per-tensor; with the 64 GB
 * heap, even 100k+ neurons per tensor is feasible.
 *
 * <p>No layer count is hardcoded — works for any model that uses the
 * {@code <prefix>.layers.{N}.{...}.weight} naming convention.
 */
public final class FullChainLoader {

    private static final Map<String, BooleanChainRunner> CACHE = new ConcurrentHashMap<>();

    /**
     * Load ALL transformer-block tensors from a safetensors file.
     *
     * @param safetensors path to the safetensors file
     * @param budgetEntries per-tensor projection budget (1<<14 = 16K
     *                    is the Wave-7 default; raise for higher fidelity)
     * @param prefix tensor-name prefix (e.g. "model" for Qwen, "transformer" for GPT-Neo)
     * @return a chain with one layer per transformer block; empty if no layers found
     */
    public static BooleanChainRunner loadAll(Path safetensors, int budgetEntries, String prefix) {
        Objects.requireNonNull(safetensors, "safetensors");
        Objects.requireNonNull(prefix, "prefix");
        String key = safetensors + ":" + budgetEntries + ":" + prefix;
        BooleanChainRunner cached = CACHE.get(key);
        if (cached != null) return cached;

        try {
            SafetensorsReader reader = new SafetensorsReader();
            TensorProjector projector = new TensorProjector(budgetEntries);
            SafetensorsReader.Header header = reader.readHeader(safetensors);

            // group tensors by layer index
            Map<Integer, List<TruthTable>> byLayer = new TreeMap<>();
            try (FileChannel ch = FileChannel.open(safetensors)) {
                for (String tn : header.tensorNames()) {
                    int layer = BooleanChainRunner.extractLayerIndex(tn, prefix);
                    if (layer < 0) continue;
                    if (tn.contains("embed_tokens") || tn.contains("lm_head")) continue;
                    try {
                        SafetensorsReader.Tensor t = reader.loadTensor(ch, header, tn);
                        if (t.data().length == 0) continue;
                        TensorProjector.Projection p = projector.project(t);
                        if (p.neuronCount() > 0) {
                            byLayer.computeIfAbsent(layer, k -> new ArrayList<>())
                                    .addAll(p.truthTables());
                        }
                    } catch (Throwable ignored) {
                        // best-effort; some tensors (norm, bias) may be too small
                    }
                }
            }
            if (byLayer.isEmpty()) return BooleanChainRunner.empty();

            // build one TruthTableLayer per block
            List<TruthTableLayer> layers = new ArrayList<>();
            for (var e : byLayer.entrySet()) {
                List<TruthTable> neurons = e.getValue();
                if (neurons.isEmpty()) continue;
                int maxK = neurons.stream().mapToInt(TruthTable::k).max().orElse(1);
                layers.add(new TruthTableLayer(neurons, maxK));
            }
            String modelName = safetensors.getFileName().toString();
            BooleanChainRunner runner = new BooleanChainRunner(modelName, safetensors.toString(), layers);
            CACHE.put(key, runner);
            return runner;
        } catch (Exception ex) {
            return BooleanChainRunner.empty();
        }
    }

    private FullChainLoader() {}
}