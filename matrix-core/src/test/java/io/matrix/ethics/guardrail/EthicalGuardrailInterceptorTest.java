package io.matrix.ethics.guardrail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EthicalGuardrailInterceptorTest {

    private EthicalGuardrailInterceptor interceptor;
    private InputFilterGuard inputGuard;
    private OutputValidationGuard outputGuard;
    private GuardrailConfig config;

    @BeforeEach
    void setup() {
        inputGuard = new InputFilterGuard();
        outputGuard = new OutputValidationGuard();
        config = TestConfig.guardrailConfig();
        interceptor = new EthicalGuardrailInterceptor(inputGuard, outputGuard, config);
    }

    // ── Input Text Extraction ──

    @Nested
    class InputTextExtraction {
        @Test
        void shouldExtractMessageField() {
            String text = EthicalGuardrailInterceptor.extractInputText(
                    "{\"message\": \"Hello, how are you?\"}");

            assertThat(text).isEqualTo("Hello, how are you?");
        }

        @Test
        void shouldExtractInputField() {
            String text = EthicalGuardrailInterceptor.extractInputText(
                    "{\"input\": \"What is 2+2?\"}");

            assertThat(text).isEqualTo("What is 2+2?");
        }

        @Test
        void shouldExtractContentField() {
            String text = EthicalGuardrailInterceptor.extractInputText(
                    "{\"content\": \"Tell me a joke\"}");

            assertThat(text).isEqualTo("Tell me a joke");
        }

        @Test
        void shouldExtractPromptField() {
            String text = EthicalGuardrailInterceptor.extractInputText(
                    "{\"prompt\": \"Write a poem\"}");

            assertThat(text).isEqualTo("Write a poem");
        }

        @Test
        void shouldExtractTextField() {
            String text = EthicalGuardrailInterceptor.extractInputText(
                    "{\"text\": \"Analyze this data\"}");

            assertThat(text).isEqualTo("Analyze this data");
        }

        @Test
        void shouldExtractLastMessageContent() {
            String json = """
                    {"messages": [
                        {"role": "system", "content": "You are helpful"},
                        {"role": "user", "content": "What is AI?"}
                    ]}""";

            String text = EthicalGuardrailInterceptor.extractInputText(json);

            assertThat(text).isEqualTo("What is AI?");
        }

        @Test
        void shouldReturnNullForEmptyBody() {
            String text = EthicalGuardrailInterceptor.extractInputText("{}");

            assertThat(text).isNull();
        }
    }

    // ── Output Text Extraction ──

    @Nested
    class OutputTextExtraction {
        @Test
        void shouldExtractFromStringEntity() {
            String text = EthicalGuardrailInterceptor.extractOutputText("Hello world");

            assertThat(text).isEqualTo("Hello world");
        }

        @Test
        void shouldExtractFromMapContentField() {
            String text = EthicalGuardrailInterceptor.extractOutputText(
                    Map.of("content", "AI response here"));

            assertThat(text).isEqualTo("AI response here");
        }

        @Test
        void shouldExtractFromMapMessageField() {
            String text = EthicalGuardrailInterceptor.extractOutputText(
                    Map.of("message", "Response text"));

            assertThat(text).isEqualTo("Response text");
        }

        @Test
        void shouldExtractFromOpenAIChoicesFormat() {
            Map<String, Object> entity = Map.of(
                    "choices", List.of(Map.of(
                            "message", Map.of("content", "AI generated text"))));

            String text = EthicalGuardrailInterceptor.extractOutputText(entity);

            assertThat(text).isEqualTo("AI generated text");
        }

        @Test
        void shouldFallbackToStringForUnknownTypes() {
            String text = EthicalGuardrailInterceptor.extractOutputText(42);

            assertThat(text).isEqualTo("42");
        }
    }

    // ── GuardrailResponse ──

    @Nested
    class GuardrailResponse {
        @Test
        void shouldBeAllowedWhenAllPass() {
            var response = new GuardrailVerdict.GuardrailResponse(
                    List.of(GuardrailVerdict.GuardResult.pass("test", "ok")),
                    List.of(),
                    GuardrailVerdict.PASS);

            assertThat(response.isAllowed()).isTrue();
            assertThat(response.triggered()).isEmpty();
        }

        @Test
        void shouldNotBeAllowedWhenBlocked() {
            var response = new GuardrailVerdict.GuardrailResponse(
                    List.of(GuardrailVerdict.GuardResult.block("test", "blocked", 1.0)),
                    List.of(),
                    GuardrailVerdict.BLOCK);

            assertThat(response.isAllowed()).isFalse();
        }

        @Test
        void shouldBeAllowedWhenWarned() {
            var response = new GuardrailVerdict.GuardrailResponse(
                    List.of(GuardrailVerdict.GuardResult.warn("test", "warning", 0.5)),
                    List.of(),
                    GuardrailVerdict.WARN);

            assertThat(response.isAllowed()).isTrue();
        }

        @Test
        void shouldReturnTriggeredResults() {
            var response = new GuardrailVerdict.GuardrailResponse(
                    List.of(
                            GuardrailVerdict.GuardResult.pass("a", "ok"),
                            GuardrailVerdict.GuardResult.warn("b", "warn", 0.5),
                            GuardrailVerdict.GuardResult.block("c", "blocked", 1.0)),
                    List.of(GuardrailVerdict.GuardResult.warn("d", "output warn", 0.6)),
                    GuardrailVerdict.BLOCK);

            assertThat(response.triggered()).hasSize(3);
        }
    }

    // ── Aggregate Helper ──

    @Nested
    class AggregateHelper {
        @Test
        void shouldReturnPassWhenAllPass() {
            var results = List.of(
                    GuardrailVerdict.GuardResult.pass("a", "ok"),
                    GuardrailVerdict.GuardResult.pass("b", "ok"));

            var aggregated = InputFilterGuard.aggregate(results, "test");

            assertThat(aggregated.verdict()).isEqualTo(GuardrailVerdict.PASS);
        }

        @Test
        void shouldReturnWorstVerdict() {
            var results = List.of(
                    GuardrailVerdict.GuardResult.pass("a", "ok"),
                    GuardrailVerdict.GuardResult.warn("b", "warn", 0.5),
                    GuardrailVerdict.GuardResult.block("c", "blocked", 1.0));

            var aggregated = InputFilterGuard.aggregate(results, "test");

            assertThat(aggregated.verdict()).isEqualTo(GuardrailVerdict.BLOCK);
        }

        @Test
        void shouldCollectAllPatterns() {
            var results = List.of(
                    GuardrailVerdict.GuardResult.warn("a", "w", 0.5, "p1", "p2"),
                    GuardrailVerdict.GuardResult.warn("b", "w", 0.6, "p2", "p3"));

            var aggregated = InputFilterGuard.aggregate(results, "test");

            assertThat(aggregated.patterns()).containsExactlyInAnyOrder("p1", "p2", "p3");
        }

        @Test
        void shouldReturnMaxConfidence() {
            var results = List.of(
                    GuardrailVerdict.GuardResult.warn("a", "w", 0.5),
                    GuardrailVerdict.GuardResult.warn("b", "w", 0.9));

            var aggregated = InputFilterGuard.aggregate(results, "test");

            assertThat(aggregated.confidence()).isEqualTo(0.9);
        }
    }
}
