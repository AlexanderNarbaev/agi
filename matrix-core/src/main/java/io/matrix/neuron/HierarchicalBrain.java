package io.matrix.neuron;

import io.matrix.agent.PretrainedLoader;
import io.matrix.reasoning.BrcChain;
import io.matrix.reasoning.BrcState;
import io.matrix.reasoning.BrcStep;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;

/**
 * Hierarchical MPDT brain with 3 layers.
 *
 * <pre>
 *   Layer 0 (sensor):  12 neurons × k=12 → 12-bit feature vector
 *   Layer 1 (feature):  8 neurons × k=12 →  8-bit compressed vector
 *   Layer 2 (action):   5 neurons × k=8  →  5-bit action code
 * </pre>
 *
 * <p>Feed-forward pipeline: sensors (20 bits) → L0 → L1 → L2 → action (0–31).
 * Each layer's output is flattened and padded to the required input width
 * of the next layer.
 *
 * <p>Total neurons: 25 (vs 5 in the flat AgentBrain).
 */
public final class HierarchicalBrain {

    private final NeuronLayer sensorLayer;
    private final NeuronLayer featureLayer;
    private final NeuronLayer actionLayer;

    /**
     * Creates a brain with randomly initialized layers.
     *
     * @param rng random generator for reproducibility
     */
    public HierarchicalBrain(Random rng) {
        this.sensorLayer = new NeuronLayer(12, 12, rng);
        this.featureLayer = new NeuronLayer(8, 12, rng);
        this.actionLayer = new NeuronLayer(5, 8, rng);
    }

    /**
     * Constructs a brain from pre-built layers (for pretrained loading or testing).
     */
    public HierarchicalBrain(NeuronLayer sensorLayer, NeuronLayer featureLayer, NeuronLayer actionLayer) {
        this.sensorLayer = sensorLayer;
        this.featureLayer = featureLayer;
        this.actionLayer = actionLayer;
    }

    /**
     * Processes 20-bit sensor input through the hierarchy and returns
     * a 5-bit action code (0–31).
     *
     * @param sensors 20-bit packed sensor data
     * @return action code in range [0, 31]
     */
    public int decide(long sensors) {
        BitSet input = toBitSet(sensors, 20);

        BitSet l0 = sensorLayer.evaluate(padInput(input, sensorLayer.outputWidth() * sensorLayer.k()));
        BitSet l1 = featureLayer.evaluate(padInput(l0, featureLayer.outputWidth() * featureLayer.k()));
        BitSet l2 = actionLayer.evaluate(padInput(l1, actionLayer.outputWidth() * actionLayer.k()));

        long[] words = l2.toLongArray();
        return (int) ((words.length > 0 ? words[0] : 0L) & 0x1F);
    }

    // ─── Layer accessors ───

    public NeuronLayer sensorLayer() { return sensorLayer; }
    public NeuronLayer featureLayer() { return featureLayer; }
    public NeuronLayer actionLayer() { return actionLayer; }

    /**
     * Returns the layers as a list for BRC integration.
     */
    public List<NeuronLayer> layers() {
        return List.of(sensorLayer, featureLayer, actionLayer);
    }

    /**
     * Creates a BrcChain from this brain's layers.
     *
     * @param maxSteps maximum reasoning steps (0 = unlimited)
     * @param convergenceThreshold Hamming distance threshold for convergence
     * @return BrcChain using this brain's layers
     */
    public BrcChain toBrcChain(int maxSteps, int convergenceThreshold) {
        BrcStep sensorStep = new BrcStep(sensorLayer, "sensor", convergenceThreshold);
        BrcStep featureStep = new BrcStep(featureLayer, "feature", convergenceThreshold);
        BrcStep actionStep = new BrcStep(actionLayer, "action", convergenceThreshold);

        return BrcChain.builder()
            .addStep(sensorStep)
            .addStep(featureStep)
            .addStep(actionStep)
            .maxSteps(maxSteps)
            .earlyStopping(true)
            .build();
    }

    /**
     * Decides with multi-step reasoning using BRC.
     *
     * <p>Processes input through the3-layer hierarchy with convergence
     * detection and early stopping. Returns both the action code and
     * the reasoning state.
     *
     * @param sensors sensor input
     * @param maxSteps maximum reasoning steps (0 = unlimited)
     * @return BrcState with final output and reasoning history
     */
    public BrcState decideWithReasoning(long sensors, int maxSteps) {
        BitSet input = toBitSet(sensors, 20);
        BrcChain chain = toBrcChain(maxSteps, 2); // threshold=2 bits
        return chain.evaluate(input, 20);
    }

    // ─── Serialization ───

