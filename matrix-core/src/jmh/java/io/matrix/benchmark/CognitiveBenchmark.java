package io.matrix.benchmark;

import io.matrix.consciousness.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * W295 — JMH benchmark for cognitive architecture.
 *
 * Measures throughput of key cognitive processing operations.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class CognitiveBenchmark {

    private CognitiveEmbedding embedding;
    private List<CognitiveGenesisProfile> profiles;
    private CognitiveSwiGLU glu;
    private CognitiveGroupedQueryAttention gqa;

    @Setup
    public void setup() {
        embedding = new CognitiveEmbedding(64, 42L);
        glu = new CognitiveSwiGLU(64, 128, 42L);
        gqa = new CognitiveGroupedQueryAttention(64, 8, 2, 42L);
        profiles = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            profiles.add(new CognitiveGenesisProfile(
                i / 100.0, i / 100.0, i / 100.0, i / 100.0,
                i / 100.0, 0.5, i / 100.0,
                50.0, 0.5, 0.5, 2, 0.5, 2.0
            ));
        }
    }

    @Benchmark
    public void embeddingThroughput(Blackhole bh) {
        for (CognitiveGenesisProfile p : profiles) {
            bh.consume(embedding.embed(p));
        }
    }

    @Benchmark
    public void swigluThroughput(Blackhole bh) {
        for (CognitiveGenesisProfile p : profiles) {
            double[] v = embedding.embed(p);
            bh.consume(glu.apply(v));
        }
    }

    @Benchmark
    public void gqaThroughput(Blackhole bh) {
        for (CognitiveGenesisProfile p : profiles) {
            bh.consume(gqa.attend(embedding.embed(p)));
        }
    }
}
