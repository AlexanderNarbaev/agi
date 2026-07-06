package io.matrix.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.matrix.evolution.EvolutionLoop;
import io.matrix.evolution.FitnessFn;
import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.HierarchicalBrain;
import io.matrix.neuron.NeuronLayer;
import io.matrix.neuron.TruthTable;
import io.matrix.observability.MatrixMetrics;
import io.matrix.redis.NeuronCacheService;
import io.matrix.simulation.AgentBrain;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.BitSet;
import java.util.List;
import java.util.Random;

@ApplicationScoped
public class AgentBrainService {

    private static final Logger log = LoggerFactory.getLogger(AgentBrainService.class);
    private static final int K = 20;
    private static final String PRETRAINED_DIR = "models/pretrained";
    private static final String PRETRAINED_MODEL = "SmolLM2-135M-synth";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private volatile HierarchicalBrain brain;
    private final Random rng = new Random();
    private volatile String lastAction = "";
    private volatile int stuckCounter = 0;
    private volatile int exploreTicks = 0;
    private static final int STUCK_THRESHOLD = 30;
    private static final int EXPLORE_WINDOW = 80; // explore for 80 ticks after stuck detected

    @Inject
    NeuronCacheService neuronCache;

    private final MatrixMetrics metrics;

    @Inject
    AgentBrainService(MatrixMetrics metrics) {
        this.metrics = metrics;
        initializeWithPretrainedFallback();
    }

    public AgentBrainService() {
        this.metrics = null;
        initializeWithPretrainedFallback();
    }

    /**
     * Initialize brain: try pretrained weights first, fall back to random.
     * Called from constructors (not @PostConstruct — Quarkus ArC lazy-init issue).
     */
    private void initializeWithPretrainedFallback() {
        Path pretrainedDir = Path.of(PRETRAINED_DIR);
        if (Files.isDirectory(pretrainedDir)) {
            // ─── Try Qwen2.5-0.5B first (24 layers, better model) ───
            Path qwenDir = Path.of("models/pretrained/qwen2.5-0.5b");
            if (Files.isDirectory(qwenDir)) {
                try {
                    loadFromQwen(qwenDir);
                    log.info("Loaded Qwen2.5-0.5B pretrained brain");
                    return;
                } catch (Exception e) {
                    log.warn("Qwen load failed: {}", e.getMessage());
                }
            }

            // ─── Fall back to SmolLM2-135M ───
            try {
                PretrainedLoader loader = new PretrainedLoader();

                List<TruthTable> layer0Tables = loader.loadLayer(pretrainedDir, PRETRAINED_MODEL, 0);
                List<TruthTable> layer1Tables = loader.loadLayer(pretrainedDir, PRETRAINED_MODEL, 1);
                List<TruthTable> layer2Tables = loader.loadLayer(pretrainedDir, PRETRAINED_MODEL, 2);

                NeuronLayer sensorLayer = buildLayer(layer0Tables, 12, 12, 0);
                NeuronLayer featureLayer = buildLayer(layer1Tables, 8, 12, 1);
                NeuronLayer actionLayer = buildLayer(layer2Tables, 5, 8, 2);

                this.brain = new HierarchicalBrain(sensorLayer, featureLayer, actionLayer);

                log.info("Loaded pretrained hierarchical brain from {} (3 layers × {} neurons total)",
                        pretrainedDir.toAbsolutePath(),
                        12 + 8 + 5);
                return;
            } catch (Exception e) {
                log.warn("Failed to load pretrained brain from {}: {}. Using random brain.",
                        pretrainedDir.toAbsolutePath(), e.getMessage());
            }
        } else {
            log.info("No pretrained weights at {} — using random brain", pretrainedDir.toAbsolutePath());
        }
        // Fall back to random
        initializeRandom();
    }

