package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HdcEncodingTest {

    @Test
    void zeroVectorHasAllZeroBits() {
        long[] v = HdcEncoding.zero();
        assertThat(v).hasSize(HdcEncoding.WORDS);
        for (long word : v) {
            assertThat(word).isEqualTo(0L);
        }
    }

    @Test
    void randomVectorHasExpectedLength() {
        Random rng = new Random(42);
        long[] v = HdcEncoding.random(rng);
        assertThat(v).hasSize(HdcEncoding.WORDS);
    }

    @Test
    void randomVectorIsBalancedForLargeN() {
        Random rng = new Random(42);
        long[] v = HdcEncoding.random(rng);
        int ones = 0;
        for (long word : v) ones += Long.bitCount(word);
        // For 1024 random bits, expect ~512 ones (binomial mean).
        assertThat(ones).isBetween(400, 624);
    }

    @Test
    void randomWithDifferentSeedsYieldsDifferentVectors() {
        long[] a = HdcEncoding.random(new Random(1));
        long[] b = HdcEncoding.random(new Random(2));
        int diff = HdcEncoding.hamming(a, b);
        // Two random 1024-bit vectors: expected distance ~512.
        assertThat(diff).isBetween(400, 624);
    }

    @Test
    void sameSeedYieldsSameVector() {
        long[] a = HdcEncoding.random(new Random(7));
        long[] b = HdcEncoding.random(new Random(7));
        assertThat(HdcEncoding.hamming(a, b)).isEqualTo(0);
    }

    @Test
    void sparseRandomCreatesSparseVector() {
        Random rng = new Random(123);
        long[] v = HdcEncoding.sparseRandom(HdcEncoding.DIM, 100, rng);
        int ones = 0;
        for (long word : v) ones += Long.bitCount(word);
        assertThat(ones).isEqualTo(HdcEncoding.DIM - 100);
    }

    @Test
    void sparseRandomRejectsBadArgs() {
        assertThatThrownBy(() -> HdcEncoding.sparseRandom(512, 10, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HdcEncoding.sparseRandom(HdcEncoding.DIM, -1, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HdcEncoding.sparseRandom(HdcEncoding.DIM, 2000, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HdcEncoding.sparseRandom(HdcEncoding.DIM, 10, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void permuteByZeroIsIdentity() {
        Random rng = new Random(1);
        long[] v = HdcEncoding.random(rng);
        long[] p = HdcEncoding.permute(v, 0);
        assertThat(HdcEncoding.hamming(v, p)).isEqualTo(0);
    }

    @Test
    void permuteByDimIsIdentity() {
        Random rng = new Random(1);
        long[] v = HdcEncoding.random(rng);
        long[] p = HdcEncoding.permute(v, HdcEncoding.DIM);
        assertThat(HdcEncoding.hamming(v, p)).isEqualTo(0);
    }

    @Test
    void permuteByOneMovesBitsAround() {
        Random rng = new Random(1);
        long[] v = HdcEncoding.random(rng);
        long[] p = HdcEncoding.permute(v, 1);
        // After shift-by-one, hamming(v, p) ~= DIM/2 for a random vector,
        // because bit i moves to position i+1 and ~half the new positions
        // held a different bit. (Test correctness via the round-trip
        // and non-identity properties below.)
        int d = HdcEncoding.hamming(v, p);
        assertThat(d).isBetween(380, 644);
        // And NOT zero (i.e. shift actually changed something).
        assertThat(d).isNotZero();
    }

    @Test
    void permuteBackAndForthIsIdentity() {
        Random rng = new Random(1);
        long[] v = HdcEncoding.random(rng);
        long[] p = HdcEncoding.permute(v, 5);
        long[] pp = HdcEncoding.permute(p, HdcEncoding.DIM - 5);
        assertThat(HdcEncoding.hamming(v, pp)).isEqualTo(0);
    }

    @Test
    void hammingOfVectorWithItselfIsZero() {
        long[] v = HdcEncoding.random(new Random(11));
        assertThat(HdcEncoding.hamming(v, v)).isEqualTo(0);
    }

    @Test
    void hammingOfComplementIsDim() {
        long[] v = HdcEncoding.random(new Random(11));
        long[] complement = new long[HdcEncoding.WORDS];
        for (int i = 0; i < HdcEncoding.WORDS; i++) {
            complement[i] = ~v[i];
        }
        assertThat(HdcEncoding.hamming(v, complement)).isEqualTo(HdcEncoding.DIM);
    }

    @Test
    void similarityIsPlusOneForIdentical() {
        long[] v = HdcEncoding.random(new Random(1));
        assertThat(HdcEncoding.similarity(v, v)).isEqualTo(1.0);
    }

    @Test
    void similarityIsMinusOneForComplement() {
        long[] v = HdcEncoding.random(new Random(1));
        long[] complement = new long[HdcEncoding.WORDS];
        for (int i = 0; i < HdcEncoding.WORDS; i++) {
            complement[i] = ~v[i];
        }
        assertThat(HdcEncoding.similarity(v, complement)).isEqualTo(-1.0);
    }

    @Test
    void similarityOfRandomVectorsIsNearZero() {
        long[] a = HdcEncoding.random(new Random(1));
        long[] b = HdcEncoding.random(new Random(2));
        double sim = HdcEncoding.similarity(a, b);
        assertThat(sim).isBetween(-0.15, 0.15);
    }

    @Test
    void bundleOfSingleVectorEqualsItself() {
        long[] v = HdcEncoding.random(new Random(1));
        long[] b = HdcEncoding.bundle(v);
        assertThat(HdcEncoding.hamming(v, b)).isEqualTo(0);
    }

    @Test
    void bundleOfTwoIdenticalVectorsEqualsOriginal() {
        long[] v = HdcEncoding.random(new Random(1));
        long[] b = HdcEncoding.bundle(v, v);
        assertThat(HdcEncoding.hamming(v, b)).isEqualTo(0);
    }

    @Test
    void bundleOfIdenticalAndComplementTiesToAllOnes() {
        long[] v = HdcEncoding.random(new Random(1));
        long[] complement = new long[HdcEncoding.WORDS];
        for (int i = 0; i < HdcEncoding.WORDS; i++) {
            complement[i] = ~v[i];
        }
        long[] b = HdcEncoding.bundle(v, complement);
        // Tied vote (1 vs 1): majority picks 1 at every position.
        // So bundle is all-ones. v has ~512 ones and ~512 zeros.
        // Hamming distance between v and all-ones is #zeros in v ~ DIM/2.
        int dist = HdcEncoding.hamming(v, b);
        assertThat(dist).isBetween(400, 624); // ~DIM/2 ± 3 sigma
    }

    @Test
    void bundleOfThreeCopiesPreservesOriginal() {
        long[] v = HdcEncoding.random(new Random(1));
        long[] b = HdcEncoding.bundle(v, v, v);
        assertThat(HdcEncoding.hamming(v, b)).isEqualTo(0);
    }

    @Test
    void bundleRejectsEmptyInput() {
        assertThatThrownBy(() -> HdcEncoding.bundle())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bundleRejectsMismatchedLengths() {
        long[] a = HdcEncoding.random(new Random(1));
        long[] b = new long[2]; // wrong length
        assertThatThrownBy(() -> HdcEncoding.bundle(a, b))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void xorIsItsOwnInverse() {
        long[] a = HdcEncoding.random(new Random(1));
        long[] b = HdcEncoding.random(new Random(2));
        long[] x = HdcEncoding.xor(a, b);
        long[] back = HdcEncoding.xor(x, b);
        assertThat(HdcEncoding.hamming(a, back)).isEqualTo(0);
    }

    @Test
    void xorOfIdenticalVectorsIsZero() {
        long[] v = HdcEncoding.random(new Random(1));
        long[] x = HdcEncoding.xor(v, v);
        for (long word : x) {
            assertThat(word).isEqualTo(0L);
        }
    }

    @Test
    void xorRejectsBadLength() {
        long[] a = HdcEncoding.random(new Random(1));
        long[] b = new long[2];
        assertThatThrownBy(() -> HdcEncoding.xor(a, b))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hammingRejectsNullOrBadLength() {
        assertThatThrownBy(() -> HdcEncoding.hamming(null, null))
                .isInstanceOf(IllegalArgumentException.class);
        long[] v = HdcEncoding.random(new Random(1));
        assertThatThrownBy(() -> HdcEncoding.hamming(v, new long[2]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void popcountIsCorrect() {
        assertThat(HdcEncoding.popcount(0L)).isEqualTo(0);
        assertThat(HdcEncoding.popcount(1L)).isEqualTo(1);
        assertThat(HdcEncoding.popcount(0xFFFFFFFFFFFFFFFFL)).isEqualTo(64);
        assertThat(HdcEncoding.popcount(0x5555555555555555L)).isEqualTo(32);
    }

    @Test
    void toSymbolsHasDimChars() {
        long[] v = HdcEncoding.random(new Random(1));
        String s = HdcEncoding.toSymbols(v);
        assertThat(s).hasSize(HdcEncoding.DIM);
        // Should contain both + and - characters (high probability for random)
        assertThat(s.chars().anyMatch(c -> c == '+')).isTrue();
        assertThat(s.chars().anyMatch(c -> c == '-')).isTrue();
    }

    @Test
    void toSymbolsOfZeroIsAllMinus() {
        long[] v = HdcEncoding.zero();
        String s = HdcEncoding.toSymbols(v);
        assertThat(s.chars().allMatch(c -> c == '-')).isTrue();
    }

    @Test
    void codebookWithSameSeedIsIdentical() {
        // Application pattern: generate a deterministic codebook
        Random rng1 = new Random(99);
        Random rng2 = new Random(99);
        long[][] codebook = new long[5][];
        for (int i = 0; i < 5; i++) codebook[i] = HdcEncoding.random(rng1);
        long[][] codebook2 = new long[5][];
        for (int i = 0; i < 5; i++) codebook2[i] = HdcEncoding.random(rng2);
        for (int i = 0; i < 5; i++) {
            assertThat(HdcEncoding.hamming(codebook[i], codebook2[i])).isEqualTo(0);
        }
    }

    @Test
    void codebookVectorsAreApproximatelyOrthogonal() {
        Random rng = new Random(13);
        long[][] codebook = new long[20][];
        for (int i = 0; i < 20; i++) codebook[i] = HdcEncoding.random(rng);
        // Pairwise distances should be ~512
        for (int i = 0; i < 20; i++) {
            for (int j = i + 1; j < 20; j++) {
                int d = HdcEncoding.hamming(codebook[i], codebook[j]);
                assertThat(d).isBetween(380, 644); // ±3 sigma
            }
        }
    }

    @Test
    void randomRejectsNullRng() {
        assertThatThrownBy(() -> HdcEncoding.random(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
