package io.matrix.cluster;

import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NeuronIdTest {

    @Test
    void shouldCreateWithRandomUUID() {
        NeuronId id = NeuronId.create();

        assertThat(id.uuid()).isNotNull();
        assertThat(id.generation()).isZero();
    }

    @Test
    void shouldIncrementGeneration() {
        NeuronId id = NeuronId.create();
        NeuronId next = id.nextGeneration();

        assertThat(next.uuid()).isEqualTo(id.uuid());
        assertThat(next.generation()).isEqualTo(1);

        NeuronId next2 = next.nextGeneration();
        assertThat(next2.generation()).isEqualTo(2);
    }

    @Test
    void shouldHaveToString() {
        NeuronId id = NeuronId.create();
        String str = id.toString();

        assertThat(str).contains(":0");
        assertThat(str).contains(id.uuid().toString());
    }

    @Test
    void shouldParseToStringRoundtrip() {
        NeuronId original = NeuronId.create();
        String str = original.toString();

        NeuronId parsed = NeuronId.parse(str);
        assertThat(parsed).isEqualTo(original);
    }

    @Test
    void shouldParseWithGeneration() {
        NeuronId original = NeuronId.create().nextGeneration().nextGeneration();

        NeuronId parsed = NeuronId.parse(original.toString());
        assertThat(parsed).isEqualTo(original);
        assertThat(parsed.generation()).isEqualTo(2);
    }

    @Test
    void shouldRejectInvalidParse() {
        assertThatThrownBy(() -> NeuronId.parse("invalid"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> NeuronId.parse("a:b:c"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

class NeuronInstanceTest {

    @Test
    void shouldCreateStable() {
        NeuronId id = NeuronId.create();
        TruthTable table = TruthTable.random(3, new Random(42));
        NeuronInstance instance = NeuronInstance.stable(id, table);

        assertThat(instance.id()).isEqualTo(id);
        assertThat(instance.truthTable()).isEqualTo(table);
        assertThat(instance.state()).isEqualTo(NeuronInstance.State.STABLE);
        assertThat(instance.k()).isEqualTo(3);
        assertThat(instance.isFrozen()).isFalse();
        assertThat(instance.isMutable()).isTrue();
    }

    @Test
    void shouldCreateFrozen() {
        NeuronId id = NeuronId.create();
        TruthTable table = TruthTable.random(4, new Random(42));
        NeuronInstance instance = NeuronInstance.frozen(id, table);

        assertThat(instance.state()).isEqualTo(NeuronInstance.State.FROZEN);
        assertThat(instance.isFrozen()).isTrue();
        assertThat(instance.isMutable()).isFalse();
    }

    @Test
    void shouldTrackLastOutput() {
        NeuronId id = NeuronId.create();
        NeuronInstance instance = NeuronInstance.stable(id,
                TruthTable.random(2, new Random(42)));

        assertThat(instance.lastOutput()).isFalse();

        instance.setLastOutput(true);
        assertThat(instance.lastOutput()).isTrue();

        instance.setLastOutput(false);
        assertThat(instance.lastOutput()).isFalse();
    }

    @Test
    void shouldSupportAllStates() {
        NeuronId id = NeuronId.create();
        TruthTable table = TruthTable.random(3, new Random(42));

        for (NeuronInstance.State state : NeuronInstance.State.values()) {
            NeuronInstance instance = new NeuronInstance(id, table, state);
            assertThat(instance.state()).isEqualTo(state);
        }
    }

    @Test
    void shouldRejectNullArguments() {
        NeuronId id = NeuronId.create();
        TruthTable table = TruthTable.random(2, new Random(42));

        assertThatThrownBy(() -> new NeuronInstance(null, table, NeuronInstance.State.STABLE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new NeuronInstance(id, null, NeuronInstance.State.STABLE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new NeuronInstance(id, table, null))
                .isInstanceOf(NullPointerException.class);
    }
}

class SignalTest {

    @Test
    void shouldCreateSignal() {
        NeuronId src = NeuronId.create();
        NeuronId tgt = NeuronId.create();
        Signal signal = new Signal(src, tgt, true);

        assertThat(signal.sourceId()).isEqualTo(src);
        assertThat(signal.targetId()).isEqualTo(tgt);
        assertThat(signal.value()).isTrue();
        assertThat(signal.timestamp()).isPositive();
    }

    @Test
    void shouldCreateSignalWithFalse() {
        NeuronId src = NeuronId.create();
        NeuronId tgt = NeuronId.create();
        Signal signal = new Signal(src, tgt, false);

        assertThat(signal.value()).isFalse();
    }

    @Test
    void shouldHaveUniqueTimestamps() {
        Signal s1 = new Signal(NeuronId.create(), NeuronId.create(), true);
        Signal s2 = new Signal(NeuronId.create(), NeuronId.create(), true);

        assertThat(s2.timestamp()).isGreaterThanOrEqualTo(s1.timestamp());
    }
}

class ClusterConfigTest {

    @Test
    void shouldCreateDefaults() {
        ClusterConfig config = ClusterConfig.defaults();

        assertThat(config.maxNeurons()).isEqualTo(1000);
        assertThat(config.signalBufferCapacity()).isEqualTo(10_000);
        assertThat(config.tickIntervalMs()).isEqualTo(1);
    }

    @Test
    void shouldCreateForSize() {
        ClusterConfig config = ClusterConfig.forSize(500);

        assertThat(config.maxNeurons()).isEqualTo(500);
        assertThat(config.signalBufferCapacity()).isEqualTo(5000);
        assertThat(config.tickIntervalMs()).isEqualTo(1);
    }

    @Test
    void shouldCreateForLargeCluster() {
        ClusterConfig config = ClusterConfig.forSize(10_000);

        assertThat(config.maxNeurons()).isEqualTo(10_000);
        assertThat(config.signalBufferCapacity()).isEqualTo(100_000);
    }
}
