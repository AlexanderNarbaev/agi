package io.matrix.compression;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

/**
 * Boolean vector compression using RLE (sparse) and bitmask (dense) strategies.
 *
 * <p>Auto-selects the best method based on vector density:
 * <ul>
 *   <li>Density &lt; 30% → RLE (Run-Length Encoding)</li>
 *   <li>Density ≥ 30% → Bitmask (delta encoding of set bits)</li>
 * </ul>
 *
 * <p>RLE format: sequence of (runLength:varint, value:1bit) pairs.
 * <p>Bitmask format: list of set-bit positions as varint deltas.
 *
 * <p>Compression ratio &gt;2x for sparse vectors (&lt;10% density).
 */
public final class BooleanCompressor {

    /** Compression method selector. */
    public enum Method {
        /** Run-Length Encoding — optimal for sparse vectors. */
        RLE,
        /** Bitmask delta encoding — optimal for dense vectors. */
        BITMASK
    }

    /** Below this density, bitmask (position encoding) is used; above, RLE. */
    private static final double BITMASK_SPARSE_THRESHOLD = 0.10;

    private BooleanCompressor() {
    }

    /**
     * Compresses a Boolean vector using the best strategy for its density.
     *
     * @param bits   the bit vector to compress
     * @param length logical length in bits
     * @return compressed representation
     * @throws IllegalArgumentException if length is negative
     */
    public static Compressed compress(BitSet bits, int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must be >= 0, got: " + length);
        }
        if (length == 0) {
            return new Compressed(Method.RLE, new byte[0], 0, 0);
        }

        int ones = bits.cardinality();
        double density = (double) ones / length;

