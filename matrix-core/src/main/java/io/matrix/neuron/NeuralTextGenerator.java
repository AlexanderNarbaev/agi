package io.matrix.neuron;

import io.matrix.agent.PretrainedLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.List;
import java.util.Random;

/**
 * Neural text generator using MPDT boolean neurons.
 *
 * <p>Architecture:
 * <pre>
 *   Input:   64-bit sensor vector (text encoding)
 *   Layer 0: 32 neurons × k=16 → 32-bit feature vector
 *   Layer 1: 16 neurons × k=16 → 16-bit compressed vector
 *   Layer 2:  8 neurons × k=16 → 8-bit output (one ASCII character)
 * </pre>
 *
 * <p>Autoregressive generation: each output character is fed back as input
 * for the next character, enabling multi-character response generation.
 *
 * <p>No templates, no hardcoded responses — pure neural boolean logic.
 */
public final class NeuralTextGenerator {

    private static final int INPUT_BITS = 64;
    private static final int MAX_RESPONSE_LENGTH = 256;
    private static final String PRETRAINED_DIR = "models/pretrained";

    private final NeuronLayer encoderLayer;
    private final NeuronLayer compressionLayer;
    private final NeuronLayer outputLayer;
    private final Random rng;

    /**
     * Creates a generator with random weights.
     */
    public NeuralTextGenerator(Random rng) {
        this.rng = rng;
        this.encoderLayer = new NeuronLayer(32, 16, rng);
        this.compressionLayer = new NeuronLayer(16, 16, rng);
        this.outputLayer = new NeuronLayer(8, 16, rng);
    }

    /**
     * Creates a generator from pretrained layers.
     */
    public NeuralTextGenerator(NeuronLayer encoder, NeuronLayer compression, NeuronLayer output) {
        this.rng = new Random(42);
        this.encoderLayer = encoder;
        this.compressionLayer = compression;
        this.outputLayer = output;
    }

    /**
     * Loads pretrained weights from the models directory.
     *
     * <p>Tries to load from Qwen3-1.7B first, then falls back to other models.
     *
     * @return NeuralTextGenerator with pretrained weights, or null if loading fails
     */
    public static NeuralTextGenerator loadPretrained(Random rng) {
        Path pretrainedDir = Path.of(PRETRAINED_DIR);
        if (!Files.isDirectory(pretrainedDir)) {
            System.err.println("NeuralTextGenerator: No pretrained dir at " + pretrainedDir.toAbsolutePath());
            return null;
        }

        // Try models in priority order
        String[][] models = {
            {"qwen3-1.7b", "Qwen3-1.7B"},
            {"qwen3-0.6b", "Qwen3-0.6B"},
            {"deepseek-r1-distill-qwen-1.5b", "DeepSeek-R1-Distill-Qwen-1.5B"},
            {"qwen2.5-1.5b", "Qwen2.5-1.5B"},
            {"qwen2.5-0.5b", "Qwen2.5-0.5B"},
            {"smollm2-360m", "SmolLM2-360M"},
        };

        for (String[] model : models) {
            Path modelDir = pretrainedDir.resolve(model[0]);
            if (Files.isDirectory(modelDir)) {
                try {
                    NeuralTextGenerator gen = loadFromModel(modelDir, model[1], rng);
                    System.err.println("NeuralTextGenerator: Loaded pretrained from " + model[1]);
                    return gen;
                } catch (Exception e) {
                    System.err.println("NeuralTextGenerator: Failed to load " + model[1] + ": " + e.getMessage());
                }
            }
        }

        System.err.println("NeuralTextGenerator: No pretrained models loaded");
        return null;
    }

    /**
     * Loads weights from a specific model directory.
     */
    private static NeuralTextGenerator loadFromModel(Path modelDir, String modelName, Random rng) throws IOException {
        PretrainedLoader loader = new PretrainedLoader();

        // Load layers for encoder (layers 0-1), compression (layers 2-3), output (layers 4-5)
        List<TruthTable> l0 = loader.loadLayer(modelDir, modelName, 0);
        List<TruthTable> l1 = loader.loadLayer(modelDir, modelName, 1);
        List<TruthTable> l2 = loader.loadLayer(modelDir, modelName, 2);
        List<TruthTable> l3 = loader.loadLayer(modelDir, modelName, 3);
        List<TruthTable> l4 = loader.loadLayer(modelDir, modelName, 4);
        List<TruthTable> l5 = loader.loadLayer(modelDir, modelName, 5);

        // Build layers
        NeuronLayer encoder = buildLayer(l0, l1, 32, 16);
        NeuronLayer compression = buildLayer(l2, l3, 16, 16);
        NeuronLayer output = buildLayer(l4, l5, 8, 16);

        return new NeuralTextGenerator(encoder, compression, output);
    }

