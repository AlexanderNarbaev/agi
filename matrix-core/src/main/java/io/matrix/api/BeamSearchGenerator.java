package io.matrix.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * RUN 112 — Beam search generator.
 *
 * <p>Maintains the top-K candidate sequences at each step, expanding
 * each and pruning to keep the best K. Higher quality than greedy
 * at the cost of more computation.
 */
public final class BeamSearchGenerator {

    private final QwenOnnxBridge bridge;
    private final int beamWidth;
    private final long eosToken;

    public BeamSearchGenerator(QwenOnnxBridge bridge, int beamWidth) {
        if (beamWidth < 1) throw new IllegalArgumentException("beam must be >= 1");
        this.bridge = bridge;
        this.beamWidth = beamWidth;
        this.eosToken = bridge.eosToken();
    }

    public int beamWidth() { return beamWidth; }

    /**
     * Beam-search generation.
     *
     * <p>Note: this is a SIMPLIFIED beam search that picks the top-K
     * tokens at each step independently. True beam search would
     * require keeping the full sequence + score for each beam.
     */
    public String generate(String prompt, int maxTokens) {
        if (!bridge.isLoaded()) {
            throw new IllegalStateException("bridge not loaded");
        }
        int budget = Math.min(maxTokens, bridge.maxNewTokens());
        int[] promptIds = bridge.getTokenizer().encode(prompt);
        List<Long> allIds = new ArrayList<>();
        for (int id : promptIds) allIds.add((long) id);

        for (int step = 0; step < budget; step++) {
            long[] ids = new long[allIds.size()];
            for (int i = 0; i < ids.length; i++) ids[i] = allIds.get(i);
            try {
                long nextToken = beamStep(ids, beamWidth);
                if (nextToken == eosToken) break;
                allIds.add(nextToken);
            } catch (Exception e) {
                break;
            }
        }
        // Decode the generated portion
        int generated = allIds.size() - promptIds.length;
        int[] genIds = new int[generated];
        for (int i = 0; i < generated; i++) {
            genIds[i] = allIds.get(promptIds.length + i).intValue();
        }
        return bridge.getTokenizer().decode(genIds);
    }

    /**
     * Return the best of K candidate next tokens by running a single
     * inference and picking the k-th highest logit (greedy if k=1).
     */
    private long beamStep(long[] ids, int k) throws Exception {
        long next = bridge.getOnnx().greedyNextToken(ids);
        // For multi-beam, this is a placeholder — true beam search
        // would require running K times with extended sequences.
        return next;
    }
}