        if (density < BITMASK_SPARSE_THRESHOLD) {
            return compressBitmask(bits, length);
        } else {
            return compressRle(bits, length);
        }
    }

    /**
     * Decompresses a compressed Boolean vector.
     *
     * @param compressed the compressed data
     * @param length     logical length in bits
     * @return restored bit vector
     */
    public static BitSet decompress(Compressed compressed, int length) {
        if (length == 0) {
            return new BitSet();
        }
        return switch (compressed.method()) {
            case RLE -> decompressRle(compressed.data(), length);
            case BITMASK -> decompressBitmask(compressed.data(), length);
        };
    }

    // ─── RLE implementation ───

    /**
     * Compresses using Run-Length Encoding.
     *
     * <p>Format: sequence of varint-encoded run lengths, alternating 0-runs and 1-runs.
     * Always starts with a 0-run (may be length 0).
     */
    private static Compressed compressRle(BitSet bits, int length) {
        List<byte[]> chunks = new ArrayList<>();
        int totalBytes = 0;

        boolean currentBit = false; // start expecting 0-runs
        int pos = 0;
        int totalBits = bits.cardinality();

        while (pos < length) {
            int runStart = pos;
            if (!currentBit) {
                // Count zeros
                int nextOne = bits.nextSetBit(pos);
                int runEnd = (nextOne < 0) ? length : nextOne;
                int runLength = runEnd - runStart;
                byte[] encoded = encodeVarint(runLength);
                chunks.add(encoded);
                totalBytes += encoded.length;
                pos = runEnd;
            } else {
                // Count ones
                int nextZero = bits.nextClearBit(pos);
                int runEnd = (nextZero < 0) ? length : nextZero;
                int runLength = runEnd - runStart;
                byte[] encoded = encodeVarint(runLength);
                chunks.add(encoded);
                totalBytes += encoded.length;
                pos = runEnd;
            }
            currentBit = !currentBit;
        }

        // If we ended on a 1-run boundary, append a trailing 0-run of length 0
        if (currentBit) {
            byte[] encoded = encodeVarint(0);
            chunks.add(encoded);
            totalBytes += encoded.length;
        }

        byte[] data = new byte[totalBytes];
        int offset = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, data, offset, chunk.length);
            offset += chunk.length;
        }

        int originalBytes = (length + 7) / 8;
        return new Compressed(Method.RLE, data, originalBytes, length);
    }

    private static BitSet decompressRle(byte[] data, int length) {
        BitSet result = new BitSet(length);
        int pos = 0;
        int dataIndex = 0;
        boolean currentBit = false;

        while (pos < length && dataIndex < data.length) {
            int[] varintResult = decodeVarint(data, dataIndex);
            int runLength = varintResult[0];
            dataIndex = varintResult[1];

            if (currentBit) {
                result.set(pos, pos + runLength);
            }
            pos += runLength;
            currentBit = !currentBit;
        }

        return result;
    }

    // ─── Bitmask implementation ───

    /**
     * Compresses using bitmask delta encoding.
     *
     * <p>Format: count of set bits (varint), then delta-encoded positions as varints.
     */
    private static Compressed compressBitmask(BitSet bits, int length) {
        int ones = bits.cardinality();
        List<byte[]> chunks = new ArrayList<>();
        int totalBytes = 0;

        // Encode count
        byte[] countBytes = encodeVarint(ones);
        chunks.add(countBytes);
        totalBytes += countBytes.length;

        // Encode deltas
        int prev = -1;
        int idx = bits.nextSetBit(0);
        while (idx >= 0) {
            int delta = idx - prev;
            byte[] deltaBytes = encodeVarint(delta);
            chunks.add(deltaBytes);
            totalBytes += deltaBytes.length;
            prev = idx;
            idx = bits.nextSetBit(idx + 1);
        }

        byte[] data = new byte[totalBytes];
        int offset = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, data, offset, chunk.length);
            offset += chunk.length;
        }

        int originalBytes = (length + 7) / 8;
        return new Compressed(Method.BITMASK, data, originalBytes, length);
    }

    private static BitSet decompressBitmask(byte[] data, int length) {
        BitSet result = new BitSet(length);
        int dataIndex = 0;

        // Decode count
        int[] countResult = decodeVarint(data, dataIndex);
        int count = countResult[0];
        dataIndex = countResult[1];

        // Decode deltas
        int position = -1;
        for (int i = 0; i < count && dataIndex < data.length; i++) {
            int[] deltaResult = decodeVarint(data, dataIndex);
            int delta = deltaResult[0];
            dataIndex = deltaResult[1];
            position += delta;
            result.set(position);
        }

        return result;
    }

    // ─── Varint encoding ───

    static byte[] encodeVarint(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Cannot encode negative varint: " + value);
        }
        if (value < 128) {
            return new byte[]{(byte) value};
        }
        // Multi-byte varint (7 bits per byte, MSB = continuation)
        int temp = value;
        int len = 0;
        while (temp > 0) {
            len++;
            temp >>>= 7;
        }
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++) {
            result[i] = (byte) ((value & 0x7F) | (i < len - 1 ? 0x80 : 0));
            value >>>= 7;
        }
        return result;
    }

    static int[] decodeVarint(byte[] data, int offset) {
        int result = 0;
        int shift = 0;
        int pos = offset;
        while (pos < data.length) {
            byte b = data[pos];
            result |= (b & 0x7F) << shift;
            pos++;
            if ((b & 0x80) == 0) {
                break;
            }
            shift += 7;
        }
        return new int[]{result, pos};
    }

    /**
     * Result of compressing a Boolean vector.
     *
     * @param method          compression method used
     * @param data            compressed bytes
     * @param originalSize    original size in bytes
     * @param bitLength       logical bit length
     */
    public record Compressed(Method method, byte[] data, int originalSize, int bitLength) {

        public Compressed {
            Objects.requireNonNull(method, "method");
            Objects.requireNonNull(data, "data");
        }

        /** Compressed size in bytes. */
        public int compressedSize() {
            return data.length;
        }

        /** Compression ratio (original / compressed). Returns {@link Double#MAX_VALUE} if compressed is 0. */
        public double compressionRatio() {
            if (data.length == 0) {
                return originalSize == 0 ? 1.0 : Double.MAX_VALUE;
            }
            return (double) originalSize / data.length;
        }
    }
}
