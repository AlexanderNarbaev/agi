package io.matrix.noosphere;

import io.matrix.neuron.EnrichedNeuron;
import io.matrix.neuron.Neurotransmitter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * DESIGN-26 — REM-cycle dream replay for LTM consolidation.
 * Pure function (CONSTITUTION I).
 */
public final class DreamReplayer {

    public static final long DEFAULT_REPLAY_INTERVAL_MS = 24L * 60 * 60 * 1000;  // 24h
    public static final double RECENT_BOOST = 0.10;       // +10% magnitude
    public static final double OLD_DECAY = 0.05;          // -5% magnitude
    public static final long RECENT_THRESHOLD_MS = 24L * 60 * 60 * 1000;
    public static final long OLD_THRESHOLD_MS = 7L * 24 * 60 * 60 * 1000;

    private DreamReplayer() {}

    /** Replay a single entry: increase magnitude if recent, decay if old. */
    public static FnlEntry replay(FnlEntry entry, long currentTimestamp,
                                 long replayIntervalMs) {
        if (entry == null) throw new IllegalArgumentException("null entry");
        long age = currentTimestamp - entry.createdTimestamp();
        double newMag = entry.magnitude();
        if (age < RECENT_THRESHOLD_MS) {
            newMag = Math.min(1.0, newMag + RECENT_BOOST);
        } else if (age > OLD_THRESHOLD_MS) {
            newMag = Math.max(0.0, newMag - OLD_DECAY);
        }
        return new FnlEntry(entry.id(), entry.table(), newMag,
                entry.chemicalVector(), entry.tag(), entry.provenance(),
                entry.parents(), entry.createdTimestamp(), entry.generation());
    }

    public static FnlEntry replay(FnlEntry entry, long currentTimestamp) {
        return replay(entry, currentTimestamp, DEFAULT_REPLAY_INTERVAL_MS);
    }

    /** Select up to maxBatch entries for replay — most recent first. */
    public static List<FnlEntry> selectForReplay(List<FnlEntry> entries, int maxBatch) {
        if (entries == null || maxBatch <= 0) return List.of();
        List<FnlEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingLong(FnlEntry::createdTimestamp).reversed());
        return sorted.subList(0, Math.min(maxBatch, sorted.size()));
    }

    /** Apply replay to a batch. */
    public static List<FnlEntry> replayBatch(List<FnlEntry> batch, long currentTimestamp) {
        List<FnlEntry> result = new ArrayList<>(batch.size());
        for (FnlEntry e : batch) result.add(replay(e, currentTimestamp));
        return result;
    }
}
