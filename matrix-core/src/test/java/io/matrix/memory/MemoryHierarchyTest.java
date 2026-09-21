package io.matrix.memory;

import io.matrix.cluster.FNLMetadata;
import io.matrix.cluster.NeuronId;
import io.matrix.cluster.NeuronInstance;
import io.matrix.cluster.Signal;
import io.matrix.events.ClusterEventType;
import io.matrix.events.InMemoryEventJournal;
import io.matrix.noosphere.NoosphereRegistry;
import io.matrix.snapshot.ClusterSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryHierarchyTest {

    private static MemoryHierarchy fresh() {
        return new MemoryHierarchy(
                new InMemoryEventJournal(),
                new NoosphereRegistry(),
                8,
                "test-instance");
    }

    private static NeuronInstance frozenNeuron() {
        return NeuronInstance.frozen(NeuronId.create(),
                io.matrix.neuron.TruthTable.fromLong(2, 0b1010));
    }

    // ─── L1 ───────────────────────────────────────────────────────────────

    @Test
    void storeAndRecallL1Signals() {
        MemoryHierarchy mh = fresh();
        NeuronId src = NeuronId.create();
        NeuronId tgt = NeuronId.create();

        mh.storeL1(new Signal(src, tgt, true));
        mh.storeL1(new Signal(src, tgt, false));
        mh.storeL1(new Signal(src, tgt, true));

        List<Signal> recalled = mh.recallL1(10);
        assertThat(recalled).hasSize(3);
        // newest first
        assertThat(recalled.get(0).value()).isTrue();
        assertThat(recalled.get(1).value()).isFalse();
        assertThat(recalled.get(2).value()).isTrue();
        assertThat(mh.l1Size()).isEqualTo(3);
    }

    @Test
    void l1RingBufferEvictsOldestWhenFull() {
        MemoryHierarchy mh = fresh();
        NeuronId src = NeuronId.create();
        for (int i = 0; i < 12; i++) {
            mh.storeL1(new Signal(src, src, i % 2 == 0));
        }
        assertThat(mh.l1Size()).isEqualTo(8);
        List<Signal> recalled = mh.recallL1(8);
        assertThat(recalled).hasSize(8);
    }

    @Test
    void recallL1WithNonPositiveCountReturnsEmpty() {
        MemoryHierarchy mh = fresh();
        mh.storeL1(new Signal(NeuronId.create(), NeuronId.create(), true));
        assertThat(mh.recallL1(0)).isEmpty();
        assertThat(mh.recallL1(-1)).isEmpty();
    }

    // ─── L1 → L2 checkpoint ──────────────────────────────────────────────

    @Test
    void checkpointL2FlushesL1IntoWal() {
        MemoryHierarchy mh = fresh();
        mh.storeL1(new Signal(NeuronId.create(), NeuronId.create(), true));
        mh.storeL1(new Signal(NeuronId.create(), NeuronId.create(), false));

        assertThat(mh.l1Size()).isEqualTo(2);
        assertThat(mh.l2Size()).isEqualTo(0L);

        int flushed = mh.checkpointL2();
        assertThat(flushed).isEqualTo(2);
        assertThat(mh.l1Size()).isZero();
        assertThat(mh.l2Size()).isEqualTo(2L);

        var events = mh.replayL2(0);
        assertThat(events).hasSize(2);
        assertThat(events).allMatch(e -> e.type() == ClusterEventType.SIGNAL_EMITTED);
    }

    @Test
    void checkpointL2WithEmptyL1FlushesNothing() {
        MemoryHierarchy mh = fresh();
        assertThat(mh.checkpointL2()).isZero();
        assertThat(mh.l2Size()).isZero();
    }

    @Test
    void replayL2FromOffsetReturnsTail() {
        MemoryHierarchy mh = fresh();
        for (int i = 0; i < 5; i++) {
            mh.storeL1(new Signal(NeuronId.create(), NeuronId.create(), true));
        }
        mh.checkpointL2();

        assertThat(mh.replayL2(2)).hasSize(3);
        assertThat(mh.replayL2(10)).isEmpty();
    }

    // ─── L2 → L3 consolidation ───────────────────────────────────────────

    @Test
    void consolidateL3CreatesSnapshotFromFrozenNeurons() {
        MemoryHierarchy mh = fresh();
        NeuronInstance n1 = frozenNeuron();
        NeuronInstance n2 = frozenNeuron();
        mh.registerFrozenL4(n1);
        mh.registerFrozenL4(n2);
        mh.checkpointL2();

        UUID snapshotId = mh.consolidateL3();
        assertThat(snapshotId).isNotNull();
        assertThat(mh.l3Size()).isEqualTo(1);

        Optional<ClusterSnapshot> loaded = mh.loadL3(snapshotId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().neuronCount()).isEqualTo(2);
        assertThat(loaded.get().instanceId()).isEqualTo("test-instance");
    }

    @Test
    void loadL3ReturnsEmptyForUnknownId() {
        MemoryHierarchy mh = fresh();
        assertThat(mh.loadL3(UUID.randomUUID())).isEmpty();
    }

    // ─── L4 ───────────────────────────────────────────────────────────────

    @Test
    void registerAndRetrieveFrozenL4() {
        MemoryHierarchy mh = fresh();
        NeuronInstance neuron = frozenNeuron();

        mh.registerFrozenL4(neuron);
        assertThat(mh.l4Size()).isEqualTo(1);

        Optional<NeuronInstance> retrieved = mh.getFrozenL4(neuron.id().uuid());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().id()).isEqualTo(neuron.id());
        assertThat(retrieved.get().isFrozen()).isTrue();
    }

    @Test
    void registerL4RejectsNonFrozenNeuron() {
        MemoryHierarchy mh = fresh();
        NeuronInstance stable = NeuronInstance.stable(NeuronId.create(),
                io.matrix.neuron.TruthTable.fromLong(2, 0b1010));
        assertThatThrownBy(() -> mh.registerFrozenL4(stable))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getFrozenL4ReturnsEmptyForUnknownId() {
        MemoryHierarchy mh = fresh();
        assertThat(mh.getFrozenL4(UUID.randomUUID())).isEmpty();
    }

    // ─── L5 ───────────────────────────────────────────────────────────────

    @Test
    void publishAndSearchNoosphereL5() {
        MemoryHierarchy mh = fresh();
        FNLMetadata fnl = new FNLMetadata(
                UUID.randomUUID(), "vision-cortex", 128, 3, 0.92, System.currentTimeMillis());

        UUID entryId = mh.publishToNoosphereL5(fnl, new byte[]{1, 2, 3});
        assertThat(entryId).isNotNull();
        assertThat(mh.l5Size()).isEqualTo(1);

        List<FNLMetadata> visionResults = mh.searchNoosphereL5("vision");
        assertThat(visionResults).hasSize(1);
        assertThat(visionResults.get(0).name()).isEqualTo("vision-cortex");
        assertThat(visionResults.get(0).accuracy()).isEqualTo(0.92);

        assertThat(mh.searchNoosphereL5("nonexistent")).isEmpty();
    }

    @Test
    void publishToNoosphereL5AcceptsEmptyData() {
        MemoryHierarchy mh = fresh();
        FNLMetadata fnl = new FNLMetadata(
                UUID.randomUUID(), "motor", 4, 1, 0.5, 0L);
        UUID entryId = mh.publishToNoosphereL5(fnl, null);
        assertThat(entryId).isNotNull();
        assertThat(mh.searchNoosphereL5("motor")).hasSize(1);
    }

    // ─── Full pipeline ───────────────────────────────────────────────────

    @Test
    void fullPipelineL1ThroughL5() {
        MemoryHierarchy mh = fresh();

        // L1
        mh.storeL1(new Signal(NeuronId.create(), NeuronId.create(), true));
        // L1 → L2
        mh.checkpointL2();
        // L4
        mh.registerFrozenL4(frozenNeuron());
        // L2 + L4 → L3
        UUID snapshotId = mh.consolidateL3();
        // L5
        FNLMetadata fnl = new FNLMetadata(
                UUID.randomUUID(), "language", 64, 2, 0.88, System.currentTimeMillis());
        mh.publishToNoosphereL5(fnl, new byte[]{9, 9});

        assertThat(mh.l1Size()).isZero();
        assertThat(mh.l2Size()).isEqualTo(1L);
        assertThat(mh.l3Size()).isEqualTo(1);
        assertThat(mh.l4Size()).isEqualTo(1);
        assertThat(mh.l5Size()).isEqualTo(1);
        assertThat(mh.loadL3(snapshotId)).isPresent();
        assertThat(mh.searchNoosphereL5("language")).hasSize(1);
    }

    @Test
    void nullArgumentsRejected() {
        MemoryHierarchy mh = fresh();
        assertThatThrownBy(() -> mh.storeL1(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> mh.registerFrozenL4(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> mh.publishToNoosphereL5(null, new byte[0])).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> mh.searchNoosphereL5(null)).isInstanceOf(NullPointerException.class);
    }
}