    /**
     * Serializes all 3 layers to a single byte array.
     *
     * <p>Format: 4B layerCount(=3) | for each layer: 4B len + bytes.
     */
    public byte[] toAvroBytes() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            dos.writeInt(3); // layer count

            byte[] sBytes = sensorLayer.toAvroBytes();
            dos.writeInt(sBytes.length);
            dos.write(sBytes);

            byte[] fBytes = featureLayer.toAvroBytes();
            dos.writeInt(fBytes.length);
            dos.write(fBytes);

            byte[] aBytes = actionLayer.toAvroBytes();
            dos.writeInt(aBytes.length);
            dos.write(aBytes);

            dos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize HierarchicalBrain", e);
        }
    }

    /**
     * Deserializes a brain from bytes produced by {@link #toAvroBytes()}.
     */
    public static HierarchicalBrain fromAvroBytes(byte[] data) {
        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
            int layerCount = dis.readInt();
            if (layerCount != 3) {
                throw new IllegalArgumentException("Expected 3 layers, got: " + layerCount);
            }

            int sLen = dis.readInt();
            byte[] sBytes = new byte[sLen];
            dis.readFully(sBytes);

            int fLen = dis.readInt();
            byte[] fBytes = new byte[fLen];
            dis.readFully(fBytes);

            int aLen = dis.readInt();
            byte[] aBytes = new byte[aLen];
            dis.readFully(aBytes);

            return new HierarchicalBrain(
                    NeuronLayer.fromAvroBytes(sBytes),
                    NeuronLayer.fromAvroBytes(fBytes),
                    NeuronLayer.fromAvroBytes(aBytes));
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize HierarchicalBrain", e);
        }
    }

    // ─── Pretrained loading ───

    /**
     * Loads pretrained weights from model directory into all 3 layers.
     *
     * <p>Maps pretrained layer files (layer0..layer2) to sensor/feature/action layers.
     * If a pretrained file has fewer neurons than needed, the remaining slots
     * are filled with random neurons (seeded for reproducibility).
     *
     * @param modelDir directory containing Avro layer files
     * @param rng      random generator for fallback neurons
     * @return a brain with pretrained weights
     */
    public static HierarchicalBrain fromPretrained(String modelDir, Random rng) {
        var loader = new PretrainedLoader();
        Path dir = Path.of(modelDir);

        NeuronLayer sensorLayer = loadPretrainedLayer(loader, dir, 0, 12, 12, rng);
        NeuronLayer featureLayer = loadPretrainedLayer(loader, dir, 1, 8, 12, rng);
        NeuronLayer actionLayer = loadPretrainedLayer(loader, dir, 2, 5, 8, rng);

        return new HierarchicalBrain(sensorLayer, featureLayer, actionLayer);
    }

    private static NeuronLayer loadPretrainedLayer(PretrainedLoader loader, Path dir,
                                                    int layerIdx, int neuronCount, int k,
                                                    Random rng) {
        try {
            List<TruthTable> tables = loader.loadLayer(dir, "SmolLM2-135M-synth", layerIdx);
            List<TruthTable> selected = new ArrayList<>(neuronCount);

            for (int i = 0; i < neuronCount; i++) {
                if (i < tables.size()) {
                    selected.add(tables.get(i));
                } else {
                    // Deterministic fallback for missing neurons
                    Random fallbackRng = new Random(rng.nextLong());
                    selected.add(TruthTable.random(k, fallbackRng));
                }
            }
            return NeuronLayer.fromTruthTables(selected);
        } catch (IOException e) {
            // If file is missing, fall back to random layer
            Random fallbackRng = new Random(rng.nextLong());
            return new NeuronLayer(neuronCount, k, fallbackRng);
        }
    }

    // ─── Utility ───

    /**
     * Pads a BitSet to the specified total bit count, preserving existing bits.
     * Bits beyond {@code totalBits} are discarded.
     */
    static BitSet padInput(BitSet input, int totalBits) {
        BitSet padded = new BitSet(totalBits);
        for (int i = input.nextSetBit(0); i >= 0 && i < totalBits; i = input.nextSetBit(i + 1)) {
            padded.set(i);
        }
        return padded;
    }

    static BitSet toBitSet(long bits, int count) {
        BitSet bs = new BitSet(count);
        for (int i = 0; i < count; i++) {
            if ((bits & (1L << i)) != 0) {
                bs.set(i);
            }
        }
        return bs;
    }

    @Override
    public String toString() {
        return "HierarchicalBrain{sensor=" + sensorLayer.outputWidth()
                + "×k" + sensorLayer.k()
                + ", feature=" + featureLayer.outputWidth()
                + "×k" + featureLayer.k()
                + ", action=" + actionLayer.outputWidth()
                + "×k" + actionLayer.k() + "}";
    }
}