    /**
     * Loads Qwen2.5-0.5B pretrained weights as a HierarchicalBrain.
     *
     * <p>Uses layers 0-5 (deeper layers for better features):
     * <ul>
     * <li>Layers 0-1 → sensor layer (12 neurons each)</li>
     * <li>Layers 2-3 → feature layer (8 neurons each)</li>
     * <li>Layers 4-5 → action layer (5 neurons each)</li>
     * </ul>
     */
    private void loadFromQwen(Path qwenDir) throws IOException {
        PretrainedLoader loader = new PretrainedLoader();
        String qwenModel = "Qwen2.5-0.5B";

        List<TruthTable> s0 = loader.loadLayer(qwenDir, qwenModel, 0);
        List<TruthTable> s1 = loader.loadLayer(qwenDir, qwenModel, 1);
        List<TruthTable> f2 = loader.loadLayer(qwenDir, qwenModel, 2);
        List<TruthTable> f3 = loader.loadLayer(qwenDir, qwenModel, 3);
        List<TruthTable> a4 = loader.loadLayer(qwenDir, qwenModel, 4);
        List<TruthTable> a5 = loader.loadLayer(qwenDir, qwenModel, 5);

        // Merge layer pairs: each sensor/feature/action layer gets neurons from 2 Qwen layers
        List<TruthTable> sensorTables = new ArrayList<>(s0);
        sensorTables.addAll(s1);
        List<TruthTable> featureTables = new ArrayList<>(f2);
        featureTables.addAll(f3);
        List<TruthTable> actionTables = new ArrayList<>(a4);
        actionTables.addAll(a5);

        NeuronLayer sensorLayer = buildLayer(sensorTables, 12, 12, 0);
        NeuronLayer featureLayer = buildLayer(featureTables, 8, 12, 1);
        NeuronLayer actionLayer = buildLayer(actionTables, 5, 8, 2);

        this.brain = new HierarchicalBrain(sensorLayer, featureLayer, actionLayer);
    }

    private static NeuronLayer buildLayer(List<TruthTable> tables, int neuronCount, int k,
                                           int layerIdx) {
        List<TruthTable> selected = new ArrayList<>(neuronCount);
        Random fallbackRng = new Random(42 + layerIdx);
        for (int i = 0; i < neuronCount; i++) {
            if (i < tables.size()) {
                selected.add(tables.get(i));
            } else {
                selected.add(TruthTable.random(k, fallbackRng));
            }
        }
        return NeuronLayer.fromTruthTables(selected);
    }

    public void initializeRandom() {
        this.brain = new HierarchicalBrain(rng);
        log.info("Agent brain initialized with hierarchical brain: {}", brain);
    }

    /**
     * Evaluates sensor input and returns the chosen action as a string.
     *
     * <p>Actions: MOVE_N, MOVE_S, MOVE_W, MOVE_E, STAY, MINE, CRAFT, EAT, TOOL_UP
     */
    public String act(long sensorBits) {
        HierarchicalBrain b = this.brain;
        int actionCode = b.decide(sensorBits);

        // Map 5-bit action code to behavior
        boolean bit0 = (actionCode & 0x01) != 0;
        boolean bit1 = (actionCode & 0x02) != 0;
        boolean bit2 = (actionCode & 0x04) != 0;
        boolean bit3 = (actionCode & 0x08) != 0;
        boolean bit4 = (actionCode & 0x10) != 0;

        // Priority-based action selection (matches original semantics)
        String action;
        if (bit4 && hungerUrgent(sensorBits)) {
            action = "EAT";
        } else if (bit3) {
            action = "CRAFT";
        } else if (bit2) {
            action = "TOOL_UP";
        } else if (bit1) {
            action = "MINE";
        } else if (bit0) {
            action = "MOVE_N";
        } else {
            action = "STAY";
        }

        // ── Stuck detection + exploration: if same action 30+ times, force random exploration for 80 ticks ──
        if (action.equals(lastAction)) {
            stuckCounter++;
            if (stuckCounter >= STUCK_THRESHOLD) {
                exploreTicks = EXPLORE_WINDOW;
                stuckCounter = 0;
                if (metrics != null) metrics.recordStuck();
                log.info("Stuck detected after {} STAY — enabling exploration mode for {} ticks", STUCK_THRESHOLD, EXPLORE_WINDOW);
            }
        } else {
            stuckCounter = 0;
        }

        if (exploreTicks > 0) {
            exploreTicks--;
            String[] moves = {"MOVE_N", "MOVE_S", "MOVE_W", "MOVE_E", "MINE", "MINE", "CRAFT", "MOVE_N"};
            String exploreAction = moves[rng.nextInt(moves.length)];
            if (rng.nextInt(4) == 0) { // 25% use brain, 75% random
                action = exploreAction;
            }
        }
        if (metrics != null) metrics.recordExploration(exploreTicks);

        lastAction = action;
        return action;
    }

