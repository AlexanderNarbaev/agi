package io.matrix.shadow;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlackBoxExplainerTest {

    private final BlackBoxExplainer explainer = new BlackBoxExplainer();

    @Test
    void shouldDetectTransparentDecision() {
        var explanation = explainer.explain(
                "Denied because user lacks permission for this action",
                List.of());

        assertThat(explanation.level()).isEqualTo(BlackBoxExplainer.TransparencyLevel.TRANSPARENT);
        assertThat(explanation.reliable()).isTrue();
    }

    @Test
    void shouldFlagOpaqueDecision() {
        var explanation = explainer.explain("Request denied", List.of());

        assertThat(explanation.level()).isEqualTo(BlackBoxExplainer.TransparencyLevel.OPAQUE);
        assertThat(explanation.reliable()).isFalse();
    }

    @Test
    void shouldProvidePartialExplanationWithContext() {
        var explanation = explainer.explain("Content removed",
                List.of("policy violation", "user reported", "automatic filter"));

        assertThat(explanation.level()).isEqualTo(BlackBoxExplainer.TransparencyLevel.PARTIAL);
        assertThat(explanation.reasoning()).contains("policy violation");
    }

    @Test
    void shouldVerifyTrustedSource() {
        var explanation = explainer.verifySource("https://arxiv.org/abs/2501.12345",
                List.of("arxiv.org", "ieee.org"));

        assertThat(explanation.reliable()).isTrue();
    }

    @Test
    void shouldFlagUntrustedSource() {
        var explanation = explainer.verifySource("https://random-blog.example.com/post",
                List.of("arxiv.org", "ieee.org"));

        assertThat(explanation.reliable()).isFalse();
    }

    @Test
    void shouldHandleNullSource() {
        var explanation = explainer.verifySource(null, List.of("arxiv.org"));

        assertThat(explanation.reliable()).isFalse();
    }
}
