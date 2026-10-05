package io.matrix.consciousness;

import io.matrix.memory.ConsolidationCycle;
import io.matrix.memory.MemoryHierarchyTier;
import io.matrix.memory.PersistentMemory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * RUN 205 — BrainLoopMemoryAdapter (consolidation on cycles).
 *
 * <p>Wraps BrainLoopService with a PersistentMemory backend.
 * Every N cycles, runs TR + REM consolidation, then persists
 * to disk.
 *
 * <p>Deterministic given cycle count and seed.
 */
public final class BrainLoopMemoryAdapter {

    private final BrainLoopService brain;
    private final ConsolidationCycle cycle;
    private final PersistentMemory persistent;
    private final Path storagePath;
    private final int consolidateEvery;
    private int cyclesSinceConsolidation = 0;
    private int totalCycles = 0;

    public BrainLoopMemoryAdapter(BrainLoopService brain,
                                  Path storagePath) {
        this(brain, storagePath, 50);
    }

    public BrainLoopMemoryAdapter(BrainLoopService brain,
                                  Path storagePath,
                                  int consolidateEvery) {
        this.brain = brain;
        this.cycle = new ConsolidationCycle();
        this.persistent = new PersistentMemory(storagePath);
        this.storagePath = storagePath;
        this.consolidateEvery = consolidateEvery;
    }

    public synchronized void persistOnStart() throws IOException {
        persistent.load();
    }

    public BrainLoopService.CycleResult cycle(String input) {
        var r = brain.cycle(input);
        totalCycles++;
        cyclesSinceConsolidation++;
        // Promote the input to M2 (as a learning trace)
        if (r.accepted()) {
            cycle.store("input-" + totalCycles,
                    input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        if (cyclesSinceConsolidation >= consolidateEvery) {
            cycle.tick();
            // RECON-W32.26: was `catch (IOException ignored) {}` — an EMPTY body, the
            // worst shape in the audit. The consequence is a silent data-loss window:
            // consolidation had ALREADY run (cycle.tick()), the save that persists it
            // failed, and cyclesSinceConsolidation was reset anyway — so the next
            // attempt was consolidateEvery cycles away (50 by default). A crash in that
            // window lost everything consolidated since the last good save, with no
            // counter, no log, and nothing to look at afterwards.
            try {
                persistent.save();
                saveFailures = 0;
            } catch (Exception e) {
                saveFailures++;
                if (saveFailures == 1) {
                    System.err.println("[BrainLoopMemoryAdapter] consolidation save FAILED: "
                        + e + " - this consolidation exists in memory ONLY and WILL be "
                        + "lost on restart; the next attempt is " + consolidateEvery
                        + " cycles away, so treat that window as unpersisted");
                }
            }
            cyclesSinceConsolidation = 0;
        }
        return r;
    }

    /**
     * Consolidation saves that failed. Unit: calls.
     *
     * <p>RECON-W32.26. Non-zero means a consolidation has been computed and NOT
     * persisted, and the next attempt is {@code consolidateEvery} cycles away.</p>
     */
    private int saveFailures = 0;

    /** True when every consolidation has been persisted. Unit: a boolean. */
    public boolean durable() { return saveFailures == 0; }

    public int saveFailures() { return saveFailures; }

    public int totalCycles() { return totalCycles; }
    public int consolidationSteps() {
        return cycle.trSteps() + cycle.remSteps();
    }

    public synchronized byte[] getFromMemory(String key) {
        return cycle.get(key);
    }
}
