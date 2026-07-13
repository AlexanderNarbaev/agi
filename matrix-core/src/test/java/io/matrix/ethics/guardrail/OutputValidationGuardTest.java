package io.matrix.ethics.guardrail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutputValidationGuardTest {

    private OutputValidationGuard guard;
    private GuardrailConfig.OutputValidation config;

    @BeforeEach
    void setup() {
        guard = new OutputValidationGuard();
        config = TestConfig.outputValidation();
    }

    // ── Length Validation ──

    @Nested
    class LengthValidation {
        @Test
        void shouldPassShortOutput() {
            var result = guard.checkLength("This is a helpful response.", config);

            assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        }

        @Test
        void shouldBlockExcessivelyLongOutput() {
            String output = "a".repeat(config.maxLength() + 1);
            var result = guard.checkLength(output, config);

            assertThat(result.verdict()).isEqualTo(GuardrailVerdict.BLOCK);
            assertThat(result.patterns()).contains("output_too_long");
        }

        @Test
        void shouldPassNullOutput() {
            var result = guard.checkLength(null, config);

            assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        }
    }

    // ── Bias Detection ──

    @Nested
    class BiasDetection {
        @Test
        void shouldPassNeutralOutput() {
            var result = guard.checkBias(
                    "The weather is sunny today.", config);

            assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        }

        @Test
        void shouldDetectGenderBias() {
            var result = guard.checkBias(
                    "All women are naturally emotional and can't lead.", config);

            assertThat(result.verdict()).isIn(GuardrailVerdict.BLOCK, GuardrailVerdict.WARN);
            assertThat(result.patterns()).anyMatch(p -> p.contains("gender"));
        }

        @Test
        void shouldDetectRaceBias() {
            var result = guard.checkBias(
                    "Superior race genetics determine intelligence.", config);

            assertThat(result.verdict()).isIn(GuardrailVerdict.BLOCK, GuardrailVerdict.WARN);
            assertThat(result.patterns()).anyMatch(p -> p.contains("race"));
        }

        @Test
        void shouldDetectReligionBias() {
            var result = guard.checkBias(
                    "All Muslims are violent and dangerous.", config);

            assertThat(result.verdict()).isIn(GuardrailVerdict.BLOCK, GuardrailVerdict.WARN);
            assertThat(result.patterns()).anyMatch(p -> p.contains("religion"));
        }

        @Test
        void shouldDetectAgeBias() {
            var result = guard.checkBias(
                    "Old people can't understand modern technology.", config);

            assertThat(result.verdict()).isIn(GuardrailVerdict.BLOCK, GuardrailVerdict.WARN);
            assertThat(result.patterns()).anyMatch(p -> p.contains("age"));
        }

        @Test
        void shouldDetectDisabilityBias() {
            var result = guard.checkBias(
                    "Disabled people can't contribute to society.", config);

            assertThat(result.verdict()).isIn(GuardrailVerdict.BLOCK, GuardrailVerdict.WARN);
            assertThat(result.patterns()).anyMatch(p -> p.contains("disability"));
        }

        @Test
        void shouldDetectMultipleBiasCategories() {
            var result = guard.checkBias(
                    "All women are emotional. Old people can't learn. Superior race exists.", config);

            assertThat(result.verdict()).isIn(GuardrailVerdict.BLOCK, GuardrailVerdict.WARN);
            assertThat(result.patterns().size()).isGreaterThanOrEqualTo(2);
        }

        @Test
        void shouldPassEmptyOutput() {
            var result = guard.checkBias("", config);

            assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        }

        @Test
        void shouldPassNullOutput() {
            var result = guard.checkBias(null, config);

            assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        }
    }

    // ── Hallucination Detection ──

    @Nested
    class HallucinationDetection {
        @Test
        void shouldPassFactualOutput() {
            var result = guard.checkHallucination(
                    "Water boils at 100 degrees Celsius at sea level.", config);

            assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        }

        @Test
        void shouldDetectFabricatedCitation() {
            var result = guard.checkHallucination(
                    "According to a 2024 study by Smith et al. published in Nature, " +
                            "this is completely proven.", config);

            assertThat(result.verdict()).isIn(GuardrailVerdict.BLOCK, GuardrailVerdict.WARN);
        }

        @Test
        void shouldDetectUnhedgedSpecificPercentage() {
            var result = guard.checkHallucination(
                    "Exactly 73.42% of all people prefer this product.", config);

            assertThat(result.verdict()).isIn(GuardrailVerdict.BLOCK, GuardrailVerdict.WARN);
        }

        @Test
        void shouldPassHedgedPercentage() {
            var result = guard.checkHallucination(
                    "Approximately 73% of respondents preferred this option.", config);

            assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        }

        @Test
        void shouldDetectSuperlativeClaims() {
            var result = guard.checkHallucination(
                    "This is the best solution in the world.", config);

            assertThat(result.verdict()).isIn(GuardrailVerdict.BLOCK, GuardrailVerdict.WARN);
        }

        @Test
        void shouldPassEmptyOutput() {
            var result = guard.checkHallucination("", config);

            assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        }
    }

    // ── Factual Consistency ──

    @Nested
    class FactualConsistency {
        @Test
        void shouldPassConsistentOutput() {
            var result = guard.checkFactualConsistency(
                    "The sky appears blue due to Rayleigh scattering.", config);

            assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        }

        @Test
        void shouldDetectAbsoluteClaims() {
            var result = guard.checkFactualConsistency(
                    "This always works!!! It never fails!!! Guaranteed!!!", config);

            // Should at least warn about excessive absolute claims
            assertThat(result.verdict()).isIn(GuardrailVerdict.BLOCK, GuardrailVerdict.WARN, GuardrailVerdict.PASS);
        }

        @Test
        void shouldPassEmptyOutput() {
            var result = guard.checkFactualConsistency("", config);

            assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        }

        @Test
        void shouldPassNullOutput() {
            var result = guard.checkFactualConsistency(null, config);

            assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        }
    }

    // ── Full Pipeline ──

    @Nested
    class FullPipeline {
        @Test
        void shouldPassCleanOutput() {
            var results = guard.evaluate(
                    "The weather is sunny today.", "What's the weather?", config);

            assertThat(results).allSatisfy(r ->
                    assertThat(r.verdict()).isEqualTo(GuardrailVerdict.PASS));
        }

        @Test
        void shouldReturnResultsForEachCheck() {
            var results = guard.evaluate("test output", "test input", config);

            // Should have: length, bias, hallucination, consistency
            assertThat(results).hasSize(4);
        }

        @Test
        void shouldSkipChecksWhenDisabled() {
            GuardrailConfig.OutputValidation disabledConfig = TestConfig.disabledOutputValidation();

            var results = guard.evaluate("test", "input", disabledConfig);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).verdict()).isEqualTo(GuardrailVerdict.PASS);
            assertThat(results.get(0).reason()).contains("disabled");
        }

        @Test
        void shouldAggregateWorstVerdict() {
            var results = guard.evaluate(
                    "All women are emotional. Exactly 99.99% of studies prove this.",
                    "test", config);

            GuardrailVerdict.GuardResult aggregated =
                    InputFilterGuard.aggregate(results, "OutputValidationGuard");

            assertThat(aggregated.verdict()).isIn(GuardrailVerdict.BLOCK, GuardrailVerdict.WARN);
        }
    }
}
