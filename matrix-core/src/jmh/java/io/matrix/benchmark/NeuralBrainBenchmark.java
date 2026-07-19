package io.matrix.benchmark;

import io.matrix.minecraft.BlockAgent;
import io.matrix.minecraft.BlockWorld;
import io.matrix.minecraft.NeuralBrain;
import org.openjdk.jmh.annotations.*;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for {@link NeuralBrain} — the decision-making heart of every
 * headless Minecraft bot. Measures single-tick latency and throughput.
 *
 * <p>Setup: random 5-tree brain with k=20 inputs (the canonical depth used
 * by {@code new NeuralBrain(Random)}).
 *
 * <p>Run with: {@code ./gradlew :matrix-core:jmh -PjmhBenchmark=NeuralBrainBenchmark}
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class NeuralBrainBenchmark {

    private NeuralBrain brain;
    private BlockAgent agent;
    private BlockWorld world;
    private long sensorBits;

    @Setup(Level.Trial)
    public void setup() {
        Random rng = new Random(42L);
        brain = new NeuralBrain(rng);
        world = new BlockWorld(20, 20, rng);
        agent = new BlockAgent(new BlockWorld.Position(10, 10));
        sensorBits = agent.encodeSensors(world);
    }

    /** Throughput benchmark: tick decisions per second. */
    @Benchmark
    public BlockAgent.Action decisionThroughput() {
        return brain.act(sensorBits);
    }

    /** Average-time benchmark: per-decision latency in microseconds. */
    @Benchmark
    public BlockAgent.Action decisionLatency() {
        return brain.act(sensorBits);
    }

    /** Benchmark the encoding step (sensor → bit field) separately. */
    @Benchmark
    public long sensorEncoding() {
        return agent.encodeSensors(world);
    }
}