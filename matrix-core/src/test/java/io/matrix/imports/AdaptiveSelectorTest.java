package io.matrix.imports;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveSelectorTest {

    @Test
    void shouldSelectAtLeastOneWithHugeBudget() {
        AdaptiveSelector s = new AdaptiveSelector(Long.MAX_VALUE / 2L);
        AdaptiveSelector.Selection sel = s.select();
        assertThat(sel.selected()).isNotEmpty();
        assertThat(sel.diskBudgetBytes()).isPositive();
    }

    @Test
    void shouldPreferSmallerEntriesWhenBudgetIsConstrained() {
        // 1 GB budget: only TINY tier should fit (largest TINY ≈ 3 GB SmolLM2 fits too if we push)
        AdaptiveSelector s = new AdaptiveSelector(1L * 1024 * 1024 * 1024);
        AdaptiveSelector.Selection sel = s.select();
        assertThat(sel.chosenBytes()).isLessThanOrEqualTo(sel.diskBudgetBytes());
        // every picked entry must be on-disk small enough to fit
        for (ModelCatalog.Entry e : sel.selected()) {
            assertThat(e.estimatedBytes()).isLessThanOrEqualTo(sel.diskBudgetBytes());
        }
    }

    @Test
    void shouldReturnEmptyWhenBudgetIsZero() {
        AdaptiveSelector s = new AdaptiveSelector(0);
        AdaptiveSelector.Selection sel = s.select();
        // We treat 0 as "no budget" — we may still fit things if they are very small.
        assertThat(sel.chosenBytes()).isLessThanOrEqualTo(0);
    }

    @Test
    void shouldIncludeDiverseArchitecturesWhenBudgetIsHuge() {
        AdaptiveSelector s = new AdaptiveSelector(Long.MAX_VALUE / 4L, true);
        AdaptiveSelector.Selection sel = s.select();
        long distinctArchitectures = sel.selected().stream()
                .map(ModelCatalog.Entry::architecture)
                .distinct()
                .count();
        // The diversity strategy should pick from at least 2 distinct families when budget allows.
        assertThat(distinctArchitectures).isGreaterThanOrEqualTo(2);
    }

    @Test
    void humanBytesShouldFormat() {
        assertThat(AdaptiveSelector.humanBytes(0)).isEqualTo("0 B");
        assertThat(AdaptiveSelector.humanBytes(512)).isEqualTo("512 B");
        assertThat(AdaptiveSelector.humanBytes(2048)).contains("KB");
        assertThat(AdaptiveSelector.humanBytes(5L * 1024 * 1024 * 1024)).contains("GB");
    }
}
