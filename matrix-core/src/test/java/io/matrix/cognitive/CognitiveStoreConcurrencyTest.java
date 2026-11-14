package io.matrix.cognitive;

import io.matrix.cognitive.algorithms.CostLedger;
import io.matrix.cognitive.algorithms.MethodOutcome;
import io.matrix.cognitive.algorithms.MethodVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W53 — concurrent writers on one store.
 *
 * <p><b>Why this test exists.</b> The distributed benchmark ran four tasks at once and
 * every one of them failed with "the request id … is already dispatched" — a message that
 * is only true for a task that was really dispatched before. The tasks had <i>distinct</i>
 * request ids, so either the store was misreporting a different failure as a duplicate, or
 * two writers were racing on one file.
 *
 * <p><b>What this test holds.</b> N threads, one store, N <i>distinct</i> dispatch intents:
 * every one of them must be recorded, and none of them may be refused as a duplicate. A
 * refusal here is not a conflict — there was no conflict — and it is the defect this test
 * was written to catch.</p>
 *
 * <p><b>The second thing this class holds.</b> The store keeps ONE JDBC connection, and a
 * JDBC connection is not thread-safe. Every write method of the store is therefore
 * {@code synchronized}, and the second test below proves that the promise holds under real
 * overlap: six writers on one instance, and not one row, count or sequence number is lost
 * on the way.</p>
 */
class CognitiveStoreConcurrencyTest {

    /** The instant the fixtures claim. */
    private static final String AT = "2026-11-09T12:00:00Z";

    /** The workspace the fixtures belong to. */
    private static final String WORKSPACE = "bridged";

    /** How many threads write at once. */
    private static final int WRITERS = 4;

    /** How many intents each thread writes. */
    private static final int PER_WRITER = 4;

    /** How many writers write at the same time in the concurrent-write test. */
    private static final int CONCURRENT_WRITERS = 6;

    /** How many writes each of those writers makes. */
    private static final int WRITES_PER_WRITER = 30;

    /** The sequence the concurrent writers draw their numbers from. */
    private static final String SEQUENCE = "event-position";

    /** The family the concurrent writers record their verdicts in. */
    private static final String FAMILY = "concurrent-family";

    /** The version of the capability the concurrent writers record their verdicts in. */
    private static final String CAPABILITY_VERSION = "1.0.0";

    /** The cost one measured call is charged with. */
    private static final long NANOS = 1_000L;

    @TempDir
    Path directory;

    /** One intent for a task of its own. */
    private static DispatchIntent intent(int index) {
        String requestId = "req-" + index;
        return new DispatchIntent(requestId, "tsk-" + index, "ep-" + index, WORKSPACE,
                "brain.cognitive-inference", "hash-" + index,
                DispatchIntent.State.DISPATCH_PENDING, null, null, null, null, null, null,
                null, AT, AT);
    }

    @Test
    @DisplayName("distinct intents written at once are all recorded, and none is refused")
    void distinctIntentsWrittenAtOnceAreAllRecorded() throws Exception {
        Path database = directory.resolve("cognitive.sqlite");
        List<String> failures = new ArrayList<>();
        try (CognitiveStore store = new CognitiveStore(database)) {
            ExecutorService pool = Executors.newFixedThreadPool(WRITERS);
            List<Callable<Void>> writers = new ArrayList<>();
            for (int writer = 0; writer < WRITERS; writer++) {
                final int base = writer * PER_WRITER;
                writers.add(() -> {
                    for (int step = 0; step < PER_WRITER; step++) {
                        try {
                            store.recordDispatchIntent(intent(base + step));
                        } catch (RuntimeException e) {
                            synchronized (failures) {
                                failures.add(base + step + ": " + e.getClass().getSimpleName()
                                        + " — " + e.getMessage());
                            }
                        }
                    }
                    return null;
                });
            }
            List<Future<Void>> futures = pool.invokeAll(writers, 60, TimeUnit.SECONDS);
            pool.shutdown();
            for (Future<Void> future : futures) {
                future.get();
            }
            assertTrue(failures.isEmpty(),
                    "concurrent intents with distinct ids must not be refused: " + failures);
            assertEquals(WRITERS * PER_WRITER, store.dispatches(WORKSPACE).size(),
                    "every intent must be in the durable record");
        }
    }

    @Test
    @DisplayName("the same intent twice is refused as a duplicate — and says so")
    void theSameIntentTwiceIsRefused() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            store.recordDispatchIntent(intent(1));

