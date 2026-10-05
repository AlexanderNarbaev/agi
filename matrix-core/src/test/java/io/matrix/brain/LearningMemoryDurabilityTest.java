package io.matrix.brain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RECON-W32.22 - a save that fails must not look like a save that worked.
 *
 * <p>{@code LearningMemory.save()} caught {@code IOException} and did nothing. That is a
 * materially worse failure than a read that skips a line: a read skip loses data the mind
 * already knew it had, whereas a write failure means <em>the mind believes it saved</em>.
 * The in-memory map keeps growing, every later call reports success, and the loss only
 * surfaces at the next restart - with nothing having looked wrong at any point.</p>
 *
 * <p>The write is still attempted and still does not throw: a memory subsystem that
 * throws on a full disk takes the mind down with it, and losing the ability to think is
 * worse than losing the file. What changed is that the failure is counted, reported once
 * with its consequence, and queryable.</p>
 */
class LearningMemoryDurabilityTest {

    /** Makes the parent directory a FILE so Files.createDirectories cannot succeed. */
    private static Path unwritableTarget(Path dir) throws Exception {
        Path blocker = dir.resolve("not-a-directory");
        Files.writeString(blocker, "this is a file where a directory would need to be");
        return blocker.resolve("memory.json");
    }

    @Test
    void aFailedSaveIsCountedAndReported(@TempDir Path dir) throws Exception {
        Path target = unwritableTarget(dir);
        LearningMemory m = new LearningMemory(target);

        PrintStream realErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
            m.store("k", "v");
        } finally {
            System.setErr(realErr);
        }
        String out = captured.toString(StandardCharsets.UTF_8);

