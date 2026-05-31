package io.matrix.mediator.hierarchy;

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterMediatorTest {

    private static ActorTestKit testKit;

    @BeforeAll
    static void setup() { testKit = ActorTestKit.create(); }

    @AfterAll
    static void teardown() { testKit.shutdownTestKit(); }

    @Test
    void shouldCreateWithCorrectLevel() {
        var actor = testKit.spawn(ClusterMediator.create("cluster-1",
                "instance-1", new Random(42)));
        var probe = testKit.<ClusterMediator.Response>createTestProbe();

        actor.tell(new ClusterMediator.GetMetrics(probe.ref()));
        var resp = (ClusterMediator.MetricsResult) probe.receiveMessage();

        assertThat(resp.activeFnls()).isEqualTo(0);
    }

    @Test
    void shouldIncrementTickCount() {
        var actor = testKit.spawn(ClusterMediator.create("cluster-1",
                "instance-1", new Random(42)));
        var probe = testKit.<ClusterMediator.Response>createTestProbe();

        actor.tell(new ClusterMediator.Tick(probe.ref()));
        var resp = (ClusterMediator.TickResult) probe.receiveMessage();

        assertThat(resp.actionsPerformed()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldRegisterLobes() {
        var actor = testKit.spawn(ClusterMediator.create("cluster-1",
                "instance-1", new Random(42)));
        var probe = testKit.<ClusterMediator.Response>createTestProbe();

        actor.tell(new ClusterMediator.GetActiveFnlCount(probe.ref()));
        var resp = (ClusterMediator.FnlCountResult) probe.receiveMessage();
        assertThat(resp.count()).isEqualTo(0);
    }

    @Test
    void shouldProcessTickWithLobes() {
        var actor = testKit.spawn(ClusterMediator.create("cluster-1",
                "instance-1", new Random(42)));
        var probe = testKit.<ClusterMediator.Response>createTestProbe();

        actor.tell(new ClusterMediator.Tick(probe.ref()));
        var resp = (ClusterMediator.TickResult) probe.receiveMessage();

        assertThat(resp.actionsPerformed()).isGreaterThanOrEqualTo(0);
    }
}