            CognitiveConflictException refusal = org.junit.jupiter.api.Assertions.assertThrows(
                    CognitiveConflictException.class,
                    () -> store.recordDispatchIntent(intent(1)),
                    "the same request id must be refused");

            assertTrue(refusal.getMessage().contains("already dispatched"),
                    refusal.getMessage());
        }
    }

    /**
     * W53 — concurrent writers through ONE store instance lose nothing.
     *
     * <p><b>What this test holds.</b> The store keeps ONE JDBC connection, and a JDBC
     * connection is not thread-safe: two threads that use it at the same time are not two
     * writers, they are one writer that has lost the thread of its own work. The write
     * methods of the store are therefore {@code synchronized} — the sequence, the
     * measurement and the verdict among them — and this test proves that the promise holds
     * when the writes really overlap.</p>
     *
     * <p><b>What it proves.</b> Six writers, one store, thirty writes each, released
     * together by a barrier so that the writes of the six of them interleave. Three
     * things must hold when they are done: no write failed; every write is readable back
     * with its exact count (each writer has a method of its own, so the counts can be
     * attributed); and the durable sequence handed out every number exactly once — the
     * first number is one, the last is the number of writes, and no two writers were
     * given the same one. A lost update in the sequence is the defect this was written
     * for: two writers reading the same counter and each adding one to it would leave a
     * gap in the numbering that no later reader could explain.</p>
     */
    @Test
    @DisplayName("concurrent writers on one store lose no row and no sequence number")
    void concurrentWritersOnOneStoreLoseNothing() throws Exception {
        Path database = directory.resolve("concurrent-writers.sqlite");
        List<String> failures = Collections.synchronizedList(new ArrayList<>());
        List<Long> numbers = Collections.synchronizedList(new ArrayList<>());
        int expected = CONCURRENT_WRITERS * WRITES_PER_WRITER;
        try (CognitiveStore store = new CognitiveStore(database)) {
            CyclicBarrier together = new CyclicBarrier(CONCURRENT_WRITERS);
            ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_WRITERS);
            List<Callable<Void>> writers = new ArrayList<>();
            for (int writer = 0; writer < CONCURRENT_WRITERS; writer++) {
                final String methodId = "method-" + writer;
                writers.add(() -> {
                    together.await();
                    for (int step = 0; step < WRITES_PER_WRITER; step++) {
                        try {
                            numbers.add(store.nextSequence(SEQUENCE));
                            store.recordMeasurement(WORKSPACE, methodId, NANOS, true, AT);
                            store.recordVerdict(WORKSPACE, methodId, FAMILY, CAPABILITY_VERSION,
                                    MethodOutcome.VALIDATED, AT);
                        } catch (RuntimeException e) {
                            failures.add(methodId + "/" + step + ": " + e.getClass().getSimpleName()
                                    + " — " + e.getMessage());
                        }
                    }
                    return null;
                });
            }
            List<Future<Void>> futures = pool.invokeAll(writers, 300, TimeUnit.SECONDS);
            pool.shutdown();
            for (Future<Void> future : futures) {
                future.get();
            }

            assertTrue(failures.isEmpty(),
                    "no write of a concurrent writer may fail: " + failures);

            assertEquals(expected, numbers.size(),
                    "every writer must have taken a number from the sequence");
            assertEquals(expected, new HashSet<>(numbers).size(),
                    "no two writers may be handed the same number");
            assertEquals(1L, (long) Collections.min(numbers),
                    "the sequence starts at one");
            assertEquals((long) expected, (long) Collections.max(numbers),
                    "the numbers must be contiguous — a gap is a lost update");

            Map<String, CostLedger.Measurement> measurements = store.measurements(WORKSPACE)
                    .stream()
                    .collect(Collectors.toMap(CostLedger.Measurement::methodId, m -> m));
            Map<String, MethodVerdict> verdicts = store.verdicts(WORKSPACE)
                    .stream()
                    .collect(Collectors.toMap(MethodVerdict::methodId, v -> v));
            for (int writer = 0; writer < CONCURRENT_WRITERS; writer++) {
                String methodId = "method-" + writer;
                assertEquals(WRITES_PER_WRITER, measurements.get(methodId).calls(),
                        "every measurement of " + methodId + " must be in the durable record");
                assertEquals(WRITES_PER_WRITER, verdicts.get(methodId).calls(),
                        "every verdict of " + methodId + " must be in the durable record");
                assertEquals(WRITES_PER_WRITER, verdicts.get(methodId).validated(),
                        "every verdict of " + methodId + " must have been counted as validated");
            }
        }
    }
}
