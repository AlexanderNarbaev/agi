package io.matrix.nativekernel;

import io.matrix.bir.TtForm;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * W51 — the Java/C boundary on one inference kernel: the batched evaluation
 * of a boolean truth table (the BIR {@code TtForm} form).
 *
 * <h2>What this class is NOT</h2>
 * <p>It is not a second implementation of the truth-table lookup. Every Java
 * result it produces comes from {@link TtForm#eval} or
 * {@link TtForm#evalBatch} — the BIR reference — and every native result
 * comes from the C library, whose arithmetic is the reference's arithmetic
 * written in unsigned integers. The two are compared against each other in
 * the tests, so a divergence is a failure, not a surprise.
 *
 * <h2>The three paths</h2>
 * <ul>
 *   <li>{@link #evaluateBatchJavaReference(long[])} — the BIR reference API,
 *       {@code TtForm.evalBatch(long[][], long[][])}. This is what a caller
 *       pays when it uses the reference directly, including the construction
 *       of the two-dimensional arrays the reference API takes.</li>
 *   <li>{@link #evaluateBatchJavaFlat(long[])} — the same BIR code, called one
 *       row at a time through {@code TtForm.eval}, into a flat array. It is
 *       the shape the native path uses, so the difference between this and
 *       the native path is the LANGUAGE and nothing else.</li>
 *   <li>{@link #evaluateBatchNative(long[])} — the C library through FFM.</li>
 * </ul>
 * <p>{@link #evaluateBatch(long[])} chooses between the reference path and
 * the native path by the policy in {@link NativeKernelPolicy}; it never
 * chooses the flat path, because the flat path is a measurement instrument
 * and not a production shape.
 *
 * <h2>Why this kernel</h2>
 * <p>It is the canonical evaluation of a boolean function in BIR
 * ({@code io.matrix.bir.TtForm.eval}), it has a JMH benchmark
 * ({@code io.matrix.bir.BirEvaluateBenchmark}), it is reachable from
 * production code ({@code BooleanRuntime.evaluate} /
 * {@code BooleanRuntime.evaluateBatch}, and it is deterministic and
 * integer-only. Whether it is a MEASURED bottleneck is a separate question,
 * and the answer is in the document that accompanies this class: it is a
 * candidate, not a proven hot spot.
 */
public final class TruthTableNativeKernel implements AutoCloseable {

    private final TtForm form;
    private final int arity;
    private final int tableWords;
    private final long[] tableSnapshot;
    private final NativeKernelLoader loader;

    private Arena tableArena;
    private MemorySegment tableSegment;

    /**
     * Creates a kernel over a BIR truth table, with the process-wide loader.
     *
     * @param form the BIR truth table; it is the reference for every result.
     */
    public TruthTableNativeKernel(TtForm form) {
        this(form, NativeKernelLoader.instance());
    }

    /**
     * Creates a kernel over a BIR truth table, with a given loader.
     *
     * <p>The loader is a parameter so that the "the shared object is not
     * there" case can be exercised without touching the process-wide loader.
     *
     * @param form   the BIR truth table; it is the reference for every result.
     * @param loader the loader that may or may not have the library.
     */
    public TruthTableNativeKernel(TtForm form, NativeKernelLoader loader) {
        if (form == null) {
            throw new IllegalArgumentException("form must not be null");
        }
        this.form = form;
        this.loader = loader == null ? NativeKernelLoader.instance() : loader;
        this.arity = form.k();
        this.tableSnapshot = form.table();
        this.tableWords = this.tableSnapshot.length;
    }

    /**
     * The arity of the function.
     *
     * @return the number of input bits.
     */
    public int arity() {
        return arity;
    }

    /**
     * The number of 64-bit words in the truth table.
     *
     * @return the word count.
     */
    public int tableWords() {
        return tableWords;
    }

    /**
     * The loader this kernel uses.
     *
     * @return the loader.
     */
    public NativeKernelLoader loader() {
        return loader;
    }

    /**
     * Whether the native path is usable at all.
     *
     * @return true when the shared object was loaded.
     */
    public boolean isNativeAvailable() {
        return loader.isAvailable();
    }

    /**
     * The path a batch of the given size would be evaluated by.
     *
     * @param batchSize how many rows the batch holds.
     * @return the path the policy selects.
     */
    public NativeKernelPolicy.Path selectedPath(int batchSize) {
        return NativeKernelPolicy.select(loader.isAvailable(), batchSize, arity);
    }

    /**
     * Evaluates a batch, by the path the policy selects.
     *
     * <p>This is the production-shaped entry point: the caller hands over a
     * flat array of packed input words and receives a flat array of results.
     *
     * <p>The Java path here is {@link #evaluateBatchJavaFlat(long[])}, not
     * {@link #evaluateBatchJavaReference(long[])}. Both call the BIR
     * reference — the first one row at a time through {@code TtForm.eval},
     * the second in one call through {@code TtForm.evalBatch} — but the
     * second one makes the caller build two-dimensional arrays first, which
     * is the dominant cost of the whole operation. The 2D path is kept
     * because it is the ground truth every comparison in this package is
     * made against, not because it is the cheapest way to be served.
     *
     * @param inputs the packed input words, one per row.
     * @return the results, one packed word per row, each 0 or 1.
     */
    public long[] evaluateBatch(long[] inputs) {
        if (inputs == null) {
            throw new IllegalArgumentException("inputs must not be null");
        }
        if (selectedPath(inputs.length) == NativeKernelPolicy.Path.NATIVE) {
            return evaluateBatchNative(inputs);
        }
        return evaluateBatchJavaFlat(inputs);
    }

    /**
     * Evaluates a batch through the BIR reference API.
     *
     * <p>This is the ground truth of every comparison in this package: it is
     * {@code TtForm.evalBatch} itself, with the two-dimensional arrays that
     * API takes.
     *
     * @param inputs the packed input words, one per row.
     * @return the results, one packed word per row, each 0 or 1.
     */
    public long[] evaluateBatchJavaReference(long[] inputs) {
        int n = inputs.length;
        long[][] rows = new long[n][1];
        long[][] results = new long[n][1];
        for (int i = 0; i < n; i++) {
            rows[i][0] = inputs[i];
        }
        form.evalBatch(rows, results);
        long[] flat = new long[n];
        for (int i = 0; i < n; i++) {
            flat[i] = results[i][0];
        }
        return flat;
    }

    /**
     * Evaluates a batch one row at a time, through the BIR reference.
     *
     * <p>The data shape is the one the native path uses, so the difference
     * between this and the native path is the language and nothing else.
     * The BIR code is still the one that computes the answer.
     *
     * @param inputs the packed input words, one per row.
     * @return the results, one packed word per row, each 0 or 1.
     */
    public long[] evaluateBatchJavaFlat(long[] inputs) {
        int n = inputs.length;
        long[] flat = new long[n];
        long[] row = new long[1];
        long[] answer = new long[1];
        for (int i = 0; i < n; i++) {
            row[0] = inputs[i];
            form.eval(row, answer);
            flat[i] = answer[0];
        }
        return flat;
    }

    /**
     * Evaluates a batch natively.
     *
     * @param inputs the packed input words, one per row.
     * @return the results, one packed word per row, each 0 or 1.
     * @throws NativeKernelException when the native side refuses the call.
     * @throws IllegalStateException  when the shared object is not available.
     */
    public long[] evaluateBatchNative(long[] inputs) {
        if (!loader.isAvailable()) {
            throw new IllegalStateException("the native kernel is not available: "
                    + loader.unavailableReason());
        }
        int n = inputs.length;
        MemorySegment table = nativeTable();
        try (Arena arena = Arena.ofConfined()) {
            Marshalled marshalled = marshal(arena, inputs);
            int status = loader.evalBatch(table, tableWords, arity,
                    marshalled.inputs(), marshalled.outputs(), n);
            if (!NativeKernelStatus.isOk(status)) {
                throw new NativeKernelException(status, "the native truth-table evaluation was refused");
            }
            if (n == 0) {
                return new long[0];
            }
            return marshalled.outputs().toArray(ValueLayout.JAVA_LONG);
        }
    }

    /**
     * The marshalling of a batch, without the call.
     *
     * <p>A MEASUREMENT INSTRUMENT, not a way to produce a result: it allocates
     * the arena, copies the inputs in and copies the outputs out, and it does
     * not call the native side. The difference between this and
     * {@link #evaluateBatchNative(long[])} is the cost of the crossing, and
     * the difference between this and {@link #measureNativeCallOnly(long[])}
     * is the cost of the marshalling alone.
     *
     * @param inputs the packed input words, one per row.
     * @return the output buffer, which holds zeros; the values are not computed.
     */
    public long[] measureMarshallingOnly(long[] inputs) {
        try (Arena arena = Arena.ofConfined()) {
            Marshalled marshalled = marshal(arena, inputs);
            if (inputs.length == 0) {
                return new long[0];
            }
            return marshalled.outputs().toArray(ValueLayout.JAVA_LONG);
        }
    }

    /**
     * The marshalling of a batch plus a downcall that computes nothing.
     *
     * <p>A MEASUREMENT INSTRUMENT, not a way to produce a result: it crosses
     * the boundary with the same ABI and the same buffers as the real call,
     * but the native entry point it reaches does not write anything. The
     * difference between this and {@link #measureMarshallingOnly(long[])} is
     * the cost of the downcall itself.
     *
     * @param inputs the packed input words, one per row.
     * @return the output buffer, which holds zeros; the values are not computed.
     */
    public long[] measureNativeCallOnly(long[] inputs) {
        if (!loader.isAvailable()) {
            throw new IllegalStateException("the native kernel is not available: "
                    + loader.unavailableReason());
        }
        int n = inputs.length;
        MemorySegment table = nativeTable();
        try (Arena arena = Arena.ofConfined()) {
            Marshalled marshalled = marshal(arena, inputs);
            int status = loader.evalBatchNoop(table, tableWords, arity,
                    marshalled.inputs(), marshalled.outputs(), n);
            if (!NativeKernelStatus.isOk(status)) {
                throw new NativeKernelException(status, "the native probe call was refused");
            }
            if (n == 0) {
                return new long[0];
            }
            return marshalled.outputs().toArray(ValueLayout.JAVA_LONG);
        }
    }

    /**
     * The buffers a batch is marshalled into.
     *
     * @param inputs the packed input words.
     * @return the input and output segments.
     */
    private Marshalled marshal(Arena arena, long[] inputs) {
        int n = inputs.length;
        if (n == 0) {
            // A zero-length batch still crosses the boundary, so the native
            // side sees a real call with a count of zero.
            return new Marshalled(arena.allocate(ValueLayout.JAVA_LONG, 1),
                    arena.allocate(ValueLayout.JAVA_LONG, 1));
        }
        return new Marshalled(arena.allocateFrom(ValueLayout.JAVA_LONG, inputs),
                arena.allocate(ValueLayout.JAVA_LONG, n));
    }

    /**
     * The two buffers of a marshalled batch.
     *
     * @param inputs  the input segment.
     * @param outputs the output segment.
     */
    private record Marshalled(MemorySegment inputs, MemorySegment outputs) {
    }

    /**
     * The truth table in native memory.
     *
     * <p>It is copied once, when it is first needed, and it lives as long as
     * this kernel: the table does not change, and copying it per call would
     * put a cost into the measurement that belongs to neither side.
     *
     * @return the native segment holding the table.
     */
    public synchronized MemorySegment nativeTableSegment() {
        return nativeTable();
    }

    private synchronized MemorySegment nativeTable() {
        if (tableSegment == null) {
            tableArena = Arena.ofShared();
            tableSegment = tableArena.allocateFrom(ValueLayout.JAVA_LONG, tableSnapshot);
        }
        return tableSegment;
    }

    @Override
    public void close() {
        synchronized (this) {
            if (tableArena != null) {
                tableArena.close();
                tableArena = null;
                tableSegment = null;
            }
        }
    }
}
