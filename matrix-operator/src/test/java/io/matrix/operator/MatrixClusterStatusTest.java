package io.matrix.operator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatrixClusterStatusTest {

    @Test
    void shouldDefaultPhaseToPending() {
        MatrixClusterStatus status = new MatrixClusterStatus();
        assertThat(status.getPhase()).isEqualTo("Pending");
    }

    @Test
    void shouldSetPhaseCorrectly() {
        MatrixClusterStatus status = new MatrixClusterStatus();
        status.setPhase("Running");
        assertThat(status.getPhase()).isEqualTo("Running");

        status.setPhase("Degraded");
        assertThat(status.getPhase()).isEqualTo("Degraded");
    }

    @Test
    void shouldTrackNeuronCounts() {
        MatrixClusterStatus status = new MatrixClusterStatus();
        status.setActiveNeurons(42);
        status.setFrozenNeurons(7);

        assertThat(status.getActiveNeurons()).isEqualTo(42);
        assertThat(status.getFrozenNeurons()).isEqualTo(7);
    }

    @Test
    void shouldTrackMetrics() {
        MatrixClusterStatus status = new MatrixClusterStatus();
        status.setMessagesPerSecond(1337.5);
        status.setLastSnapshot("snap-2026-06-04T12:00:00Z");

        assertThat(status.getMessagesPerSecond()).isEqualTo(1337.5);
        assertThat(status.getLastSnapshot()).isEqualTo("snap-2026-06-04T12:00:00Z");
    }

    @Test
    void shouldManageConditions() {
        MatrixClusterStatus.Condition cond = new MatrixClusterStatus.Condition();
        cond.setType("Ready");
        cond.setStatus("True");
        cond.setReason("AllNeuronsActive");
        cond.setMessage("All 100 neurons are active");
        cond.setLastTransitionTime("2026-06-04T12:00:00Z");

        assertThat(cond.getType()).isEqualTo("Ready");
        assertThat(cond.getStatus()).isEqualTo("True");
        assertThat(cond.getReason()).isEqualTo("AllNeuronsActive");
        assertThat(cond.getMessage()).isEqualTo("All 100 neurons are active");
        assertThat(cond.getLastTransitionTime()).isEqualTo("2026-06-04T12:00:00Z");
    }

    @Test
    void shouldSetMultipleConditions() {
        MatrixClusterStatus status = new MatrixClusterStatus();
        MatrixClusterStatus.Condition c1 = new MatrixClusterStatus.Condition();
        c1.setType("Ready");
        c1.setStatus("True");
        MatrixClusterStatus.Condition c2 = new MatrixClusterStatus.Condition();
        c2.setType("Progressing");
        c2.setStatus("False");

        status.setConditions(List.of(c1, c2));

        assertThat(status.getConditions()).hasSize(2);
        assertThat(status.getConditions().get(0).getType()).isEqualTo("Ready");
        assertThat(status.getConditions().get(1).getType()).isEqualTo("Progressing");
    }
}
