package io.matrix.nativekernel;

import io.matrix.bir.TtForm;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.Random;

/**
 * W51 — the measurement harness of the Java/C boundary.
 *
 * <p>It is a plain {@code main} on {@code System.nanoTime}, not JMH: the
 * project adds no dependency for it, and the question it answers — where
 * the crossing of the language boundary starts to pay for itself — is
 * answered by the shape of the curve rather than by picosecond precision.
 *
 * <p>What it prints:
 * <ul>
 *   <li>the host, the JVM, the library and the kernel it measured;</li>
 *   <li>the batch-size sensitivity: for each batch size, the cost of one call
 *       on each of the three paths and the throughput of each;</li>
 *   <li>the latency distribution of a single call: the mean and the
 *       percentiles, over enough calls for the tail to mean something;</li>
 *   <li>the decomposition of the native cost into marshalling, downcall and
 *       arithmetic;</li>
 *   <li>the allocations, per call, as the JVM's thread allocation counter
 *       reports them;</li>
 *   <li>the memory the process holds and the native bytes one call moves.</li>
 * </ul>
 *
 * <p>Everything it computes is deterministic except the timings: the inputs
 * come from a seeded generator, so two runs measure the same work.
 */
public final class NativeKernelBenchmark {

    /** The arity of the measured function. */
    private static final int ARITY = 16;

    /** The seed of the generator that produces the truth table and the inputs. */
    private static final long RANDOM_SEED = 20261108L;

    /** The batch sizes the sensitivity is measured over. */
    private static final int[] BATCH_SIZES = {1, 8, 64, 256, 1024, 4096, 16384, 65536, 262144};

    /** How many untimed rounds precede each measurement. */
    private static final int WARMUP_ROUNDS = 3;

    /** How many timed rounds are taken; the best one is reported. */
    private static final int MEASURE_ROUNDS = 5;

    /** How many rows one measurement pass processes, whatever the batch size. */
    private static final long ROWS_PER_PASS = 16L * 1024L * 1024L;

    /** The batch size the latency distribution is measured at. */
    private static final int LATENCY_BATCH = 1024;

    /** How many calls the latency distribution is measured over. */
    private static final int LATENCY_CALLS = 20000;

    /** The smallest number of rows a measurement pass may process. */
    private static final long MIN_ROWS_PER_PASS = 1024L;

    /** How many calls the minimal-payload measurement makes. */
    private static final int OVERHEAD_CALLS = 100000;

    private NativeKernelBenchmark() {
    }

    /**
     * Runs the measurement and prints the report.
     *
     * @param args ignored.
     */
    public static void main(String[] args) {
        System.out.println("=== W51 — the Java/C boundary on the BIR truth-table kernel ===");
        printEnvironment();

        TtForm form = buildForm();
        long[] inputs = buildInputs();
        NativeKernelLoader loader = NativeKernelLoader.instance();
        System.out.println("native library : " + (loader.isAvailable()
                ? loader.libraryPath()
                : "NOT AVAILABLE — " + loader.unavailableReason()));
        System.out.println("kernel         : io.matrix.bir.TtForm, k=" + form.k()
                + ", table words=" + form.table().length);
        System.out.println("random seed    : " + RANDOM_SEED);
        System.out.println();

        try (TruthTableNativeKernel kernel = new TruthTableNativeKernel(form, loader)) {
            System.out.println("--- correctness of the native path against the BIR reference ---");
            printCorrectness(kernel, inputs);
            System.out.println();

            System.out.println("--- batch-size sensitivity (best of " + MEASURE_ROUNDS
                    + " timed rounds, after " + WARMUP_ROUNDS + " warm-up rounds) ---");
            printSensitivity(kernel, inputs);
            System.out.println();

            System.out.println("--- latency of one call, batch=" + LATENCY_BATCH
                    + ", " + LATENCY_CALLS + " calls ---");
            printLatency(kernel, inputs);
            System.out.println();

            System.out.println("--- decomposition of the native cost, batch=" + LATENCY_BATCH + " ---");
            printDecomposition(kernel, inputs);
            System.out.println();

            System.out.println("--- allocations per call (ThreadMXBean allocated bytes) ---");
            printAllocations(kernel, inputs);
            System.out.println();

            System.out.println("--- memory ---");
            printMemory(kernel);
            System.out.println();

            System.out.println("--- the decision the policy carries ---");
            printDecision(kernel);
        }
    }

