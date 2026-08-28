package io.matrix.model;

import io.matrix.bir.Bir;
import io.matrix.bir.BooleanRuntime;
import io.matrix.bir.TtForm;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ModelRegistry}: registers default models and
 * verifies basic BIR evaluation works.
 */
class ModelRegistryTest {

    @Test
    void defaultsAreRegistered() {
        ModelRegistry reg = new ModelRegistry();
        assertThat(reg.names()).contains("sentiment-classifier", "topic-router");
    }

    @Test
    void sentimentClassifierProducesBit() {
        ModelRegistry reg = new ModelRegistry();
        long[] input = new long[1];
        input[0] = 0b1010_1010_1010_1010_1010L;
        int sentiment = reg.predictSentiment(input);
        assertThat(sentiment).isIn(0, 1);
        assertThat(reg.totalEvaluations()).isEqualTo(1L);
    }

    @Test
    void topicRouterReturnsCode() {
        ModelRegistry reg = new ModelRegistry();
        long[] input = new long[1];
        input[0] = 0b0101L;
        int topic = reg.routeTopic(input);
        assertThat(topic).isIn(0, 1, 2, 3);
    }

    @Test
    void describeHasAllEntries() {
        ModelRegistry reg = new ModelRegistry();
        var desc = reg.describe();
        assertThat(desc).containsKey("totalEvaluations");
        assertThat(((java.util.List<?>) desc.get("models"))).hasSize(2);
    }

    @Test
    void customModelCanBeRegistered() {
        ModelRegistry reg = new ModelRegistry();
        // tiny custom TT
        TtForm custom = new TtForm(2, new long[]{0b10, 0b01}, "test/custom", 1.0);
        reg.register(new ModelRegistry.Entry(
                "test-custom", "W6.2", custom, "test entry"));
        assertThat(reg.names()).contains("test-custom");
        assertThat(reg.eval("test-custom", new long[]{0b01L})).isNotEmpty();
    }

    @Test
    void unknownModelThrows() {
        ModelRegistry reg = new ModelRegistry();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> reg.eval("not-a-real-model", new long[]{0}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}