package io.matrix.bir.convert;

import io.matrix.bir.BirCompiler;
import io.matrix.bir.ClauseSetForm;
import io.matrix.bir.TtForm;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ClauseSetToTtConverter}: exact CLAUSESET → TT conversion
 * for k ≤ 20, fidelity measurement, and the arity limit.
 */
class ClauseSetToTtConverterTest {

    private static ClauseSetForm singleClause(int k, long pos, long neg) {
        var clause = new ClauseSetForm.Clause(new long[]{pos}, new long[]{neg});
        return new ClauseSetForm(k, List.of(clause), "test", 1.0);
    }

    @Test
    void convertSingleClause() {
        // Clause x0 AND NOT x1 over k=2 → table 0b0100 (fires only at input 1)
        TtForm tt = ClauseSetToTtConverter.convert(singleClause(2, 0b01L, 0b10L));
        assertThat(tt.table()).isEqualTo(new long[]{0b0010L});
        assertThat(tt.fidelity()).isEqualTo(1.0);
        assertThat(tt.provenance()).isEqualTo("clauseset-to-tt-converter");
    }

    @Test
    void convertEmptyClauseSetIsConstantZero() {
        ClauseSetForm cs = new ClauseSetForm(3, List.of(), "test", 1.0);
        assertThat(ClauseSetToTtConverter.convert(cs).table()).isEqualTo(new long[]{0L});
    }

    @Test
    void convertEmptyClauseIsConstantOne() {
        var empty = new ClauseSetForm.Clause(new long[]{0L}, new long[]{0L});
        ClauseSetForm cs = new ClauseSetForm(3, List.of(empty), "test", 1.0);
        assertThat(ClauseSetToTtConverter.convert(cs).table()).isEqualTo(new long[]{0xFFL});
    }

    @Test
    void convertRoundTripFromTt() {
        // TT → CLAUSESET → TT reconstructs the original table exactly.
        TtForm original = new TtForm(3,
                new long[]{(1L << 3) | (1L << 5) | (1L << 6) | (1L << 7)}, "test-maj3", 1.0);
        ClauseSetForm cs = BirCompiler.ttToClauseSet(original);
        TtForm back = ClauseSetToTtConverter.convert(cs);
        assertThat(back.table()).isEqualTo(original.table());
    }

    @Test
    void measureFidelityPerfect() {
        ClauseSetForm cs = singleClause(2, 0b01L, 0L); // f = x0
        TtForm tt = ClauseSetToTtConverter.convert(cs);
        assertThat(ClauseSetToTtConverter.measureFidelity(tt, cs)).isEqualTo(1.0);
    }

    @Test
    void measureFidelityPartial() {
        ClauseSetForm cs = singleClause(2, 0b01L, 0L); // f = x0 → [0,1,0,1]
        TtForm constOne = new TtForm(2, new long[]{0b1111L}, "test", 1.0);
        // match at inputs 1 and 3 → 2/4
        assertThat(ClauseSetToTtConverter.measureFidelity(constOne, cs)).isEqualTo(0.5);
    }

    @Test
    void convertRejectsArityAbove20() {
        ClauseSetForm cs = new ClauseSetForm(21, List.of(), "test", 1.0);
        assertThatThrownBy(() -> ClauseSetToTtConverter.convert(cs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("21");
    }
}
