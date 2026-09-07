package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 179 — ApiRegistry unit tests. */
class ApiRegistryTest {

    @Test
    void allEndpointsPresent() {
        assertThat(ApiRegistry.endpointCount()).isGreaterThanOrEqualTo(9);
    }

    @Test
    void brainEndpointsOutnumberLlm() {
        // 8 brain / 1 LLM
        assertThat(ApiRegistry.brainCount()).isEqualTo(8);
        assertThat(ApiRegistry.llmCount()).isEqualTo(1);
    }

    @Test
    void endpointRecords() {
        ApiRegistry.Endpoint e = new ApiRegistry.Endpoint(
                "/v1/test", "GET", "test", false);
        assertThat(e.path()).isEqualTo("/v1/test");
        assertThat(e.method()).isEqualTo("GET");
        assertThat(e.purpose()).isEqualTo("test");
        assertThat(e.usesLlm()).isFalse();
    }

    @Test
    void specificEndpointsExist() {
        boolean hasBrainCycle = false;
        boolean hasDistill = false;
        for (var e : ApiRegistry.endpoints()) {
            if (e.path().equals("/v1/brain/cycle")) hasBrainCycle = true;
            if (e.path().equals("/v1/distill/run")) hasDistill = true;
        }
        assertThat(hasBrainCycle).isTrue();
        assertThat(hasDistill).isTrue();
    }

    @Test
    void llmEndpointMarked() {
        for (var e : ApiRegistry.endpoints()) {
            if (e.path().equals("/v1/distill/run")) {
                assertThat(e.usesLlm()).isTrue();
            } else {
                assertThat(e.usesLlm()).isFalse();
            }
        }
    }
}
