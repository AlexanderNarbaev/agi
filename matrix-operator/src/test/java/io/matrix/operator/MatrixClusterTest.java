package io.matrix.operator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatrixClusterTest {

    @Test
    void shouldCreateCustomResource() {
        MatrixClusterSpec spec = new MatrixClusterSpec();
        spec.setNeurons(100);
        spec.setK(16);
        spec.setInstanceId("test-instance");

        MatrixCluster cr = MatrixCluster.create("test-cluster", "matrix-ns", spec);

        assertThat(cr.getMetadata().getName()).isEqualTo("test-cluster");
        assertThat(cr.getMetadata().getNamespace()).isEqualTo("matrix-ns");
        assertThat(cr.getSpec().getNeurons()).isEqualTo(100);
        assertThat(cr.getSpec().getK()).isEqualTo(16);
        assertThat(cr.getStatus()).isNotNull();
        assertThat(cr.getStatus().getPhase()).isEqualTo("Pending");
    }

    @Test
    void shouldInitializeStatusToPending() {
        MatrixClusterSpec spec = new MatrixClusterSpec();
        spec.setNeurons(200);

        MatrixCluster cr = MatrixCluster.create("c1", "ns", spec);

        assertThat(cr.getStatus().getPhase()).isEqualTo("Pending");
        assertThat(cr.getStatus().getActiveNeurons()).isEqualTo(0);
    }

    @Test
    void shouldSetSpecValues() {
        MatrixClusterSpec spec = new MatrixClusterSpec();
        spec.setNeurons(500);
        spec.setK(20);
        spec.setFrozenNeurons(List.of("n1", "n2", "n3"));
        spec.setInstanceId("prod-01");

        assertThat(spec.getNeurons()).isEqualTo(500);
        assertThat(spec.getK()).isEqualTo(20);
        assertThat(spec.getFrozenNeurons()).containsExactly("n1", "n2", "n3");
        assertThat(spec.getInstanceId()).isEqualTo("prod-01");
    }

    @Test
    void shouldDefaultMediatorConfigValues() {
        MatrixClusterSpec.MediatorConfig config = new MatrixClusterSpec.MediatorConfig();

        assertThat(config.getTickIntervalMs()).isEqualTo(1000);
        assertThat(config.getResourceFactorStart()).isEqualTo(0.8);
        assertThat(config.getMaxActiveGoals()).isEqualTo(10);
    }

    @Test
    void shouldSetMediatorConfigValues() {
        MatrixClusterSpec.MediatorConfig config = new MatrixClusterSpec.MediatorConfig();
        config.setTickIntervalMs(500);
        config.setResourceFactorStart(0.5);
        config.setMaxActiveGoals(20);

        assertThat(config.getTickIntervalMs()).isEqualTo(500);
        assertThat(config.getResourceFactorStart()).isEqualTo(0.5);
        assertThat(config.getMaxActiveGoals()).isEqualTo(20);
    }
}
