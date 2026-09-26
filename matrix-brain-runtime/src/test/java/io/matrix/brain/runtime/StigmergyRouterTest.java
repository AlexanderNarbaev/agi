package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StigmergyRouterTest {

    @Test
    void step_reinforces_pheromone() {
        StigmergyRouter r = new StigmergyRouter(0.1, 1.0);
        r.step("A", "B");
        assertThat(r.pheromones().get("A->B")).isEqualTo(1.0);
    }

    @Test
    void multiple_steps_accumulate() {
        StigmergyRouter r = new StigmergyRouter(0.1, 1.0);
        r.step("A", "B");
        r.step("A", "B");
        r.step("A", "B");
        assertThat(r.pheromones().get("A->B")).isEqualTo(3.0);
    }

    @Test
    void evaporate_reduces_pheromone() {
        StigmergyRouter r = new StigmergyRouter(0.1, 10.0);
        r.step("A", "B");
        r.evaporate();
        assertThat(r.pheromones().get("A->B")).isCloseTo(9.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void pheromone_vanishes_below_threshold() {
        StigmergyRouter r = new StigmergyRouter(0.5, 0.001);
        r.step("A", "B");
        // Evaporate many times to drop below 1e-9 threshold
        for (int i = 0; i < 100; i++) r.evaporate();
        assertThat(r.pheromones()).doesNotContainKey("A->B");
    }

    @Test
    void strongestFrom_returns_max() {
        StigmergyRouter r = new StigmergyRouter(0.1, 1.0);
        r.step("A", "X");
        r.step("A", "X");
        r.step("A", "X");
        r.step("A", "Y");
        assertThat(r.strongestFrom("A")).isEqualTo("X");
    }

    @Test
    void strongestFrom_returns_null_if_no_trail() {
        StigmergyRouter r = new StigmergyRouter(0.1, 1.0);
        assertThat(r.strongestFrom("A")).isNull();
    }

    @Test
    void route_returns_a_candidate() {
        StigmergyRouter r = new StigmergyRouter(0.1, 1.0);
        r.step("A", "X");
        String[] candidates = {"X", "Y", "Z"};
        String choice = r.route("A", candidates);
        assertThat(choice).isIn("X", "Y", "Z");
    }

    @Test
    void route_with_single_candidate_returns_it() {
        StigmergyRouter r = new StigmergyRouter(0.1, 1.0);
        assertThat(r.route("A", new String[]{"X"})).isEqualTo("X");
    }

    @Test
    void route_with_null_or_empty_returns_null() {
        StigmergyRouter r = new StigmergyRouter(0.1, 1.0);
        assertThat(r.route("A", null)).isNull();
        assertThat(r.route("A", new String[]{})).isNull();
    }

    @Test
    void invalid_args_rejected() {
        assertThat(catchThrowable(() -> new StigmergyRouter(0.0, 1.0)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(catchThrowable(() -> new StigmergyRouter(1.0, 1.0)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(catchThrowable(() -> new StigmergyRouter(0.5, -1.0)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void null_step_safe() {
        StigmergyRouter r = new StigmergyRouter(0.1, 1.0);
        r.step(null, "B");
        r.step("A", null);
        assertThat(r.pheromones()).isEmpty();
    }

    @Test
    void edgeCount_reflects_unique_edges() {
        StigmergyRouter r = new StigmergyRouter(0.1, 1.0);
        r.step("A", "B");
        r.step("A", "B");
        r.step("A", "C");
        assertThat(r.edgeCount()).isEqualTo(2);
    }

    private static Throwable catchThrowable(Runnable r) {
        try { r.run(); return null; } catch (Throwable t) { return t; }
    }
}
