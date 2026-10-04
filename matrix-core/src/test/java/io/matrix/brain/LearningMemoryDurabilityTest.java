package io.matrix.brain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
