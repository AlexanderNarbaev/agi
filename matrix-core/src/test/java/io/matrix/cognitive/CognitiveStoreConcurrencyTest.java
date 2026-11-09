package io.matrix.cognitive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W53 — concurrent dispatch intents on one store.
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
}
