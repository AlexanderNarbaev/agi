package io.matrix.actions;

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
 * Tests for DESIGN-13 additions: {@link PlanRunner} (Hoare P{Q}R) and
 * {@link VersionedContract} (atomic versioned swap).
 */
class PlanRunnerTest {

    private static Map<String, Object> state(int counter) {
        return Map.of("counter", counter);
    }

    @SuppressWarnings("unchecked")
    private static int counter(Map<String, Object> state) {
        return ((Number) state.get("counter")).intValue();
    }

    // --- PlanRunner ---

    @Test
    void executesPlanAndReturnsFinalState() {
        var inc = new PlanRunner.Step("inc",
                s -> counter(s) < 10,
                s -> counter(s) <= 10,
                s -> state(counter(s) + 1));
        Map<String, Object> out = PlanRunner.run(List.of(inc, inc, inc), state(0),
                s -> counter(s) >= 0);
        assertThat(counter(out)).isEqualTo(3);
    }

    @Test
    void preconditionViolationFailsFastWithoutCommit() {
        var dec = new PlanRunner.Step("dec",
                s -> counter(s) > 0,
                s -> counter(s) >= 0,
                s -> state(counter(s) - 1));
        assertThatThrownBy(() -> PlanRunner.run(List.of(dec), state(0), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("precondition_violated");
    }

    @Test
    void postconditionAndInvariantViolationsDetected() {
        var bad = new PlanRunner.Step("bad",
                s -> true,
                s -> false,
                s -> state(counter(s) + 1));
        assertThatThrownBy(() -> PlanRunner.run(List.of(bad), state(0), null))
                .hasMessageContaining("postcondition_violated");

        var ok = new PlanRunner.Step("ok", s -> true, s -> true,
                s -> state(counter(s) + 1));
        assertThatThrownBy(() -> PlanRunner.run(List.of(ok, ok), state(5),
                s -> counter(s) < 6))
                .hasMessageContaining("invariant_violated");
    }

    // --- VersionedContract ---

    @Test
    void swapAcceptsOnlyConsecutiveSameDomainVersions() {
        var v1 = new VersionedContract("move", 1, "dom-1");
        var v2 = v1.swap(new VersionedContract("move", 2, "dom-1"));
        assertThat(v2.version()).isEqualTo(2);

        assertThatThrownBy(() -> v1.swap(new VersionedContract("move", 3, "dom-1")))
                .hasMessageContaining("exactly 1");
        assertThatThrownBy(() -> v1.swap(new VersionedContract("move", 2, "dom-2")))
                .hasMessageContaining("domainHash");
        assertThatThrownBy(() -> v1.swap(new VersionedContract("grab", 2, "dom-1")))
                .hasMessageContaining("name");
    }

    // --- Property: invariant preserved across random valid plans ---

    @Provide
    Arbitrary<List<Boolean>> opSequences() {
        return Arbitraries.of(true, false).list().ofMaxSize(40);
    }

    @Property
    void nonNegativeCounterInvariantHolds(@ForAll("opSequences") List<Boolean> bits) {
        // Build a FEASIBLE plan by simulating the counter while generating:
        // true → up when below ceiling, false → down when above floor.
        int simulated = 0;
        List<PlanRunner.Step> plan = new java.util.ArrayList<>();
        var up = new PlanRunner.Step("up",
                s -> counter(s) < 50,
                s -> counter(s) >= 0 && counter(s) <= 50,
                s -> state(counter(s) + 1));
        var down = new PlanRunner.Step("down",
                s -> counter(s) > 0,
                s -> counter(s) >= 0 && counter(s) <= 50,
                s -> state(counter(s) - 1));
        for (Boolean bit : bits) {
            if (bit && simulated < 50) {
                plan.add(up);
                simulated++;
            } else if (!bit && simulated > 0) {
                plan.add(down);
                simulated--;
            }
        }

        Map<String, Object> out = PlanRunner.run(plan, state(0),
                s -> counter(s) >= 0 && counter(s) <= 50);
        assertThat(counter(out)).isBetween(0, 50);
    }
}
