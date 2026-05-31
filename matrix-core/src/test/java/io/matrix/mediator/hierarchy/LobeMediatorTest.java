package io.matrix.mediator.hierarchy;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class LobeMediatorTest {

    @Test
    void shouldCreateWithCorrectLevel() {
        LobeMediator lobe = new LobeMediator("lobe-1", "cluster-1");

        assertThat(lobe.level()).isEqualTo(MediatorLevel.LOBE);
        assertThat(lobe.level().defaultWeight()).isEqualTo(0.2);
        assertThat(lobe.parentClusterId()).isEqualTo("cluster-1");
    }

    @Test
    void shouldIncrementTickCount() {
        LobeMediator lobe = new LobeMediator("lobe-1", "cluster-1");

        lobe.tick();

        assertThat(lobe.tickCount()).isEqualTo(1);
    }

    @Test
    void shouldProduceActionsOnTick() {
        LobeMediator lobe = new LobeMediator("lobe-1", "cluster-1");

        var actions = lobe.tick();

        assertThat(actions).isNotEmpty();
        assertThat(actions.get(0)).contains("LOBE");
        assertThat(actions.get(0)).contains("lobe-1");
    }

    @Test
    void shouldRequestResourcesFromParent() {
        LobeMediator lobe = new LobeMediator("lobe-1", "cluster-1");

        var cmd = lobe.requestResources("need more CPU");

        assertThat(cmd.targetLevel()).isEqualTo(MediatorLevel.CLUSTER);
        assertThat(cmd.targetId()).isEqualTo("cluster-1");
        assertThat(cmd.action()).isEqualTo("REQUEST_RESOURCES");
        assertThat(cmd.payload()).contains("need more CPU");
    }

    @Test
    void shouldReportMutation() {
        LobeMediator lobe = new LobeMediator("lobe-1", "cluster-1");
        var neuronId = io.matrix.cluster.NeuronId.create();

        var cmd = lobe.reportMutation(neuronId, "leaf flipped");

        assertThat(cmd.action()).isEqualTo("REPORT_MUTATION");
        assertThat(cmd.payload()).contains(neuronId.toString());
        assertThat(cmd.payload()).contains("leaf flipped");
    }

    @Test
    void shouldLogActions() {
        LobeMediator lobe = new LobeMediator("lobe-1", "cluster-1");
        lobe.tick();
        lobe.tick();

        assertThat(lobe.actionLog()).hasSize(2);
    }
}
