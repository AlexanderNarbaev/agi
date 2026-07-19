package io.matrix.imports;

import java.io.File;
import java.nio.file.Path;

/**
 * Adaptive model-selection policy for {@link WeightImporter}.
 *
 * <p>Given the machine's free disk space (best-effort heuristic) and maximum
 * heap budget, this class picks the subset of {@link ModelCatalog} entries that
 * (a) fit within the configured resources and (b) maximise coverage by preferring
 * diverse architectures + size classes.
 *
 * <p>Selection algorithm (deterministic, O(n log n)):
 * <ol>
 *   <li>Drop any tier whose smallest entry exceeds {@code diskBudget}.</li>
 *   <li>Within the remaining entries, sort by {@code estimatedBytes ASC}
 *       so we lean toward smaller models unless asked otherwise.</li>
 *   <li>Add entries greedily while accumulated bytes &le; {@code diskBudget}.</li>
 *   <li>Apply a diversity bonus: if a new entry shares an architecture with one
 *       already picked, demote it one slot unless the budget is wide open.</li>
 *   <li>Return the surviving subset (possibly empty if nothing fits).</li>
 * </ol>
 *
 * <p>If {@code diskBudget = 0} (the default) we read {@link File#getUsableSpace()}
 * from the current user's working directory with a 5 GB safety reserve.
 */
public final class AdaptiveSelector {

    /** Default safety margin subtracted from {@link File#getUsableSpace()}. */
    public static final long DEFAULT_DISK_RESERVE_BYTES = 5L * 1024L * 1024L * 1024L;  // 5 GB

    private final long diskBudget;
    private final boolean preferDiversity;
    /** Sentinel value: explicit default-budget selection. */
    private static final long DEFAULT_BUDGET_SENTINEL = -1L;

    public AdaptiveSelector() { this(DEFAULT_BUDGET_SENTINEL, true); }

    public AdaptiveSelector(long diskBudget) { this(diskBudget, true); }

    public AdaptiveSelector(long diskBudget, boolean preferDiversity) {
        // Sentinel: a non-positive value means "use auto-detected budget"
        // unless we explicitly pass zero (then it stays zero).
        if (diskBudget < 0 && diskBudget != DEFAULT_BUDGET_SENTINEL) {
            this.diskBudget = 0;  // clamp explicit negatives to zero
        } else {
            this.diskBudget = diskBudget;
        }
        this.preferDiversity = preferDiversity;
    }

    /** Result: subset of catalog entries that fit, plus diagnostics. */
    public record Selection(
            java.util.List<ModelCatalog.Entry> selected,
            long diskBudgetBytes,
            long chosenBytes,
            int skippedOverBudget) {

        public boolean isEmpty() { return selected.isEmpty(); }

        public String summary() {
            return String.format("Adaptive selection: picked %d / %d entries (%s / %s budget)",
                    selected.size(), skippedOverBudget,
                    humanBytes(chosenBytes), humanBytes(diskBudgetBytes));
        }
    }

    /** Selects the entries that best fit the budget. */
    public Selection select() {
        // Sentinel -1 means "auto": derive from disk; explicit 0 means "really zero".
        long budget = (diskBudget == DEFAULT_BUDGET_SENTINEL) ? computeDefaultDiskBudget() : Math.max(0, diskBudget);

        java.util.List<ModelCatalog.Entry> pool = new java.util.ArrayList<>(
                ModelCatalog.allEntries());
        pool.sort((a, b) -> Long.compare(a.estimatedBytes(), b.estimatedBytes()));

        java.util.List<ModelCatalog.Entry> picked = new java.util.ArrayList<>();
        java.util.Set<String> architecturesSeen = new java.util.HashSet<>();
        long accumulated = 0;
        int skipped = 0;

        for (ModelCatalog.Entry e : pool) {
            if (e.estimatedBytes() > budget) { skipped++; continue; }
            if (accumulated + e.estimatedBytes() > budget) { skipped++; continue; }

            // Diversity penalty: if same architecture as an existing pick, try later entries.
            if (preferDiversity && architecturesSeen.contains(e.architecture())
                    && picked.size() >= 2) {
                // defer — try another entry that hasn't been considered yet.
                continue;
            }
            picked.add(e);
            accumulated += e.estimatedBytes();
            architecturesSeen.add(e.architecture());
        }

        // If we deferred too aggressively (e.g., only one architecture in catalog),
        // fall back to the unconstrained smallest-fit subset.
        if (picked.isEmpty() && budget > 0) {
            for (ModelCatalog.Entry e : pool) {
                if (e.estimatedBytes() <= budget) {
                    picked.add(e);
                    accumulated += e.estimatedBytes();
                }
            }
        }
        return new Selection(java.util.List.copyOf(picked), budget, accumulated, skipped);
    }

    /** Reads free space on the current working dir and subtracts {@link #DEFAULT_DISK_RESERVE_BYTES}. */
    static long computeDefaultDiskBudget() {
        try {
            Path cwd = Path.of(".");
            return Math.max(0, new File(cwd.toString()).getUsableSpace() - DEFAULT_DISK_RESERVE_BYTES);
        } catch (SecurityException | java.nio.file.InvalidPathException e) {
            return 0L;  // 0 means "no budget"
        }
    }

    /** Pretty-print bytes. */
    public static String humanBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / 1024.0 / 1024);
        if (bytes < 1024L * 1024 * 1024 * 1024) return String.format("%.2f GB", bytes / 1024.0 / 1024 / 1024);
        return String.format("%.2f TB", bytes / 1024.0 / 1024 / 1024 / 1024);
    }
}
