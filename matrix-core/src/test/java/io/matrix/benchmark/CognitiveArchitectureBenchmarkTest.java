package io.matrix.benchmark;

import io.matrix.consciousness.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W295 — Quick benchmark test for cognitive architecture.
 */
class CognitiveArchitectureBenchmarkTest {

    private static final int ITERATIONS = 10_000;
    private static final int WARMUP = 1_000;

    @Test
    void embeddingThroughput() {
        CognitiveEmbedding e = new CognitiveEmbedding(64, 42L);
        CognitiveGenesisProfile p = new CognitiveGenesisProfile(
            0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5,
            50.0, 0.5, 0.5, 2, 0.5, 2.0);

        for (int i = 0; i < WARMUP; i++) e.embed(p);

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) e.embed(p);
        long elapsed = System.nanoTime() - start;

        double opsPerSec = (double) ITERATIONS / (elapsed / 1_000_000_000.0);
        System.out.println("CognitiveEmbedding throughput: " + (long)opsPerSec + " ops/sec");
        assertThat(opsPerSec).isGreaterThan(10_000.0);
    }

    @Test
    void swigluThroughput() {
        CognitiveEmbedding e = new CognitiveEmbedding(64, 42L);
        CognitiveSwiGLU glu = new CognitiveSwiGLU(64, 128, 42L);
        double[] v = e.embed(new CognitiveGenesisProfile(
            0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5,
            50.0, 0.5, 0.5, 2, 0.5, 2.0));

        for (int i = 0; i < WARMUP; i++) glu.apply(v);

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) glu.apply(v);
        long elapsed = System.nanoTime() - start;

        double opsPerSec = (double) ITERATIONS / (elapsed / 1_000_000_000.0);
        System.out.println("CognitiveSwiGLU throughput: " + (long)opsPerSec + " ops/sec");
        assertThat(opsPerSec).isGreaterThan(1_000.0);
    }

    @Test
    void attentionThroughput() {
        CognitiveEmbedding e = new CognitiveEmbedding(64, 42L);
        CognitiveGroupedQueryAttention gqa =
            new CognitiveGroupedQueryAttention(64, 8, 2, 42L);
        double[] v = e.embed(new CognitiveGenesisProfile(
            0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5,
            50.0, 0.5, 0.5, 2, 0.5, 2.0));

        for (int i = 0; i < WARMUP; i++) gqa.attend(v);

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) gqa.attend(v);
        long elapsed = System.nanoTime() - start;

        double opsPerSec = (double) ITERATIONS / (elapsed / 1_000_000_000.0);
        System.out.println("CognitiveGQA throughput: " + (long)opsPerSec + " ops/sec");
        assertThat(opsPerSec).isGreaterThan(1_000.0);
    }

    @Test
    void constitutionalEvalThroughput() {
        CognitiveConstitutionalAI.evaluate(
            new CognitiveGenesisProfile(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5,
                50.0, 0.5, 0.5, 2, 0.5, 2.0),
            CognitiveConstitutionalAI.DEFAULT_CONSTITUTION, 0.5);

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            CognitiveConstitutionalAI.evaluate(
                new CognitiveGenesisProfile(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5,
                    50.0, 0.5, 0.5, 2, 0.5, 2.0),
                CognitiveConstitutionalAI.DEFAULT_CONSTITUTION, 0.5);
        }
        long elapsed = System.nanoTime() - start;

        double opsPerSec = (double) ITERATIONS / (elapsed / 1_000_000_000.0);
        System.out.println("ConstitutionalAI throughput: " + (long)opsPerSec + " ops/sec");
        assertThat(opsPerSec).isGreaterThan(1_000.0);
    }
}
