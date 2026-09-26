package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlushkovAutomatonTest {

    @Test
    void simple_sequence_matches() {
        GlushkovAutomaton a = GlushkovAutomaton.compile("capital of france");
        assertThat(a.matches(List.of("capital", "of", "france"))).isTrue();
        assertThat(a.matches(List.of("capital", "of", "germany"))).isFalse();
    }

    @Test
    void alternation_matches_any_branch() {
        GlushkovAutomaton a = GlushkovAutomaton.compile("paris|berlin|moscow");
        assertThat(a.matches(List.of("paris"))).isTrue();
        assertThat(a.matches(List.of("berlin"))).isTrue();
        assertThat(a.matches(List.of("moscow"))).isTrue();
        assertThat(a.matches(List.of("london"))).isFalse();
    }

    @Test
    void extra_tokens_break_match() {
        GlushkovAutomaton a = GlushkovAutomaton.compile("hello world");
        // Extra tokens after a complete match cause the next state to fail.
        assertThat(a.matches(List.of("hello", "world", "extra"))).isFalse();
        // Partial input should NOT match (must consume all tokens).
        assertThat(a.matches(List.of("hello"))).isFalse();
        // A single-token pattern DOES match a single-token input.
        GlushkovAutomaton single = GlushkovAutomaton.compile("hello");
        assertThat(single.matches(List.of("hello"))).isTrue();
    }

    @Test
    void empty_input_does_not_match_unless_pattern_is_empty() {
        GlushkovAutomaton a = GlushkovAutomaton.compile("a b");
        // Pattern with 2 tokens cannot match empty input.
        assertThat(a.matches(List.of())).isFalse();
        GlushkovAutomaton empty = GlushkovAutomaton.compile("");
        // Empty pattern matches empty input.
        assertThat(empty.matches(List.of())).isTrue();
    }

    @Test
    void tokens_are_recorded() {
        GlushkovAutomaton a = GlushkovAutomaton.compile("a b c");
        assertThat(a.tokens()).hasSize(3);
        assertThat(a.tokens().get(0)).isEqualTo("a");
        assertThat(a.tokens().get(1)).isEqualTo("b");
        assertThat(a.tokens().get(2)).isEqualTo("c");
    }

    @Test
    void alternation_produces_branching_states() {
        GlushkovAutomaton a = GlushkovAutomaton.compile("a|b c");
        // 3 token positions (a, b, c)
        assertThat(a.tokens().size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void empty_pattern_returns_automaton() {
        GlushkovAutomaton a = GlushkovAutomaton.compile("");
        // Empty pattern → empty automaton.
        assertThat(a.tokens()).isEmpty();
        // Empty pattern trivially matches empty input.
        assertThat(a.matches(java.util.List.of())).isTrue();
    }
}
