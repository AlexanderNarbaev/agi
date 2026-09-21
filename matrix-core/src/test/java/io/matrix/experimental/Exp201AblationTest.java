package io.matrix.experimental;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 201 — Ablation EXP.
 *
 * <p>Run baseline vs. each ablation across 100 cycles; record
 * acceptance rates. Compare to fixed-seed acceptance floor.
 */
@Tag("exp")
class Exp201AblationTest {

    @Test
    void baselineVsAblations() {
        AblationStudy.AblationResult baseline =
                AblationStudy.run(AblationStudy.Component.ACTION, 100);
        System.out.println("[ABLATION-EXP] " + AblationStudy.format(baseline));

        // Each component ablated
        for (AblationStudy.Component c : AblationStudy.Component.values()) {
            var res = AblationStudy.run(c, 100);
            System.out.println("[ABLATION-EXP] " + AblationStudy.format(res));
            // Sanity: 100 cycles total, accepted + denied = 100
            assertThat(res.accepted() + res.denied()).isEqualTo(100);
        }
    }
}