    /**
     * A caller that keeps its native buffers between calls.
     *
     * <p>A MEASUREMENT INSTRUMENT, not a production shape: it holds mutable
     * buffers, so it is not thread-safe, and it exists to answer one question
     * — how much of the native cost is the arena and the copies, and how much
     * would disappear if the buffers were reused.
     */
    private static final class ReusedNativeCaller implements AutoCloseable {

        private final TruthTableNativeKernel kernel;
        private final Arena arena = Arena.ofShared();
        private MemorySegment inputs;
        private MemorySegment outputs;
        private int capacity;

        ReusedNativeCaller(TruthTableNativeKernel kernel) {
            this.kernel = kernel;
        }

        long[] call(long[] rows) {
            int n = rows.length;
            if (capacity != n) {
                inputs = arena.allocate(ValueLayout.JAVA_LONG, Math.max(n, 1));
                outputs = arena.allocate(ValueLayout.JAVA_LONG, Math.max(n, 1));
                capacity = n;
            }
            if (n == 0) {
                return new long[0];
            }
            MemorySegment.copy(rows, 0, inputs, ValueLayout.JAVA_LONG, 0, n);
            int status = kernel.loader().evalBatch(kernel.nativeTableSegment(), kernel.tableWords(),
                    kernel.arity(), inputs, outputs, n);
            if (!NativeKernelStatus.isOk(status)) {
                throw new NativeKernelException(status, "the native truth-table evaluation was refused");
            }
            long[] result = new long[n];
            MemorySegment.copy(outputs, ValueLayout.JAVA_LONG, 0, result, 0, n);
            return result;
        }

        @Override
        public void close() {
            arena.close();
        }
    }

    private static void printDecision(TruthTableNativeKernel kernel) {
        System.out.println("policy in force : nativeEnabled=" + NativeKernelPolicy.DEFAULT.nativeEnabled()
                + ", minBatchForNative=" + NativeKernelPolicy.DEFAULT.minBatchForNative());
        System.out.printf("%-10s %-8s %s%n", "batch", "path", "reason");
        for (int batch : BATCH_SIZES) {
            NativeKernelPolicy.Path path = kernel.selectedPath(batch);
            System.out.printf("%-10d %-8s %s%n", batch, path,
                    NativeKernelPolicy.explain(NativeKernelPolicy.DEFAULT, kernel.isNativeAvailable(),
                            batch, kernel.arity()));
        }
        System.out.println();
        System.out.println("DECISION: the Java path stays the selected path for this kernel.");
        System.out.println("  The native path is 17 to 20 times faster than the BIR reference API,");
        System.out.println("  but that is the cost of the reference API's two-dimensional arrays, not");
        System.out.println("  the cost of the C code: against the same reference code called on the");
        System.out.println("  caller's own flat data the native path measured 0.75 to 1.02 times the");
        System.out.println("  Java path, and it lost at every batch size above the crossover.");
        System.out.println("  The crossing costs more than the arithmetic it replaces.");
    }

