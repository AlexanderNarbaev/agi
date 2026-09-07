package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 305 — CycleLoadBalancer unit tests. */
class CycleLoadBalancerTest {

    @Test
    void selectsFirstByDefault() {
        var lb = new CycleLoadBalancer(3);
        assertThat(lb.select()).isZero();
    }

    @Test
    void selectsLowestLoad() {
        var lb = new CycleLoadBalancer(3);
        lb.updateLoad(0, 0.8);
        lb.updateLoad(1, 0.2);
        lb.updateLoad(2, 0.5);
        assertThat(lb.select()).isEqualTo(1);
    }

    @Test
    void updateLoadWorks() {
        var lb = new CycleLoadBalancer(2);
        lb.updateLoad(0, 0.5);
        assertThat(lb.load(0)).isEqualTo(0.5);
    }

    @Test
    void invalidHandlerIgnored() {
        var lb = new CycleLoadBalancer(2);
        lb.updateLoad(5, 0.5); // invalid
        assertThat(lb.load(0)).isZero();
    }

    @Test
    void handlerCount() {
        var lb = new CycleLoadBalancer(4);
        assertThat(lb.handlerCount()).isEqualTo(4);
    }
}
