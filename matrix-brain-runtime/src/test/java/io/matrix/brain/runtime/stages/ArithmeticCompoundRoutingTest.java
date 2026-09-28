package io.matrix.brain.runtime.stages;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * RECON-W23 — compound arithmetic routing and operator precedence.
 *
 * <p>PD-1..PD-4 are frozen probe contracts, reproduced verbatim from
 * {@code EvalBattery}. Before W23 the greedy single-pair regex answered
 * "2 + 3 * 4" as "2 + 3 = 5" and "5 - 1 + 2" as "5 - 1 = 4", discarding the
 * remaining operands; and the word-numeral probes could not be tokenised at all.</p>
 */
class ArithmeticCompoundRoutingTest {

    private final ArithmeticStage stage = new ArithmeticStage(new PlanningStage());

    private String eval(String input) {
        var r = stage.tryEvaluate(input, new java.util.ArrayList<>());
        return r.matched() ? r.reply() : "<MISS>";
    }

    // ---- The four frozen PD probes ------------------------------------------

    @Test
    void PD1_twice_five_plus_three_is_13() {
        assertThat(eval("twice five plus three")).contains("13");
    }

    @Test
    void PD2_multiplication_binds_tighter_than_addition() {
        // The W23 headline regression: previously answered "2 + 3 = 5".
        assertThat(eval("2 + 3 * 4")).contains("14");
        assertThat(eval("2 + 3 * 4")).doesNotContain("2 + 3 = 5");
    }

    @Test
    void PD3_left_to_right_within_equal_precedence() {
        // "+" and "-" share precedence and associate left to right.
        assertThat(eval("5 - 1 + 2")).contains("6");
    }

    @Test
    void PD4_ten_times_two_minus_five_is_15() {
        assertThat(eval("ten times two minus five")).contains("15");
    }

    // ---- Precedence unit level ----------------------------------------------

    @Test
    void precedence_evaluator_respects_mult_over_add() {
        assertThat(ArithmeticStage.evaluateWithPrecedence(List.of("2","+","3","*","4")))
            .isEqualTo(java.math.BigInteger.valueOf(14));
    }

    @Test
    void precedence_evaluator_respects_div_over_sub() {
        assertThat(ArithmeticStage.evaluateWithPrecedence(List.of("10","-","8","/","2")))
            .isEqualTo(java.math.BigInteger.valueOf(6));
    }

    @Test
    void equal_precedence_associates_left_to_right() {
        // 10 - 4 - 3 == 3, NOT 10 - (4 - 3) == 9
        assertThat(ArithmeticStage.evaluateWithPrecedence(List.of("10","-","4","-","3")))
            .isEqualTo(java.math.BigInteger.valueOf(3));
    }

    @Test
    void division_is_left_to_right_and_exact() {
        // 8 / 2 * 3 == 12 ; 100 / 10 / 2 == 5
        assertThat(ArithmeticStage.evaluateWithPrecedence(List.of("8","/","2","*","3")))
            .isEqualTo(java.math.BigInteger.valueOf(12));
        assertThat(ArithmeticStage.evaluateWithPrecedence(List.of("100","/","10","/","2")))
            .isEqualTo(java.math.BigInteger.valueOf(5));
    }

    // ---- Honest declines instead of fabricated answers -----------------------

    @Test
    void non_exact_division_declines_rather_than_truncating() {
        assertThat(ArithmeticStage.evaluateWithPrecedence(List.of("5","/","2"))).isNull();
    }

    @Test
    void division_by_zero_declines() {
        assertThat(ArithmeticStage.evaluateWithPrecedence(List.of("5","/","0"))).isNull();
    }

    @Test
    void malformed_token_streams_decline() {
        assertThat(ArithmeticStage.evaluateWithPrecedence(List.of("2","+"))).isNull();
        assertThat(ArithmeticStage.evaluateWithPrecedence(List.of("2","+","3","*"))).isNull();
    }

    // ---- The single-operation fast path must still work ---------------------

    @Test
    void simple_binary_queries_are_unaffected() {
        assertThat(eval("2+3")).contains("5");
        assertThat(eval("7*6")).contains("42");
        assertThat(eval("10-4")).contains("6");
        assertThat(eval("What is 2+3?")).contains("5");
    }

    // ---- Word normalisation --------------------------------------------------

    @Test
    void word_numerals_and_operators_normalise() {
        String n = ArithmeticStage.normalizeWordArithmetic("twice five plus three");
        assertThat(n).isEqualTo("5 * 2 + 3");
        assertThat(ArithmeticStage.normalizeWordArithmetic("ten times two minus five"))
            .isEqualTo("10 * 2 - 5");
    }

    @Test
    void normalisation_leaves_plain_expressions_untouched() {
        assertThat(ArithmeticStage.normalizeWordArithmetic("2 + 3 * 4")).isEqualTo("2 + 3 * 4");
    }

    // ---- Article III determinism --------------------------------------------

    @Test
    void compound_evaluation_is_deterministic() {
        String first = eval("2 + 3 * 4");
        for (int i = 0; i < 20; i++) {
            assertThat(eval("2 + 3 * 4")).isEqualTo(first);
        }
    }

    // ---- Non-arithmetic input must not be hijacked --------------------------

    @Test
    void non_arithmetic_input_is_not_claimed() {
        assertThat(eval("Who wrote Hamlet?")).isEqualTo("<MISS>");
        assertThat(eval("hello there")).isEqualTo("<MISS>");
    }
}
