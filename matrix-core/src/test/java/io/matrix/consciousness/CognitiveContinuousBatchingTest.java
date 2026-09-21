package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveContinuousBatchingTest {

    @Test
    void emptyQueueReturnsNull() {
        CognitiveContinuousBatching b = new CognitiveContinuousBatching(4);
        assertThat(b.formBatch()).isNull();
    }

    @Test
    void enqueueIncreasesQueue() {
        CognitiveContinuousBatching b = new CognitiveContinuousBatching(4);
        b.enqueue(makeProfile(0.5));
        assertThat(b.queueSize()).isEqualTo(1);
    }

    @Test
    void invalidBatchSizeThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveContinuousBatching(0)
        );
    }

    @Test
    void nullEnqueueNoOp() {
        CognitiveContinuousBatching b = new CognitiveContinuousBatching(4);
        b.enqueue(null);
        assertThat(b.queueSize()).isEqualTo(0);
    }

    @Test
    void formBatchRespectsMaxSize() {
        CognitiveContinuousBatching b = new CognitiveContinuousBatching(3);
        for (int i = 0; i < 7; i++) b.enqueue(makeProfile(i / 7.0));
        CognitiveContinuousBatching.Batch batch1 = b.formBatch();
        assertThat(batch1.profiles().size()).isEqualTo(3);
        CognitiveContinuousBatching.Batch batch2 = b.formBatch();
        assertThat(batch2.profiles().size()).isEqualTo(3);
        CognitiveContinuousBatching.Batch batch3 = b.formBatch();
        assertThat(batch3.profiles().size()).isEqualTo(1);
    }

    @Test
    void processBatchReturnsResult() {
        CognitiveContinuousBatching b = new CognitiveContinuousBatching(4);
        for (int i = 0; i < 3; i++) b.enqueue(makeProfile(i / 3.0));
        CognitiveContinuousBatching.Batch batch = b.formBatch();
        CognitiveContinuousBatching.BatchResult r = b.process(batch);
        assertThat(r.profilesProcessed()).isEqualTo(3);
        assertThat(r.batchMeanEmbedding().length).isEqualTo(64);
    }

    @Test
    void formAndProcess() {
        CognitiveContinuousBatching b = new CognitiveContinuousBatching(4);
        for (int i = 0; i < 5; i++) b.enqueue(makeProfile(i / 5.0));
        CognitiveContinuousBatching.BatchResult r = b.formAndProcess();
        assertThat(r.profilesProcessed()).isEqualTo(4); // 4 is max
    }

    @Test
    void batchIdsUnique() {
        CognitiveContinuousBatching b = new CognitiveContinuousBatching(2);
        for (int i = 0; i < 6; i++) b.enqueue(makeProfile(i / 6.0));
        CognitiveContinuousBatching.Batch batch1 = b.formBatch();
        CognitiveContinuousBatching.Batch batch2 = b.formBatch();
        CognitiveContinuousBatching.Batch batch3 = b.formBatch();
        assertThat(batch1.batchId()).isNotEqualTo(batch2.batchId());
        assertThat(batch2.batchId()).isNotEqualTo(batch3.batchId());
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
