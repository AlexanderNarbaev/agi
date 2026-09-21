package io.matrix.imports;

import io.matrix.imports.BooleanChainRunner.ChainResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Multi-model chain loader (Phase 1+3 of the "Distilled weights = MATRIX
 * internal data" plan).
 *
 * <p>Loads every {@code model.safetensors} under {@code models/external/},
 * projects each via {@link FullChainLoader}, and concatenates the
 * resulting layers into a single {@link BooleanChainRunner}.
 *
 * <p>The result is one matrix instance: the chat pipeline sees a
 * single chain whose neuron pool is the union of all loaded models'
 * neurons. Layer count is the max across models (per-layer), and
 * total neurons is the sum of per-model neuron counts.
 */
public final class MultiModelLoader {

    private static final Logger log = LoggerFactory.getLogger(MultiModelLoader.class);

    /** One entry per loaded model, with its source path and layer count. */
    public record ModelEntry(String name, Path safetensorsPath, int layers, long neurons) {}

    /** Build a chain that combines all safetensors under {@code models/external}. */
    public static LoadResult loadFromDirectory(Path modelsRoot) {
        Objects.requireNonNull(modelsRoot, "modelsRoot");
        if (!Files.isDirectory(modelsRoot)) {
            return LoadResult.empty("models root not found: " + modelsRoot);
        }
        List<Path> safetensors = new ArrayList<>();
        try (var stream = Files.walk(modelsRoot, 4)) {
            stream.filter(p -> p.getFileName().toString().endsWith("model.safetensors"))
                    .filter(p -> Files.isRegularFile(p))
                    // skip files inside the .cache directory — those are the same safetensors
                    .filter(p -> !p.toString().contains("/.cache/"))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(safetensors::add);
        } catch (IOException e) {
            return LoadResult.empty("failed to walk models root: " + e.getMessage());
        }
        if (safetensors.isEmpty()) {
            return LoadResult.empty("no model.safetensors found under " + modelsRoot);
        }
        return loadAll(safetensors);
    }

    /**
     * Build a chain from an explicit list of safetensors. Each file
     * contributes its own layers; we concatenate them into one chain.
     */
    public static LoadResult loadAll(List<Path> safetensors) {
        if (safetensors.isEmpty()) {
            return LoadResult.empty("no safetensors provided");
        }
        List<BooleanChainRunner> perModel = new ArrayList<>();
        List<ModelEntry> entries = new ArrayList<>();
        for (Path p : safetensors) {
            try {
                BooleanChainRunner chain = FullChainLoader.loadAll(p, 1 << 14, "model");
                if (chain.layerCount() == 0) continue;
                perModel.add(chain);
                String name = p.getParent() != null
                        ? p.getParent().getFileName().toString()
                        : p.getFileName().toString();
                entries.add(new ModelEntry(name, p, chain.layerCount(), chain.totalNeurons()));
                log.info("Loaded {} — {} layers, {} neurons", name, chain.layerCount(), chain.totalNeurons());
            } catch (Exception e) {
                log.warn("Failed to load {}: {}", p, e.getMessage());
            }
        }
        if (perModel.isEmpty()) {
            return LoadResult.empty("no model loaded any layers");
        }
        return combine(perModel, entries);
    }

    /**
     * Combine N per-model chains into ONE matrix instance.
     * Layers from all models are concatenated; the resulting
     * chain's layer count is the sum.
     */
    static LoadResult combine(List<BooleanChainRunner> perModel, List<ModelEntry> entries) {
        // concatenate all layers from all models
        List<io.matrix.imports.TruthTableLayer> allLayers = new ArrayList<>();
        for (BooleanChainRunner chain : perModel) {
            // reflectively read the private 'layers' list — we know it's a List<TruthTableLayer>
            try {
                var f = BooleanChainRunner.class.getDeclaredField("layers");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<io.matrix.imports.TruthTableLayer> chainLayers =
                        (List<io.matrix.imports.TruthTableLayer>) f.get(chain);
                allLayers.addAll(chainLayers);
            } catch (Exception e) {
                throw new IllegalStateException("cannot read layers from BooleanChainRunner", e);
            }
        }
        String combinedName = "MultiModel[" + String.join("+",
                entries.stream().map(ModelEntry::name).toList()) + "]";
        String combinedSource = entries.stream()
                .map(e -> e.safetensorsPath().toString())
                .reduce((a, b) -> a + ";" + b).orElse("");
        BooleanChainRunner combined = new BooleanChainRunner(
                combinedName, combinedSource, allLayers);
        long totalNeurons = entries.stream().mapToLong(ModelEntry::neurons).sum();
        return new LoadResult(combined, entries, totalNeurons, allLayers.size());
    }

    /**
     * Result of a multi-model load: a single chain + per-model metadata.
     */
    public record LoadResult(BooleanChainRunner chain, List<ModelEntry> entries,
                             long totalNeurons, int totalLayers) {
        public static LoadResult empty(String reason) {
            log.warn("MultiModelLoader: {}", reason);
            return new LoadResult(BooleanChainRunner.empty(), List.of(), 0L, 0);
        }
        public boolean isEmpty() { return entries.isEmpty(); }
    }
}