    /**
     * Runs evolution training synchronously and updates the hierarchical brain.
     */
    public synchronized EvolutionResult train(int generations, int population, int k) {
        log.info("Starting training: generations={}, population={}, k={}", generations, population, k);

        FitnessFn fitness = new FitnessFn(K, K, 5, 10, 50, 3, rng);
        EvolutionLoop loop = new EvolutionLoop(generations, population, k, fitness, rng);
        loop.run();

        AgentBrain best = loop.bestBrain();

        // Build new action layer from the 4 evolved trees + 1 random
        List<DecisionTree> actionTrees = new ArrayList<>(5);
        actionTrees.add(best.nNeuron());
        actionTrees.add(best.sNeuron());
        actionTrees.add(best.wNeuron());
        actionTrees.add(best.eNeuron());
        actionTrees.add(DecisionTree.random(k, 8, rng));

        List<TruthTable> actionTables = actionTrees.stream()
                .map(t -> t.toTruthTable(k))
                .toList();

        NeuronLayer newActionLayer = NeuronLayer.fromTruthTables(actionTables);

        // Preserve sensor/feature layers, replace action layer
        this.brain = new HierarchicalBrain(
                brain.sensorLayer(), brain.featureLayer(), newActionLayer);

        List<Long> history = loop.bestFitnessHistory();
        long bestFit = history.isEmpty() ? 0 : history.get(history.size() - 1);

        log.info("Training complete: bestFitness={}, generations={}", bestFit, history.size());

        return new EvolutionResult(history, bestFit, history.size());
    }

    /**
     * Saves the hierarchical brain as Avro-encoded layers to a JSON file.
     */
    public Path save(String filePath) throws IOException {
        Path path = Path.of(filePath);
        ObjectNode root = MAPPER.createObjectNode();

        root.put("brain", Base64.getEncoder().encodeToString(brain.toAvroBytes()));
        root.put("k", K);
        root.put("version", "hierarchical-1");

        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        Files.writeString(path, json);

        log.info("Hierarchical brain saved to {}", path.toAbsolutePath());
        return path;
    }

    /**
     * Loads a hierarchical brain from a JSON file.
     */
    public void load(String filePath) throws IOException {
        Path path = Path.of(filePath);
        String json = Files.readString(path);
        ObjectNode root = (ObjectNode) MAPPER.readTree(json);

        String version = root.has("version") ? root.get("version").asText() : "flat";

        if ("hierarchical-1".equals(version)) {
            byte[] brainBytes = Base64.getDecoder().decode(root.get("brain").asText());
            this.brain = HierarchicalBrain.fromAvroBytes(brainBytes);
        } else {
            // Legacy flat format — load individual trees and convert
            DecisionTree move = decodeTree(root.get("moveTree").asText());
            DecisionTree mine = decodeTree(root.get("mineTree").asText());
            DecisionTree craft = decodeTree(root.get("craftTree").asText());
            DecisionTree eat = decodeTree(root.get("eatTree").asText());
            DecisionTree toolUp = decodeTree(root.get("toolUpTree").asText());

            this.brain = new HierarchicalBrain(rng);
            log.warn("Loaded legacy brain format — converted to hierarchical (other layers random)");
        }

        log.info("Brain loaded from {}", path.toAbsolutePath());
    }

    // ─── Backward-compatible accessors (deprecated) ───

    /**
     * @deprecated Use {@link #brain()} for hierarchical access instead.
     */
    @Deprecated
    public DecisionTree moveTree() {
        return brain.actionLayer().neurons().get(0);
    }

    /**
     * @deprecated Use {@link #brain()} for hierarchical access instead.
     */
    @Deprecated
    public DecisionTree mineTree() {
        return brain.actionLayer().neurons().get(1);
    }

