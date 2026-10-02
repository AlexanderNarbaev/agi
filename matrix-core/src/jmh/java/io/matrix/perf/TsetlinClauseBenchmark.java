package io.matrix.perf;

import io.matrix.tsetlin.AdvancedTsetlinMachine;

import java.util.Random;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * RECON-W30 — Tsetlin clause update throughput, measured against the real
 * {@link AdvancedTsetlinMachine} in {@code matrix-core}.
 *
 * <p>This is the second hot kernel the wave plan named. Rule induction is what turns
 * captured activations into executable clauses, so clause-update cost sits directly on
 * the throughput of a sleep cycle, and a 10x knowledge scale multiplies the number of
 * updates a cycle performs.</p>
 *
 * <p>Two properties of the production signature shape what is measured here, and both
 * were confirmed by reading {@code updateClause} rather than assumed:</p>
 * <ul>
 *   <li>it is <b>pure</b> — {@code (model, classIdx, clauseIdx, features, actualLabel,
 *       gamma, seed) -> Model} returns a new model and mutates nothing, so a benchmark can
 *       call it repeatedly without drift;</li>
 *   <li>it allocates a fresh {@code Random(seed)} <b>per call</b> and a fresh
 *       {@code Clause[nClauses]} array. A clause update is therefore allocation-bound as
 *       well as compute-bound, which is exactly the kind of thing a number should reveal
 *       rather than a code review should guess at.</li>
 * </ul>
 *
 * <p>Because the machine is pure, {@code updateClauseBatch} accumulates results into a
 * thread-local sink so the JIT cannot eliminate the work as dead. The sink is consumed by
 * a Blackhole at the end of each batch.</p>
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-Xms2g", "-Xmx2g"})
public class TsetlinClauseBenchmark {

    /** Features per example. 1024 matches the HDC width used elsewhere in the system. */
    @Param({"1024"})
    public int nFeatures;

    /** Clauses per class — the automaton population. */
    @Param({"1024", "8192"})
    public int nClauses;

    /** Clauses updated per benchmark invocation; a cycle updates many, not one. */
    @Param({"1", "256"})
    public int batchSize;

    /** Bit density of the input features. */
    @Param({"0.05"})
    public double density;

    private AdvancedTsetlinMachine.Model model;
    private int[][] featureBatches;
    private int setBits;
    private int sink;

    @Setup(Level.Trial)
    public void setup() {
        // CONSTITUTION III: seeded RNG, so the benchmark is reproducible.
        Random rng = new Random(42L);
        model = AdvancedTsetlinMachine.init(nFeatures, nClauses, 2, 42L);

        setBits = (int) Math.round(nFeatures * density);
        featureBatches = new int[batchSize][nFeatures];
        for (int b = 0; b < batchSize; b++) {
            for (int s = 0; s < setBits; s++) {
                featureBatches[b][rng.nextInt(nFeatures)] = 1;
            }
        }
        sink = 0;
    }

    /** One clause update, the unit the wave plan asks for. */
    @Benchmark
    public int updateSingleClause(Blackhole bh) {
        AdvancedTsetlinMachine.Model next = AdvancedTsetlinMachine.updateClause(
                model, 0, 0, featureBatches[0], 1, 0.9, 42L);
        bh.consume(next);
        return next.nClauses();
    }

    /**
     * A batch of clause updates — the shape a real induction cycle performs. Each update
     * reads the same trial model, so this measures per-update cost without compounding
     * state growth across iterations.
     */
    @Benchmark
    public int updateClauseBatch(Blackhole bh) {
        AdvancedTsetlinMachine.Model current = model;
        for (int b = 0; b < batchSize; b++) {
            current = AdvancedTsetlinMachine.updateClause(
                    current, 0, b % nClauses, featureBatches[b], 1, 0.9, 42L + b);
        }
        // Consume the result so the chain cannot be optimised away.
        sink ^= current.nClauses();
        bh.consume(current);
        return sink;
    }

    /** Prediction only — the inference side, to separate train from serve cost. */
    @Benchmark
    public AdvancedTsetlinMachine.PredictResult predict(Blackhole bh) {
        AdvancedTsetlinMachine.PredictResult r =
                AdvancedTsetlinMachine.predict(model, featureBatches[0]);
        bh.consume(r);
        return r;
    }
}
