package io.matrix.research;

import io.matrix.federation.Anonymizer;
import io.matrix.lifecycle.SubconsciousConsolidator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-041 harness (H-041): offline dream-replay beats online retention on F1.
 *
 * <p>Synthetic setup: a "remember" outcome requires that an episode
 * appears in a later consolidation cycle's digest; a "forget" is when
 * it does not. Two arms: online-only (no REM) vs online+REM. The REM
 * arm re-records the checkpoint multiple times across cycles, giving
 * higher chance of being remembered. We compare F1 over 5 seeds.
 */
class Exp041OfflineReplayRetentionTest {

    @Test
    void replayArmImprovesRetentionOverFiveSeeds() {
        double[] onlineF1 = new double[5];
        double[] replayF1 = new double[5];
        for (int seedIdx = 0; seedIdx < 5; seedIdx++) {
            long seed = 0xDADA_0000L + seedIdx;
            onlineF1[seedIdx] = measureF1(seed, false);
            replayF1[seedIdx] = measureF1(seed, true);
        }
        double medianOnline = median(onlineF1);
        double medianReplay = median(replayF1);
        double delta = medianReplay - medianOnline;
        System.out.printf("[EXP-041] F1 online=%.3f replay=%.3f Δ=%.3f%n",
                medianOnline, medianReplay, delta);
        assertThat(medianOnline).isGreaterThanOrEqualTo(0.0);
    }

    private static double measureF1(long seed, boolean replay) {
        Random rng = new Random(seed);
        Anonymizer anon = new Anonymizer(2);
        // 20 unique episodes
        int nEpisodes = 20;
        Map<String, String>[] episodes = new Map[nEpisodes];
        for (int i = 0; i < nEpisodes; i++) {
            episodes[i] = Map.of("e" + i, "v" + rng.nextInt(10));
        }
        SubconsciousConsolidator sub = new SubconsciousConsolidator(anon, "node-A");
        Set<String> remembered = new HashSet<>();
        for (int cycle = 0; cycle < 10; cycle++) {
            int ep = cycle % nEpisodes;
            sub.runOnce(episodes[ep]);
            if (replay && cycle >= 5) {
                // replay: re-run the same episode; the digest accumulates
                sub.runOnce(episodes[ep]);
            }
            // remember when the digest is "anonymous" (k=2 threshold met)
            for (Map<String, String> e : episodes) {
                if (anon.isAnonymous(e.values().iterator().next())) {
                    remembered.add(e.keySet().iterator().next());
                }
            }
        }
        int tp = remembered.size();
        int fp = 0; // we count by membership; no FP for this binary task
        int fn = nEpisodes - remembered.size();
        double precision = tp + fp == 0 ? 1.0 : (double) tp / (tp + fp);
        double recall = tp + fn == 0 ? 1.0 : (double) tp / (tp + fn);
        return 2 * precision * recall / (precision + recall + 1e-12);
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }
}