    /**
     * @deprecated Use {@link #brain()} for hierarchical access instead.
     */
    @Deprecated
    public DecisionTree craftTree() {
        return brain.actionLayer().neurons().get(2);
    }

    /**
     * @deprecated Use {@link #brain()} for hierarchical access instead.
     */
    @Deprecated
    public DecisionTree eatTree() {
        return brain.actionLayer().neurons().get(3);
    }

    /**
     * @deprecated Use {@link #brain()} for hierarchical access instead.
     */
    @Deprecated
    public DecisionTree toolUpTree() {
        return brain.actionLayer().neurons().get(4);
    }

    /** Returns the hierarchical brain. */
    public HierarchicalBrain brain() {
        return brain;
    }

    // ─── Persistence helpers ───

    private static String encodeTree(DecisionTree tree) {
        TruthTable tt = tree.toTruthTable(K);
        return Base64.getEncoder().encodeToString(tt.toAvroBytes());
    }

    private static DecisionTree decodeTree(String base64) {
        byte[] avroBytes = Base64.getDecoder().decode(base64);
        TruthTable tt = TruthTable.fromAvroBytes(avroBytes);
        return decisionTreeFromTruthTable(tt);
    }

    static DecisionTree decisionTreeFromTruthTable(TruthTable tt) {
        int k = tt.k();
        return buildCompleteTree(tt.table(), k, 0, 0);
    }

    private static DecisionTree buildCompleteTree(BitSet table, int k, int bit, int start) {
        if (bit >= k) {
            return new DecisionTree.Leaf(table.get(start));
        }
        int step = 1 << bit;
        DecisionTree left = buildCompleteTree(table, k, bit + 1, start);
        DecisionTree right = buildCompleteTree(table, k, bit + 1, start + step);
        return new DecisionTree.Split(bit, left, right);
    }

    // ─── Utility ───

    private static boolean hungerUrgent(long sensorBits) {
        return ((sensorBits >> 13) & 0x7) <= 1;
    }

    // ─── Online training (hill-climbing) ───

    /** Feedback record: what sensor triggered what successful action. */
    public record FeedbackRecord(long sensorBits, boolean success) {}

    private final List<FeedbackRecord> recentFeedback = new ArrayList<>();
    private static final int MAX_FEEDBACK = 100;

    /** Records a feedback event for later online training. */
    public void recordFeedback(long sensorBits, boolean success) {
        synchronized (recentFeedback) {
            recentFeedback.add(new FeedbackRecord(sensorBits, success));
            if (recentFeedback.size() > MAX_FEEDBACK) {
                recentFeedback.remove(0);
            }
        }
    }

    /**
     * Online training via hill-climbing on the action layer neurons.
     *
     * <p>For each neuron in the action layer, generates random candidate trees
     * and keeps the one with the best accuracy against recent feedback.
     * This is much cheaper than full GA (no population, no 1000+ evaluations).
     *
     * @param iterations number of random candidates to try per neuron
     */
    public void onlineTrain(int iterations) {
        List<FeedbackRecord> snapshot;
        synchronized (recentFeedback) {
            if (recentFeedback.isEmpty()) {
                // No feedback — fallback to random mutation of action layer
                log.info("No feedback available — performing random mutation hill-climb");
                mutateActionLayer(iterations);
                return;
            }
            snapshot = List.copyOf(recentFeedback);
        }

        HierarchicalBrain current = this.brain;
        NeuronLayer actionLayer = current.actionLayer();
        List<DecisionTree> neurons = new ArrayList<>(actionLayer.neurons());

        for (int idx = 0; idx < neurons.size(); idx++) {
            DecisionTree currentTree = neurons.get(idx);
            double bestFitness = evaluateTreeFitness(currentTree, snapshot);

            for (int i = 0; i < iterations; i++) {
                DecisionTree candidate = DecisionTree.random(K, 10, rng);
                double candidateFitness = evaluateTreeFitness(candidate, snapshot);

                if (candidateFitness > bestFitness) {
                    neurons.set(idx, candidate);
                    bestFitness = candidateFitness;
                }
            }
        }

        List<TruthTable> actionTables = neurons.stream()
                .map(t -> t.toTruthTable(K))
                .toList();

        NeuronLayer newActionLayer = NeuronLayer.fromTruthTables(actionTables);
        this.brain = new HierarchicalBrain(
                current.sensorLayer(), current.featureLayer(), newActionLayer);

        log.info("Online training complete. Action layer updated with {} neurons.",
                neurons.size());
    }

