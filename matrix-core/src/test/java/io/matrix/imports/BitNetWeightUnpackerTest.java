package io.matrix.imports;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class BitNetWeightUnpackerTest {

    @Test
    void byteZeroUnpacksToAllZeros() {
        // byte = 0b00000000 → 4 values of 0b00 = -1, -1, -1, -1
        byte[] packed = new byte[4]; // all zeros
        float[] result = BitNetWeightUnpacker.unpackTernary(packed, 16, 1);
        // 16 values, all should be -1
        for (float v : result) {
            assertThat(v).isEqualTo(-1.0f);
        }
    }

    @Test
    void byteMaxUnpacksToAllTwos() {
        // byte = 0b11111111: each 2-bit pair is 0b11 = 3, our formula maps to 3-1=2.
        // 0b11 is "unused" per Microsoft spec, but for robustness we handle it
        // consistently (maps to ternary=2). Real BitNet weights do not use 0b11.
        byte[] packed = new byte[]{(byte) 0xFF};
        float[] result = BitNetWeightUnpacker.unpackTernary(packed, 4, 1);
        for (float v : result) {
            assertThat(v).isEqualTo(2.0f);
        }
    }

    @Test
    void byteAllZerosWithAlternatingBits() {
        // byte = 0b00010101 = 21: bits [1:0]=01=0, [3:2]=01=0, [5:4]=01=0, [7:6]=00=-1
        // Shift order [0, 2, 4, 6] reads LSB first
        // → unpacked in shift order: v[0]=01=0, v[1]=01=0, v[2]=01=0, v[3]=00=-1
        // Stored in rows [4i+0, 4i+1, 4i+2, 4i+3]
        byte[] packed = new byte[]{(byte) 21};
        float[] result = BitNetWeightUnpacker.unpackTernary(packed, 4, 1);
        assertThat(result).containsExactly(0.0f, 0.0f, 0.0f, -1.0f);
    }

    @Test
    void bytePattern85() {
        // byte = 0b01010101 = 85: all 4 pairs = 0b01 = 0 → all zeros
        byte[] packed = new byte[]{(byte) 85};
        float[] result = BitNetWeightUnpacker.unpackTernary(packed, 4, 1);
        assertThat(result).containsExactly(0.0f, 0.0f, 0.0f, 0.0f);
    }

    @Test
    void bytePattern149() {
        // byte = 0b10010101 = 149: bits [1:0]=01=0, [3:2]=01=0, [5:4]=01=0, [7:6]=10=+1
        // Shift order: v[0]=01=0, v[1]=01=0, v[2]=01=0, v[3]=10=+1
        byte[] packed = new byte[]{(byte) 149};
        float[] result = BitNetWeightUnpacker.unpackTernary(packed, 4, 1);
        assertThat(result).containsExactly(0.0f, 0.0f, 0.0f, 1.0f);
    }

    @Test
    void unpackAppliesScale() {
        byte[] packed = new byte[]{(byte) 149}; // → [0, 0, 0, +1]
        float[] result = BitNetWeightUnpacker.unpack(packed, 2.0f, 4, 1);
        assertThat(result).containsExactly(0.0f, 0.0f, 0.0f, 2.0f);
    }

    @Test
    void unpackMultiRowPreservesRowGrouping() {
        // Two rows of 4 columns (8 output × 1 in_dim)
        // Row 0 packed: 0b00010101 (21) → [0, 0, 0, -1]
        // Row 1 packed: 0b10010101 (149) → [0, 0, 0, +1]
        byte[] packed = new byte[]{(byte) 21, (byte) 149};
        float[] result = BitNetWeightUnpacker.unpackTernary(packed, 8, 1);
        // Rows 0-3 = first group, rows 4-7 = second group
        assertThat(result).containsExactly(0.0f, 0.0f, 0.0f, -1.0f,
                                            0.0f, 0.0f, 0.0f, 1.0f);
    }

    @Test
    void unpackMatchesExpectedDistributionForBitNet2B() {
        // Build a synthetic BitNet-like weight distribution
        // Restrict to bytes that only use 0b00 (=-1), 0b01 (=0), 0b10 (=+1) so
        // the unpacker produces values only in {-1, 0, +1}.
        // Construct bytes from 2-bit pairs: only use combinations of 0, 1, 2.
        int outDim = 32, inDim = 64;
        byte[] packed = new byte[(outDim / 4) * inDim];
        java.util.Random rng = new java.util.Random(42);
        for (int i = 0; i < packed.length; i++) {
            int b = 0;
            for (int k = 0; k < 4; k++) {
                int two_bit = rng.nextInt(3); // only 0, 1, 2 (skip 0b11)
                b |= (two_bit << (2 * k));
            }
            packed[i] = (byte) b;
        }
        float[] result = BitNetWeightUnpacker.unpackTernary(packed, outDim, inDim);
        assertThat(result).hasSize(outDim * inDim);
        for (float v : result) {
            assertThat(v).isIn(-1.0f, 0.0f, 1.0f);
        }
    }

    @Test
    void bf16ToFloatMatchesExpectedValues() {
        // bf16 0x3F80 = 1.0 (sign=0, exp=0x7F, mantissa=0)
        assertThat(BitNetWeightUnpacker.bf16ToFloat((short) 0x3F80)).isEqualTo(1.0f);
        // bf16 0x4000 = 2.0 (sign=0, exp=0x80, mantissa=0)
        assertThat(BitNetWeightUnpacker.bf16ToFloat((short) 0x4000)).isEqualTo(2.0f);
        // bf16 0xBF80 = -1.0 (sign=1, exp=0x7F, mantissa=0)
        assertThat(BitNetWeightUnpacker.bf16ToFloat((short) 0xBF80)).isEqualTo(-1.0f);
    }

    @Test
    void readBf16FromBuffer() {
        ByteBuffer buf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort(0, (short) 0x3F80); // bf16 = 1.0
        assertThat(BitNetWeightUnpacker.readBf16(buf, 0)).isEqualTo(1.0f);
    }

    @Test
    void unpackFromBf16() {
        byte[] packed = new byte[]{(byte) 149}; // → [0, 0, 0, +1]
        ByteBuffer scaleBuf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        scaleBuf.putShort(0, (short) 0x4000); // bf16 = 2.0
        float[] result = BitNetWeightUnpacker.unpackFromBf16(
                packed, scaleBuf, 4, 1);
        assertThat(result).containsExactly(0.0f, 0.0f, 0.0f, 2.0f);
    }

    @Test
    void rejectsNullInputs() {
        assertThatThrownBy(() -> BitNetWeightUnpacker.unpack(null, 1.0f, 4, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BitNetWeightUnpacker.unpackTernary(null, 4, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonMultipleOf4OutDim() {
        byte[] packed = new byte[3];
        assertThatThrownBy(() -> BitNetWeightUnpacker.unpack(packed, 1.0f, 6, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsWrongPackedLength() {
        // outDim=8, inDim=4 → expected length = 2*4 = 8
        byte[] packed = new byte[5]; // wrong
        assertThatThrownBy(() -> BitNetWeightUnpacker.unpack(packed, 1.0f, 8, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveDimensions() {
        byte[] packed = new byte[4];
        assertThatThrownBy(() -> BitNetWeightUnpacker.unpack(packed, 1.0f, 0, 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BitNetWeightUnpacker.unpack(packed, 1.0f, 4, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
