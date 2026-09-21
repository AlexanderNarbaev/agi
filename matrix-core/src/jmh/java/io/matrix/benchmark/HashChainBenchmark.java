package io.matrix.benchmark;

import io.matrix.audit.HashChain;
import io.matrix.audit.HashLink;
import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for {@link HashChain} — measures throughput of append,
 * verify, and restore operations for the cryptographic audit trail.
 *
 * <p>The chain is used in {@code FROZENFNLGuardian.evaluate} on every
 * decision, so per-call latency matters.
 *
 * <p>Run with: {@code ./gradlew :matrix-core:jmh -PjmhBenchmark=HashChainBenchmark}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class HashChainBenchmark {

    private HashChain chain;
    private List<HashLink> prebuilt;

    @Setup(Level.Trial)
    public void setup() {
        chain = new HashChain();
        // Pre-build a snapshot for the restore benchmark (100 links).
        for (int i = 0; i < 100; i++) chain.append("payload-" + i, "tag");
        prebuilt = chain.snapshot();
    }

    @Benchmark
    public HashLink singleAppend() {
        return chain.append("benchmark-payload", "benchmark-tag");
    }

    @Benchmark
    public boolean verifySmallChain() {
        return chain.verify();
    }

    @Benchmark
    public HashChain freshChainWith100Appends() {
        HashChain c = new HashChain();
        for (int i = 0; i < 100; i++) c.append("p-" + i, "t");
        return c;
    }

    @Benchmark
    public HashChain restore100Links() {
        HashChain c = new HashChain();
        c.restore(prebuilt);
        return c;
    }
}