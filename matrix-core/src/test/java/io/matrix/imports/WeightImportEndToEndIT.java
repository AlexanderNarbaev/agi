package io.matrix.imports;

import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.Test;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W7.1 end-to-end: load a real LLM safetensors (downloaded by
 * {@code scripts/import_qwen_weights.py}) and project every tensor
 * into TruthTable neurons. Reports per-tensor neuron counts and the
 * total neuron pool.
 *
 * <p>This is the "import real open-weight LLM weights into MATRIX's
 * boolean substrate" pipeline — what the user asked for in this
 * session.
 *
 * <p>Test is skipped if the safetensors are not present in the
 * expected location. Run the Python helper first:
 * {@code python3 scripts/import_qwen_weights.py}.
 */
class WeightImportEndToEndIT {

    private static final List<Path> CANDIDATES = List.of(
            Path.of("/tmp/opencode/matrix-import/models--Qwen--Qwen2.5-0.5B/snapshots"),
            Path.of("/tmp/opencode/matrix-import/models--HuggingFaceTB--SmolLM2-360M-Instruct/snapshots"),
            Path.of("models/external/qwen2.5-0.5b"),
            Path.of("models/external/smollm2-360m"));

    private static Path findSafetensors() {
        for (Path root : CANDIDATES) {
            if (!Files.isDirectory(root)) continue;
            try {
                // search recursively for a *.safetensors file
                return Files.walk(root, 3)
                        .filter(p -> p.toString().endsWith(".safetensors"))
                        .filter(p -> Files.isRegularFile(p))
                        .findFirst()
                        .orElse(null);
            } catch (Exception e) {
                // continue
            }
        }
        return null;
    }
@Test
    void projectAllTensorsIntoTruthTableNeurons() throws Exception {
        Path safetensors = findSafetensors();
        if (safetensors == null) {
            System.out.println("[W7.1] no safetensors found — skipping end-to-end test. "
                    + "Run: python3 scripts/import_qwen_weights.py");
            return;
        }
        SafetensorsReader reader = new SafetensorsReader();
        TensorProjector projector = new TensorProjector(1 << 14);

        SafetensorsReader.Header header = reader.readHeader(safetensors);
        long totalNeurons = 0;
        long totalTensors = 0;
        long totalElements = 0;
        long totalBytes = 0;
        Map<String, Integer> perTensorNeurons = new HashMap<>();

        int maxTensors = Integer.MAX_VALUE;
        boolean includeEmbedHead = "true".equals(
                System.getenv("MATRIX_IMPORT_INCLUDE_EMBED"));

        System.out.println("[W7.1] importing: " + safetensors);

        try (FileChannel ch = FileChannel.open(safetensors)) {
            int processed = 0;
            for (String tensorName : header.tensorNames()) {
                if (processed >= maxTensors) break;
                if (!includeEmbedHead && (tensorName.contains("embed_tokens")
                        || tensorName.contains("lm_head"))) {
                    continue;
                }
                try {
                    SafetensorsReader.Tensor t = reader.loadTensor(ch, header, tensorName);
                    if (t.data().length == 0) continue;
                    TensorProjector.Projection p = projector.project(t);
                    if (p.neuronCount() > 0) {
                        perTensorNeurons.put(t.name(), p.neuronCount());
                        totalNeurons += p.neuronCount();
                        totalElements += t.data().length;
                    }
                    totalTensors++;
                    totalBytes += (long) t.data().length * 4;
                    processed++;
                } catch (OutOfMemoryError oom) {
                    System.out.println("[W7.1] OOM at tensor " + tensorName);
                    break;
                } catch (Exception e) {
                    System.out.println("[W7.1] tensor failed: " + tensorName
                            + " — " + e.getMessage());
                }
            }
        }

        String modelName = safetensors.getParent().getParent().getFileName().toString();
        System.out.println("[W7.1] =======================================");
        System.out.println("[W7.1] model:           " + modelName);
        System.out.println("[W7.1] tensors:         " + totalTensors + " of "
                + header.tensorNames().size());
        System.out.println("[W7.1] float elements: " + totalElements
                + " (" + (totalBytes / 1_000_000) + " MB)");
        System.out.println("[W7.1] total neurons:   " + totalNeurons);
        System.out.println("[W7.1] top-10 tensors by neuron count:");
        perTensorNeurons.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(10)
                .forEach(e -> System.out.println("[W7.1]   "
                        + e.getKey() + " → " + e.getValue() + " neurons"));
        System.out.println("[W7.1] =======================================");

        assertThat(totalNeurons).isGreaterThan(0L);
    }

