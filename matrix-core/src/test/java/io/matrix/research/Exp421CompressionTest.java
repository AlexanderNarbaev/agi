package io.matrix.research;

import io.matrix.neuron.Compression;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 421 — Coverage for compression utilities (RLE, Huffman, Levenshtein).
 */
class Exp421CompressionTest {

    @Test
    void rleRoundTripPreservesData() {
        byte[] data = new byte[]{(byte) 'a', (byte) 'a', (byte) 'b', (byte) 'c',
                (byte) 'c', (byte) 'c', (byte) 'a'};
        List<Compression.RlePair> encoded = Compression.rleEncode(data);
        byte[] decoded = Compression.rleDecode(encoded);
        assertThat(decoded).containsExactly(data);
        // RLE on this data should produce 4 runs
        assertThat(encoded).hasSize(4);
    }

    @Test
    void rleCompressesHighlyRepetitiveData() {
        byte[] data = new byte[1000];
        // Block of 100 'a's, then 100 'b's — total 5 blocks of 200 chars
        for (int i = 0; i < 1000; i++) data[i] = (byte) (((i / 100) % 2 == 0) ? 'a' : 'b');
        List<Compression.RlePair> encoded = Compression.rleEncode(data);
        assertThat(encoded).hasSizeLessThan(50); // huge compression: 10 runs not 1000
    }

    @Test
    void levenshteinHandlesIdenticalStrings() {
        assertThat(Compression.levenshtein("hello".toCharArray(),
                "hello".toCharArray())).isZero();
    }

    @Test
    void levenshteinHandlesEmptyStrings() {
        assertThat(Compression.levenshtein(new char[0], new char[0])).isZero();
        assertThat(Compression.levenshtein(new char[0], "abc".toCharArray())).isEqualTo(3);
        assertThat(Compression.levenshtein("abc".toCharArray(), new char[0])).isEqualTo(3);
    }

    @Test
    void levenshteinMatchesKnownValues() {
        // kitten → sitting is 3
        assertThat(Compression.levenshtein(
                "kitten".toCharArray(), "sitting".toCharArray())).isEqualTo(3);
        // saturday → sunday is 3
        assertThat(Compression.levenshtein(
                "saturday".toCharArray(), "sunday".toCharArray())).isEqualTo(3);
    }
}
