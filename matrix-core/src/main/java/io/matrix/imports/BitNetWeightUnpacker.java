package io.matrix.imports;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * RUN 456 — BitNet b1.58 weight unpacker (DESIGN-54 §10, BitNet integration).
 *
 * <p>Unpacks BitNet b1.58 ternary weights from the safetensors packed
 * format used by Microsoft (microsoft/bitnet-b1.58-2B-4T).
 *
 * <h2>Packing format (per microsoft/BitNet utils/convert-hf-to-gguf-bitnet.py)</h2>
 * <ul>
 *   <li>Each {@code uint8} byte holds 4 ternary values packed as 2-bit chunks</li>
 *   <li>LSB-first: bits [1:0] → v[0], [3:2] → v[1], [5:4] → v[2], [7:6] → v[3]</li>
 *   <li>Encoding: {@code 0b00 = -1}, {@code 0b01 = 0}, {@code 0b10 = +1}, {@code 0b11 = unused}</li>
 *   <li>Packed row i maps to dequantized rows [4i, 4i+1, 4i+2, 4i+3]</li>
 *   <li>{@code weight_scale} (bf16, [1]) is per-tensor absmean scale:
 *       {@code W_dequant = ternary × scale}</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 *   float[] unpacked = BitNetWeightUnpacker.unpack(packedBytes, scale, 6912, 2560);
 *   // unpacked.length == 6912 * 2560 (row-major, ternary × scale values)
 * </pre>
 *
 * <h2>CONSTITUTION I</h2>
 * Pure function. No RNG, no wall-clock. Deterministic given inputs.
 */
public final class BitNetWeightUnpacker {

    /** Shift order for unpacking — must match Microsoft's reference: {0, 2, 4, 6}. */
    private static final int[] SHIFTS = {0, 2, 4, 6};

    private BitNetWeightUnpacker() {}

    /**
     * Unpack BitNet b1.58 weights to float values in {-scale, 0, +scale}.
     *
     * @param packedBytes uint8 packed weights of shape [outDim/4, inDim] (row-major)
     * @param scale       per-tensor scale (bf16 scalar converted to float)
     * @param outDim      true output dimension (must be divisible by 4)
     * @param inDim       input dimension
     * @return float array of length {@code outDim * inDim}, row-major,
     *         where each element is in {-scale, 0, +scale}
     */
    public static float[] unpack(byte[] packedBytes, float scale, int outDim, int inDim) {
        if (packedBytes == null) {
            throw new IllegalArgumentException("packedBytes is null");
        }
        if (outDim <= 0 || inDim <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        if (outDim % 4 != 0) {
            throw new IllegalArgumentException("outDim must be divisible by 4, got " + outDim);
        }
        int expectedPackedLen = (outDim / 4) * inDim;
        if (packedBytes.length != expectedPackedLen) {
            throw new IllegalArgumentException("packedBytes length " + packedBytes.length
                    + " != expected " + expectedPackedLen + " (outDim/4=" + (outDim / 4)
                    + " × inDim=" + inDim + ")");
        }

        int halfRowDim = outDim / 4;
        float[] out = new float[outDim * inDim];

        for (int i = 0; i < halfRowDim; i++) {
            int rowBase = i * inDim;
            int outBase = 4 * i * inDim;
            for (int j = 0; j < inDim; j++) {
                int b = packedBytes[rowBase + j] & 0xFF;
                for (int k = 0; k < 4; k++) {
                    int tern = ((b >> SHIFTS[k]) & 0x3) - 1; // {-1, 0, +1}
                    out[outBase + k * inDim + j] = tern * scale;
                }
            }
        }
        return out;
    }

    /**
     * Unpack to raw ternary values {-1, 0, +1} without applying scale.
     *
     * @param packedBytes uint8 packed weights
     * @param outDim      true output dimension
     * @param inDim       input dimension
     * @return float array of length {@code outDim * inDim}, row-major, values in {-1, 0, +1}
     */
    public static float[] unpackTernary(byte[] packedBytes, int outDim, int inDim) {
        if (packedBytes == null) {
            throw new IllegalArgumentException("packedBytes is null");
        }
        if (outDim <= 0 || inDim <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        if (outDim % 4 != 0) {
            throw new IllegalArgumentException("outDim must be divisible by 4, got " + outDim);
        }
        int expectedPackedLen = (outDim / 4) * inDim;
        if (packedBytes.length != expectedPackedLen) {
            throw new IllegalArgumentException("packedBytes length mismatch");
        }

        int halfRowDim = outDim / 4;
        float[] out = new float[outDim * inDim];

        for (int i = 0; i < halfRowDim; i++) {
            int rowBase = i * inDim;
            int outBase = 4 * i * inDim;
            for (int j = 0; j < inDim; j++) {
                int b = packedBytes[rowBase + j] & 0xFF;
                for (int k = 0; k < 4; k++) {
                    int tern = ((b >> SHIFTS[k]) & 0x3) - 1;
                    out[outBase + k * inDim + j] = tern;
                }
            }
        }
        return out;
    }

    /**
     * Convert a bf16 (16-bit) value to float32. BitNet stores {@code weight_scale}
     * as bf16 in safetensors format.
     *
     * @param bf16Bits raw 16-bit bfloat16 representation
     * @return float32 value
     */
    public static float bf16ToFloat(short bf16Bits) {
        int bits = bf16Bits & 0xFFFF;
        // bf16 layout: 1 sign + 8 exponent + 7 mantissa
        // Upcast by shifting left 16 to fill 32-bit float exponent
        int asInt = bits << 16;
        return Float.intBitsToFloat(asInt);
    }

    /**
     * Read a single bf16 value from a byte buffer at the given offset.
     */
    public static float readBf16(ByteBuffer buffer, int offset) {
        short bits = buffer.getShort(offset);
        return bf16ToFloat(bits);
    }

    /**
     * Unpack and dequantize in one step, reading scale from a bf16 byte buffer.
     */
    public static float[] unpackFromBf16(byte[] packedBytes, ByteBuffer scaleBuffer,
                                          int outDim, int inDim) {
        float scale = readBf16(scaleBuffer, 0);
        return unpack(packedBytes, scale, outDim, inDim);
    }
}
