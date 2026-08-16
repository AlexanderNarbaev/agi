package io.matrix.api;

import io.matrix.noosphere.FnlPackage;
import io.matrix.noosphere.KnowledgeIndex;
import io.matrix.noosphere.NoosphereRegistry;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Noosphere REST API — FNL publication, search, and registry stats.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /api/v1/noosphere/publish} — publish an FNL to the registry</li>
 *   <li>{@code GET /api/v1/noosphere/search?q=...} — search published FNLs</li>
 *   <li>{@code GET /api/v1/noosphere/stats} — registry statistics</li>
 * </ul>
 *
 * @since 3.32
 */
@Path("/api/v1/noosphere")
@Produces(MediaType.APPLICATION_JSON)
public class NoosphereResource {

    @Inject
    NoosphereRegistry registry;

    @Inject
    KnowledgeIndex knowledgeIndex;

    /**
     * Publishes an FNL package to the Noosphere registry.
     */
    @POST
    @Path("/publish")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, Object> publish(Map<String, Object> request) {
        String name = (String) request.getOrDefault("name", "demo-fnl-" + System.currentTimeMillis());
        String type = (String) request.getOrDefault("type", "demo");
        String author = (String) request.getOrDefault("authorInstanceId", "instance-" + UUID.randomUUID().toString().substring(0, 8));

        if (name == null || name.isBlank()) {
            name = "demo-fnl-" + System.currentTimeMillis();
        }
        if (type == null || type.isBlank()) {
            type = "demo";
        }

        @SuppressWarnings("unchecked")
        List<String> tags = request.containsKey("tags")
                ? (List<String>) request.get("tags")
                : List.of("demo");

        double accuracy = request.containsKey("accuracy")
                ? ((Number) request.get("accuracy")).doubleValue()
                : 0.85;
        accuracy = Math.clamp(accuracy, 0.0, 1.0);

        FnlPackage fnl = FnlPackage.builder()
                .name(name)
                .type(type)
                .version("1.0.0")
                .authorInstanceId(author)
                .accuracy(accuracy)
                .generation(1)
                .description((String) request.getOrDefault("description", "Auto-published FNL"))
                .tags(tags.toArray(new String[0]))
                .build();

        NoosphereRegistry.PublishResult result = registry.publish(fnl);
        if (result.success()) {
            knowledgeIndex.reindex(); // incremental after successful publish
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.success());
        response.put("message", result.message());
        response.put("entryId", result.entryId() != null ? result.entryId().toString() : null);
        response.put("fnlName", name);
        response.put("timestamp", Instant.now().toString());
        return response;
    }

    /**
     * Searches published FNLs in the Noosphere registry.
     */
    @GET
    @Path("/search")
    public Map<String, Object> search(@QueryParam("q") String query,
                                       @QueryParam("limit") @DefaultValue("10") int limit) {
        if (query == null || query.isBlank()) {
            query = "";
        }

        List<KnowledgeIndex.SearchResult> results = knowledgeIndex.search(query);

        List<Map<String, Object>> items = new ArrayList<>();
        int count = 0;
        for (var r : results) {
            if (count >= limit) break;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", r.fnl().name());
            item.put("type", r.fnl().type());
            item.put("author", r.fnl().authorInstanceId());
            item.put("accuracy", r.fnl().accuracy());
            item.put("relevance", r.relevance());
            item.put("description", r.fnl().description());
            items.add(item);
            count++;
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("query", query);
        response.put("totalResults", results.size());
        response.put("returned", items.size());
        response.put("results", items);
        return response;
    }

    /**
     * Returns Noosphere registry statistics.
     */
    @GET
    @Path("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalEntries", registry.size());
        response.put("activeEntries", registry.activeEntries().size());
        response.put("indexedDocuments", knowledgeIndex.indexedCount());
        response.put("topTypes", registry.activeEntries().stream()
                .map(e -> e.fnlPackage().type())
                .distinct()
                .toList());
        response.put("recentEvents", registry.eventLog().stream()
                .skip(Math.max(0, registry.eventLog().size() - 5))
                .toList());
        return response;
    }
}