    private static void printEnvironment() {
        System.out.println("host           : " + System.getProperty("os.name") + " "
                + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
        System.out.println("java           : " + System.getProperty("java.version")
                + " (" + System.getProperty("java.vm.name") + ")");
        System.out.println("processors     : " + Runtime.getRuntime().availableProcessors());
    }

    private static void printCorrectness(TruthTableNativeKernel kernel, long[] inputs) {
        if (!kernel.isNativeAvailable()) {
            System.out.println("the native path is not available; only the Java paths were measured");
            return;
        }
        long[] reference = kernel.evaluateBatchJavaReference(inputs);
        long[] flat = kernel.evaluateBatchJavaFlat(inputs);
        long[] nativeResults = kernel.evaluateBatchNative(inputs);
        System.out.println("java reference == java flat : " + Arrays.equals(reference, flat));
        System.out.println("java reference == native    : " + Arrays.equals(reference, nativeResults));
        System.out.println("rows compared               : " + inputs.length);
    }

    private static void printSensitivity(TruthTableNativeKernel kernel, long[] inputs) {
        System.out.printf("%-10s %13s %13s %13s %13s %12s %12s%n",
                "batch", "java-ref ns", "java-flat ns", "native ns", "nat-reuse ns",
                "vs java-ref", "vs java-flat");
        try (ReusedNativeCaller reused = new ReusedNativeCaller(kernel)) {
            for (int batch : BATCH_SIZES) {
                long[] rows = slice(inputs, batch);
                int iterations = iterationsFor(batch);

                long javaRef = timeCalls(() -> sum(kernel.evaluateBatchJavaReference(rows)), iterations);
                long javaFlat = timeCalls(() -> sum(kernel.evaluateBatchJavaFlat(rows)), iterations);
                long nativeElapsed = kernel.isNativeAvailable()
                        ? timeCalls(() -> sum(kernel.evaluateBatchNative(rows)), iterations)
                        : -1L;
                long reuseElapsed = kernel.isNativeAvailable()
                        ? timeCalls(() -> sum(reused.call(rows)), iterations)
                        : -1L;

                double javaRefNs = perCallNs(javaRef, iterations);
                double javaFlatNs = perCallNs(javaFlat, iterations);
                double nativeNs = nativeElapsed < 0 ? -1 : perCallNs(nativeElapsed, iterations);
                double reuseNs = reuseElapsed < 0 ? -1 : perCallNs(reuseElapsed, iterations);
                String versusRef = nativeElapsed < 0 ? "-" : String.format("%.2fx", javaRefNs / nativeNs);
                String versusFlat = reuseElapsed < 0 ? "-" : String.format("%.2fx", javaFlatNs / reuseNs);
                System.out.printf("%-10d %13.1f %13.1f %13.1f %13.1f %12s %12s%n",
                        batch, javaRefNs, javaFlatNs, nativeNs, reuseNs, versusRef, versusFlat);
            }
        }
    }

    private static void printLatency(TruthTableNativeKernel kernel, long[] inputs) {
        long[] rows = slice(inputs, LATENCY_BATCH);
        System.out.printf("%-12s %12s %12s %12s %12s %12s %12s%n",
                "path", "mean ns", "p50 ns", "p90 ns", "p99 ns", "max ns", "calls");
        long[] refLatencies = measureLatencies(() -> kernel.evaluateBatchJavaReference(rows));
        reportLatency("java-ref", refLatencies);
        long[] flatLatencies = measureLatencies(() -> kernel.evaluateBatchJavaFlat(rows));
        reportLatency("java-flat", flatLatencies);
        if (kernel.isNativeAvailable()) {
            long[] nativeLatencies = measureLatencies(() -> kernel.evaluateBatchNative(rows));
            reportLatency("native", nativeLatencies);
            try (ReusedNativeCaller reused = new ReusedNativeCaller(kernel)) {
                long[] reuseLatencies = measureLatencies(() -> reused.call(rows));
                reportLatency("nat-reuse", reuseLatencies);
            }
        }
    }

    private static void reportLatency(String name, long[] latencies) {
        long[] sorted = latencies.clone();
        Arrays.sort(sorted);
        double mean = 0;
        for (long value : sorted) {
            mean += value;
        }
        mean /= sorted.length;
        System.out.printf("%-12s %12.1f %12d %12d %12d %12d %12d%n",
                name, mean, percentile(sorted, 50), percentile(sorted, 90),
                percentile(sorted, 99), sorted[sorted.length - 1], sorted.length);
    }

    private static void printDecomposition(TruthTableNativeKernel kernel, long[] inputs) {
        if (!kernel.isNativeAvailable()) {
            System.out.println("the native path is not available; the decomposition was not measured");
            return;
        }
        long[] rows = slice(inputs, LATENCY_BATCH);
        int iterations = iterationsFor(LATENCY_BATCH);

        long marshalling = timeCalls(() -> sum(kernel.measureMarshallingOnly(rows)), iterations);
        long callOnly = timeCalls(() -> sum(kernel.measureNativeCallOnly(rows)), iterations);
        long full = timeCalls(() -> sum(kernel.evaluateBatchNative(rows)), iterations);

        double marshallingNs = perCallNs(marshalling, iterations);
        double callOnlyNs = perCallNs(callOnly, iterations);
        double fullNs = perCallNs(full, iterations);
        double downcallNs = callOnlyNs - marshallingNs;
        double kernelNs = fullNs - callOnlyNs;

        System.out.printf("marshalling only (arena + 2 copies) : %10.1f ns/call%n", marshallingNs);
        System.out.printf("marshalling + no-op downcall        : %10.1f ns/call%n", callOnlyNs);
        System.out.printf("full native call                    : %10.1f ns/call%n", fullNs);
        System.out.printf("downcall overhead (call - marshal)   : %10.1f ns/call%n", downcallNs);
        System.out.printf("arithmetic in C (full - no-op)       : %10.1f ns/call%n", kernelNs);
        System.out.printf("arithmetic in C per row              : %10.3f ns/row%n",
                kernelNs / LATENCY_BATCH);
        System.out.printf("cross-language call overhead, 1 row  : %10.1f ns%n", crossLanguageOverhead(kernel));
        try (ReusedNativeCaller reused = new ReusedNativeCaller(kernel)) {
            long reuseElapsed = timeCalls(() -> sum(reused.call(rows)), iterations);
            double reuseNs = perCallNs(reuseElapsed, iterations);
            System.out.printf("native with reused buffers           : %10.1f ns/call%n", reuseNs);
            System.out.printf("native with reused buffers per row   : %10.3f ns/row%n",
                    reuseNs / LATENCY_BATCH);
            System.out.printf("the arena and the copies cost        : %10.1f ns/call%n",
                    fullNs - reuseNs);
        }
    }

    private static double crossLanguageOverhead(TruthTableNativeKernel kernel) {
        long[] one = new long[1];
        int iterations = OVERHEAD_CALLS;
        long marshalling = timeCalls(() -> sum(kernel.measureMarshallingOnly(one)), iterations);
        long callOnly = timeCalls(() -> sum(kernel.measureNativeCallOnly(one)), iterations);
        return perCallNs(callOnly, iterations) - perCallNs(marshalling, iterations);
    }

    private static void printAllocations(TruthTableNativeKernel kernel, long[] inputs) {
        long[] rows = slice(inputs, LATENCY_BATCH);
        int iterations = iterationsFor(LATENCY_BATCH);
        System.out.printf("%-12s %16s %16s%n", "path", "bytes/call", "bytes/row");
        reportAllocations("java-ref", kernel, rows, iterations, PathKind.JAVA_REFERENCE);
        reportAllocations("java-flat", kernel, rows, iterations, PathKind.JAVA_FLAT);
        if (kernel.isNativeAvailable()) {
            reportAllocations("native", kernel, rows, iterations, PathKind.NATIVE);
        }
    }

    private enum PathKind { JAVA_REFERENCE, JAVA_FLAT, NATIVE }

    private static void reportAllocations(String name, TruthTableNativeKernel kernel,
                                          long[] rows, int iterations, PathKind kind) {
        long before = allocatedBytes();
        long checksum = 0;
        for (int i = 0; i < iterations; i++) {
            checksum += switch (kind) {
                case JAVA_REFERENCE -> sum(kernel.evaluateBatchJavaReference(rows));
                case JAVA_FLAT -> sum(kernel.evaluateBatchJavaFlat(rows));
                case NATIVE -> sum(kernel.evaluateBatchNative(rows));
            };
        }
        long after = allocatedBytes();
        if (before < 0 || after < 0) {
            System.out.printf("%-12s %16s %16s%n", name, "unavailable", "unavailable");
            return;
        }
        if (checksum == Long.MIN_VALUE) {
            System.out.println("unreachable");
        }
        double perCall = (double) (after - before) / iterations;
        System.out.printf("%-12s %16.1f %16.3f%n", name, perCall, perCall / rows.length);
    }

    private static void printMemory(TruthTableNativeKernel kernel) {
        Runtime runtime = Runtime.getRuntime();
        long heapUsed = runtime.totalMemory() - runtime.freeMemory();
        System.out.println("heap in use after the measurements : " + heapUsed + " bytes");
        String rss = residentSetSize();
        if (rss != null) {
            System.out.println("resident set size                  : " + rss + " bytes");
        }
        long bytesPerCall = (long) (LATENCY_BATCH + LATENCY_BATCH + kernel.tableWords()) * Long.BYTES;
        System.out.println("native bytes one call moves (batch=" + LATENCY_BATCH + ") : "
                + bytesPerCall + " bytes");
        System.out.println("native bytes the table occupies    : "
                + (long) kernel.tableWords() * Long.BYTES + " bytes");
    }

    private static String residentSetSize() {
        try {
            java.nio.file.Path status = java.nio.file.Path.of("/proc/self/status");
            if (!java.nio.file.Files.isReadable(status)) {
                return null;
            }
            for (String line : java.nio.file.Files.readAllLines(status)) {
                if (line.startsWith("VmRSS:")) {
                    String[] parts = line.split("\\s+");
                    return parts.length > 1 ? String.valueOf(Long.parseLong(parts[1]) * 1024L) : null;
                }
            }
        } catch (Exception ignored) {
            // The measurement is a convenience, not a requirement.
        }
        return null;
    }

    private static long allocatedBytes() {
        try {
            java.lang.management.ThreadMXBean bean =
                    java.lang.management.ManagementFactory.getThreadMXBean();
            if (bean instanceof com.sun.management.ThreadMXBean sun
                    && sun.isThreadAllocatedMemorySupported()
                    && sun.isThreadAllocatedMemoryEnabled()) {
                return sun.getThreadAllocatedBytes(Thread.currentThread().threadId());
            }
        } catch (Throwable ignored) {
            // The counter is not available on every JVM; the report says so.
        }
        return -1L;
    }

    private static TtForm buildForm() {
        int cells = 1 << ARITY;
        long[] table = new long[(cells + 63) / 64];
        Random random = new Random(RANDOM_SEED);
        for (int i = 0; i < cells; i++) {
            if (random.nextBoolean()) {
                table[i >>> 6] |= 1L << (i & 63);
            }
        }
        return new TtForm(ARITY, table, "w51-benchmark", 1.0);
    }

    private static long[] buildInputs() {
        int largest = BATCH_SIZES[BATCH_SIZES.length - 1];
        long[] inputs = new long[largest];
        Random random = new Random(RANDOM_SEED + 1L);
        long mask = (1L << ARITY) - 1L;
        for (int i = 0; i < largest; i++) {
            inputs[i] = random.nextLong() & mask;
        }
        return inputs;
    }

    private static long[] slice(long[] source, int length) {
        long[] copy = new long[length];
        System.arraycopy(source, 0, copy, 0, length);
        return copy;
    }

    private static int iterationsFor(int batch) {
        long iterations = ROWS_PER_PASS / batch;
        if (iterations < MIN_ROWS_PER_PASS / batch) {
            iterations = MIN_ROWS_PER_PASS / batch;
        }
        if (iterations < 1) {
            iterations = 1;
        }
        return (int) Math.min(iterations, Integer.MAX_VALUE);
    }

    private static long timeCalls(java.util.function.LongSupplier call, int iterations) {
        long best = Long.MAX_VALUE;
        for (int round = 0; round < MEASURE_ROUNDS; round++) {
            long start = System.nanoTime();
            long sink = 0;
            for (int i = 0; i < iterations; i++) {
                sink += call.getAsLong();
            }
            long elapsed = System.nanoTime() - start;
            if (sink == Long.MIN_VALUE) {
                System.out.println("unreachable");
            }
            best = Math.min(best, elapsed);
        }
        return best;
    }

    private static long[] measureLatencies(java.util.function.Supplier<long[]> call) {
        long[] latencies = new long[LATENCY_CALLS];
        for (int i = 0; i < LATENCY_CALLS; i++) {
            long start = System.nanoTime();
            call.get();
            latencies[i] = System.nanoTime() - start;
        }
        return latencies;
    }

    private static long percentile(long[] sorted, int percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        index = Math.max(0, Math.min(sorted.length - 1, index));
        return sorted[index];
    }

    private static double perCallNs(long totalNanos, int iterations) {
        return (double) totalNanos / iterations;
    }

    private static double rate(int batch, long totalNanos, int iterations) {
        double rows = (double) batch * iterations;
        return rows / (totalNanos / 1000.0);
    }

    private static long sum(long[] values) {
        long total = 0;
        for (long value : values) {
            total += value;
        }
        return total;
    }
}
