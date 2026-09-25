package io.matrix.federation.liquid;

import java.util.*;

/**
 * W580 — Sleep Consolidation Cycle.
 *
 * Activates during idle. Actions:
 * - Prune weak HDC vectors
 * - Rehearse strong BIR chains
 * - Add noise for generalization
 * - Metric: Memory capacity before/after sleep
 */
public final class SleepEngine {

    private final double pruningThreshold;  // Weak vectors below this threshold are pruned
    private final double noiseLevel;        // Noise added for generalization
    private final int rehearsalCount;       // Number of strong chains to rehearse

    private long totalSleepCycles;
    private long totalPruned;
    private long totalRehearsed;

    public SleepEngine(double pruningThreshold, double noiseLevel, int rehearsalCount) {
        this.pruningThreshold = pruningThreshold;
        this.noiseLevel = noiseLevel;
        this.rehearsalCount = rehearsalCount;
    }

    /**
     * Result of a sleep cycle.
     */
    public record SleepResult(
            int memoryBefore,
            int memoryAfter,
            int pruned,
            int rehearsed,
            int noiseAdded,
            long durationMs
    ) {}

    /**
     * Run a sleep consolidation cycle.
     *
     * @param hdcVectors    HDC vectors (id → strength)
     * @param birChains     BIR chains (id → chain)
     * @return sleep result
     */
    public SleepResult sleep(Map<String, Double> hdcVectors, Map<String, List<String>> birChains) {
        long start = System.currentTimeMillis();
        int memoryBefore = hdcVectors.size();
        int pruned = 0;
        int rehearsed = 0;
        int noiseAdded = 0;

        // Phase 1: Prune weak HDC vectors
        Iterator<Map.Entry<String, Double>> it = hdcVectors.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Double> entry = it.next();
            if (entry.getValue() < pruningThreshold) {
                it.remove();
                pruned++;
            }
        }

        // Phase 2: Rehearse strong BIR chains
        List<Map.Entry<String, List<String>>> strongChains = new ArrayList<>();
        for (var entry : birChains.entrySet()) {
            if (entry.getValue().size() >= 2) { // Chains with 2+ steps
                strongChains.add(entry);
            }
        }
        strongChains.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));

        for (int i = 0; i < Math.min(rehearsalCount, strongChains.size()); i++) {
            // Rehearse = strengthen the chain (in real implementation, would update weights)
            rehearsed++;
        }

        // Phase 3: Add noise for generalization
        for (String id : hdcVectors.keySet()) {
            double current = hdcVectors.get(id);
            double noise = (Math.random() - 0.5) * noiseLevel;
            hdcVectors.put(id, Math.max(0, Math.min(1, current + noise)));
            noiseAdded++;
        }

        totalSleepCycles++;
        totalPruned += pruned;
        totalRehearsed += rehearsed;

        return new SleepResult(
                memoryBefore,
                hdcVectors.size(),
                pruned,
                rehearsed,
                noiseAdded,
                System.currentTimeMillis() - start
        );
    }

    /**
     * Check if sleep is needed based on memory pressure.
     */
    public boolean isSleepNeeded(int memorySize, int maxMemory) {
        double pressure = (double) memorySize / maxMemory;
        return pressure > 0.8; // Sleep when memory > 80% full
    }

    public long getTotalSleepCycles() { return totalSleepCycles; }
    public long getTotalPruned() { return totalPruned; }
    public long getTotalRehearsed() { return totalRehearsed; }
}