    /**
     * Evaluates how well a tree predicts the feedback success outcomes.
     *
     * @return accuracy as a fraction in [0.0, 1.0]
     */
    private double evaluateTreeFitness(DecisionTree tree, List<FeedbackRecord> feedback) {
        if (feedback.isEmpty()) return 0.0;
        int correct = 0;
        for (FeedbackRecord fb : feedback) {
            BitSet input = toBitSet(fb.sensorBits);
            boolean prediction = tree.evaluate(input);
            if (prediction == fb.success) correct++;
        }
        return (double) correct / feedback.size();
    }

    /**
     * Loads specific pretrained layers for a role-based brain configuration.
     *
     * <p>Each role maps pretrained layer indices to specific layers of the
     * hierarchical brain.
     *
     * @param layerIndices pretrained layer indices to load (0-based);
     *                     index 0 → sensor layer, 1 → feature layer, 2 → action layer
     */
    public void loadLayers(int[] layerIndices) throws IOException {
        Path pretrainedDir = Path.of(PRETRAINED_DIR);
        if (!Files.isDirectory(pretrainedDir)) {
            log.warn("Pretrained directory not found: {}", pretrainedDir.toAbsolutePath());
            return;
        }

        PretrainedLoader loader = new PretrainedLoader();

        HierarchicalBrain current = this.brain;
        NeuronLayer sensorLayer = current.sensorLayer();
        NeuronLayer featureLayer = current.featureLayer();
        NeuronLayer actionLayer = current.actionLayer();

        for (int layerIdx : layerIndices) {
            if (layerIdx >= 0 && layerIdx < 6) {
                try {
                    List<TruthTable> tables = loader.loadLayer(pretrainedDir,
                            PRETRAINED_MODEL, layerIdx);
                    if (!tables.isEmpty()) {
                        // Map layer index 0,1 → sensor, 2,3 → feature, 4,5 → action
                        if (layerIdx <= 1) {
                            sensorLayer = buildLayer(tables, 12, 12, layerIdx);
                        } else if (layerIdx <= 3) {
                            featureLayer = buildLayer(tables, 8, 12, layerIdx);
                        } else {
                            actionLayer = buildLayer(tables, 5, 8, layerIdx);
                        }
                        log.debug("Loaded pretrained layer {}", layerIdx);
                    }
                } catch (Exception e) {
                    log.warn("Failed to load layer {}: {}", layerIdx, e.getMessage());
                }
            }
        }

        this.brain = new HierarchicalBrain(sensorLayer, featureLayer, actionLayer);
        log.info("Loaded {} pretrained layers for role-based brain", layerIndices.length);
    }

    // ─── Internal helpers ───

    private static BitSet toBitSet(long bits) {
        BitSet bs = new BitSet(64);
        for (int i = 0; i < 64; i++) {
            if ((bits & (1L << i)) != 0) bs.set(i);
        }
        return bs;
    }

    public record EvolutionResult(List<Long> history, long bestFitness, int generations) {}

    private void mutateActionLayer(int iterations) {
        NeuronLayer actionLayer = this.brain.actionLayer();
        List<DecisionTree> neurons = new ArrayList<>(actionLayer.neurons());
        for (int i = 0; i < Math.min(iterations, neurons.size()); i++) {
            int idx = rng.nextInt(neurons.size());
            neurons.set(idx, DecisionTree.random(K, 10, rng));
        }
        NeuronLayer newAction = new NeuronLayer(neurons, actionLayer.k());
        this.brain = new HierarchicalBrain(this.brain.sensorLayer(), this.brain.featureLayer(), newAction);
        log.info("Mutated {}/{} neurons in action layer", Math.min(iterations, neurons.size()), neurons.size());
    }
}
