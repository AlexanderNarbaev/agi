package io.matrix.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.matrix.evolution.EvolutionLoop;
import io.matrix.evolution.FitnessFn;
import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.TruthTable;
import io.matrix.redis.NeuronCacheService;
import io.matrix.simulation.AgentBrain;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.BitSet;
import java.util.List;
import java.util.Random;

@ApplicationScoped
public class AgentBrainService {

    private static final Logger log = LoggerFactory.getLogger(AgentBrainService.class);
    private static final int K = 20;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private volatile DecisionTree moveTree;
    private volatile DecisionTree mineTree;
    private volatile DecisionTree craftTree;
    private volatile DecisionTree eatTree;
    private volatile DecisionTree toolUpTree;
    private final Random rng = new Random();

    @Inject
    NeuronCacheService neuronCache;

    public AgentBrainService() {
        initializeRandom();
    }

    public void initializeRandom() {
        this.moveTree = DecisionTree.random(K, 10, rng);
        this.mineTree = DecisionTree.random(K, 8, rng);
        this.craftTree = DecisionTree.random(K, 8, rng);
        this.eatTree = DecisionTree.random(K, 6, rng);
        this.toolUpTree = DecisionTree.random(K, 6, rng);
        log.info("Agent brain initialized with random trees (k={})", K);
    }

    /**
     * Evaluates sensor input and returns the chosen action as a string.
     *
     * <p>Actions: MOVE_N, MOVE_S, MOVE_W, MOVE_E, STAY, MINE, CRAFT, EAT, TOOL_UP
     */
    public String act(long sensorBits) {
        BitSet input = toBitSet(sensorBits);
        DecisionTree move = this.moveTree;
        DecisionTree mine = this.mineTree;
        DecisionTree craft = this.craftTree;
        DecisionTree eat = this.eatTree;
        DecisionTree toolUp = this.toolUpTree;

        if (eat.evaluate(input) && hungerUrgent(sensorBits)) {
            return "EAT";
        }
        if (craft.evaluate(input)) {
            return "CRAFT";
        }
        if (toolUp.evaluate(input)) {
            return "TOOL_UP";
        }
        if (mine.evaluate(input)) {
            if (move.evaluate(input)) {
                return pickDirection(move, input);
            }
            return "MINE";
        }
        return pickDirection(move, input);
    }

    private String pickDirection(DecisionTree moveTree, BitSet input) {
        if (moveTree.evaluate(input)) return "MOVE_N";
        if (moveTree.evaluate(shiftInput(input, 1))) return "MOVE_S";
        if (moveTree.evaluate(shiftInput(input, 2))) return "MOVE_W";
        if (moveTree.evaluate(shiftInput(input, 3))) return "MOVE_E";
        return "STAY";
    }

    /**
     * Runs evolution training synchronously and updates the brain trees.
     */
    public synchronized EvolutionResult train(int generations, int population, int k) {
        log.info("Starting training: generations={}, population={}, k={}", generations, population, k);

        FitnessFn fitness = new FitnessFn(K, K, 5, 10, 50, 3, rng);
        EvolutionLoop loop = new EvolutionLoop(generations, population, k, fitness, rng);
        loop.run();

        AgentBrain best = loop.bestBrain();
        this.moveTree = best.nNeuron();
        this.mineTree = best.sNeuron();
        this.craftTree = best.wNeuron();
        this.eatTree = best.eNeuron();
        this.toolUpTree = best.nNeuron();

        List<Long> history = loop.bestFitnessHistory();
        long bestFit = history.isEmpty() ? 0 : history.get(history.size() - 1);

        log.info("Training complete: bestFitness={}, generations={}", bestFit, history.size());

        return new EvolutionResult(history, bestFit, history.size());
    }

    /**
     * Saves brain trees as Avro-encoded truth tables to a JSON file.
     */
    public Path save(String filePath) throws IOException {
        Path path = Path.of(filePath);
        ObjectNode root = MAPPER.createObjectNode();

        root.put("moveTree", encodeTree(moveTree));
        root.put("mineTree", encodeTree(mineTree));
        root.put("craftTree", encodeTree(craftTree));
        root.put("eatTree", encodeTree(eatTree));
        root.put("toolUpTree", encodeTree(toolUpTree));
        root.put("k", K);

        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        Files.writeString(path, json);

        log.info("Brain saved to {}", path.toAbsolutePath());
        return path;
    }

    /**
     * Loads brain trees from a JSON file containing Avro-encoded truth tables.
     */
    public void load(String filePath) throws IOException {
        Path path = Path.of(filePath);
        String json = Files.readString(path);
        ObjectNode root = (ObjectNode) MAPPER.readTree(json);

        this.moveTree = decodeTree(root.get("moveTree").asText());
        this.mineTree = decodeTree(root.get("mineTree").asText());
        this.craftTree = decodeTree(root.get("craftTree").asText());
        this.eatTree = decodeTree(root.get("eatTree").asText());
        this.toolUpTree = decodeTree(root.get("toolUpTree").asText());

        log.info("Brain loaded from {}", path.toAbsolutePath());
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

    /**
     * Builds a complete binary decision tree from a truth table.
     */
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

    // ─── Utility methods (replicated from NeuralBrain) ───

    private static boolean hungerUrgent(long sensorBits) {
        return ((sensorBits >> 13) & 0x7) <= 1;
    }

    private static BitSet toBitSet(long bits) {
        BitSet bs = new BitSet(64);
        for (int i = 0; i < 64; i++) {
            if ((bits & (1L << i)) != 0) bs.set(i);
        }
        return bs;
    }

    private static BitSet shiftInput(BitSet input, int mod) {
        BitSet shifted = (BitSet) input.clone();
        shifted.set(0, mod);
        return shifted;
    }

    // ─── Accessors ───

    public DecisionTree moveTree() { return moveTree; }
    public DecisionTree mineTree() { return mineTree; }
    public DecisionTree craftTree() { return craftTree; }
    public DecisionTree eatTree() { return eatTree; }
    public DecisionTree toolUpTree() { return toolUpTree; }

    public record EvolutionResult(List<Long> history, long bestFitness, int generations) {}
}
