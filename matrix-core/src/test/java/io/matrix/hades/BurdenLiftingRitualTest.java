package io.matrix.hades;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BurdenLiftingRitualTest {
    @Test void shouldArchiveFailures() {
        var r = new BurdenLiftingRitual();
        String result = r.archive(List.of("neuron drift", "ethical violation"));
        assertThat(result).contains("burden_lifting").contains("2").contains("archived");
    }
    @Test void shouldHandleEmptyList() {
        var r = new BurdenLiftingRitual();
        assertThat(r.archive(List.of())).isEqualTo("{}");
    }
    @Test void shouldHandleNull() {
        var r = new BurdenLiftingRitual();
        assertThat(r.archive(null)).isEqualTo("{}");
    }
}