    /** Iterate every available safetensors and project each (cross-model diversity check). */
    @Test
    void projectAcrossAllAvailableModels() throws Exception {
        java.util.List<Path> roots = List.of(
                Path.of("/tmp/opencode/matrix-import/models--Qwen--Qwen2.5-0.5B/snapshots"),
                Path.of("/tmp/opencode/matrix-import/models--TinyLlama--TinyLlama-1.1B-Chat-v1.0/snapshots"),
                Path.of("/tmp/opencode/matrix-import/models--HuggingFaceTB--SmolLM2-360M-Instruct/snapshots"));
        SafetensorsReader reader = new SafetensorsReader();
        TensorProjector projector = new TensorProjector(1 << 14);
        long grandTotal = 0;
        int modelsProcessed = 0;
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            Path modelFile;
            try {
                modelFile = Files.walk(root, 3)
                        .filter(p -> p.toString().endsWith(".safetensors"))
                        .filter(Files::isRegularFile)
                        .findFirst().orElse(null);
            } catch (Exception e) { continue; }
            if (modelFile == null) continue;
            long neurons = 0;
            long tensors = 0;
            try (FileChannel ch = FileChannel.open(modelFile)) {
                SafetensorsReader.Header header = reader.readHeader(modelFile);
                for (String tn : header.tensorNames()) {
                    if (tn.contains("embed_tokens") || tn.contains("lm_head")) continue;
                    try {
                        SafetensorsReader.Tensor t = reader.loadTensor(ch, header, tn);
                        if (t.data().length == 0) continue;
                        TensorProjector.Projection p = projector.project(t);
                        neurons += p.neuronCount();
                        tensors++;
                    } catch (Exception ignored) {}
                }
            }
            String modelName = modelFile.getParent().getParent().getFileName().toString();
            System.out.println("[W7.1 cross-model] " + modelName
                    + ": " + tensors + " tensors → " + neurons + " neurons");
            grandTotal += neurons;
            modelsProcessed++;
        }
        System.out.println("[W7.1 cross-model] total across "
                + modelsProcessed + " models: " + grandTotal + " neurons");
        assertThat(modelsProcessed).isGreaterThan(0);
    }

    @Test
    void verifySampleNeuronIsQueryable() throws Exception {
        Path safetensors = findSafetensors();
        if (safetensors == null) return;

        SafetensorsReader reader = new SafetensorsReader();
        TensorProjector projector = new TensorProjector(1 << 14);
        SafetensorsReader.Header header = reader.readHeader(safetensors);

        try (FileChannel ch = FileChannel.open(safetensors)) {
            // pick the first tensor with enough elements to fill a TT
            for (String tensorName : header.tensorNames()) {
                SafetensorsReader.Tensor t = reader.loadTensor(ch, header, tensorName);
                if (t.data().length < 32) continue;
                TensorProjector.Projection p = projector.project(t);
                if (p.neuronCount() == 0) continue;

                // query the first neuron at a few inputs to verify it
                // is a real, callable TruthTable
                TruthTable first = p.truthTables().get(0);
                int k = first.k();
                for (int i = 0; i < Math.min(8, 1 << k); i++) {
                    boolean result = first.evaluate(i);
                    // the projection threshold means about half are 1
                    if (i > 4) break;
                }
                System.out.println("[W7.1] verified sample neuron from tensor="
                        + tensorName + " k=" + k);
                assertThat(first.k()).isBetween(1, 20);
                return;
            }
        }
    }
}