package io.matrix.integration;

import io.matrix.compression.BooleanCompressor;
import io.matrix.compression.SimdEvaluator;
import io.matrix.compression.TruthTableMinimizer;
import io.matrix.neuron.TruthTable;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 9: Integration tests for Boolean Compression subsystem.
 *
 * <p>Tests BooleanCompressor on real vectors, TruthTableMinimizer on real
 * truth tables, and SIMD vs sequential correctness.
 */
class CompressionIntegrationTest {

    @Test
    void booleanCompressorOnSparseVectors() {
        BitSet sparse = new BitSet(1024);
        sparse.set(5);
        sparse.set(100);
        sparse.set(500);
        sparse.set(999);

        BooleanCompressor.Compressed compressed = BooleanCompressor.compress(sparse, 1024);

        assertThat(compressed.method()).isEqualTo(BooleanCompressor.Method.BITMASK);
        assertThat(compressed.compressedSize()).isLessThan(compressed.originalSize());

        // Decompress and verify
        BitSet restored = BooleanCompressor.decompress(compressed, 1024);
        assertThat(restored).isEqualTo(sparse);
    }

    @Test
    void booleanCompressorOnDenseVectors() {
        BitSet dense = new BitSet(1024);
        dense.set(0, 800); // 80% density

        BooleanCompressor.Compressed compressed = BooleanCompressor.compress(dense, 1024);

        assertThat(compressed.method()).isEqualTo(BooleanCompressor.Method.RLE);

        BitSet restored = BooleanCompressor.decompress(compressed, 1024);
        assertThat(restored).isEqualTo(dense);
    }

    @Test
    void booleanCompressorRoundtrip() {
        var rng = new Random(42);

        // Test with various densities
        for (double density : new double[]{0.01, 0.05, 0.1, 0.3, 0.5, 0.8, 0.99}) {
            BitSet bits = new BitSet(512);
            for (int i = 0; i < 512; i++) {
                if (rng.nextDouble() < density) {
                    bits.set(i);
                }
            }

            BooleanCompressor.Compressed compressed = BooleanCompressor.compress(bits, 512);
            BitSet restored = BooleanCompressor.decompress(compressed, 512);
            assertThat(restored).isEqualTo(bits);
        }
    }

    @Test
    void truthTableMinimizerOnRealTables() {
        var rng = new Random(42);

        // Test minimization on k=6 truth tables (QM should be used)
        TruthTable tt = TruthTable.random(6, rng);
        TruthTableMinimizer.MinimizedDNF minimized = TruthTableMinimizer.minimize(tt);

        assertThat(minimized).isNotNull();
        assertThat(minimized.algorithm()).isEqualTo(TruthTableMinimizer.Algorithm.QUINE_MCCLUSKEY);
        assertThat(minimized.k()).isEqualTo(6);

        // Verify equivalence: minimized DNF should match original for all inputs
        int size = 1 << 6;
        for (int i = 0; i < size; i++) {
            assertThat(minimized.evaluate(i)).isEqualTo(tt.evaluate(i));
        }
    }

    @Test
    void truthTableMinimizerOnLargeK() {
        var rng = new Random(42);

        // Test minimization on k=14 truth tables (Espresso should be used)
        TruthTable tt = TruthTable.random(14, rng);
        TruthTableMinimizer.MinimizedDNF minimized = TruthTableMinimizer.minimize(tt);

        assertThat(minimized).isNotNull();
        assertThat(minimized.algorithm()).isEqualTo(TruthTableMinimizer.Algorithm.ESPRESSO);
        assertThat(minimized.k()).isEqualTo(14);
    }

    @Test
    void truthTableMinimizerConstantFunctions() {
        // All zeros
        BitSet allZeros = new BitSet(1 << 4);
        TruthTable ttFalse = TruthTable.of(4, allZeros);
        TruthTableMinimizer.MinimizedDNF minFalse = TruthTableMinimizer.minimize(ttFalse);
        assertThat(minFalse.implicants()).isEmpty();
        assertThat(minFalse.toString()).isEqualTo("FALSE");

        // All ones
        BitSet allOnes = new BitSet(1 << 4);
        allOnes.set(0, 1 << 4);
        TruthTable ttTrue = TruthTable.of(4, allOnes);
        TruthTableMinimizer.MinimizedDNF minTrue = TruthTableMinimizer.minimize(ttTrue);
        assertThat(minTrue.implicants()).hasSize(1);
        assertThat(minTrue.evaluate(0)).isTrue();
    }

    @Test
    void simdVsSequentialCorrectness() {
        var rng = new Random(42);

        List<TruthTable> tables = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            tables.add(TruthTable.random(8, rng));
        }

        int[] inputs = new int[256];
        for (int i = 0; i < 256; i++) {
            inputs[i] = rng.nextInt(1 << 8);
        }

        boolean[][] simdResults = SimdEvaluator.batchEvaluate(tables, inputs);

        // Verify SIMD results match direct TruthTable evaluation
        for (int i = 0; i < inputs.length; i++) {
            for (int t = 0; t < tables.size(); t++) {
                assertThat(simdResults[i][t])
                        .as("Mismatch at input=%d, table=%d", i, t)
                        .isEqualTo(tables.get(t).evaluate(inputs[i]));
            }
        }
    }

    @Test
    void simdBatchEvaluateSingle() {
        var rng = new Random(42);
        TruthTable tt = TruthTable.random(10, rng);

        int[] inputs = new int[512];
        for (int i = 0; i < 512; i++) {
            inputs[i] = rng.nextInt(1 << 10);
        }

        boolean[] results = SimdEvaluator.batchEvaluateSingle(tt, inputs);

        // Verify against direct evaluation
        for (int i = 0; i < inputs.length; i++) {
            assertThat(results[i]).isEqualTo(tt.evaluate(inputs[i]));
        }
    }

    @Test
    void simdBenchmarkRuns() {
        var rng = new Random(42);

        List<TruthTable> tables = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tables.add(TruthTable.random(8, rng));
        }

        int[] inputs = new int[128];
        for (int i = 0; i < 128; i++) {
            inputs[i] = rng.nextInt(1 << 8);
        }

        SimdEvaluator.BenchmarkResult result = SimdEvaluator.benchmark(tables, inputs, 50);

        assertThat(result.simdTimeMs()).isGreaterThan(0);
        assertThat(result.seqTimeMs()).isGreaterThan(0);
        assertThat(result.speedup()).isGreaterThan(0);
        assertThat(result.iterations()).isEqualTo(50);
    }

    @Test
    void compressionRatioForSparseVectors() {
        BitSet verySparse = new BitSet(10000);
        verySparse.set(42);
        verySparse.set(9999);

        BooleanCompressor.Compressed compressed = BooleanCompressor.compress(verySparse, 10000);

        // Compression ratio should be significant for very sparse vectors
        assertThat(compressed.compressionRatio()).isGreaterThan(1.0);
    }
}
