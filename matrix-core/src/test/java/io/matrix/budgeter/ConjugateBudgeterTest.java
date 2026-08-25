package io.matrix.budgeter;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests for {@link ConjugateBudgeter} (DESIGN-11 §3, H-021).
 */
class ConjugateBudgeterTest {

    private final ConjugateBudgeter budgeter = new ConjugateBudgeter();

    @Test
    void picksOptimalSubsetOnCanonicalInstance() {
        ConjugateBudgeter.Row[] rows = {
                new ConjugateBudgeter.Row("a", 60, 10),
                new ConjugateBudgeter.Row("b", 100, 20),
                new ConjugateBudgeter.Row("c", 120, 30)};
        // Classic knapsack: {b,c} dominates under envelope 50.
        ConjugateBudgeter.Allocation alloc = budgeter.allocate(rows, 50);

        assertThat(alloc.mode()).isEqualTo(ConjugateBudgeter.Mode.CONJUGATE);
        assertThat(alloc.selected()[1]).isTrue();
        assertThat(alloc.selected()[2]).isTrue();
        assertThat(alloc.objective()).isCloseTo(220.0, within(1e-6));
    }

    @Test
    void fallbackWhenEnvelopeBelowCheapestRow() {
        ConjugateBudgeter.Row[] rows = {new ConjugateBudgeter.Row("x", 5, 100)};
        ConjugateBudgeter.Allocation alloc = budgeter.allocate(rows, 10);

        assertThat(alloc.mode()).isEqualTo(ConjugateBudgeter.Mode.FALLBACK_LEVIN_PROPORTIONAL);
        assertThat(alloc.shadowPrice()).isNaN();
    }

    @Test
    void deterministicReplay() {
        ConjugateBudgeter.Row[] rows = {
                new ConjugateBudgeter.Row("a", 3, 4),
                new ConjugateBudgeter.Row("b", 7, 9),
                new ConjugateBudgeter.Row("c", 2, 1)};
        var first = budgeter.allocate(rows, 12);
        var second = budgeter.allocate(rows, 12);
        assertThat(Arrays.equals(first.selected(), second.selected())).isTrue();
        assertThat(first.objective()).isEqualTo(second.objective());
        assertThat(first.shadowPrice()).isEqualTo(second.shadowPrice());
    }

    @Test
    void rejectsInvalidInputs() {
        ConjugateBudgeter.Row[] rows = {new ConjugateBudgeter.Row("a", 1, 1)};
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> budgeter.allocate(rows, -1))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new ConjugateBudgeter.Row("bad", -1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new ConjugateBudgeter.Row("bad", 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Property: CONJUGATE objective ≥ any single-row alternative (sanity), spent ≤ envelope ---

    @Provide
    Arbitrary<ConjugateBudgeter.Row[]> rowSets() {
        Arbitrary<ConjugateBudgeter.Row> row = net.jqwik.api.Combinators
                .combine(Arbitraries.integers().between(0, 97),
                        Arbitraries.integers().between(1, 40))
                .as((v, c) -> new ConjugateBudgeter.Row("r" + v + ":" + c, v, c));
        return row.list().ofMinSize(1).ofMaxSize(8)
                .map(list -> list.toArray(ConjugateBudgeter.Row[]::new));
    }

    @Property
    void spentNeverExceedsEnvelopeAndModeIsConsistent(
            @ForAll("rowSets") ConjugateBudgeter.Row[] rows,
            @ForAll long envelope) {
        long env = Math.floorMod(envelope, 5000);
        ConjugateBudgeter.Allocation alloc = budgeter.allocate(rows, env);

        if (alloc.mode() == ConjugateBudgeter.Mode.CONJUGATE) {
            assertThat(alloc.spentEnvelope()).isLessThanOrEqualTo(env);
            assertThat(alloc.shadowPrice())
                    .describedAs("shadow price is finite in conjugate mode")
                    .isFinite();
        }
        double objectiveFromSelection = 0;
        for (int i = 0; i < rows.length; i++) {
            if (alloc.selected()[i]) {
                objectiveFromSelection += rows[i].value();
            }
        }
        assertThat(alloc.objective())
                .isCloseTo(objectiveFromSelection, within(1e-6));
    }

    @Property
    void conjugateAtLeastAsGoodAsAnySingleGreedyRow(
            @ForAll("rowSets") ConjugateBudgeter.Row[] rows,
            @ForAll long envelope) {
        long env = Math.floorMod(envelope, 5000) + 1;
        ConjugateBudgeter.Allocation alloc = budgeter.allocate(rows, env);
        if (alloc.mode() != ConjugateBudgeter.Mode.CONJUGATE) {
            return;
        }
        double bestSingleFitting = Arrays.stream(rows)
                .filter(r -> r.cost() <= env)
                .mapToDouble(ConjugateBudgeter.Row::value)
                .max()
                .orElse(0);
        assertThat(alloc.objective()).isGreaterThanOrEqualTo(bestSingleFitting - 1e-9);
    }
}
