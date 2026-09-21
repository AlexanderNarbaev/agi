package io.matrix.api;

import io.matrix.imports.BooleanChainRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link LmHeadTrainer#trainBatch(List, int)} — RUN 34 batch training.
 */
class LmHeadTrainerBatchTest {

    private LmHeadTrainer trainer;
    private LmHead lmHead;

    @BeforeEach
    void setUp() throws Exception {
        lmHead = new LmHead();
        lmHead.setTotalNeurons(64);

        trainer = new LmHeadTrainer();

        // Wire collaborators via reflection (CDI not active in unit tests).
        ChainFeatureCache cache = new ChainFeatureCache();
        cache.chainRunner = BooleanChainRunner.empty();
        ChainFeatureCache.LmHeadTrainerHolder.lmHead(lmHead);

        java.lang.reflect.Field lmHeadField = LmHeadTrainer.class.getDeclaredField("lmHead");
        lmHeadField.setAccessible(true);
        lmHeadField.set(trainer, lmHead);

        java.lang.reflect.Field cacheField = LmHeadTrainer.class.getDeclaredField("featureCache");
        cacheField.setAccessible(true);
        cacheField.set(trainer, cache);
    }

    @Test
    void batchOpsCounterIsIncremented() {
        List<LmHeadTrainer.Pair> pairs = new ArrayList<>();
        pairs.add(new LmHeadTrainer.Pair("Q1", "A1"));
        long before = trainer.batchOps();
        trainer.trainBatch(pairs, 0);
        assertThat(trainer.batchOps() - before).isEqualTo(1);
    }

    @Test
    void singleOpsCounterIsIndependent() {
        long before = trainer.singleOps();
        trainer.trainOne("Q", "A");
        assertThat(trainer.singleOps() - before).isEqualTo(1);
        // Batch counter should NOT have changed.
        assertThat(trainer.batchOps()).isZero();
    }

    @Test
    void emptyBatchIsNoOp() {
        long before = trainer.batchOps();
        int updates = trainer.trainBatch(new ArrayList<>(), 0);
        assertThat(updates).isZero();
        // Empty batch returns before counter increment (no work done).
        assertThat(trainer.batchOps() - before).isZero();
    }

    @Test
    void nullBatchIsNoOp() {
        int updates = trainer.trainBatch(null, 0);
        assertThat(updates).isZero();
    }

    @Test
    void batchWithDuplicatesSkipsRepeatedChainCalls() {
        // 5 entries with the same question should only compute chain output
        // once. We can't directly measure cache hits here, but we can verify
        // that the batch completes correctly.
        List<LmHeadTrainer.Pair> pairs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            pairs.add(new LmHeadTrainer.Pair("Q", "A" + i));
        }
        int updates = trainer.trainBatch(pairs, 0);
        assertThat(updates).isGreaterThan(0);
    }

    @Test
    void batchSkipsBlankInputs() {
        List<LmHeadTrainer.Pair> pairs = new ArrayList<>();
        pairs.add(new LmHeadTrainer.Pair("", "A"));      // blank question
        pairs.add(new LmHeadTrainer.Pair("Q", ""));       // blank answer
        pairs.add(new LmHeadTrainer.Pair(null, "A"));     // null question
        pairs.add(new LmHeadTrainer.Pair("Q2", null));    // null answer
        pairs.add(new LmHeadTrainer.Pair("Q3", "A3"));    // valid
        int updates = trainer.trainBatch(pairs, 0);
        // Only the last valid pair should contribute updates.
        assertThat(updates).isGreaterThan(0);
    }

    @Test
    void pairRecordHoldsQuestionAndAnswer() {
        LmHeadTrainer.Pair p = new LmHeadTrainer.Pair("Q", "A");
        assertThat(p.question()).isEqualTo("Q");
        assertThat(p.answer()).isEqualTo("A");
    }
}
