package io.matrix.perf;

import java.util.BitSet;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * RECON-W30 — allocation pressure and GC behaviour on the distillation hot path.
 *
 * <p>Exists to answer one question with a number instead of folklore: does the collector
 * choice matter for MATRIX? The distillation path allocates BitSets and long arrays at a
 * high rate — {@code PersistentHdcStore.cosine} alone allocates three per comparison, and
 * a sleep cycle comparing a candidate against a large corpus allocates millions — so the
 * question is legitimate rather than premature.</p>
 *
 * <p>The benchmark does not try to measure GC pause times directly, because doing that
 * with JMH in-process is unreliable: JMH forks a fresh JVM per run and the pause
 * distribution lives in a structured log this harness would have to parse. Instead it
 * measures <em>surviving-set growth and end-of-run heap occupancy</em> for an
 * allocation-heavy workload, and the GC decision is then made on those numbers plus the
 * collector's documented pause characteristics. Stating that limit here is deliberate:
 * a pause-time number this harness produced would be less trustworthy than none.</p>
 *
 * <p>Run both collectors and compare:</p>
 * <pre>
 * scripts/perf-probe.sh --include GcPressure
 * java -jar matrix-core/build/libs/matrix-core-*-jmh.jar GcPressureBenchmark \
 *      -jvmArgsAppend "-XX:+UseG1GC"
 * java -jar matrix-core/build/libs/matrix-core-*-jmh.jar GcPressureBenchmark \
 *      -jvmArgsAppend "-XX:+UseZGC"
 * </pre>
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-Xms2g", "-Xmx2g"})
public class GcPressureBenchmark {

    /** Episode code width; matches the production sparse-HDC dimension. */
    @Param({"1024"})
    public int dimension;

    /** Set-bit density, matching production sparse codes. */
    @Param({"0.05"})
    public double density;

    /** Retained items: the part of the working set that survives and forces heap growth. */
    @Param({"20000"})
    public int retainedItems;

    private BitSet[] retained;
    private Random rng;
    private long allocatedBytes;

    @Setup(Level.Trial)
    public void setup() {
        rng = new Random(42L);
        final int setBits = (int) Math.round(dimension * density);
        retained = new BitSet[retainedItems];
        for (int i = 0; i < retainedItems; i++) {
            BitSet bs = new BitSet(dimension);
            for (int b = 0; b < setBits; b++) {
                bs.set(rng.nextInt(rng.nextInt() == 0 ? 1 : dimension));
            }
            retained[i] = bs;
        }
        allocatedBytes = 0L;
    }

    /**
     * The allocation-heavy shape: a retrieval sweep that keeps a shortlist alive across
     * the scan, so each iteration both allocates heavily and retains some of it. This is
     * what a distillation merge over a large corpus actually does.
     */
    @Benchmark
    public int allocHeavySweep(Blackhole bh) {
        int setBits = (int) Math.round(dimension * density);
        // Shortlist survives the iteration, so the collector must copy it rather than
        // discard it. A pure garbage workload would let any collector look good.
        List<BitSet> shortlist = new ArrayList<>(64);

        for (int i = 0; i < retained.length; i++) {
            BitSet query = new BitSet(dimension);
            for (int b = 0; b < setBits; b++) {
                query.set(rng.nextInt(dimension));
            }
            BitSet inter = (BitSet) query.clone();
            inter.and(retained[i]);
            if (inter.cardinality() > 0 && shortlist.size() < 64) {
                shortlist.add(inter);
            }
        }
        allocatedBytes += (long) retained.length * dimension / 8;
        bh.consume(shortlist);
        return shortlist.size();
    }

    /** Pure allocation churn, for contrast: short-lived garbage with no survivors. */
    @Benchmark
    public int allocChurnOnly(Blackhole bh) {
        int setBits = (int) Math.round(dimension * density);
        int retained0 = 0;
        for (int i = 0; i < 1000; i++) {
            BitSet scratch = new BitSet(dimension);
            for (int b = 0; b < setBits; b++) {
                scratch.set(rng.nextInt(dimension));
            }
            if (scratch.cardinality() > 0) retained0++;
        }
        bh.consume(retained0);
        return retained0;
    }

    /**
     * Heap occupancy after the trial. Read together with the throughput numbers: a
     * collector that sustains throughput by growing the heap is making a trade, and the
     * trade should be visible in the document rather than discovered in production.
     */
    @TearDown(Level.Trial)
    public void reportHeap() {
        Runtime rt = Runtime.getRuntime();
        long usedMiB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        System.err.printf("[GcPressure] used=%d MiB total=%d MiB max=%d MiB approxAllocatedOverTrial=%d MiB%n",
                usedMiB,
                rt.totalMemory() / (1024 * 1024),
                rt.maxMemory() / (1024 * 1024),
                allocatedBytes / (1024 * 1024));
    }
}
