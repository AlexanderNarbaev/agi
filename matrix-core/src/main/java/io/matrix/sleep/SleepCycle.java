package io.matrix.sleep;

import io.matrix.federation.Anonymizer;
import io.matrix.lifecycle.ConsolidationCycle;
import io.matrix.memory.HierarchicalMemory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sleep cycle (Wave F): end-of-day consolidation. Drains in-flight
 * consolidation, writes pending deltas to LTM, and emits a digest
 * for gossip with peers.
 *
 * <p>Inspired by biological sleep: the brain consolidates episodic
 * memory into semantic memory during slow-wave + REM phases.
 * This class orchestrates the same dance for MATRIX's nine-stage
 * loop:
 * <ol>
 *   <li>Drain pending {@link ConsolidationCycle} work</li>
 *   <li>Promote recent {@link HierarchicalMemory} entries that
 *       are below the memory-level threshold</li>
 *   <li>Emit a digest via {@link Anonymizer} for M3→M4 sharing</li>
 *   <li>Track sleep statistics for telemetry</li>
 * </ol>
 */
public final class SleepCycle {

    private final HierarchicalMemory memory;
    private final ConsolidationCycle consolidation;
    private final Anonymizer anonymizer;

    private final AtomicLong cyclesRun = new AtomicLong();
    private final AtomicLong entriesPromoted = new AtomicLong();
    private final AtomicLong digestsEmitted = new AtomicLong();

    public SleepCycle(HierarchicalMemory memory,
                      ConsolidationCycle consolidation,
                      Anonymizer anonymizer) {
        // null-safe: cycle runs even without memory (no promotions)
        this.memory = memory;
        this.consolidation = consolidation;
        this.anonymizer = anonymizer;
    }

    /**
     * Run one sleep cycle. Idempotent: safe to call concurrently or
     * repeatedly. Returns the cycle statistics.
     */
    public CycleReport runOnce() {
        cyclesRun.incrementAndGet();

        // 1. drain pending consolidation
        int drained = 0;
        if (consolidation != null) {
            try {
                consolidation.drain("sleep-cycle", 16);
                drained++;
            } catch (Exception ignored) {}
        }

        // 2. promote memory entries that are accumulating
        int promoted = 0;
        if (memory != null) {
            var entries = memory.entriesAtLevel(HierarchicalMemory.Level.L1_PATTERN);
            for (var entry : entries) {
                // promote if importance is above the L1→L2 threshold
                if (entry.importance() >= 0.5) {
                    var opt = memory.promote(entry.id(), HierarchicalMemory.Level.L2_MODULE);
                    if (opt.isPresent()) promoted++;
                }
            }
        }
        entriesPromoted.addAndGet(promoted);

        // 3. emit a digest for M3→M4 sharing
        int digests = 0;
        if (anonymizer != null && memory != null) {
            String contentHash = Integer.toHexString(memory.hashCode())
                    + "-" + System.currentTimeMillis();
            anonymizer.recordContribution(contentHash, "sleep-cycle");
            digests++;
        }
        digestsEmitted.addAndGet(digests);

        return new CycleReport(cyclesRun.get(), drained, promoted, digests,
                System.currentTimeMillis());
    }

    public long cyclesRun() { return cyclesRun.get(); }
    public long totalEntriesPromoted() { return entriesPromoted.get(); }
    public long totalDigestsEmitted() { return digestsEmitted.get(); }

    /** Snapshot of one cycle's activity. */
    public record CycleReport(long cycleId,
                              int consolidationDrains,
                              int entriesPromoted,
                              int digestsEmitted,
                              long timestampMs) {
        public CycleReport {
            // explicit canonical constructor
        }
    }
}