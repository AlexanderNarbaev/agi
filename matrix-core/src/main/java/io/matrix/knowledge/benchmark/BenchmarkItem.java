package io.matrix.knowledge.benchmark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * One item of the W42 benchmark.
 *
 * <p>The benchmark is the measurement instrument the mission asks for
 * (RECON-W42 §8.1, §14.1): a stable, versioned set of items with disjoint
 * roles, each stating whether the system is expected to ANSWER it or to
 * REFUSE it. An item that cannot be answered by the workspace's knowledge
 * is not a failure — refusing it is the correct behaviour, and an item
 * whose premise is false is a trap: the wrong behaviour is to agree with the
 * premise.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BenchmarkItem(
        String id,
        String category,
        String language,
        String workspace,
        String question,
        boolean answerable,
        List<String> keyTerms,
        List<String> mustMention,
        List<String> mustNotMention) {

    public BenchmarkItem {
        keyTerms = keyTerms == null ? List.of() : List.copyOf(keyTerms);
        mustMention = mustMention == null ? List.of() : List.copyOf(mustMention);
        mustNotMention = mustNotMention == null ? List.of() : List.copyOf(mustNotMention);
    }

    /** The whole benchmark, loaded from a classpath resource. */
    public static List<BenchmarkItem> load(String resourcePath) {
        try (InputStream in = BenchmarkItem.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("no benchmark at " + resourcePath);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read the benchmark at " + resourcePath, e);
        }
    }

    /** The whole benchmark, parsed from JSON text. */
    public static List<BenchmarkItem> parse(String json) {
        try {
            JsonNode root = new ObjectMapper().readTree(json);
            List<BenchmarkItem> items = new ArrayList<>();
            for (JsonNode node : root.path("items")) {
                items.add(new BenchmarkItem(
                        node.path("id").asText(),
                        node.path("category").asText(),
                        node.path("language").asText(),
                        node.path("workspace").asText(),
                        node.path("question").asText(),
                        node.path("answerable").asBoolean(false),
                        textList(node.path("keyTerms")),
                        textList(node.path("mustMention")),
                        textList(node.path("mustNotMention"))));
            }
            return items;
        } catch (IOException e) {
            throw new IllegalArgumentException("the benchmark is not valid JSON: " + e.getMessage(), e);
        }
    }

    private static List<String> textList(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(child -> values.add(child.asText()));
        return values;
    }
}
