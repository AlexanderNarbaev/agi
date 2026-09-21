package io.matrix.neuron;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * DESIGN-22 §4 — Compact enriched neurons into a binary format.
 *
 * <p>BLN v2 format (extends RUN 325's BLN v1):
 * <pre>
 *   Header:
 *     MAGIC   = "BLN\2"           (4 bytes)
 *     VERSION = 2                  (int)
 *     TOTAL_NEURONS                (int)
 *     BLOCK_SIZE                   (int; default 256)
 *     BLOCK_COUNT                  (int)
 *
 *   Per block:
 *     k                            (int; neuron input arity)
 *     neuron_count                 (int)
 *     reference_index              (int; index within block)
 *     reference_full               (full EnrichedNeuron bytes)
 *     per_delta_neuron:
 *       hamming_xor_bytes          (long[k] bytes)
 *       magnitude_delta            (float, 4 bytes)
 *       chemical_delta[4]          (4 floats, 16 bytes)
 *       tag_byte                   (1 byte; Neurotransmitter ordinal)
 * </pre>
 *
 * <p>Reference neuron is the centroid (smallest sum of pairwise Hamming
 * distances to others). Pure function — same input → same output.
 */
public final class NeuronCompactor {

    /** Default block size — matches the BLOCK_SIZE field in the header. */
    public static final int DEFAULT_BLOCK_SIZE = 256;
    /** BLN v2 magic. */
    public static final byte[] MAGIC = new byte[]{'B', 'L', 'N', 2};

    private NeuronCompactor() {}

    /**
     * Compact a list of enriched neurons into a byte array.
     *
     * @param neurons    list of enriched neurons to compact
     * @param blockSize  neurons per block (default 256)
     * @return byte[] containing the BLN v2 representation
     */
    public static byte[] compact(List<EnrichedNeuron> neurons, int blockSize) {
        if (neurons == null) throw new IllegalArgumentException("neurons");
        if (blockSize <= 0) blockSize = DEFAULT_BLOCK_SIZE;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            out.write(MAGIC);
            out.writeInt(2);                       // VERSION
            out.writeInt(neurons.size());
            out.writeInt(blockSize);
            int blockCount = (neurons.size() + blockSize - 1) / blockSize;
            out.writeInt(blockCount);

            for (int b = 0; b < blockCount; b++) {
                int from = b * blockSize;
                int to = Math.min(from + blockSize, neurons.size());
                List<EnrichedNeuron> block = neurons.subList(from, to);
                writeBlock(out, block);
            }
        } catch (IOException e) {
            throw new RuntimeException("compaction failed", e);
        }
        return baos.toByteArray();
    }

    /** Convenience overload with default block size. */
    public static byte[] compact(List<EnrichedNeuron> neurons) {
        return compact(neurons, DEFAULT_BLOCK_SIZE);
    }

    /**
     * Load enriched neurons back from a BLN v2 byte array. Inverse of
     * {@link #compact}.
     */
    public static List<EnrichedNeuron> decompact(byte[] data) {
        if (data == null || data.length < 16) {
            throw new IllegalArgumentException("invalid BLN v2 data");
        }
        List<EnrichedNeuron> result = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(
                new java.io.ByteArrayInputStream(data))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (magic[0] != MAGIC[0] || magic[1] != MAGIC[1]
                    || magic[2] != MAGIC[2] || magic[3] != MAGIC[3]) {
                throw new IOException("bad magic: " + new String(magic));
            }
            int version = in.readInt();
            if (version != 2) throw new IOException("bad version: " + version);
            int totalNeurons = in.readInt();
            int blockSize = in.readInt();
            int blockCount = in.readInt();

            for (int b = 0; b < blockCount; b++) {
                readBlock(in, result, blockSize);
            }
            if (result.size() != totalNeurons) {
                throw new IOException(
                        "size mismatch: header=" + totalNeurons
                                + " actual=" + result.size());
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("decompaction failed", e);
        }
    }

    /**
     * Compression ratio: bytes_in / bytes_out. > 1.0 means compression.
     */
    public static double compressionRatio(int rawBytes, int compactedBytes) {
        if (compactedBytes == 0) return 0.0;
        return (double) rawBytes / compactedBytes;
    }

    /** Save compacted bytes to a file. */
    public static void saveTo(Path file, List<EnrichedNeuron> neurons) throws IOException {
        Files.createDirectories(file.getParent());
        byte[] data = compact(neurons);
        Files.write(file, data);
    }

    /** Load compacted bytes from a file. */
    public static List<EnrichedNeuron> loadFrom(Path file) throws IOException {
        return decompact(Files.readAllBytes(file));
    }

    // -- internals --

    private static void writeBlock(DataOutputStream out, List<EnrichedNeuron> block)
            throws IOException {
        int n = block.size();
        int k = n == 0 ? 0 : block.get(0).table().k();
        out.writeInt(k);
        out.writeInt(n);

        // Reference: pick the centroid (smallest sum of distances)
        int refIdx = pickCentroid(block);
        out.writeInt(refIdx);
        EnrichedNeuron ref = block.get(refIdx);
        writeFullNeuron(out, ref);

        // Per-neuron delta (skip reference at refIdx)
        for (int i = 0; i < n; i++) {
            if (i == refIdx) continue;
            EnrichedNeuron nrn = block.get(i);
            // XOR table
            BitSet xor = (BitSet) ref.table().table().clone();
            xor.xor(nrn.table().table());
            byte[] xorBytes = xor.toByteArray();
            out.writeInt(xorBytes.length);
            out.write(xorBytes);
            // Magnitude delta (float)
            out.writeFloat((float) (nrn.magnitude() - ref.magnitude()));
            // Chemical deltas (4 floats)
            for (int j = 0; j < EnrichedNeuron.CHEMICAL_DIM; j++) {
                out.writeFloat((float)
                        (nrn.chemicalVector()[j] - ref.chemicalVector()[j]));
            }
            // Tag byte
            out.writeByte((byte) nrn.tag().ordinal());
        }
    }

    private static void readBlock(DataInputStream in, List<EnrichedNeuron> acc,
                                  int blockSize) throws IOException {
        int k = in.readInt();
        int n = in.readInt();
        int refIdx = in.readInt();
        EnrichedNeuron ref = readFullNeuron(in, k);

        // Place reference at refIdx, fill other slots with deltas
        List<EnrichedNeuron> block = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            if (i == refIdx) {
                block.add(ref);
            } else {
                int xorLen = in.readInt();
                byte[] xorBytes = new byte[xorLen];
                in.readFully(xorBytes);
                BitSet xor = BitSet.valueOf(xorBytes);
                // Reconstruct table: ref XOR xor
                BitSet reconstructed = (BitSet) ref.table().table().clone();
                reconstructed.xor(xor);
                TruthTable table = TruthTable.of(k, reconstructed);
                float magDelta = in.readFloat();
                double magnitude = ref.magnitude() + magDelta;
                double[] chemical = new double[EnrichedNeuron.CHEMICAL_DIM];
                for (int j = 0; j < EnrichedNeuron.CHEMICAL_DIM; j++) {
                    chemical[j] = ref.chemicalVector()[j] + in.readFloat();
                }
                int tagOrdinal = in.readByte();
                Neurotransmitter tag = Neurotransmitter.values()[tagOrdinal];
                // Clamp to valid ranges
                magnitude = Math.max(0.0, Math.min(1.0, magnitude));
                for (int j = 0; j < EnrichedNeuron.CHEMICAL_DIM; j++) {
                    chemical[j] = Math.max(0.0, Math.min(1.0, chemical[j]));
                }
                block.add(new EnrichedNeuron(table, magnitude, chemical, tag));
            }
        }
        acc.addAll(block);
    }

    private static void writeFullNeuron(DataOutputStream out, EnrichedNeuron nrn)
            throws IOException {
        int k = nrn.table().k();
        out.writeInt(k);
        byte[] tableBytes = nrn.table().table().toByteArray();
        out.writeInt(tableBytes.length);
        out.write(tableBytes);
        out.writeFloat((float) nrn.magnitude());
        for (int j = 0; j < EnrichedNeuron.CHEMICAL_DIM; j++) {
            out.writeFloat((float) nrn.chemicalVector()[j]);
        }
        out.writeByte((byte) nrn.tag().ordinal());
    }

    private static EnrichedNeuron readFullNeuron(DataInputStream in, int k)
            throws IOException {
        int declaredK = in.readInt();
        int len = in.readInt();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        BitSet bs = BitSet.valueOf(bytes);
        TruthTable table = TruthTable.of(declaredK, bs);
        float mag = in.readFloat();
        double[] chem = new double[EnrichedNeuron.CHEMICAL_DIM];
        for (int j = 0; j < EnrichedNeuron.CHEMICAL_DIM; j++) {
            chem[j] = in.readFloat();
        }
        int tagOrdinal = in.readByte();
        Neurotransmitter tag = Neurotransmitter.values()[tagOrdinal];
        return new EnrichedNeuron(table, mag, chem, tag);
    }

    /**
     * Pick the centroid: neuron with smallest sum of pairwise Hamming
     * distances to all others in the block.
     */
    private static int pickCentroid(List<EnrichedNeuron> block) {
        if (block.size() <= 1) return 0;
        int bestIdx = 0;
        double bestSum = Double.POSITIVE_INFINITY;
        for (int i = 0; i < block.size(); i++) {
            double sum = 0;
            for (int j = 0; j < block.size(); j++) {
                if (i != j) {
                    sum += EnrichedNeuron.hammingDistance(block.get(i), block.get(j));
                }
            }
            if (sum < bestSum) {
                bestSum = sum;
                bestIdx = i;
            }
        }
        return bestIdx;
    }
}
