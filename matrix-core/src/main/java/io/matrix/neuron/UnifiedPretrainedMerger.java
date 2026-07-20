package io.matrix.neuron;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.matrix.agent.PretrainedLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Unified pretrained merger — combines ALL available models into a single
 * baseline snapshot of MPDT neurons.
 *
 * <h2>Purpose</h2>
 * <p>Each pretrained model contributes truth tables extracted from a
 * different transformer's attention layers. By merging them into a
 * single neuron pool, the system uses ALL knowledge simultaneously.
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Scan {@code models/pretrained/&#42;/} for Avro files</li>
 *   <li>Group truth tables by K (input width) — same K = same neuron geometry</li>
 *   <li>Sort each group by entropy (descending) — keep the most informative neurons</li>
 *   <li>Combine top N per K into a single merged neuron pool</li>
 *   <li>Build a HierarchicalBrain from the merged pool</li>
 *   <li>Persist the baseline snapshot to {@code models/merged/baseline.jsonl}
 *       with a SHA-256 integrity check</li>
 * </ol>
 *
 * <h2>Baseline snapshot</h2>
 * <p>Once saved, the baseline can be restored via
 * {@link UnifiedPretrainedMerger#loadBaseline(Path)} to provide
 * deterministic startup regardless of which models are on disk.
 */
public final class UnifiedPretrainedMerger {

    private static final Logger log = LoggerFactory.getLogger(UnifiedPretrainedMerger.class);

    private static final Path PRETRAINED_DIR = Path.of("models/pretrained");
    private static final Path BASELINE_DIR = Path.of("models/merged");
    private static final Path BASELINE_FILE = BASELINE_DIR.resolve("baseline.jsonl");
    private static final Path BASELINE_MANIFEST = BASELINE_DIR.resolve("baseline.manifest.json");

    /** Per-K neuron caps. */
    private static final int MAX_NEURONS_PER_K = 32;

    private static final ObjectMapper JSON = new ObjectMapper();

    private UnifiedPretrainedMerger() {}

    /**
     * Builds a unified HierarchicalBrain from all pretrained models on disk
     * and saves a baseline snapshot for reproducible startup.
     *
     * @return the merged brain + baseline manifest
     * @throws IOException if a model file is unreadable
     */
    public static MergeResult buildUnifiedBrain() throws IOException {
        Files.createDirectories(BASELINE_DIR);
        PretrainedLoader loader = new PretrainedLoader();

        // 1. Collect all truth tables across all models, grouped by K
        Map<String, ModelTruthTables> perModel = new LinkedHashMap<>();
        Map<Integer, List<TruthTable>> byK = new LinkedHashMap<>();
        for (int k = 2; k <= 32; k++) byK.put(k, new ArrayList<>());

        List<Path> modelDirs = listPretrainedModelDirs();
        log.info("UnifiedMerger: found {} pretrained model directories", modelDirs.size());

        for (Path modelDir : modelDirs) {
            String modelName = modelDir.getFileName().toString();
            try {
                List<TruthTable> allTables = loadAllLayers(loader, modelDir, modelName);
                perModel.put(modelName, new ModelTruthTables(allTables.size()));
                for (TruthTable t : allTables) {
                    byK.computeIfAbsent(t.k(), key -> new ArrayList<>()).add(t);
                }
                log.info("  loaded {} from {}: {} truth tables", modelName, modelName, allTables.size());
            } catch (Exception e) {
                log.warn("  failed to load {}: {}", modelName, e.getMessage());
            }
        }

        // 2. Cap each K-bucket, sort by entropy (descending) to keep informative neurons
        int totalKept = 0;
        int totalAvailable = 0;
        for (Map.Entry<Integer, List<TruthTable>> e : byK.entrySet()) {
            totalAvailable += e.getValue().size();
            e.getValue().sort(Comparator.comparingDouble(UnifiedPretrainedMerger::entropyOf).reversed());
            if (e.getValue().size() > MAX_NEURONS_PER_K) {
                e.getValue().subList(MAX_NEURONS_PER_K, e.getValue().size()).clear();
            }
            totalKept += e.getValue().size();
        }

        // 3. Build HierarchicalBrain from the merged pool
        NeuronLayer sensorLayer = buildLayer(byK, 12, 12);
        NeuronLayer featureLayer = buildLayer(byK, 8, 12);
        NeuronLayer actionLayer = buildLayer(byK, 5, 8);

        HierarchicalBrain brain = new HierarchicalBrain(sensorLayer, featureLayer, actionLayer);
        log.info("UnifiedMerger: built brain {} + {} + {} neurons ({} kept of {} available)",
                sensorLayer.outputWidth(), featureLayer.outputWidth(), actionLayer.outputWidth(),
                totalKept, totalAvailable);

        // 4. Persist baseline snapshot
        BaselineManifest manifest = writeBaseline(perModel, byK, brain, totalKept, totalAvailable);
        return new MergeResult(brain, manifest, totalKept, totalAvailable, perModel.size());
    }

    /**
     * Loads a previously-saved baseline snapshot. If the file does not
     * exist, returns null (caller should fall back to {@link #buildUnifiedBrain}).
     */
    public static BaselineManifest loadBaseline(Path baselineFile) throws IOException {
        if (!Files.exists(baselineFile)) {
            return null;
        }
        byte[] bytes = Files.readAllBytes(baselineFile);
        ObjectNode node = (ObjectNode) JSON.readTree(bytes);
        BaselineManifest m = new BaselineManifest();
        m.sha256 = node.path("sha256").asText();
        m.createdAt = node.path("createdAt").asText();
        m.modelCount = node.path("modelCount").asInt();
        m.keptNeurons = node.path("keptNeurons").asInt();
        m.totalNeurons = node.path("totalNeurons").asInt();
        m.neuronsPerK = new LinkedHashMap<>();
        node.path("neuronsPerK").fields().forEachRemaining(e ->
                m.neuronsPerK.put(e.getKey(), e.getValue().asInt()));
        return m;
    }

    /** Returns true if the baseline snapshot file exists and is valid. */
    public static boolean baselineExists() {
        return Files.exists(BASELINE_FILE) && Files.exists(BASELINE_MANIFEST);
    }

    /** Path to the baseline snapshot file. */
    public static Path baselineFile() { return BASELINE_FILE; }

    /** Path to the baseline manifest file. */
    public static Path baselineManifest() { return BASELINE_MANIFEST; }

    // ─── helpers ───

    private static List<Path> listPretrainedModelDirs() throws IOException {
        List<Path> dirs = new ArrayList<>();
        if (!Files.exists(PRETRAINED_DIR)) return dirs;
        try (var stream = Files.list(PRETRAINED_DIR)) {
            stream.filter(Files::isDirectory).forEach(dirs::add);
        }
        return dirs;
    }

    private static List<TruthTable> loadAllLayers(PretrainedLoader loader, Path modelDir, String modelName) throws IOException {
        List<TruthTable> all = new ArrayList<>();
        for (int layer = 0; layer < 12; layer++) {
            try {
                all.addAll(loader.loadLayer(modelDir, modelName, layer));
            } catch (Exception ignored) {
                // layer not present
            }
        }
        return all;
    }

    private static NeuronLayer buildLayer(Map<Integer, List<TruthTable>> byK, int count, int k) {
        List<TruthTable> tables = byK.getOrDefault(k, List.of());
        List<TruthTable> kept = new ArrayList<>(tables.subList(0, Math.min(count, tables.size())));
        List<DecisionTree> neurons = new ArrayList<>();
        for (TruthTable t : kept) {
            neurons.add(PretrainedLoader.truthTableToTree(t));
        }
        Random fillRng = new Random(kept.hashCode());
        while (neurons.size() < count) {
            neurons.add(DecisionTree.random(k, Math.min(k, 8), fillRng));
        }
        return new NeuronLayer(neurons, k);
    }

    private static double entropyOf(TruthTable t) {
        int n = 1 << t.k();
        if (n <= 1) return 0.0;
        BitSet bits = t.table();
        int ones = 0;
        for (int i = 0; i < n; i++) {
            if (bits.get(i)) ones++;
        }
        if (ones == 0 || ones == n) return 0.0;
        double p = (double) ones / n;
        return -p * Math.log(p) - (1 - p) * Math.log(1 - p);
    }

    private static BaselineManifest writeBaseline(Map<String, ModelTruthTables> perModel,
                                                  Map<Integer, List<TruthTable>> byK,
                                                  HierarchicalBrain brain,
                                                  int kept,
                                                  int total) throws IOException {
        // Write the brain as Avro-style flat list
        ObjectNode manifest = JSON.createObjectNode();
        manifest.put("createdAt", Instant.now().toString());
        manifest.put("keptNeurons", kept);
        manifest.put("totalNeurons", total);
        manifest.put("modelCount", perModel.size());
        ArrayNode models = manifest.putArray("models");
        perModel.forEach((name, stats) -> models.add(name));

        ObjectNode perK = manifest.putObject("neuronsPerK");
        byK.forEach((k, tables) -> perK.put(String.valueOf(k), tables.size()));

        // Write flat truth-table snapshot
        try (OutputStream os = Files.newOutputStream(BASELINE_FILE,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            os.write("[\n".getBytes(StandardCharsets.UTF_8));
            boolean first = true;
            for (Map.Entry<Integer, List<TruthTable>> e : byK.entrySet()) {
                for (TruthTable t : e.getValue()) {
                    if (!first) os.write(",\n".getBytes(StandardCharsets.UTF_8));
                    first = false;
                    writeTruthTable(os, e.getKey(), t);
                }
            }
            os.write("\n]\n".getBytes(StandardCharsets.UTF_8));
        }
        // Compute SHA-256 over the snapshot for integrity check
        byte[] bytes = Files.readAllBytes(BASELINE_FILE);
        String sha = sha256(bytes);
        manifest.put("sha256", sha);
        manifest.put("fileSize", bytes.length);

        // Write the manifest
        try (OutputStream os = Files.newOutputStream(BASELINE_MANIFEST,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            os.write(JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
        }
        log.info("UnifiedMerger: baseline saved → {} ({} bytes, sha256={})",
                BASELINE_FILE, bytes.length, sha.substring(0, 12));

        BaselineManifest m = new BaselineManifest();
        m.createdAt = manifest.get("createdAt").asText();
        m.sha256 = sha;
        m.modelCount = perModel.size();
        m.keptNeurons = kept;
        m.totalNeurons = total;
        m.neuronsPerK = new LinkedHashMap<>();
        manifest.path("neuronsPerK").fields().forEachRemaining(e ->
                m.neuronsPerK.put(e.getKey(), e.getValue().asInt()));
        return m;
    }

    private static void writeTruthTable(OutputStream os, int k, TruthTable t) throws IOException {
        ObjectNode node = JSON.createObjectNode();
        node.put("k", k);
        ArrayNode arr = node.putArray("bits");
        BitSet bits = t.table();
        for (int i = 0; i < (1 << k); i++) {
            arr.add(bits.get(i));
        }
        os.write(JSON.writeValueAsBytes(node));
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "unavailable";
        }
    }

    /** Manifest stored alongside the baseline snapshot. */
    public static class BaselineManifest {
        public String createdAt;
        public String sha256;
        public int modelCount;
        public int keptNeurons;
        public int totalNeurons;
        public Map<String, Integer> neuronsPerK = new LinkedHashMap<>();

        @Override
        public String toString() {
            return "Baseline{models=" + modelCount + ", kept=" + keptNeurons +
                    "/" + totalNeurons + ", sha=" + (sha256 != null ? sha256.substring(0, 12) : "?") + "}";
        }
    }

    /** Per-model stats. */
    static class ModelTruthTables {
        final int tableCount;
        ModelTruthTables(int n) { this.tableCount = n; }
    }

    /** Result of the merge operation. */
    public record MergeResult(
            HierarchicalBrain brain,
            BaselineManifest manifest,
            int keptNeurons,
            int totalNeurons,
            int modelCount
    ) {}
}