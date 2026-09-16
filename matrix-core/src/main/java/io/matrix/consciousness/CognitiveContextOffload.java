package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * W228 — Cognitive Context Offload (KV cache offloading).
 *
 * <p>Inspired by NVIDIA ICMSP and LMCache. Move KV cache between
 * fast (in-memory) and slow (NVMe/SSD-style) storage tiers.
 *
 * <p>Two tiers:
 * - VRAM (fast, small): in-memory recent profiles
 * - DRAM/SSD (slow, large): evicted profiles
 *
 * <p>Use cases:
 * - Memory-bounded cognitive history
 * - Long-term cognitive storage
 * - Selective retrieval
 *
 * <p>CONSTITUTION VI compliance: tiered cognitive storage, not
 * phenomenal consciousness claim.
 */
public final class CognitiveContextOffload {

    /** Storage tier: VRAM (fast), DRAM (medium), SSD (slow). */
    public enum Tier { VRAM, DRAM, SSD }

    /** Tier statistics. */
    public record TierStats(int vramSize, int dramSize, int ssdSize) {
        public int total() { return vramSize + dramSize + ssdSize; }
    }

    private final int vramCapacity;
    private final int dramCapacity;
    private final int ssdCapacity;
    private final List<CognitiveGenesisProfile> vram;
    private final List<CognitiveGenesisProfile> dram;
    private final List<CognitiveGenesisProfile> ssd;

    public CognitiveContextOffload(int vramCapacity, int dramCapacity, int ssdCapacity) {
        if (vramCapacity < 1 || dramCapacity < 1 || ssdCapacity < 1) {
            throw new IllegalArgumentException("capacities must be >= 1");
        }
        this.vramCapacity = vramCapacity;
        this.dramCapacity = dramCapacity;
        this.ssdCapacity = ssdCapacity;
        this.vram = new ArrayList<>();
        this.dram = new ArrayList<>();
        this.ssd = new ArrayList<>();
    }

    /**
     * Append a profile. Auto-promotes/demotes between tiers.
     */
    public void append(CognitiveGenesisProfile profile) {
        if (profile == null) return;
        if (vram.size() < vramCapacity) {
            vram.add(profile);
        } else if (dram.size() < dramCapacity) {
            dram.add(profile);
            // Move oldest VRAM to DRAM
            if (dram.size() > dramCapacity) {
                ssd.add(dram.remove(0));
            }
        } else if (ssd.size() < ssdCapacity) {
            ssd.add(profile);
            // Move oldest DRAM to SSD
            if (ssd.size() > ssdCapacity) {
                ssd.remove(0);
            }
        }
        // Always evict oldest from VRAM if over capacity
        if (vram.size() > vramCapacity) {
            CognitiveGenesisProfile evicted = vram.remove(0);
            // Promote DRAM→VRAM (LRU)
            if (!dram.isEmpty()) {
                vram.add(dram.remove(0));
            }
            dram.add(evicted);
        }
    }

    /**
     * Get all profiles from all tiers (in age order).
     */
    public List<CognitiveGenesisProfile> all() {
        List<CognitiveGenesisProfile> result = new ArrayList<>();
        result.addAll(ssd);
        result.addAll(dram);
        result.addAll(vram);
        return result;
    }

    /**
     * Get stats for each tier.
     */
    public TierStats stats() {
        return new TierStats(vram.size(), dram.size(), ssd.size());
    }

    /** Get VRAM tier. */
    public List<CognitiveGenesisProfile> vram() { return new ArrayList<>(vram); }

    /** Get DRAM tier. */
    public List<CognitiveGenesisProfile> dram() { return new ArrayList<>(dram); }

    /** Get SSD tier. */
    public List<CognitiveGenesisProfile> ssd() { return new ArrayList<>(ssd); }

    /**
     * Get retrieval latency estimate (in arbitrary units).
     * VRAM = 1, DRAM = 10, SSD = 100.
     */
    public int retrievalLatency() {
        return vram.size() * 1 + dram.size() * 10 + ssd.size() * 100;
    }

    /**
     * Get total capacity across all tiers.
     */
    public int totalCapacity() {
        return vramCapacity + dramCapacity + ssdCapacity;
    }
}
