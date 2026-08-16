package io.matrix.explainability;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExplanationPrimitiveTest {

    @Test
    void shouldHave16Primitives() {
        assertThat(ExplanationPrimitive.values()).hasSize(16);
    }

    @Test
    void shouldHaveUniqueNames() {
        for (ExplanationPrimitive p : ExplanationPrimitive.values()) {
            assertThat(p.primitiveName()).isNotBlank();
        }
        // Verify all 16 have distinct primitiveName values
        long distinctNames = java.util.Arrays.stream(ExplanationPrimitive.values())
                .map(ExplanationPrimitive::primitiveName)
                .distinct()
                .count();
        assertThat(distinctNames).isEqualTo(16);
    }

    @Test
    void shouldHaveCorrectCategories() {
        assertThat(ExplanationPrimitive.P1.category())
                .isEqualTo(ExplanationPrimitive.Category.ATTRIBUTION);
        assertThat(ExplanationPrimitive.P4.category())
                .isEqualTo(ExplanationPrimitive.Category.ATTRIBUTION);

        assertThat(ExplanationPrimitive.P5.category())
                .isEqualTo(ExplanationPrimitive.Category.PROTOTYPE);
        assertThat(ExplanationPrimitive.P7.category())
                .isEqualTo(ExplanationPrimitive.Category.PROTOTYPE);

        assertThat(ExplanationPrimitive.P8.category())
                .isEqualTo(ExplanationPrimitive.Category.CONCEPT);
        assertThat(ExplanationPrimitive.P10.category())
                .isEqualTo(ExplanationPrimitive.Category.CONCEPT);

        assertThat(ExplanationPrimitive.P11.category())
                .isEqualTo(ExplanationPrimitive.Category.COUNTERFACTUAL);
        assertThat(ExplanationPrimitive.P13.category())
                .isEqualTo(ExplanationPrimitive.Category.COUNTERFACTUAL);

        assertThat(ExplanationPrimitive.P14.category())
                .isEqualTo(ExplanationPrimitive.Category.MECHANISTIC);
        assertThat(ExplanationPrimitive.P16.category())
                .isEqualTo(ExplanationPrimitive.Category.MECHANISTIC);
    }

    @Test
    void shouldHave5Categories() {
        assertThat(ExplanationPrimitive.Category.values()).hasSize(5);
    }

    @Test
    void shouldFilterByCategory() {
        List<ExplanationPrimitive> attribution = ExplanationPrimitive.byCategory(
                ExplanationPrimitive.Category.ATTRIBUTION);
        assertThat(attribution).hasSize(4);
        assertThat(attribution).containsExactly(
                ExplanationPrimitive.P1, ExplanationPrimitive.P2,
                ExplanationPrimitive.P3, ExplanationPrimitive.P4);

        List<ExplanationPrimitive> prototype = ExplanationPrimitive.byCategory(
                ExplanationPrimitive.Category.PROTOTYPE);
        assertThat(prototype).hasSize(3);

        List<ExplanationPrimitive> concept = ExplanationPrimitive.byCategory(
                ExplanationPrimitive.Category.CONCEPT);
        assertThat(concept).hasSize(3);

        List<ExplanationPrimitive> counterfactual = ExplanationPrimitive.byCategory(
                ExplanationPrimitive.Category.COUNTERFACTUAL);
        assertThat(counterfactual).hasSize(3);

        List<ExplanationPrimitive> mechanistic = ExplanationPrimitive.byCategory(
                ExplanationPrimitive.Category.MECHANISTIC);
        assertThat(mechanistic).hasSize(3);
    }

    @Test
    void eachPrimitiveShouldHaveDescription() {
        for (ExplanationPrimitive p : ExplanationPrimitive.values()) {
            assertThat(p.primitiveName()).isNotBlank();
            assertThat(p.description()).isNotBlank();
            assertThat(p.category()).isNotNull();
        }
    }
}
