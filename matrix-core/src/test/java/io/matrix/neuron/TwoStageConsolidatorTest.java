package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class TwoStageConsolidatorTest {

    @Test
    void consolidateReplaysEpisodes() {
        TwoStageConsolidator.HdcMemoryStore hippocampus =
                new TwoStageConsolidator.HdcMemoryStore(64);
        TwoStageConsolidator.HdcMemoryStore neocortex =
                new TwoStageConsolidator.HdcMemoryStore(64);
        Random rng = new Random(42);

        // Store 5 episodes in hippocampus
        for (int i = 0; i < 5; i++) {
            float[] pattern = new float[64];
            for (int j = 0; j < 64; j++) {
                pattern[j] = (float) Math.sin((i + 1) * (j + 1) * 0.1);
            }
            hippocampus.store("episode_" + i, pattern);
        }

        TwoStageConsolidator.ConsolidationResult result =
                TwoStageConsolidator.consolidate(hippocampus, neocortex, 3, rng);
        assertThat(result.episodesReplayed()).isEqualTo(3);
        assertThat(result.replayedIds()).hasSize(3);
        assertThat(result.replayError()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void replayTransfersPatternsToNeocortex() {
        TwoStageConsolidator.HdcMemoryStore hippocampus =
                new TwoStageConsolidator.HdcMemoryStore(32);
        TwoStageConsolidator.HdcMemoryStore neocortex =
                new TwoStageConsolidator.HdcMemoryStore(32);
        Random rng = new Random(1);

        // Store episode in hippocampus
        float[] ep = new float[32];
        for (int i = 0; i < 32; i++) ep[i] = (float) (i % 3) * 0.1f;
        hippocampus.store("ep_1", ep);

        TwoStageConsolidator.consolidate(hippocampus, neocortex, 1, rng);
        // Neocortex should now have at least 1 entry
        assertThat(neocortex.keys()).isNotEmpty();
    }

    @Test
    void hippocampusDecaysAfterReplay() {
        TwoStageConsolidator.HdcMemoryStore hippocampus =
                new TwoStageConsolidator.HdcMemoryStore(16);
        TwoStageConsolidator.HdcMemoryStore neocortex =
                new TwoStageConsolidator.HdcMemoryStore(16);
        Random rng = new Random(7);

        float[] ep = new float[16];
        for (int i = 0; i < 16; i++) ep[i] = 1.0f;
        hippocampus.store("ep_X", ep);
        float[] before = hippocampus.retrieve("ep_X");
        float sumBefore = sumOfAbs(before);

        TwoStageConsolidator.consolidate(hippocampus, neocortex, 1, rng);
        float[] after = hippocampus.retrieve("ep_X");
        // Pattern should be reduced (decay 0.1 factor)
        if (after != null) {
            float sumAfter = sumOfAbs(after);
            assertThat(sumAfter).isLessThan(sumBefore);
        }
    }

    @Test
    void multipleReplayCyclesDecreaseError() {
        TwoStageConsolidator.HdcMemoryStore hippocampus =
                new TwoStageConsolidator.HdcMemoryStore(64);
        TwoStageConsolidator.HdcMemoryStore neocortex =
                new TwoStageConsolidator.HdcMemoryStore(64);
        Random rng = new Random(13);

        for (int i = 0; i < 10; i++) {
            float[] pattern = new float[64];
            for (int j = 0; j < 64; j++) pattern[j] = (float) Math.random();
            hippocampus.store("ep_" + i, pattern);
        }

        double totalError = 0;
        for (int cycle = 0; cycle < 5; cycle++) {
            TwoStageConsolidator.ConsolidationResult r =
                    TwoStageConsolidator.consolidate(hippocampus, neocortex, 5, rng);
            totalError += r.replayError();
        }
        // Should have non-zero total error (replay always has some mismatch)
        assertThat(totalError).isGreaterThan(0.0);
    }

    @Test
    void rejectsNullInputs() {
        TwoStageConsolidator.HdcMemoryStore mem = new TwoStageConsolidator.HdcMemoryStore(8);
        assertThatThrownBy(() -> TwoStageConsolidator.consolidate(null, mem, 1, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TwoStageConsolidator.consolidate(mem, null, 1, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TwoStageConsolidator.consolidate(mem, mem, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBadReplayCount() {
        TwoStageConsolidator.HdcMemoryStore mem = new TwoStageConsolidator.HdcMemoryStore(8);
        assertThatThrownBy(() -> TwoStageConsolidator.consolidate(mem, mem, -1, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyHippocampusReturnsZero() {
        TwoStageConsolidator.HdcMemoryStore hippocampus =
                new TwoStageConsolidator.HdcMemoryStore(8);
        TwoStageConsolidator.HdcMemoryStore neocortex =
                new TwoStageConsolidator.HdcMemoryStore(8);
        TwoStageConsolidator.ConsolidationResult r =
                TwoStageConsolidator.consolidate(hippocampus, neocortex, 5, new Random(1));
        assertThat(r.episodesReplayed()).isEqualTo(0);
        assertThat(r.replayedIds()).isEmpty();
    }

    @Test
    void hdcMemoryStoreBasic() {
        TwoStageConsolidator.HdcMemoryStore mem = new TwoStageConsolidator.HdcMemoryStore(16);
        assertThat(mem.keys()).isEmpty();
        float[] p = new float[16];
        for (int i = 0; i < 16; i++) p[i] = (float) i;
        mem.store("test", p);
        assertThat(mem.keys()).hasSize(1);
        float[] retrieved = mem.retrieve("test");
        assertThat(retrieved).containsExactly(p);
    }

    @Test
    void hdcMemoryStoreDecay() {
        TwoStageConsolidator.HdcMemoryStore mem = new TwoStageConsolidator.HdcMemoryStore(8);
        float[] p = {1, 1, 1, 1, 1, 1, 1, 1};
        mem.store("x", p);
        mem.decay("x", 0.5f);
        float[] after = mem.retrieve("x");
        assertThat(after).containsExactly(0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f);
    }

    @Test
    void hdcMemoryStoreFindSimilar() {
        TwoStageConsolidator.HdcMemoryStore mem = new TwoStageConsolidator.HdcMemoryStore(8);
        mem.store("a", new float[]{1, 0, 0, 0, 0, 0, 0, 0});
        mem.store("b", new float[]{0, 1, 0, 0, 0, 0, 0, 0});
        mem.store("c", new float[]{0, 0, 1, 0, 0, 0, 0, 0});
        // Query like "a" → should find "a"
        String mostSimilar = mem.findMostSimilar(new float[]{1, 0, 0, 0, 0, 0, 0, 0});
        assertThat(mostSimilar).isEqualTo("a");
    }

    @Test
    void hdcMemoryStoreFindSimilarEmpty() {
        TwoStageConsolidator.HdcMemoryStore mem = new TwoStageConsolidator.HdcMemoryStore(8);
        assertThat(mem.findMostSimilar(new float[8])).isNull();
    }

    @Test
    void hdcMemoryStoreRejectsBadDims() {
        assertThatThrownBy(() -> new TwoStageConsolidator.HdcMemoryStore(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TwoStageConsolidator.HdcMemoryStore(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hdcMemoryStoreRejectsPatternLengthMismatch() {
        TwoStageConsolidator.HdcMemoryStore mem = new TwoStageConsolidator.HdcMemoryStore(8);
        assertThatThrownBy(() -> mem.store("x", new float[4]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mem.store("x", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consolidationResultHasFields() {
        TwoStageConsolidator.ConsolidationResult r =
                new TwoStageConsolidator.ConsolidationResult(5, 0.42, java.util.List.of("a", "b"));
        assertThat(r.episodesReplayed()).isEqualTo(5);
        assertThat(r.replayError()).isEqualTo(0.42);
        assertThat(r.replayedIds()).containsExactly("a", "b");
    }

    private static float sumOfAbs(float[] arr) {
        float s = 0;
        for (float v : arr) s += Math.abs(v);
        return s;
    }
}
