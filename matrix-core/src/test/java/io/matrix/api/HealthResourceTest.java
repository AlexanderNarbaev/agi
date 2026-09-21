package io.matrix.api;

import io.matrix.imports.BooleanChainRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HealthResource} — RUN 38 production health checks.
 */
class HealthResourceTest {

    private HealthResource resource;

    @BeforeEach
    void setUp() {
        resource = new HealthResource();
        // Inject null collaborators via reflection (CDI not active in unit tests).
        try {
            java.lang.reflect.Field chainField = HealthResource.class.getDeclaredField("chainRunner");
            chainField.setAccessible(true);
            chainField.set(resource, BooleanChainRunner.empty());

            QaCorpusIndex corpus = new QaCorpusIndex();
            java.lang.reflect.Field corpusField = HealthResource.class.getDeclaredField("qaIndex");
            corpusField.setAccessible(true);
            corpusField.set(resource, corpus);

            LmHeadTrainer trainer = new LmHeadTrainer();
            java.lang.reflect.Field trainerField = HealthResource.class.getDeclaredField("lmHeadTrainer");
            trainerField.setAccessible(true);
            trainerField.set(resource, trainer);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void liveAlwaysReturnsUp() {
        var resp = resource.live();
        assertThat(resp.get("status")).isEqualTo("UP");
        assertThat(resp).containsKey("uptimeMs");
    }

    @Test
    void readyReturnsDegradedWithEmptyCorpus() {
        // No corpus entries loaded → DEGRADED.
        var resp = resource.ready();
        assertThat(resp.getStatus()).isEqualTo(503);
        var body = (Map<?, ?>) resp.getEntity();
        assertThat(body.get("status")).isEqualTo("DEGRADED");
    }

    @Test
    void healthIncludesAllServices() {
        var body = resource.health();
        assertThat(body.containsKey("status")).isTrue();
        assertThat(body.containsKey("uptimeMs")).isTrue();
        assertThat(body.containsKey("services")).isTrue();
        var services = (Map<?, ?>) body.get("services");
        assertThat(services.containsKey("chain")).isTrue();
        assertThat(services.containsKey("corpus")).isTrue();
        assertThat(services.containsKey("lmHead")).isTrue();
    }

    @Test
    void overallStatusReportsCorrectly() {
        // With empty corpus and empty chain, overall = DEGRADED.
        var body = resource.health();
        assertThat(body.get("status")).isEqualTo("DEGRADED");
    }

    @Test
    void statusEnumHasExpectedValues() {
        // The enum has 2 values.
        assertThat(HealthResource.Status.values()).hasSize(2);
        assertThat(HealthResource.Status.valueOf("UP")).isNotNull();
        assertThat(HealthResource.Status.valueOf("DEGRADED")).isNotNull();
    }
}
