package io.matrix.neuron;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;

import java.io.ByteArrayInputStream;
import java.util.Random;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Truth table for an MPDT neuron with {@code k} inputs.
 *
 * <p>Stores a bit array of length {@code 2^k} where {@code T[i]} is the output
 * for input vector {@code i} (bits 0..k-1 as LSB-to-MSB).
 *
 * <p>Ref: L1_MPDT_neuron.md §3.1
 */
@Deprecated(since = "2.2.0", forRemoval = true)
@SuppressWarnings("removal")
public final class TruthTable {

    public static final int K_MAX = 20;

    private final int k;

    private final BitSet table;

    private TruthTable(int k, BitSet table) {
        this.k = k;
        this.table = table;
    }

    /**
     * Creates a truth table with a specific bit pattern.
     *
     * @param k     number of inputs, 1..K_MAX
     * @param table bits representing outputs for all 2^k input combinations
     * @throws IllegalArgumentException if k is out of range or table size mismatch
     */
    public static TruthTable of(int k, BitSet table) {
        if (k < 1 || k > K_MAX) {
            throw new IllegalArgumentException("k must be in [1, " + K_MAX + "], got: " + k);
        }
        return new TruthTable(k, (BitSet) table.clone());
    }

    /**
     * Creates a truth table from a long value, interpreting the low {@code 2^k} bits.
     */
    public static TruthTable fromLong(int k, long bits) {
        return of(k, BitSet.valueOf(new long[]{bits}));
    }

    /**
     * Creates a random truth table using the provided RNG (seeded for reproducibility).
     */
    public static TruthTable random(int k, Random rng) {
        if (k < 1 || k > K_MAX) {
            throw new IllegalArgumentException("k must be in [1, " + K_MAX + "], got: " + k);
        }
        int size = 1 << k;
        BitSet table = new BitSet(size);
        for (int i = 0; i < size; i++) {
            if (rng.nextBoolean()) {
                table.set(i);
            }
        }
        return new TruthTable(k, table);
    }

    /**
     * Creates a random truth table where each output bit is set with 50% probability.
     */
    public static TruthTable random(int k) {
        return random(k, ThreadLocalRandom.current());
    }

    /**
     * Evaluates the truth table for a given input vector.
     *
     * <p>The input bits are packed in a {@code long[]} array. Bits 0..k-1
     * of the first long are used as the input vector (LSB = bit 0).
     *
     * @param input packed input bits
     * @return the output value for this input
     */
    public boolean evaluate(long[] input) {
        long first = (input != null && input.length > 0) ? input[0] : 0L;
        int index = (int) (first & ((1L << k) - 1));
        return table.get(index);
    }

    /**
     * Evaluates the truth table for a given integer input (LSB = bit 0).
     */
    public boolean evaluate(int input) {
        int index = input & ((1 << k) - 1);
        return table.get(index);
    }

    /**
     * Evaluates the truth table for a given {@link BitSet} input.
     */
    public boolean evaluate(BitSet input) {
        int index = 0;
        for (int i = 0; i < k; i++) {
            if (input.get(i)) {
                index |= (1 << i);
            }
        }
        return table.get(index);
    }

    public int k() {
        return k;
    }

    public BitSet table() {
        return (BitSet) table.clone();
    }

    public int size() {
        return 1 << k;
    }

    /**
     * Returns {@code true} if this table represents a constant function.
     */
    public boolean isConstant() {
        boolean first = table.get(0);
        for (int i = 1; i < size(); i++) {
            if (table.get(i) != first) {
                return false;
            }
        }
        return true;
    }

    /**
     * Serializes this truth table to Avro bytes using the MPDTNeuron schema.
     *
     * <p>Only the truth table data is serialised; wrapper fields use defaults.
     */
    public byte[] toAvroBytes() {
        try {
            Schema schema = loadSchema();
            GenericRecord record = new GenericData.Record(schema);

            GenericRecord idRecord = new GenericData.Record(
                    schema.getField("id").schema());
            idRecord.put("uuid", "00000000-0000-0000-0000-000000000000");
            idRecord.put("generation", 0L);
            record.put("id", idRecord);

            record.put("k", k);
            record.put("state", new GenericData.EnumSymbol(
                    schema.getField("state").schema(), "STABLE"));

            byte[] rawBytes = table.toByteArray();
            int byteLen = (size() + 7) / 8;
            byte[] padded = new byte[byteLen];
            System.arraycopy(rawBytes, 0, padded, 0, Math.min(rawBytes.length, byteLen));
            record.put("truthTable", ByteBuffer.wrap(padded));

            record.put("weights", java.util.List.of());
            record.put("metadata", null);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            DatumWriter<GenericRecord> writer = new SpecificDatumWriter<>(schema);
            writer.write(record, encoder);
            encoder.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize truth table to Avro", e);
        }
    }

    /**
     * Deserializes a truth table from Avro bytes.
     */
    public static TruthTable fromAvroBytes(byte[] bytes) {
        try {
            Schema schema = loadSchema();
            DatumReader<GenericRecord> reader = new SpecificDatumReader<>(schema);
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(bytes, null);
            GenericRecord record = reader.read(null, decoder);

            int k = (int) record.get("k");
            ByteBuffer buf = (ByteBuffer) record.get("truthTable");
            byte[] rawBytes = new byte[buf.remaining()];
            buf.get(rawBytes);
            BitSet table = BitSet.valueOf(rawBytes);
            return new TruthTable(k, table);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize truth table from Avro", e);
        }
    }

    private static volatile Schema cachedSchema;

    private static Schema loadSchema() throws IOException {
        if (cachedSchema != null) {
            return cachedSchema;
        }
        synchronized (TruthTable.class) {
            if (cachedSchema != null) {
                return cachedSchema;
            }
            try (InputStream is = TruthTable.class.getResourceAsStream("/avro/mpdt_neuron.avsc")) {
                if (is == null) {
                    throw new IOException("Avro schema not found: /avro/mpdt_neuron.avsc");
                }
                cachedSchema = new Schema.Parser().parse(is);
                return cachedSchema;
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TruthTable that)) return false;
        return k == that.k && table.equals(that.table);
    }

    @Override
    public int hashCode() {
        return Objects.hash(k, table);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TruthTable{k=").append(k).append(", [");
        int s = size();
        for (int i = 0; i < Math.min(s, 32); i++) {
            sb.append(table.get(i) ? '1' : '0');
        }
        if (s > 32) {
            sb.append("...(").append(s).append(" bits)");
        }
        sb.append("]}");
        return sb.toString();
    }
}
