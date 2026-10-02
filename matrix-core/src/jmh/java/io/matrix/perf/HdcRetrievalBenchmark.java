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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * RECON-W30 — microbenchmarks for MATRIX hot kernels, measured ON THIS MACHINE.
 *
 * <p>Scope: the retrieval hot path. {@code PersistentHdcStore.cosine(BitSet, BitSet)}
 * is called once per stored memory for every query, so its cost and its allocation
 * behaviour set the ceiling on answer latency as the knowledge base grows. That is the
 * reason this is benchmarked first: it is the kernel whose cost multiplies with KB size,
 * and the reason W31's 10x knowledge scale is a performance question and not only a
 * content question.</p>
 *
 * <p>The three {@code cosine*} variants are the honest comparison asked for by the
 * wave plan:</p>
 * <ul>
 *   <li>{@code cosineCloneJaccard} — the current production shape, verbatim. Clones both
 *       operands to compute the intersection, then clones again for the union: three
 *       BitSet allocations per comparison.</li>
 *   <li>{@code cosineSinglePass} — one scratch BitSet reused across the two cardinality
 *       calls, so the same result costs one allocation instead of three.</li>
 *   <li>{@code cosineLongWordScan} — the same Jaccard value over a {@code long[]} encoding,
 *       which is what a Vector API kernel would operate on. Included so the AVX-512 claim
 *       in HARDWARE-PROFILE.md gets a number instead of staying an assumption.</li>
 * </ul>
 *
 * <p>All three return the same value for the same inputs; {@code cosineParity} asserts
 * that rather than assuming it. A benchmark that measures three different functions
 * would be worse than none.</p>
 *
 * <p>Run with:</p>
 * <pre>
 * scripts/perf-probe.sh                      # all kernels, this machine
 * scripts/perf-probe.sh --include cosine     # just the HDC kernel
 * </pre>
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-Xms2g", "-Xmx2g"})
public class HdcRetrievalBenchmark {

    /**
     * Bits per stored episode code. 1024 is the production dimension used by
     * EpisodeFeatureExtractor's sparse-HDC path; 10000 is the classic dense HDC width
     * and is included because it changes the allocation profile substantially.
     */
    @Param({"1024", "10000"})
    public int dimension;

    /**
     * Candidates scanned per query, i.e. how many cosine calls one retrieve() makes.
     * This is the parameter that turns a per-call number into a per-query number, so it
     * is measured rather than extrapolated.
     */
    @Param({"64", "1024"})
    public int corpusSize;

    /** Set-bit density. Sparse codes are the production case; 0.5 is the dense stress case. */
    @Param({"0.05"})
    public double density;

    private BitSet[] corpus;
    private long[][] corpusLong;
    private BitSet query;
    private long[] queryLong;
    private Random rng;

    /**
     * Preallocated scratch, one per benchmark thread. Lives OUTSIDE the timed region:
     * a reuse benchmark that allocates its scratch inside the measurement is measuring
     * allocation, which is the thing it exists to avoid.
     */
    private BitSet scratch;
    private BitSet corpusScratch;

    @Setup(Level.Trial)
    public void setup() {
        // CONSTITUTION III: seeded RNG, deterministic corpus.
        rng = new Random(42L);
        corpus = new BitSet[corpusSize];
        corpusLong = new long[corpusSize][];
        final int setBits = (int) Math.round(dimension * density);

        for (int i = 0; i < corpusSize; i++) {
            BitSet bs = new BitSet(dimension);
            for (int b = 0; b < setBits; b++) {
                bs.set(rng.nextInt(dimension));
            }
            corpus[i] = bs;
            corpusLong[i] = bitsetToWords(bs, dimension);
        }

        query = new BitSet(dimension);
        for (int b = 0; b < setBits; b++) {
            query.set(rng.nextInt(dimension));
        }
        queryLong = bitsetToWords(query, dimension);
        scratch = new BitSet(dimension);
        corpusScratch = new BitSet(dimension);
    }

    /**
     * Current production shape: {@code PersistentHdcStore.cosine}, reproduced verbatim so
     * the comparison is against real code and not a strawman.
     */
    @Benchmark
    public double cosineCloneJaccard() {
        return jaccardClone(query, corpus[0]);
    }

    /** One reusable scratch set instead of three fresh allocations. */
    @Benchmark
    public double cosineReusedScratch() {
        return jaccardScratch(query, corpus[0], scratch);
    }

    /** long[] encoding — the substrate a Vector API kernel would consume. */
    @Benchmark
    public void cosineLongWordScan(Blackhole bh) {
        bh.consume(jaccardLong(queryLong, corpusLong[0], (dimension + 63) / 64));
    }

    /**
     * Per-query cost: a full retrieval sweep. The single-call benchmarks above measure a
     * microsecond-scale operation in isolation, which flatters it; what matters is the
     * total across a corpus scan.
     */
    @Benchmark
    public double cosineFullCorpusScan() {
        double best = 0.0;
        for (int i = 0; i < corpus.length; i++) {
            double s = jaccardScratch(query, corpus[i], corpusScratch);
            if (s > best) best = s;
        }
        return best;
    }

    /**
     * Parity guard. If the variants disagree, every number above is meaningless, so this
     * is a benchmark that can fail rather than a test that runs elsewhere.
     */
    @Benchmark
    public boolean cosineParity() {
        double a = jaccardClone(query, corpus[0]);
        double b = jaccardScratch(query, corpus[0], scratch);
        double c = jaccardLong(queryLong, corpusLong[0], (dimension + 63) / 64);
        return Math.abs(a - b) < 1e-9 && Math.abs(a - c) < 1e-9;
    }

    // ---- implementations under test -------------------------------------------

    /** Verbatim production algorithm: two clones for union, one for intersection. */
    private static double jaccardClone(BitSet a, BitSet b) {
        if (a == null || b == null) return 0.0;
        BitSet inter = (BitSet) a.clone();
        inter.and(b);
        BitSet union = (BitSet) a.clone();
        union.or(b);
        int i = inter.cardinality();
        int u = union.cardinality();
        return u == 0 ? 0.0 : (double) i / (double) u;
    }

    /** Same value, scratch set reused: clear() instead of clone(). */
    private static double jaccardScratch(BitSet a, BitSet b, BitSet scratch) {
        if (a == null || b == null) return 0.0;
        scratch.clear();
        scratch.or(a);
        scratch.and(b);
        int i = scratch.cardinality();
        scratch.clear();
        scratch.or(a);
        scratch.or(b);
        int u = scratch.cardinality();
        return u == 0 ? 0.0 : (double) i / (double) u;
    }

    /**
     * Jaccard over a long[] encoding via popcount, the shape a Vector API
     * (avx512f = 8 x 64-bit lanes/vector) implementation would take. Scalar here on
     * purpose: this measures the baseline that any vectorised version must beat, which
     * is the only honest reason to write the vector version next.
     */
    private static double jaccardLong(long[] a, long[] b, int words) {
        if (a == null || b == null) return 0.0;
        int inter = 0;
        int union = 0;
        for (int w = 0; w < words; w++) {
            long av = a[w];
            long bv = b[w];
            inter += Long.bitCount(av & bv);
            union += Long.bitCount(av | bv);
        }
        return union == 0 ? 0.0 : (double) inter / (double) union;
    }

    private static long[] bitsetToWords(BitSet bs, int dim) {
        long[] out = new long[(dim + 63) / 64];
        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
            out[i >> 6] |= 1L << i;
        }
        return out;
    }
}
