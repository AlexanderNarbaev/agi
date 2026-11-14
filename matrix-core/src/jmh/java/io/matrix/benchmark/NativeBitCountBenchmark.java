package io.matrix.benchmark;

import io.matrix.imports.HammingNative;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * REP-02 — one harness, both sides of the bit-count comparison.
 *
 * <p><b>Why this class exists.</b> The comparison "the FFM downcall costs 112.65 ns, the
 * Java path costs 6–16 ns" came from <b>two</b> harnesses, and the two disagreed on the
 * absolute figure by 2.7×. Two harnesses cannot settle a question about the same code: the
 * difference between them is the harness, not the code. This class measures both sides
 * <b>inside one harness</b>, with the same inputs, the same JVM and the same clock — so
 * what it publishes is a <b>ratio</b>, and the ratio is the only figure that transfers.
 *
 * <p><b>The three arms.</b></p>
 * <ul>
 *   <li>{@code javaBitCount} — the pure Java path, {@code Long.bitCount(a ^ b)};
 *   <li>{@code nativeDowncall} — the FFM downcall to the repository's own C function
 *       {@code hamming_distance_u64}, bound <b>here</b> through
 *       {@code SymbolLookup.libraryLookup};
 *   <li>{@code productionHelper} — {@code HammingNative.hamming}, the path the running
 *       system actually takes.</li>
 * </ul>
 *
 * <p><b>Why the third arm matters.</b> The production helper loads its library with
 * {@code System.load} and then asks {@code Linker.nativeLinker().defaultLookup()} for the
 * symbol — and a library loaded that way is <b>not</b> in the default lookup. The symbol is
 * therefore never found, the static initialiser swallows the exception, and the production
 * path silently falls back to {@code Long.bitCount}. The measured difference between the
 * first and the third arm is the cost of that silence.
 *
 * <p><b>What this class does not do.</b> It does not change the production code: the native
 * path stays dormant, as the wave's directive requires. It measures what a downcall would
 * cost if it were ever wired correctly, and it measures what the system pays today.
 *
 * <p><b>The honesty rule.</b> If the library is missing, the setup <b>fails loudly</b> —
 * a benchmark that measures nothing must never exit cleanly with an empty result.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class NativeBitCountBenchmark {

    /** The seed of the inputs. */
    private static final long SEED = 42L;

    /**
     * How many pairs one operation compares.
     *
     * <p>The parameter is the difference between a per-call figure and a per-batch one: at
     * 1024 pairs the JIT may vectorize the loop of the Java arm, and the downcall arm
     * cannot be vectorized at all. Both values are measured, and the report names which is
     * which.</p>
     */
    @Param({ "1", "1024" })
    public int values;

    /** The inputs. */
    private long[] left;

    /** The inputs. */
    private long[] right;

    /** The downcall handle. */
    private MethodHandle hamming;

    /**
     * The arena the library is bound in.
     *
     * <p>It must outlive the setup: JMH runs the benchmark in a different thread than the
     * one that prepared the state, so a confined arena would be closed and foreign to the
     * calling thread, and the downcall would fail with "Already closed" at the first call.</p>
     */
    private Arena arena;

    /**
     * The candidate paths of the native library, in the order they are tried.
     *
     * @return the candidates
     */
    private static java.util.List<Path> candidates() {
        java.util.List<Path> paths = new java.util.ArrayList<>();
        String work = System.getProperty("user.dir", ".");
        paths.add(Path.of(work, "matrix-core/src/main/c/libtruthy/libtruthy_hamming.so"));
        paths.add(Path.of(work, "src/main/c/libtruthy/libtruthy_hamming.so"));
        Path parent = Path.of(work).toAbsolutePath().getParent();
        if (parent != null) {
            paths.add(parent.resolve("matrix-core/src/main/c/libtruthy/libtruthy_hamming.so"));
        }
        return paths;
    }

    /**
     * Binds the native function and prepares the inputs.
     */
    @Setup
    public void setup() {
        Path found = null;
        for (Path candidate : candidates()) {
            if (Files.isRegularFile(candidate)) {
                found = candidate;
                break;
            }
        }
        if (found == null) {
            throw new IllegalStateException(
                    "the native library was not found, and a benchmark that measures nothing "
                            + "must not exit cleanly: " + candidates());
        }
        Linker linker = Linker.nativeLinker();
        final Path library = found;
        arena = Arena.ofShared();
        SymbolLookup lookup = SymbolLookup.libraryLookup(library, arena);
        try {
            hamming = linker.downcallHandle(
                    lookup.find("hamming_distance_u64").orElseThrow(
                            () -> new IllegalStateException("the symbol is missing in " + library)),
                    FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG));
        } catch (RuntimeException e) {
            arena.close();
            throw e;
        }

        Random rng = new Random(SEED);
        left = new long[values];
        right = new long[values];
        for (int index = 0; index < values; index++) {
            left[index] = rng.nextLong();
            right[index] = rng.nextLong();
        }
    }

    /**
     * Closes the arena the library was bound in.
     */
    @TearDown
    public void tearDown() {
        if (arena != null) {
            arena.close();
            arena = null;
        }
    }

    /**
     * The pure Java path.
     *
     * @param bh the blackhole
     */
    @Benchmark
    public void javaBitCount(Blackhole bh) {
        int sum = 0;
        for (int index = 0; index < values; index++) {
            sum += Long.bitCount(left[index] ^ right[index]);
        }
        bh.consume(sum);
    }

    /**
     * The FFM downcall to the repository's own C function.
     *
     * @param bh the blackhole
     * @throws Throwable when the call fails
     */
    @Benchmark
    public void nativeDowncall(Blackhole bh) throws Throwable {
        int sum = 0;
        for (int index = 0; index < values; index++) {
            sum += (int) hamming.invokeExact(left[index], right[index]);
        }
        bh.consume(sum);
    }

    /**
     * The path the running system takes today.
     *
     * @param bh the blackhole
     */
    @Benchmark
    public void productionHelper(Blackhole bh) {
        int sum = 0;
        for (int index = 0; index < values; index++) {
            sum += HammingNative.hamming(left[index], right[index]);
        }
        bh.consume(sum);
    }
}
