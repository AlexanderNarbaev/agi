package io.matrix.simulation;

import io.matrix.cluster.NeuronId;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterTopologyTest {

    @Test
    void smallTopologyShouldHave40Neurons() {
        ClusterTopology topo = ClusterTopology.small(new Random(42));

        assertThat(topo.totalNeurons()).isEqualTo(40);
        assertThat(topo.sensorIds()).hasSize(18);
        assertThat(topo.motorIds()).hasSize(4);
        assertThat(topo.motorIds().keySet()).containsExactlyInAnyOrder(
                Direction.N, Direction.S, Direction.W, Direction.E);
    }

    @Test
    void topology1000ShouldHave1000Neurons() {
        ClusterTopology topo = ClusterTopology.with1000Neurons(new Random(42));

        assertThat(topo.totalNeurons()).isEqualTo(1000);
        assertThat(topo.allNeuronIds()).hasSize(1000);
    }

    @Test
    void everyNeuronShouldHaveTruthTable() {
        ClusterTopology topo = ClusterTopology.small(new Random(42));

        for (NeuronId id : topo.allNeuronIds()) {
            assertThat(topo.neuronTables()).containsKey(id);
            assertThat(topo.neuronTables().get(id)).isNotNull();
        }
    }

    @Test
    void shouldBeDeterministicWithSameSeed() {
        ClusterTopology topo1 = ClusterTopology.small(new Random(42));
        ClusterTopology topo2 = ClusterTopology.small(new Random(42));

        assertThat(topo1.allNeuronIds()).hasSize(topo2.allNeuronIds().size());
        assertThat(topo1.neuronTables()).hasSize(topo2.neuronTables().size());
    }

    @Test
    void motorNeuronsShouldHaveUniqueIds() {
        ClusterTopology topo = ClusterTopology.with1000Neurons(new Random(42));

        var ids = topo.motorIds().values();
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void connectionsShouldBeWired() {
        ClusterTopology topo = ClusterTopology.small(new Random(42));

        long connections = topo.connections().values().stream()
                .filter(list -> !list.isEmpty())
                .count();
        assertThat(connections).isGreaterThan(0);
    }
}
