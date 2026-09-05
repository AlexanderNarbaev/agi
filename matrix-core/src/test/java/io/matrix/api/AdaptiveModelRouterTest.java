package io.matrix.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RUN 119 — AdaptiveModelRouter unit tests. */
class AdaptiveModelRouterTest {

    @Test
    void smallForShortPrompts() {
        AdaptiveModelRouter r = AdaptiveModelRouter.defaultRouter();
        assertThat(r.tierFor(10)).isEqualTo(AdaptiveModelRouter.Tier.SMALL);
        assertThat(r.modelFor(10)).isEqualTo("qwen:0.5b");
    }

    @Test
    void mediumForMidPrompts() {
        AdaptiveModelRouter r = AdaptiveModelRouter.defaultRouter();
        assertThat(r.tierFor(200)).isEqualTo(AdaptiveModelRouter.Tier.MEDIUM);
        assertThat(r.modelFor(200)).isEqualTo("qwen:1.5b");
    }

    @Test
    void largeForLongPrompts() {
        AdaptiveModelRouter r = AdaptiveModelRouter.defaultRouter();
        assertThat(r.tierFor(1000)).isEqualTo(AdaptiveModelRouter.Tier.LARGE);
        assertThat(r.modelFor(1000)).isEqualTo("qwen:7b");
    }

    @Test
    void boundaryConditions() {
        AdaptiveModelRouter r = AdaptiveModelRouter.defaultRouter();
        // Just below threshold
        assertThat(r.tierFor(99)).isEqualTo(AdaptiveModelRouter.Tier.SMALL);
        // At threshold
        assertThat(r.tierFor(100)).isEqualTo(AdaptiveModelRouter.Tier.MEDIUM);
        // Just below large threshold
        assertThat(r.tierFor(499)).isEqualTo(AdaptiveModelRouter.Tier.MEDIUM);
        // At large threshold
        assertThat(r.tierFor(500)).isEqualTo(AdaptiveModelRouter.Tier.LARGE);
    }

    @Test
    void customThresholds() {
        AdaptiveModelRouter r = new AdaptiveModelRouter(
                50, 200, List.of("a", "b", "c"));
        assertThat(r.tierFor(40)).isEqualTo(AdaptiveModelRouter.Tier.SMALL);
        assertThat(r.tierFor(150)).isEqualTo(AdaptiveModelRouter.Tier.MEDIUM);
        assertThat(r.tierFor(300)).isEqualTo(AdaptiveModelRouter.Tier.LARGE);
    }

    @Test
    void invalidThresholdsRejected() {
        assertThatThrownBy(() -> new AdaptiveModelRouter(0, 100,
                List.of("a", "b", "c")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdaptiveModelRouter(200, 100,
                List.of("a", "b", "c")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidModelsListRejected() {
        assertThatThrownBy(() -> new AdaptiveModelRouter(10, 100, List.of("a")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdaptiveModelRouter(10, 100, List.of("a", "b")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdaptiveModelRouter(10, 100, List.of("a", "b", "c", "d")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void accessors() {
        AdaptiveModelRouter r = AdaptiveModelRouter.defaultRouter();
        assertThat(r.smallThreshold()).isEqualTo(100);
        assertThat(r.largeThreshold()).isEqualTo(500);
        assertThat(r.models()).hasSize(3);
    }
}
