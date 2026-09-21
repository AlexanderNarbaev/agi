package io.matrix.lifecycle;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ConsolidationCycle} (DESIGN-07 §4 sleep window).
 */
class ConsolidationCycleTest {

    @Test
    void drainPartialThenFullAndSummarize() {
        ConsolidationCycle cycle = new ConsolidationCycle();
        cycle.open(Map.of("route-a", 5, "route-b", 2));

        assertThat(cycle.drain("route-a", 3)).isEqualTo(3);
        assertThat(cycle.backlog("route-a")).isEqualTo(2);
        assertThat(cycle.drain("route-a", 10)).isEqualTo(2);
        assertThat(cycle.drain("route-b", 2)).isEqualTo(2);

        var summary = cycle.close();
        assertThat(summary.routesDrained()).isEqualTo(2);
        assertThat(summary.itemsMigrated()).isEqualTo(7);
    }

    @Test
    void closedCycleRejectsDrain() {
        ConsolidationCycle cycle = new ConsolidationCycle();
        assertThatThrownBy(() -> cycle.drain("r", 1))
                .hasMessageContaining("cycle_closed");
        cycle.open(Map.of());
        cycle.close();
        assertThatThrownBy(() -> cycle.drain("r", 1))
                .hasMessageContaining("cycle_closed");
    }

    @Test
    void negativeInputsRejected() {
        ConsolidationCycle cycle = new ConsolidationCycle();
        assertThatThrownBy(() -> cycle.open(Map.of("r", -1)))
                .hasMessageContaining("invalid backlog");
        cycle.open(Map.of());
        assertThatThrownBy(() -> cycle.drain("r", -5))
                .hasMessageContaining("batchSize");
    }

    // --- Property: total migrated never exceeds initial backlog ---

    @Provide
    Arbitrary<List<Integer>> batches() {
        return Arbitraries.integers().between(0, 6).list().ofMaxSize(12);
    }

    @Property
    void migratedBoundedByInitialBacklog(@ForAll("batches") List<Integer> batchSizes) {
        ConsolidationCycle cycle = new ConsolidationCycle();
        cycle.open(Map.of("r", 10));
        int drained = 0;
        for (int b : batchSizes) {
            drained += cycle.drain("r", b);
        }
        assertThat(drained).isLessThanOrEqualTo(10);
        assertThat(cycle.close().itemsMigrated()).isEqualTo(drained);
    }
}
