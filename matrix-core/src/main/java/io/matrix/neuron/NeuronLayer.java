package io.matrix.neuron;

import io.matrix.agent.PretrainedLoader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * A composable layer of MPDT neurons.
 *
 * <p>Each neuron is a {@link DecisionTree} that processes a fixed-width slice
 * (k bits) of the input. The layer concatenates the per-neuron slices into a
 * single flattened input BitSet of size {@code outputWidth × k}.
 *
 * <p>Layers are designed to be stacked: the output of layer N (a BitSet of
 * {@code outputWidth} bits) can be padded and fed as input to layer N+1.
 *
 * <h3>Serialization</h3>
 * Uses a simple binary format: [neuronCount:int][k:int] then for each neuron
 * [avroLen:int][avroBytes:byte[]] where avroBytes is the Avro-serialized
 * {@link TruthTable}.
 */
public final class NeuronLayer {

    private final List<DecisionTree> neurons;
    private final int k;
    private final int outputWidth;

    /**
     * Creates a layer with randomly initialized neurons.
     *
     * @param neuronCount number of neurons (output width)
     * @param k           input width per neuron
     * @param rng         random generator for reproducibility
     */
    public NeuronLayer(int neuronCount, int k, Random rng) {
        if (neuronCount < 1) {
            throw new IllegalArgumentException("neuronCount must be >= 1, got: " + neuronCount);
        }
        if (k < 1) {
            throw new IllegalArgumentException("k must be >= 1, got: " + k);
        }
        this.k = k;
        this.outputWidth = neuronCount;
        this.neurons = new ArrayList<>(neuronCount);
        for (int i = 0; i < neuronCount; i++) {
            neurons.add(DecisionTree.random(k, Math.min(k, 8), rng));
        }
    }

    /**
     * Package-private constructor for {@link #fromTruthTables(List)}.
     */
    NeuronLayer(List<DecisionTree> neurons, int k) {
        this.neurons = List.copyOf(neurons);
        this.k = k;
        this.outputWidth = neurons.size();
    }

    /**
     * Evaluates all neurons on the input, producing {@code outputWidth} bits.
     *
     * @param input flattened BitSet of size at least {@code outputWidth × k};
     *              neuron {@code i} processes bits {@code [i*k, (i+1)*k)}
     * @return BitSet of size {@code outputWidth} (bit i = output of neuron i)
     */
    public BitSet evaluate(BitSet input) {
        BitSet output = new BitSet(outputWidth);
        for (int i = 0; i < outputWidth; i++) {
            BitSet neuronInput = input.get(i * k, (i + 1) * k);
            if (neurons.get(i).evaluate(neuronInput)) {
                output.set(i);
            }
        }
        return output;
    }

    /** Returns an unmodifiable view of the neurons in this layer. */
    public List<DecisionTree> neurons() {
        return Collections.unmodifiableList(neurons);
    }

    /** Input width per neuron. */
    public int k() {
        return k;
    }

    /** Number of neurons (equals output bit width). */
    public int outputWidth() {
        return outputWidth;
    }

    /**
     * Serializes this layer to bytes using per-neuron Avro truth tables.
     *
     * <p>Format: 4B neuronCount | 4B k | for each neuron: 4B len + Avro bytes.
     */
    public byte[] toAvroBytes() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            dos.writeInt(outputWidth);
            dos.writeInt(k);
            for (DecisionTree neuron : neurons) {
                TruthTable tt = neuron.toTruthTable(k);
                byte[] ttBytes = tt.toAvroBytes();
                dos.writeInt(ttBytes.length);
                dos.write(ttBytes);
            }
            dos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize NeuronLayer", e);
        }
    }

    /**
     * Deserializes a layer from bytes produced by {@link #toAvroBytes()}.
     */
    public static NeuronLayer fromAvroBytes(byte[] data) {
        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
            int neuronCount = dis.readInt();
            int k = dis.readInt();
            List<TruthTable> tables = new ArrayList<>(neuronCount);
            for (int i = 0; i < neuronCount; i++) {
                int len = dis.readInt();
                byte[] ttBytes = new byte[len];
                dis.readFully(ttBytes);
                tables.add(TruthTable.fromAvroBytes(ttBytes));
            }
            return fromTruthTables(tables);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize NeuronLayer", e);
        }
    }

    /**
     * Creates a layer from pretrained truth tables (one per neuron).
     *
     * <p>Each truth table is converted to an equivalent {@link DecisionTree}
     * via {@link PretrainedLoader#truthTableToTree(TruthTable)}.
     *
     * @param tables truth tables, one per neuron; all must have the same {@code k}
     * @return a new NeuronLayer with the given neurons
     * @throws IllegalArgumentException if tables is empty or k values differ
     */
    public static NeuronLayer fromTruthTables(List<TruthTable> tables) {
        if (tables.isEmpty()) {
            throw new IllegalArgumentException("At least one truth table required");
        }
        int k = tables.get(0).k();
        for (int i = 1; i < tables.size(); i++) {
            if (tables.get(i).k() != k) {
                throw new IllegalArgumentException(
                        "All truth tables must have the same k. Table 0 has k=" + k
                                + ", table " + i + " has k=" + tables.get(i).k());
            }
        }
        List<DecisionTree> trees = new ArrayList<>(tables.size());
        for (TruthTable tt : tables) {
            trees.add(PretrainedLoader.truthTableToTree(tt));
        }
        return new NeuronLayer(trees, k);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NeuronLayer that)) return false;
        return k == that.k && outputWidth == that.outputWidth && neurons.equals(that.neurons);
    }

    @Override
    public int hashCode() {
        return Objects.hash(neurons, k, outputWidth);
    }

    @Override
    public String toString() {
        return "NeuronLayer{neurons=" + outputWidth + ", k=" + k + "}";
    }
}