        assertEquals(1, m.saveFailures(),
            "a failed write must be counted - it used to be swallowed entirely");
        assertFalse(m.durable(),
            "and the memory must admit it is not durable right now");
        assertTrue(out.contains("WILL be lost on restart"),
            "the report must state the consequence, not merely that an exception "
                + "occurred: " + out);
    }

    @Test
    void aSuccessfulSaveIsDurableAndSilent(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("memory.json");
        LearningMemory m = new LearningMemory(file);

        PrintStream realErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
            m.store("colour", "red");
            m.store("shape", "circle");
        } finally {
            System.setErr(realErr);
        }

        assertEquals(0, m.saveFailures());
        assertTrue(m.durable(), "a clean write must report durability");
        assertTrue(captured.toString(StandardCharsets.UTF_8).isEmpty(),
            "and must print nothing, or the warning is noise nobody reads");
        assertEquals("red", new LearningMemory(file).recall("colour"),
            "and the fact must really be on disk - asserted through the real producer, "
                + "not a hand-written fixture");
    }

    @Test
    void aFailedLoadIsReportedBecauseAnEmptyMemoryLooksLikeAFreshOne(@TempDir Path dir)
            throws Exception {
        // A MISSING file is not a failed load: load() returns early when the file is
        // absent, and correctly so — there is nothing to fail. My first version used a
        // missing path and the test failed, which is the test being wrong rather than the
        // code. A failed load needs an UNREADABLE file, so a directory stands where a
        // file is expected: Files.readString then throws genuinely.
        Path unreadable = dir.resolve("memory.json");
        Files.createDirectories(unreadable);
        PrintStream realErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        LearningMemory m;
        try {
            System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
            m = new LearningMemory(unreadable);
        } finally {
            System.setErr(realErr);
        }
        String out = captured.toString(StandardCharsets.UTF_8);

        assertTrue(m.loadFailures() > 0, "a failed load must be counted");
        assertTrue(out.contains("EMPTY"),
            "and reported as starting empty, which is indistinguishable from a fresh "
                + "start without it: " + out);
    }

    // ---- RECON-W32.24: durable() must not lie --------------------------------
    //
    // Each of these encodes a MEASURED reproduction from an independent code review,
    // not a hypothetical. The reviewer ran them and got 15 737 escaped
    // ConcurrentModificationExceptions and 3 792 lost counter updates; these tests are
    // the same attacks, so a regression is a test failure rather than a surprise.

    @Test
    void durableIsFalseWhenTheFileWasNeverCreated(@TempDir Path dir) {
        // getParent() is null for a bare filename, so Files.createDirectories(null) NPEs
        // inside save(). The old save() caught IOException only, so the NPE escaped and
        // durable() -- then saveFailures == 0 -- answered "everything is on disk".
        Path bare = dir.getFileSystem().getPath("learned-facts.json");
        LearningMemory m = new LearningMemory(bare);
        try {
            m.store("k", "v");
        } catch (RuntimeException expected) {
            // acceptable: the caller is told. What is NOT acceptable is a silent lie.
        }
        assertFalse(m.durable(),
            "durable() must not report every fact is on disk when no file was written");
    }

    @Test
    void aNullFactIsRejectedWithAMessageRatherThanInsideSave(@TempDir Path dir) {
        // A null value used to reach escape() and NPE from INSIDE save(), walking past
        // the failure counter. durable() then said all-clear for an unwritten fact.
        LearningMemory m = new LearningMemory(dir.resolve("m.json"));
        assertThrows(NullPointerException.class, () -> m.store("k", null),
            "a null fact must be refused at the door, with the counter untouched");
        assertEquals(0, m.saveFailures(),
            "an argument error is not a save failure and must not be counted as one");
        assertTrue(m.durable(),
            "nothing was written and nothing failed, so the (empty) memory is durable");
    }

    @Test
    void concurrentStoresLoseNothingAndDoNotEscape(@TempDir Path dir) throws Exception {
        // The HashMap iteration race. 4 threads x 40k stores threw 15 737
        // ConcurrentModificationExceptions, each losing facts, each saying durable().
        int threads = 4, perThread = 4000;
        LearningMemory m = new LearningMemory(dir.resolve("m.json"));
        List<Throwable> escaped = Collections.synchronizedList(new ArrayList<>());
        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int id = t;
            Thread w = new Thread(() -> {
                for (int i = 0; i < perThread; i++) {
                    try { m.store("t" + id + "-f" + i, "v" + i); }
                    catch (Throwable e) { escaped.add(e); }
                }
            });
            workers.add(w); w.start();
        }
        for (Thread w : workers) w.join();
        assertEquals(0, escaped.size(),
            "no store() may throw: " + escaped.stream().findFirst().orElse(null));
        assertEquals(threads * perThread, m.size(),
            "every concurrent fact must survive in memory");
    }

    @Test
    void theFailureCounterDoesNotLoseUpdatesUnderConcurrency(@TempDir Path dir) throws Exception {
        // volatile int is not atomic. 8 threads x 20k lost 3 792 of 160 000 updates, and
        // a counter that under-counts failures under-reports data loss.
        int threads = 8, perThread = 2000;
        LearningMemory m = new LearningMemory(dir.resolve("m.json"));
        // A DIRECTORY where a file is expected: createDirectories succeeds on its
        // parent, and writeString then throws for real. My first version passed a plain
        // filename here, every write SUCCEEDED, and the test reported 0 failures - which
        // is what a green-looking zero is worth.
        Path blocked = Files.createDirectories(dir.resolve("blocked.json"));
        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            Thread w = new Thread(() -> {
                for (int i = 0; i < perThread; i++) m.saveTo(blocked);
            });
            workers.add(w); w.start();
        }
        for (Thread w : workers) w.join();
        assertEquals(threads * perThread, m.saveFailures(),
            "every failed save must be counted exactly once");
    }

    @Test
    void durableIsFalseWheneverAnyWriteHasFailed(@TempDir Path dir) throws Exception {
        // The end-to-end statement of intent: after a failed write, nothing may claim
        // the facts are safe.
        Path asDir = dir.resolve("m.json");
        Files.createDirectories(asDir);
        LearningMemory m = new LearningMemory(asDir);
        m.store("k", "v");
        assertTrue(m.saveFailures() > 0, "the write must have failed");
        assertFalse(m.durable(), "and durable() must say so");
    }
}
