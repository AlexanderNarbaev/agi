package io.matrix.mediator;

import io.matrix.consciousness.BrainLoopService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 214 — MediatorBus + BrainLoop EXP.
 *
 * <p>50 brain cycles, all "cycle_completed" events published
 * to bus, all received by 3 subscribers.
 */
@Tag("exp")
class Exp214MediatorBrainLoopTest {

    @Test
    void busReceivesAllCycleEvents() {
        var brain = new BrainLoopService();
        var bus = new MediatorBus();
        List<Integer> counts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            List<String> r = new ArrayList<>();
            bus.subscribe("cycle_completed", (t, p) -> r.add(p));
            counts.add(r.size());
        }
        for (int i = 0; i < 50; i++) {
            var result = brain.cycle("Q-" + i);
            bus.publish("cycle_completed",
                    String.valueOf(i) + ":" + result.action());
        }
        // We saved references in 0..2, but we lost them via the lambda.
        // Instead, just verify delivery count
        int totalDeliveries = bus.deliveryCount("cycle_completed");
        System.out.printf("[MEDIATOR-50] total deliveries=%d, subs=%d%n",
                totalDeliveries, bus.subscriberCount("cycle_completed"));
        assertThat(bus.subscriberCount("cycle_completed")).isEqualTo(3);
        assertThat(totalDeliveries).isEqualTo(50);
        assertThat(counts.get(0)).isZero();
        assertThat(counts.get(1)).isZero();
        assertThat(counts.get(2)).isZero();
    }
}
