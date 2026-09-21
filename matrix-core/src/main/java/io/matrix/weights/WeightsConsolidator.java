package io.matrix.weights;

import io.matrix.bir.ClauseSetForm;
import io.matrix.bir.TtForm;
import io.matrix.neuron.TruthTable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Weights Consolidator: merges all per-model Avro neuron files into a single
 * unified weights file.
 *
 * <p>Per user requirement: "сбор весов из уже обученных моделей в avro схемы
 * MATRIX и так же коммить их, но не как отдельные, а как единственные и общие веса".
 *
 * <p>The consolidator reads all .avro files from models/pretrained/, merges
 * them into a single ConsolidatedWeights record, and writes to
 * models/pretrained/consolidated_weights.avro.
 */
public final class WeightsConsolidator {

    private final Path pretrainedDir;

    public WeightsConsolidator(Path pretrainedDir) {
        this.pretrainedDir = pretrainedDir;
    }

    /** Consolidate all Avro files into a single unified file. */
    public ConsolidatedWeights consolidate() throws IOException {
        Map<String, List<LayerWeights>> byModel = new TreeMap<>();
        int totalNeurons = 0;
        int totalLayers = 0;

        // Scan for .avro files
        try (var stream = java.nio.file.Files.walk(pretrainedDir)) {
            var avroFiles = stream
                    .filter(p -> p.toString().endsWith(".avro"))
                    .sorted()
                    .toList();

            for (var avroFile : avroFiles) {
                String modelId = extractModelId(avroFile);
                int layerIndex = extractLayerIndex(avroFile);
                byte[] weights = java.nio.file.Files.readAllBytes(avroFile);

                var layer = new LayerWeights(layerIndex, weights.length, weights, "avro-mpdt");
                byModel.computeIfAbsent(modelId, k -> new ArrayList<>()).add(layer);
                totalNeurons += weights.length / 100; // rough estimate
                totalLayers++;
            }
        }

        // Build unified weights per model
        List<UnifiedWeights> models = new ArrayList<>();
        for (var entry : byModel.entrySet()) {
            var layers = entry.getValue();
            layers.sort(java.util.Comparator.comparingInt(LayerWeights::layerIndex));
            byte[] hash = computeHash(layers);
            models.add(new UnifiedWeights(
                    entry.getKey(), "1.0.0",
                    layers.stream().mapToInt(LayerWeights::neuronCount).sum(),
                    layers.size(), layers, hash, System.currentTimeMillis()));
        }

        return new ConsolidatedWeights(
                models, "consolidated-" + System.currentTimeMillis(),
                System.currentTimeMillis(), models.size(), totalNeurons);
    }

    /** Write consolidated weights to file. */
    public void write(ConsolidatedWeights weights, Path output) throws IOException {
        try (var out = new DataOutputStream(java.nio.file.Files.newOutputStream(output))) {
            out.writeInt(weights.models().size());
            for (var model : weights.models()) {
                out.writeUTF(model.modelId());
                out.writeUTF(model.version());
                out.writeInt(model.totalNeurons());
                out.writeInt(model.totalLayers());
                out.writeInt(model.layers().size());
                for (var layer : model.layers()) {
                    out.writeInt(layer.layerIndex());
                    out.writeInt(layer.neuronCount());
                    out.writeInt(layer.weights().length);
                    out.write(layer.weights());
                    out.writeUTF(layer.format());
                }
                out.writeInt(model.contentHash().length);
                out.write(model.contentHash());
                out.writeLong(model.createdAt());
            }
            out.writeUTF(weights.consolidationId());
            out.writeLong(weights.createdAt());
            out.writeInt(weights.totalModels());
            out.writeInt(weights.totalNeurons());
        }
    }

    private String extractModelId(Path path) {
        String name = path.getFileName().toString();
        // e.g., "Qwen3-1.7B_layer0_neurons.avro" → "qwen3-1.7b"
        if (name.contains("_layer")) {
            return name.substring(0, name.indexOf("_layer")).toLowerCase();
        }
        return name.replace(".avro", "").toLowerCase();
    }

    private int extractLayerIndex(Path path) {
        String name = path.getFileName().toString();
        // e.g., "Qwen3-1.7B_layer0_neurons.avro" → 0
        if (name.contains("_layer")) {
            String layerPart = name.substring(name.indexOf("_layer") + 6);
            return Integer.parseInt(layerPart.replace("_neurons.avro", ""));
        }
        return 0;
    }

    private byte[] computeHash(List<LayerWeights> layers) {
        try {
            var md = MessageDigest.getInstance("SHA3-256");
            for (var layer : layers) {
                md.update(layer.weights());
            }
            return md.digest();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public record LayerWeights(int layerIndex, int neuronCount, byte[] weights, String format) {}
    public record UnifiedWeights(String modelId, String version, int totalNeurons,
                                  int totalLayers, List<LayerWeights> layers,
                                  byte[] contentHash, long createdAt) {}
    public record ConsolidatedWeights(List<UnifiedWeights> models, String consolidationId,
                                       long createdAt, int totalModels, int totalNeurons) {}
}
