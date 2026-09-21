package io.matrix.consciousness;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 249 — BrainLoopRelay EXP.
 *
 * <p>100 inputs through relay of capacity 10. Verify all delivered
 * and all consumed.
 */
@Tag("exp")
class Exp249RelayTest {

    @Test
    void hundredInputsThroughRelay() {
        var svc = new BrainLoopService();
        var relay = new BrainLoopRelay(10);
        // Producer: offer 100 inputs (might need to wait)
        for (int i = 0; i < 100; i++) {
            // Simple loop: keep offering until success
            while (!relay.offer("test", "X-" + i)) {
                // Drain one and try again
                var o = relay.take();
                if (o != null) svc.cycle(o.input());
            }
        }
        // Drain remaining
        var o = relay.take();
        while (o != null) {
            svc.cycle(o.input());
            o = relay.take();
        }
        System.out.printf("[RELAY-100] offered=%d taken=%d cycles=%d%n",
                relay.totalOffered(), relay.totalTaken(), svc.trace().count() / 5);
        assertThat(relay.totalOffered()).isEqualTo(100);
        assertThat(relay.totalTaken()).isEqualTo(100);
    }
}
