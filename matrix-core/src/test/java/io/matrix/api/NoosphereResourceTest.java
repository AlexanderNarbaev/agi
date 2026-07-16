package io.matrix.api;

import io.matrix.noosphere.KnowledgeIndex;
import io.matrix.noosphere.NoosphereRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link NoosphereResource}.
 */
class NoosphereResourceTest {

    private NoosphereResource resource;

    @BeforeEach
    void setUp() {
        resource = new NoosphereResource();
        resource.registry = new NoosphereRegistry();
        resource.knowledgeIndex = new KnowledgeIndex(resource.registry);
    }

    @Test
    void publishShouldSucceedWithMinimalRequest() {
        Map<String, Object> result = resource.publish(Map.of());

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("entryId")).isNotNull();
        assertThat((String) result.get("fnlName")).contains("demo-fnl");
    }

    @Test
    void publishShouldAcceptCustomFields() {
        Map<String, Object> result = resource.publish(Map.of(
                "name", "navigation-v3",
                "type", "navigation",
                "authorInstanceId", "instance-1",
                "tags", List.of("pathfinding", "spatial"),
                "accuracy", 0.92,
                "description", "Advanced navigation FNL"
        ));

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("fnlName")).isEqualTo("navigation-v3");
    }

    @Test
    void searchShouldReturnPublishedFnls() {
        // Publish some FNLs
        resource.publish(Map.of("name", "nav-v1", "type", "navigation"));
        resource.publish(Map.of("name", "vision-v1", "type", "vision"));
        resource.publish(Map.of("name", "nav-v2", "type", "navigation"));

        Map<String, Object> result = resource.search("navigation", 10);
        assertThat(result.get("query")).isEqualTo("navigation");
        assertThat((int) result.get("totalResults")).isPositive();
    }

    @Test
    void searchWithLimitShouldRespectLimit() {
        for (int i = 0; i < 5; i++) {
            resource.publish(Map.of("name", "fnl-" + i, "type", "test"));
        }

        Map<String, Object> result = resource.search("", 3);
        assertThat((int) result.get("returned")).isLessThanOrEqualTo(3);
    }

    @Test
    void statsShouldReturnRegistryInfo() {
        resource.publish(Map.of("name", "demo", "type", "demo"));
        resource.publish(Map.of("name", "demo2", "type", "demo"));

        Map<String, Object> result = resource.stats();

        assertThat(result.get("totalEntries")).isEqualTo(2);
        assertThat(result.get("activeEntries")).isEqualTo(2);
        assertThat(result).containsKeys("indexedDocuments", "topTypes", "recentEvents");
    }

    @Test
    void statsOnEmptyRegistryShouldReturnZeroes() {
        Map<String, Object> result = resource.stats();

        assertThat(result.get("totalEntries")).isEqualTo(0);
        assertThat(result.get("activeEntries")).isEqualTo(0);
    }

    @Test
    void searchEmptyQueryShouldReturnAllEntries() {
        resource.publish(Map.of("name", "a", "type", "x"));
        resource.publish(Map.of("name", "b", "type", "y"));

        Map<String, Object> result = resource.search("", 10);
        assertThat((int) result.get("totalResults")).isEqualTo(2);
    }
}
