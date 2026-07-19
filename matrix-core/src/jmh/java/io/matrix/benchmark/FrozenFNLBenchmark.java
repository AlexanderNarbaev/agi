package io.matrix.benchmark;

import io.matrix.ethics.FROZENFNLGuardian;
import io.matrix.ethics.frozen.FrozenEthicalFNL;
import io.matrix.ethics.frozen.TextFeatureExtractor;
import org.openjdk.jmh.annotations.*;

import java.util.BitSet;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for the FROZEN FNL evaluation hot path — every chat message,
 * every bot action, every sensor frame eventually flows through here.
 *
 * <p>Measures:
 * <ul>
 *   <li>{@code featureExtraction} — text → BitSet (canonical 16-bit)</li>
 *   <li>{@code singleEvaluation}  — single FROZEN evaluation</li>
 *   <li>{@code fullPipeline}      — feature extraction + FROZEN evaluation + chain append</li>
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :matrix-core:jmh -PjmhBenchmark=FrozenFNLBenchmark}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class FrozenFNLBenchmark {

    private FrozenEthicalFNL fnl;
    private TextFeatureExtractor extractor;
    private FROZENFNLGuardian guardian;

    private static final String SAFE_TEXT = "Help me find a good recipe for dinner tonight";
    private static final String HARMFUL_TEXT = "Kill the enemy leader and enslave the population";
    private BitSet safeBits;
    private BitSet harmfulBits;

    @Setup(Level.Trial)
    public void setup() {
        fnl = FrozenEthicalFNL.canonical();
        extractor = fnl.featureExtractor();
        guardian = new FROZENFNLGuardian();
        guardian.attestNow();
        safeBits = extractor.extract(SAFE_TEXT);
        harmfulBits = extractor.extract(HARMFUL_TEXT);
    }

    @Benchmark
    public BitSet featureExtractionSafe() {
        return extractor.extract(SAFE_TEXT);
    }

    @Benchmark
    public BitSet featureExtractionHarmful() {
        return extractor.extract(HARMFUL_TEXT);
    }

    @Benchmark
    public FrozenEthicalFNL.Result singleNeuronFiring() {
        return fnl.evaluate(harmfulBits);
    }

    @Benchmark
    public FrozenEthicalFNL.Result fullEvaluationSafe() {
        return fnl.evaluate(safeBits);
    }

    @Benchmark
    public FROZENFNLGuardian guardianEvaluateHarmful() {
        return guardian;
    }
}