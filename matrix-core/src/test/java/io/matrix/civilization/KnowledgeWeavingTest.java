package io.matrix.civilization;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeWeavingTest {

    private final KnowledgeWeaving weaver = new KnowledgeWeaving();

    @Test
    void shouldAcceptIdenticalKnowledge() {
        String knowledge = "axiom:do_no_harm\naxiom:tell_truth";

        var result = weaver.weave("CultureA", knowledge,
                "CultureB", knowledge, List.of("en", "ru"));

        assertThat(result.resolution())
                .isEqualTo(KnowledgeWeaving.Resolution.ACCEPTED_AS_IS);
    }

    @Test
    void shouldPreserveMinorDifferences() {
        String ka = "axiom:do_no_harm\naxiom:tell_truth";
        String kb = "axiom:do_no_harm\naxiom:transparency";

        var result = weaver.weave("CultureA", ka, "CultureB", kb, List.of("en"));

        assertThat(result.resolution())
                .isEqualTo(KnowledgeWeaving.Resolution.MERGED_WITH_PRESERVATION);
        assertThat(result.preservedDifferences()).isNotEmpty();
    }

    @Test
    void shouldKeepDivergentKnowledge() {
        String ka = "rule:collective\nrule:harmony\nrule:tradition";
        String kb = "rule:individual\nrule:freedom\nrule:innovation";

        var result = weaver.weave("CultureA", ka, "CultureB", kb, List.of("en", "ja"));

        assertThat(result.resolution())
                .isEqualTo(KnowledgeWeaving.Resolution.DIVERGENT_KEPT);
        assertThat(result.preservedDifferences()).hasSizeGreaterThan(0);
    }

    @Test
    void shouldTrackExchangeId() {
        var result = weaver.weave("A", "x", "B", "y", List.of("en"));

        assertThat(result.exchangeId()).isNotNull();
        assertThat(result.sideA()).isEqualTo("A");
        assertThat(result.sideB()).isEqualTo("B");
    }

    @Test
    void shouldGenerateInsight() {
        var result = weaver.weave("West", "freedom", "East", "harmony",
                List.of("en", "ru"));

        assertThat(result.insight()).isNotEmpty();
    }
}