    /**
     * Builds a NeuronLayer from two truth table lists using pretrained weights.
     */
    private static NeuronLayer buildLayer(List<TruthTable> tables1, List<TruthTable> tables2, int neuronCount, int k) {
        // Combine tables from two layers
        java.util.List<TruthTable> allTables = new java.util.ArrayList<>(tables1);
        allTables.addAll(tables2);

        // Filter tables with matching k and limit to neuronCount
        List<TruthTable> matching = allTables.stream()
                .filter(t -> t.k() == k)
                .limit(neuronCount)
                .toList();

        if (matching.size() >= neuronCount) {
            // Use real pretrained weights via NeuronLayer.fromTruthTables
            return NeuronLayer.fromTruthTables(matching);
        }

        // Not enough matching tables — fill remaining with random, but use real ones first
        java.util.List<DecisionTree> neurons = new java.util.ArrayList<>();
        for (TruthTable table : matching) {
            neurons.add(PretrainedLoader.truthTableToTree(table));
        }
        // Fill remaining with random neurons
        Random fillRng = new Random(42);
        for (int i = neurons.size(); i < neuronCount; i++) {
            neurons.add(DecisionTree.random(k, Math.min(k, 8), fillRng));
        }
        return new NeuronLayer(neurons, k);
    }

    /**
     * Generates text autoregressively using the neural network.
     *
     * <p>Process:
     * 1. Encode input text to64-bit sensor vector
     * 2. Forward pass through encoder → compression → output (8 bits)
     * 3. Decode8-bit output to ASCII character
     * 4. Append character to response
     * 5. Shift input window and repeat until max length or null char
     *
     * @param inputText input text to respond to
     * @return generated response text
     */
    public String generate(String inputText) {
        if (inputText == null || inputText.isBlank()) {
            return "";
        }

        // Encode input to sensor vector
        long sensorState = encodeText(inputText);

        StringBuilder response = new StringBuilder();
        int maxLen = Math.min(MAX_RESPONSE_LENGTH, inputText.length() * 3 + 50);

        for (int i = 0; i < maxLen; i++) {
            // Forward pass through neural hierarchy
            int charCode = forwardPass(sensorState);

            // Stop on null character or control characters
            if (charCode == 0 || charCode > 127) {
                break;
            }

            char c = (char) charCode;
            response.append(c);

            // Autoregressive: shift state and incorporate new character
            sensorState = updateState(sensorState, c, i);
        }

        return response.toString().trim();
    }

    /**
     * Forward pass through the3-layer neural hierarchy.
     *
     * @param sensorState64-bit input state
     * @return8-bit output (character code)
     */
    private int forwardPass(long sensorState) {
        BitSet input = toBitSet(sensorState, INPUT_BITS);

        // Layer 0: encoder (32 neurons × k=16 → 32 bits)
        BitSet encoded = encoderLayer.evaluate(padInput(input, 32 * 16));

        // Layer 1: compression (16 neurons × k=16 → 16 bits)
        BitSet compressed = compressionLayer.evaluate(padInput(encoded, 16 * 16));

        // Layer 2: output (8 neurons × k=16 → 8 bits)
        BitSet outputBits = outputLayer.evaluate(padInput(compressed, 8 * 16));

        // Convert8-bit output to character code
        return bitsToCharCode(outputBits);
    }

    /**
     * Encodes text to a64-bit sensor vector using rolling hash.
     *
     * <p>Captures character-level patterns and word boundaries.
     */
    private long encodeText(String text) {
        long state = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // Rolling hash: shift and XOR with character
            state = Long.rotateLeft(state, 5) ^ c;
            // Mix in position
            state ^= (long) i * 0x9E3779B97F4A7C15L;
        }
        return state;
    }

    /**
     * Updates sensor state autoregressively with new character.
     *
     * <p>Shifts state left and mixes in the new character,
     * simulating a sliding context window.
     */
    private long updateState(long state, char c, int position) {
        // Shift state and incorporate new character
        state = Long.rotateLeft(state, 7) ^ c;
        // Mix in position to maintain temporal awareness
        state ^= (long) position * 0x517CC1B727220A95L;
        // Ensure some entropy from previous state
        state ^= state >>> 32;
        return state;
    }

    /**
     * Converts8-bit BitSet to character code (0-255).
     */
    private int bitsToCharCode(BitSet bits) {
        int code = 0;
        for (int i = 0; i < 8; i++) {
            if (bits.get(i)) {
                code |= (1 << i);
            }
        }
        return code;
    }

    /**
     * Converts long to BitSet of specified width.
     */
    private BitSet toBitSet(long value, int width) {
        BitSet bits = new BitSet(width);
        for (int i = 0; i < width; i++) {
            if ((value & (1L << i)) != 0) {
                bits.set(i);
            }
        }
        return bits;
    }

    /**
     * Pads input BitSet to required width by repeating pattern.
     */
    private BitSet padInput(BitSet input, int requiredWidth) {
        if (input.length() >= requiredWidth) {
            return input.get(0, requiredWidth);
        }
        BitSet padded = new BitSet(requiredWidth);
        // Copy original
        for (int i = 0; i < input.length(); i++) {
            if (input.get(i)) {
                padded.set(i);
            }
        }
        // Repeat pattern to fill width
        int srcLen = Math.max(1, input.length());
        for (int i = srcLen; i < requiredWidth; i++) {
            if (input.get(i % srcLen)) {
                padded.set(i);
            }
        }
        return padded;
    }

    /**
     * Returns the internal layers for serialization.
     */
    public NeuronLayer encoderLayer() { return encoderLayer; }
    public NeuronLayer compressionLayer() { return compressionLayer; }
    public NeuronLayer outputLayer() { return outputLayer; }
}
