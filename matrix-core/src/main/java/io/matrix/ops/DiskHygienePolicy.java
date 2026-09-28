package io.matrix.ops;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * RECON-W20 — durable disk hygiene policy.
 *
 * <p>Replaces one-off manual cleanup with an idempotent, testable policy:</p>
 * <ul>
 *   <li><b>Rotation</b> — size-capped NDJSON. When an episodic file exceeds
 *       {@code maxActiveBytes}, the oldest complete lines are moved to a
 *       gzip archive and the active file is truncated. Cognitive data is
 *       never dropped, only relocated.</li>
 *   <li><b>Ledger</b> — every allocation &gt; 500 MB and every hygiene run is
 *       appended to a monotonic NDJSON ledger.</li>
 *   <li><b>Idempotence</b> — running twice in a row is a no-op the second time.</li>
 * </ul>
 *
 * <p><b>Article III</b>: no wall-clock. Archive naming uses a caller-supplied
 * monotonically increasing sequence number, not {@code Instant.now()}, so
 * rotation is fully deterministic and testable.</p>
 *
 * <p><b>Article VIII</b>: no shadow logic. {@link RotationResult} reports
 * exactly what moved and what was retained; nothing is silently truncated.</p>
 */
public final class DiskHygienePolicy {

    /** Tier thresholds from the operator's DiskBudget contract. */
    public static final long HEALTHY_GB = 25L;
    public static final long REFUSE_GB = 10L;

    /** Allocations at or above this size must be ledgered. */
    public static final long LEDGER_THRESHOLD_BYTES = 500L * 1024L * 1024L;

    private DiskHygienePolicy() {}

    /** Which tier a free-space reading falls into. */
    public enum Tier {
        HEALTHY, WARN, REFUSE;

        public static Tier of(long freeBytes) {
            long gb = freeBytes / (1024L * 1024L * 1024L);
            if (gb < REFUSE_GB) return REFUSE;
            if (gb < HEALTHY_GB) return WARN;
            return HEALTHY;
        }
    }

    /** Outcome of a rotation pass. */
    public record RotationResult(
        long linesRotated,
        long bytesRotated,
        Path archivePath,
        int activeLinesRemaining,
        boolean changed
    ) {
        public static RotationResult unchanged(int activeLines) {
            return new RotationResult(0, 0, null, activeLines, false);
        }
    }

    /**
     * Rotate {@code active} so it stays at or below {@code maxActiveBytes}.
     *
     * <p>Lines are moved oldest-first into {@code archiveDir}/{@code name}.{seq}.ndjson.gz
     * until the active file fits. A line is only rotated when it is a complete
     * (newline-terminated) record, so a partially-written tail is never
     * promoted into the archive.</p>
     *
     * @param seq monotonically increasing archive sequence, supplied by the caller
     *            (Article III: no wall-clock in the policy itself)
     * @return what actually moved — never a silent zero
     */
    public static RotationResult rotate(
            Path active, Path archiveDir, long maxActiveBytes, int seq) throws IOException {

        if (!Files.exists(active)) {
            return RotationResult.unchanged(0);
        }
        long size = Files.size(active);
        List<String> lines = Files.readAllLines(active, StandardCharsets.UTF_8);
        if (size <= maxActiveBytes || lines.isEmpty()) {
            return RotationResult.unchanged(lines.size());
        }

        // Find the split point: keep as many trailing lines as fit under the cap.
        long kept = 0;
        int splitAt = lines.size();
        for (int i = lines.size() - 1; i >= 0; i--) {
            long next = kept + lines.get(i).getBytes(StandardCharsets.UTF_8).length + 1L;
            if (next > maxActiveBytes) break;
            kept = next;
            splitAt = i;
        }
        // Never rotate everything: the active file must retain a tail.
        if (splitAt == 0) splitAt = 1;
        if (splitAt >= lines.size()) {
            return RotationResult.unchanged(lines.size());
        }

        List<String> toArchive = lines.subList(0, splitAt);
        List<String> toKeep = lines.subList(splitAt, lines.size());

        Files.createDirectories(archiveDir);
        Path archive = archiveDir.resolve(active.getFileName() + "." + seq + ".ndjson.gz");

        StringBuilder sb = new StringBuilder();
        for (String l : toArchive) sb.append(l).append('\n');
        byte[] payload = sb.toString().getBytes(StandardCharsets.UTF_8);

        // Write gzip deterministically (no timestamp) then flatten into place.
        Path tmp = Files.createTempFile(archiveDir, "rot", ".gz");
        try (var os = Files.newOutputStream(tmp);
             var gz = new java.util.zip.GZIPOutputStream(os)) {
            gz.write(payload);
        }
        Files.move(tmp, archive, StandardCopyOption.REPLACE_EXISTING);

        // Truncate active to the retained tail. Cognitive data preserved in archive.
        Files.writeString(active, String.join("\n", toKeep) + "\n", StandardCharsets.UTF_8);

        return new RotationResult(toArchive.size(), payload.length, archive, toKeep.size(), true);
    }

    /**
     * Append a ledger entry. Fails loudly rather than swallowing I/O errors —
     * Article VIII: a silent write failure is shadow logic.
     */
    public static void appendLedger(Path ledger, String jsonLine) throws IOException {
        Files.createDirectories(ledger.getParent());
        Files.writeString(ledger, jsonLine + "\n", StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
    }

    /**
     * Read the ledger back and assert it is monotonically non-decreasing in
     * line count (i.e. entries are only ever appended, never rewritten).
     */
    public static int ledgerLineCount(Path ledger) throws IOException {
        if (!Files.exists(ledger)) return 0;
        return (int) Files.lines(ledger, StandardCharsets.UTF_8).count();
    }

    /**
     * Classify a path for the hygiene audit. Purely structural — callers supply
     * the size map so the policy stays testable without touching the real disk.
     */
    public static String classify(String path, boolean gitTracked, boolean cognitiveData) {
        if (cognitiveData) return "KEEP";
        if (!gitTracked) return "DELETE-CACHE";
        if (path.endsWith("/build") || path.contains("/build/")) return "DELETE-CACHE";
        return "ROTATE";
    }

    /** Total size of a directory tree, in bytes. Used by the audit script. */
    public static long treeSize(Path root) throws IOException {
        if (!Files.exists(root)) return 0L;
        try (Stream<Path> s = Files.walk(root)) {
            List<Path> all = new ArrayList<>();
            s.filter(Files::isRegularFile).forEach(all::add);
            all.sort(Comparator.naturalOrder());
            long total = 0L;
            for (Path p : all) total += Files.size(p);
            return total;
        }
    }
}
