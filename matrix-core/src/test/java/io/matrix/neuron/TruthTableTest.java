package io.matrix.neuron;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.BitSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TruthTableTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 8, 12, 16})
    void randomTableShouldHaveCorrectSize(int k) {
        TruthTable tt = TruthTable.random(k);
        assertThat(tt.k()).isEqualTo(k);
        assertThat(tt.size()).isEqualTo(1 << k);
        assertThat(tt.table().length()).isLessThanOrEqualTo(1 << k);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 17, 100})
    void shouldRejectInvalidK(int k) {
        assertThatThrownBy(() -> TruthTable.random(k))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> TruthTable.of(k, new BitSet()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldEvaluateConstantTrue() {
        for (int k = 1; k <= 4; k++) {
            int size = 1 << k;
            BitSet bits = new BitSet(size);
            bits.set(0, size);
            TruthTable tt = TruthTable.of(k, bits);

            for (int input = 0; input < size; input++) {
                assertThat(tt.evaluate(input))
                        .as("k=%d, input=%d", k, input)
                        .isTrue();
            }
        }
    }

    @Test
    void shouldEvaluateConstantFalse() {
        for (int k = 1; k <= 4; k++) {
            int size = 1 << k;
            BitSet bits = new BitSet(size);
            TruthTable tt = TruthTable.of(k, bits);

            for (int input = 0; input < size; input++) {
                assertThat(tt.evaluate(input))
                        .as("k=%d, input=%d", k, input)
                        .isFalse();
            }
        }
    }

    @Test
    void shouldEvaluateCorrectlyForAllInputsK2() {
        evaluateAllInputsForK(2);
    }

    @Test
    void shouldEvaluateCorrectlyForAllInputsK3() {
        evaluateAllInputsForK(3);
    }

    @Test
    void shouldEvaluateCorrectlyForAllInputsK4() {
        evaluateAllInputsForK(4);
    }

    private void evaluateAllInputsForK(int k) {
        int size = 1 << k;
        BitSet bits = new BitSet(size);

        for (int i = 0; i < size; i++) {
            if (Integer.bitCount(i) % 2 == 0) {
                bits.set(i);
            }
        }

        TruthTable tt = TruthTable.of(k, bits);

        for (int input = 0; input < size; input++) {
            boolean expected = Integer.bitCount(input) % 2 == 0;
            boolean viaInt = tt.evaluate(input);
            BitSet bs = toBitSet(input, k);
            boolean viaBitSet = tt.evaluate(bs);
            boolean viaLong = tt.evaluate(new long[]{input});

            assertThat(viaInt).as("k=%d, input=%d", k, input).isEqualTo(expected);
            assertThat(viaBitSet).as("k=%d, input=%d via BitSet", k, input).isEqualTo(expected);
            assertThat(viaLong).as("k=%d, input=%d via long[]", k, input).isEqualTo(expected);
        }
    }

    @Test
    void shouldEvaluateLongArrayInput() {
        TruthTable tt = TruthTable.fromLong(3, 0b10101010);

        assertThat(tt.evaluate(new long[]{0b000})).isFalse();
        assertThat(tt.evaluate(new long[]{0b001})).isTrue();
        assertThat(tt.evaluate(new long[]{0b010})).isFalse();
        assertThat(tt.evaluate(new long[]{0b011})).isTrue();
        assertThat(tt.evaluate(new long[]{0b100})).isFalse();
        assertThat(tt.evaluate(new long[]{0b101})).isTrue();
        assertThat(tt.evaluate(new long[]{0b110})).isFalse();
        assertThat(tt.evaluate(new long[]{0b111})).isTrue();
    }

    @Test
    void shouldMaskExtraBitsInInput() {
        TruthTable tt = TruthTable.fromLong(3, 0b10101010);

        assertThat(tt.evaluate(new long[]{0b1001})).isTrue();
        assertThat(tt.evaluate(new long[]{0b0000})).isFalse();
        assertThat(tt.evaluate(new long[]{0b1111})).isTrue();
    }

    @Test
    void isConstantShouldWork() {
        int k = 4;
        int size = 1 << k;
        BitSet allOnes = new BitSet(size);
        allOnes.set(0, size);
        assertThat(TruthTable.of(k, allOnes).isConstant()).isTrue();

        BitSet allZeros = new BitSet(size);
        assertThat(TruthTable.of(k, allZeros).isConstant()).isTrue();

        BitSet mixed = new BitSet(size);
        mixed.set(0);
        assertThat(TruthTable.of(k, mixed).isConstant()).isFalse();
    }

    @Test
    void fromLongShouldWork() {
        TruthTable tt = TruthTable.fromLong(2, 0b1001);
        assertThat(tt.k()).isEqualTo(2);
        assertThat(tt.evaluate(0)).isTrue();
        assertThat(tt.evaluate(1)).isFalse();
        assertThat(tt.evaluate(2)).isFalse();
        assertThat(tt.evaluate(3)).isTrue();
    }

    @Test
    void shouldEqualAndHash() {
        TruthTable a = TruthTable.fromLong(3, 0b10101010);
        TruthTable b = TruthTable.fromLong(3, 0b10101010);
        TruthTable c = TruthTable.fromLong(3, 0b11111111);

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void avroSerializationRoundtrip() {
        TruthTable original = TruthTable.random(8);
        byte[] bytes = original.toAvroBytes();
        TruthTable restored = TruthTable.fromAvroBytes(bytes);

        assertThat(restored.k()).isEqualTo(original.k());
        assertThat(restored.table()).isEqualTo(original.table());
        assertThat(original).isEqualTo(restored);
    }

    @Test
    void avroSerializationRoundtripK16() {
        TruthTable original = TruthTable.random(16);
        byte[] bytes = original.toAvroBytes();
        TruthTable restored = TruthTable.fromAvroBytes(bytes);

        assertThat(restored.k()).isEqualTo(original.k());
        assertThat(restored.table()).isEqualTo(original.table());
    }

    @Test
    void avroSerializationRoundtripK1() {
        TruthTable original = TruthTable.random(1);
        byte[] bytes = original.toAvroBytes();
        TruthTable restored = TruthTable.fromAvroBytes(bytes);

        assertThat(restored.k()).isEqualTo(original.k());
        assertThat(restored.table()).isEqualTo(original.table());
    }

    private static BitSet toBitSet(int value, int bits) {
        BitSet bs = new BitSet(bits);
        for (int i = 0; i < bits; i++) {
            if ((value & (1 << i)) != 0) {
                bs.set(i);
            }
        }
        return bs;
    }
}
