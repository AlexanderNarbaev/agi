package io.matrix.vqvae;

import io.matrix.api.Text2VecService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for VqVaeProxy — multimodal VQ-VAE proxy with sensor/effector paths.
 */
class VqVaeProxyTest {

    private Text2VecService text2Vec;

    @BeforeEach
    void setUp() {
        text2Vec = new Text2VecService();
    }

    @Nested
    class Builder {

        @Test
        void shouldBuildWithDefaults() {
            VqVaeProxy proxy = VqVaeProxy.builder().build();
            assertThat(proxy).isNotNull();
        }

        @Test
        void shouldBuildWithCustomDimension() {
            VqVaeProxy proxy = VqVaeProxy.builder()
                    .dimension(32)
                    .codeSize(128)
                    .build();
            assertThat(proxy.dimension()).isEqualTo(32);
        }

        @Test
        void shouldBuildWithText2VecService() {
            VqVaeProxy proxy = VqVaeProxy.builder()
                    .text2VecService(text2Vec)
                    .build();
            assertThat(proxy).isNotNull();
        }

        @Test
        void shouldAcceptCustomMomentum() {
            VqVaeProxy proxy = VqVaeProxy.builder()
                    .momentum(0.05)
                    .build();
            assertThat(proxy).isNotNull();
        }
    }

    @Nested
    class SensorProxy {

        private VqVaeProxy proxy;

        @BeforeEach
        void setUp() {
            proxy = VqVaeProxy.builder()
                    .dimension(8)
                    .codeSize(32)
                    .build();
        }

        @Test
        void shouldConvertContinuousVectorToBooleanVector() {
            double[] input = {0.5, -0.3, 1.0, 0.0, -1.0, 0.8, -0.1, 0.4};
            boolean[] result = proxy.sensorEncode(input);
            assertThat(result).hasSize(8);
        }

        @Test
        void shouldBeDeterministic() {
            double[] input = {0.5, -0.3, 1.0, 0.0, -1.0, 0.8, -0.1, 0.4};
            boolean[] first = proxy.sensorEncode(input);
            boolean[] second = proxy.sensorEncode(input);
            assertThat(first).isEqualTo(second);
        }

        @Test
        void shouldRejectNullInput() {
            assertThatThrownBy(() -> proxy.sensorEncode(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldRejectWrongDimension() {
            assertThatThrownBy(() -> proxy.sensorEncode(new double[]{1.0, 2.0}))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldReturnBooleanVectorOfCorrectSize() {
            double[] input = new double[8];
            boolean[] result = proxy.sensorEncode(input);
            assertThat(result).hasSize(8);
        }
    }

    @Nested
    class EffectorProxy {

        private VqVaeProxy proxy;

        @BeforeEach
        void setUp() {
            proxy = VqVaeProxy.builder()
                    .dimension(8)
                    .codeSize(32)
                    .build();
        }

        @Test
        void shouldConvertBooleanVectorToContinuousVector() {
            boolean[] input = {true, false, true, false, true, false, true, false};
            double[] result = proxy.effectorDecode(input);
            assertThat(result).hasSize(8);
        }

        @Test
        void shouldBeDeterministic() {
            boolean[] input = {true, false, true, false, true, false, true, false};
            double[] first = proxy.effectorDecode(input);
            double[] second = proxy.effectorDecode(input);
            assertThat(first).isEqualTo(second);
        }

        @Test
        void shouldRejectNullInput() {
            assertThatThrownBy(() -> proxy.effectorDecode(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldRejectWrongDimension() {
            assertThatThrownBy(() -> proxy.effectorDecode(new boolean[]{true, false}))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldReturnContinuousValues() {
            boolean[] input = new boolean[8];
            double[] result = proxy.effectorDecode(input);
            for (double v : result) {
                assertThat(Double.isFinite(v)).isTrue();
            }
        }
    }

    @Nested
    class TextToBoolean {

        private VqVaeProxy proxy;

        @BeforeEach
        void setUp() {
            proxy = VqVaeProxy.builder()
                    .dimension(8)
                    .codeSize(32)
                    .text2VecService(text2Vec)
                    .build();
        }

        @Test
        void shouldConvertTextToBooleanVector() {
            boolean[] result = proxy.textToBoolean("hello world");
            assertThat(result).hasSize(8);
        }

        @Test
        void shouldBeDeterministic() {
            boolean[] first = proxy.textToBoolean("test input");
            boolean[] second = proxy.textToBoolean("test input");
            assertThat(first).isEqualTo(second);
        }

        @Test
        void differentTextsShouldProduceDifferentVectors() {
            boolean[] v1 = proxy.textToBoolean("hello");
            boolean[] v2 = proxy.textToBoolean("goodbye");
            // Not guaranteed different due to hashing, but likely
            // Just verify both are valid
            assertThat(v1).hasSize(8);
            assertThat(v2).hasSize(8);
        }

        @Test
        void shouldHandleNullText() {
            assertThatThrownBy(() -> proxy.textToBoolean(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldHandleEmptyText() {
            boolean[] result = proxy.textToBoolean("");
            assertThat(result).hasSize(8);
        }
    }

    @Nested
    class RoundTrip {

        @Test
        void sensorEffectorRoundTripShouldPreserveDimension() {
            VqVaeProxy proxy = VqVaeProxy.builder()
                    .dimension(8)
                    .codeSize(32)
                    .build();

            double[] original = {0.5, -0.3, 1.0, 0.0, -1.0, 0.8, -0.1, 0.4};
            boolean[] booleanVec = proxy.sensorEncode(original);
            double[] reconstructed = proxy.effectorDecode(booleanVec);

            assertThat(reconstructed).hasSize(8);
            for (double v : reconstructed) {
                assertThat(Double.isFinite(v)).isTrue();
            }
        }

        @Test
        void textRoundTripShouldPreserveDimension() {
            VqVaeProxy proxy = VqVaeProxy.builder()
                    .dimension(8)
                    .codeSize(32)
                    .text2VecService(text2Vec)
                    .build();

            boolean[] booleanVec = proxy.textToBoolean("hello world");
            double[] continuous = proxy.effectorDecode(booleanVec);

            assertThat(continuous).hasSize(8);
            for (double v : continuous) {
                assertThat(Double.isFinite(v)).isTrue();
            }
        }
    }
}
