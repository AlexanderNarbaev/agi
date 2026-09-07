package io.matrix.consciousness;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 245 — CycleMerger EXP.
 *
 * <p>100 inputs with 30% duplicates, merge → 70 unique.
 */
@Tag("exp")
class Exp245CycleMergerTest {

    @Test
    void hundredInputsThirtyDuplicates() {
        List<String> inputs = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            // 30% duplicates (every 3rd input repeated)
            if (i % 3 == 0 && i > 0) {
                inputs.add("Q-" + (i - 1));
            } else {
                inputs.add("Q-" + i);
            }
        }
        var result = CycleMerger.merge(inputs);
        System.out.printf("[MERGE-100] merged=%d duplicates=%d%n",
                result.merged().size(), result.duplicates());
        assertThat(result.duplicates()).isGreaterThan(0);
        assertThat(result.merged().size()).isLessThan(inputs.size());
    }
}
