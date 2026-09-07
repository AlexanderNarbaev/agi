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
            try {
                persistent.save();
            } catch (IOException ignored) {}
            cyclesSinceConsolidation = 0;
        }
        return r;
    }

    public int totalCycles() { return totalCycles; }
    public int consolidationSteps() {
        return cycle.trSteps() + cycle.remSteps();
    }

    public synchronized byte[] getFromMemory(String key) {
        return cycle.get(key);
    }
}
