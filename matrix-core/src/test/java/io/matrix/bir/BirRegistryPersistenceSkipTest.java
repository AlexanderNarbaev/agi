package io.matrix.bir;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RECON-W32.20 — a rule lost to a torn write must be VISIBLE.
 *
 * <p>The loader skipped malformed lines with a bare {@code catch (Exception ignored)}.
 * A rule missing from the registry then looked identical to a rule that was never
 * created, and an operator watching a mind forget one had nothing to look at. Same shape
 * as the loader that destroyed 2 910 HDC facts in W32.4 — the only difference being that
 * this file is append-only, so the loss was invisible rather than catastrophic, which
 * is not a defence of the silence.</p>
 *
 * <p>Skipping REMAINS the right behaviour for an append-only log: a torn trailing line is
 * expected, and refusing to load would turn a recoverable file into an unusable one. What
 * was wrong was doing it without a count. So the assertions here are about the count and
 * the report, not about refusing.</p>
 */
class BirRegistryPersistenceSkipTest {

    /**
     * Builds a real record with the REAL writer, then interleaves a torn line.
     *
     * <p>The first version of this test hand-wrote the JSON and loaded 0 of 2 records,
     * because the format needs {@code inputBits}/{@code kWords}/{@code clauses} for
     * {@code reconstructBir} and my guess omitted them. A test that invents a fixture
     * instead of using the producer tests its own guess — so this uses the writer.</p>
     */
    private static void writeGood(BirRegistryPersistence p, String id) throws Exception {
        // Built the way production builds them — register() produces the Entry, and a
        // real ClauseSetForm is what makes reconstructBir succeed on replay.
        var c = new ClauseSetForm.Clause(new long[]{0xFL}, new long[]{0x1L});
        Bir bir = ClauseSetForm.lossy(4, java.util.List.of(c), "src-" + id, 0.8);
        BirRegistry reg = new BirRegistry();
        p.appendRegister(reg.register("rule-" + id, bir, "rule-" + id, 0.8, ("x" + id).getBytes()));
    }

    @Test
    void skippedLinesAreCountedAndReported(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("bir.ndjson");
        BirRegistryPersistence w = new BirRegistryPersistence(file);
        writeGood(w, "rule-a");
        Files.writeString(file, "{\"op\":\"register\",\"id\":\"torn\"\n",
            StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        writeGood(w, "rule-b");

        BirRegistryPersistence p = new BirRegistryPersistence(file);
        BirRegistry reg = new BirRegistry();

        PrintStream realErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        int loaded;
        try {
            System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
            loaded = p.replayInto(reg);
        } finally {
            System.setErr(realErr);
        }
        String report = captured.toString(StandardCharsets.UTF_8);

        assertTrue(loaded >= 1, "the well-formed records must still load, got " + loaded);
        assertEquals(1, p.lastSkippedLines(),
            "the torn line must be COUNTED, not silently dropped — that was the defect");
        assertTrue(report.contains("SKIPPED"),
            "and the count must be reported where an operator can see it, got: " + report);
        assertTrue(report.contains("absent from the registry"),
            "the report must say the consequence, not merely that something happened: "
                + report);
    }

    @Test
    void aCleanFileReportsNoSkips(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("bir.ndjson");
        BirRegistryPersistence w = new BirRegistryPersistence(file);
        writeGood(w, "rule-a");
        writeGood(w, "rule-b");

        BirRegistryPersistence p = new BirRegistryPersistence(file);
        BirRegistry reg = new BirRegistry();
        PrintStream realErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        int loaded;
        try {
            System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
            loaded = p.replayInto(reg);
        } finally {
            System.setErr(realErr);
        }
        assertEquals(2, loaded);
        assertEquals(0, p.lastSkippedLines(),
            "a clean file must not report skips — a counter that is always positive is "
                + "a counter nobody reads");
        assertTrue(captured.toString(StandardCharsets.UTF_8).isEmpty(),
            "and must not print anything");
    }
}
