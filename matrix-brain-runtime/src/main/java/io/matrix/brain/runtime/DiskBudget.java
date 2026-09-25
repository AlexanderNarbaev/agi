package io.matrix.brain.runtime;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TRUE-W0 — Disk budget guard.
 *
 * <p>Pre-flight free-space checks before any heavy operation (native
 * build, distillation, dataset download). The thresholds are:</p>
 *
 * <ul>
 *   <li>{@code &gt;= 25 GB} free: healthy; log only.</li>
 *   <li>{@code &lt; 25 GB} free: WARN; archive/compress large artifacts.</li>
 *   <li>{@code &lt; 10 GB} free: REFUSE heavy operations; throw
 *       {@link DiskBudgetExceeded}.</li>
 * </ul>
 *
 * <p>Each operation that writes a sizable artifact records a row in the
 * ledger (size + timestamp + tag) so the operator can audit consumption.</p>
 */
public final class DiskBudget {

    private static final Logger LOG = Logger.getLogger(DiskBudget.class.getName());
    public static final long WARN_THRESHOLD_BYTES = 25L * 1024 * 1024 * 1024;  // 25 GB
    public static final long REFUSE_THRESHOLD_BYTES = 10L * 1024 * 1024 * 1024; // 10 GB

    private final Path root;
    private long ledgerBytes = 0;

    public DiskBudget(Path root) {
        this.root = root;
    }

    public static DiskBudget forRoot(Path root) {
        return new DiskBudget(root);
    }

    /** Bytes free at {@link #root}. */
    public long freeBytes() {
        try {
            FileStore fs = Files.getFileStore(root);
            return fs.getUsableSpace();
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "DiskBudget.freeBytes failed: {0}", ex.getMessage());
            return 0L;
        }
    }

    /** GB free at {@link #root}. */
    public double freeGigabytes() {
        return freeBytes() / (1024.0 * 1024.0 * 1024.0);
    }

    /** Classify free space into one of three tiers. */
    public Tier tier() {
        long free = freeBytes();
        if (free < REFUSE_THRESHOLD_BYTES) return Tier.REFUSE;
        if (free < WARN_THRESHOLD_BYTES)   return Tier.WARN;
        return Tier.HEALTHY;
    }

    public enum Tier { HEALTHY, WARN, REFUSE }

    /**
     * Pre-flight check before an operation that needs {@code neededBytes} of
     * free space. Throws {@link DiskBudgetExceeded} if refused.
     */
    public void check(long neededBytes, String op) {
        long free = freeBytes();
        long projected = free - neededBytes;
        if (projected < REFUSE_THRESHOLD_BYTES) {
            throw new DiskBudgetExceeded(
                op + " needs " + neededBytes + " bytes; only " + free
                + " bytes free (would drop below 10 GB threshold).");
        }
        if (projected < WARN_THRESHOLD_BYTES) {
            LOG.log(Level.WARNING,
                "{0}: post-operation free = {1} bytes (< 25 GB). Consider archiving artifacts.",
                new Object[]{op, projected});
        }
        LOG.log(Level.INFO, "{0}: free={1} needed={2} -> ok", new Object[]{op, free, neededBytes});
    }

    /** Record an artifact write in the in-memory ledger. */
    public void recordWrite(String tag, long bytes) {
        ledgerBytes += bytes;
        LOG.log(Level.INFO, "DiskBudget: wrote {0} bytes for {1} (cumulative={2} bytes)",
            new Object[]{bytes, tag, ledgerBytes});
    }

    public long ledgerBytes() { return ledgerBytes; }

    /** Snapshot for /v1/status rendering. */
    public java.util.Map<String, Object> snapshot() {
        var m = new java.util.LinkedHashMap<String, Object>();
        m.put("free_bytes", freeBytes());
        m.put("free_gb", String.format("%.2f", freeGigabytes()));
        m.put("tier", tier().name());
        m.put("warn_threshold_gb", 25);
        m.put("refuse_threshold_gb", 10);
        m.put("cumulative_writes_bytes", ledgerBytes);
        return m;
    }

    /** Convenience: do a static check on a path. */
    public static void requireFreeBytes(Path root, long needed, String op) {
        new DiskBudget(root).check(needed, op);
    }

    /** Raised when an operation would drop free space below the refuse threshold. */
    public static class DiskBudgetExceeded extends RuntimeException {
        public DiskBudgetExceeded(String msg) { super(msg); }
    }
}
