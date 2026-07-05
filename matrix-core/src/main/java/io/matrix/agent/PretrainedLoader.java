package io.matrix.agent;

import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.TruthTable;
import io.matrix.simulation.AgentBrain;

import org.apache.avro.file.DataFileReader;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Loads MPDT neurons pre-trained from transformer model weights.
 *
 * <p>Reads Avro container files produced by {@code scripts/pretrain_neurons.py}
 * and converts the extracted truth tables into {@link DecisionTree} instances
 * for use in {@link AgentBrain}.
 *
 * <h3>Typical usage</h3>
 * <pre>{@code
 *   var loader = new PretrainedLoader();
 *   AgentBrain brain = loader.loadPretrainedBrain("models/pretrained", 0);
 *   // Use brain.act(sensorBits) in simulation
 * }</pre>
 *
 * <h3>Avro file naming convention</h3>
 * Files are named {@code <model_name>_layer<N>_neurons.avro} as produced
 * by the Python pretraining script.
 */
public final class PretrainedLoader {

    /** Maximum number of neurons loaded per file (safety limit). */
    private static final int MAX_NEURONS_PER_FILE = 10_000;

    /**
     * Loads all truth tables from an Avro file for a specific layer.
     *
     * @param avroFilePath path to the Avro container file
     * @return list of truth tables, one per neuron record
     * @throws IOException if the file cannot be read or is corrupt
     */
    public List<TruthTable> loadTruthTables(Path avroFilePath) throws IOException {
        if (!Files.exists(avroFilePath)) {
            throw new IOException("Avro file not found: " + avroFilePath);
        }

        List<TruthTable> tables = new ArrayList<>();
        GenericDatumReader<GenericRecord> datumReader = new GenericDatumReader<>();

        try (DataFileReader<GenericRecord> reader =
                     new DataFileReader<>(avroFilePath.toFile(), datumReader)) {

            int count = 0;
            while (reader.hasNext()) {
                if (count >= MAX_NEURONS_PER_FILE) {
                    break;
                }
                GenericRecord record = reader.next();
                TruthTable table = recordToTruthTable(record);
                tables.add(table);
                count++;
            }
        }

        return tables;
    }

    /**
     * Loads all truth tables from a directory containing layer Avro files.
     *
     * @param modelDir   directory containing Avro files
     * @param modelName  model name prefix (e.g. "SmolLM2-135M-synth")
     * @param layerIndex layer index to load
     * @return list of truth tables for the specified layer
     * @throws IOException if files cannot be read
     */
    public List<TruthTable> loadLayer(Path modelDir, String modelName,
                                      int layerIndex) throws IOException {
        String filename = modelName + "_layer" + layerIndex + "_neurons.avro";
        Path avroPath = modelDir.resolve(filename);
        return loadTruthTables(avroPath);
    }

    /**
     * Loads truth tables and builds an {@link AgentBrain} from the first
     * 4 neurons in the specified layer.
     *
     * <p>The four neurons are assigned to directions N, S, W, E in order.
     * If fewer than 4 neurons are available, remaining directions get
     * constant-{@code false} neurons.
     *
     * @param modelDir   directory containing Avro files
     * @param modelName  model name prefix
     * @param layerIndex layer index to load neurons from
     * @return an AgentBrain populated with pretrained decision trees
     * @throws IOException if files cannot be read
     */
    public AgentBrain loadPretrainedBrain(Path modelDir, String modelName,
                                          int layerIndex) throws IOException {
        List<TruthTable> tables = loadLayer(modelDir, modelName, layerIndex);

        DecisionTree n = tables.size() > 0 ? truthTableToTree(tables.get(0))
                : DecisionTree.constant(false);
        DecisionTree s = tables.size() > 1 ? truthTableToTree(tables.get(1))
                : DecisionTree.constant(false);
        DecisionTree w = tables.size() > 2 ? truthTableToTree(tables.get(2))
                : DecisionTree.constant(false);
        DecisionTree e = tables.size() > 3 ? truthTableToTree(tables.get(3))
                : DecisionTree.constant(false);

        return new AgentBrain(n, s, w, e);
    }

    /**
     * Convenience overload — uses the default model name matching the Python
     * script default ({@code SmolLM2-135M-synth}).
     */
    public AgentBrain loadPretrainedBrain(String modelDir, int layerIndex)
            throws IOException {
        return loadPretrainedBrain(Path.of(modelDir), "SmolLM2-135M-synth",
                layerIndex);
    }

    /**
     * Converts a TruthTable to an equivalent DecisionTree.
     *
     * <p>Builds a complete binary decision tree that tests input bits
     * {@code 0, 1, ..., k-1} in order. Each leaf corresponds to exactly
     * one input vector and returns the truth table value for that input.
     *
     * <p>For {@code k = 16}, the tree has 65,535 split nodes and 65,536
     * leaves (~4.5 MB). For large k, consider using the truth table
     * directly via {@link TruthTable#evaluate(int)}.
     *
     * @param table the truth table to convert
     * @return an equivalent decision tree
     */
    public static DecisionTree truthTableToTree(TruthTable table) {
        int k = table.k();
        if (k == 0) {
            return DecisionTree.constant(table.evaluate(0));
        }
        return buildTree(table, k, 0, 0);
    }

    /**
     * Recursively builds a complete binary tree for the given truth table.
     *
     * @param table    the truth table being converted
     * @param k        total number of input bits
     * @param bitIdx   current bit index being tested (0..k-1)
     * @param prefix   bit pattern fixed by ancestor splits (bits below bitIdx)
     * @return a DecisionTree node for this subtree
     */
    private static DecisionTree buildTree(TruthTable table, int k,
                                          int bitIdx, int prefix) {
        if (bitIdx >= k) {
            return new DecisionTree.Leaf(table.evaluate(prefix));
        }

        int withZero = prefix;                        // bitIdx = 0
        int withOne = prefix | (1 << bitIdx);         // bitIdx = 1

        DecisionTree left = buildTree(table, k, bitIdx + 1, withZero);
        DecisionTree right = buildTree(table, k, bitIdx + 1, withOne);

        return new DecisionTree.Split(bitIdx, left, right);
    }

    /**
     * Extracts a TruthTable from an Avro GenericRecord.
     *
     * <p>The record must have fields {@code k} (int) and {@code truthTable}
     * (bytes) matching the MPDTNeuron schema.
     */
    private static TruthTable recordToTruthTable(GenericRecord record) {
        int k = (int) record.get("k");
        ByteBuffer buf = (ByteBuffer) record.get("truthTable");
        byte[] rawBytes = new byte[buf.remaining()];
        buf.get(rawBytes);
        BitSet table = BitSet.valueOf(rawBytes);
        return TruthTable.of(k, table);
    }

    /**
     * Deserializes a single TruthTable from Avro-encoded bytes (same format
     * as {@link TruthTable#toAvroBytes()}).
     *
     * <p>This method is useful when reading individual records from a
     * non-container source (e.g., Kafka, Redis).
     */
    public static TruthTable fromAvroBytes(byte[] bytes) {
        return TruthTable.fromAvroBytes(bytes);
    }
}
