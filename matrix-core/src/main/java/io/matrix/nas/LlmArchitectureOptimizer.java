package io.matrix.nas;

import io.matrix.nas.ArchitectureSpec.Activation;
import io.matrix.nas.ArchitectureSpec.LayerSpec;
import io.matrix.nas.ArchitectureSpec.LayerType;
import io.matrix.nas.ArchitectureSpec.MutationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * LLM-Assisted Architecture Optimizer using the CRISPE prompt framework.
 *
 * <p>Generates structured prompts for LLMs to suggest architecture improvements,
 * parses responses into {@link MutationResult} instances, and supports multiple
 * LLM backends via a pluggable function interface.
 *
 * <p>CRISPE Framework:
 * <ul>
 *   <li><b>C</b>apacity — role definition (NAS expert)</li>
 *   <li><b>R</b>ole — architecture analyst</li>
 *   <li><b>I</b>nsight — current architecture context</li>
 *   <li><b>S</b>tatement — specific improvement task</li>
 *   <li><b>P</b>ersonality — analytical, data-driven</li>
 *   <li><b>E</b>xperiment — provide concrete mutation</li>
 * </ul>
 *
 * <p>Thread-safe: all mutable state is confined to method-local variables.
 *
 * <p>Ref: arXiv:2406.05433 — LLM-Assisted Adversarial Robustness NAS
 */
public final class LlmArchitectureOptimizer {

    private static final Logger log = LoggerFactory.getLogger(LlmArchitectureOptimizer.class);

    private final Function<String, CompletableFuture<String>> llmBackend;
    private final MutationOperator mutationOperator;

    /**
     * Creates an optimizer with a custom LLM backend.
     *
     * @param llmBackend       function that sends a prompt to an LLM and returns the response
     * @param mutationOperator fallback mutation operator
     */
    public LlmArchitectureOptimizer(Function<String, CompletableFuture<String>> llmBackend,
                                     MutationOperator mutationOperator) {
        this.llmBackend = Objects.requireNonNull(llmBackend);
        this.mutationOperator = Objects.requireNonNull(mutationOperator);
    }

    /**
     * Creates an optimizer with the default HuggingFace backend.
     *
     * @param mutationOperator fallback mutation operator
     */
    public LlmArchitectureOptimizer(MutationOperator mutationOperator) {
        this(createDefaultBackend(), mutationOperator);
    }

    // ─── Prompt Generation (CRISPE Framework) ───

    /**
     * Generates a CRISPE-structured prompt for architecture improvement.
     *
     * @param spec           current architecture
     * @param fitnessHistory recent fitness values (best per generation)
     * @param targetMetric   metric to optimize ("accuracy", "latency", "memory")
     * @return structured prompt string
     */
    public String generateCrispePrompt(ArchitectureSpec spec,
                                        List<Long> fitnessHistory,
                                        String targetMetric) {
        Objects.requireNonNull(spec);
        Objects.requireNonNull(fitnessHistory);
        Objects.requireNonNull(targetMetric);

        var sb = new StringBuilder();

        // C — Capacity
        sb.append("You are a Neural Architecture Search expert specializing in ")
          .append("MPDT (Multi-Path Decision Tree) architectures.\n\n");

        // R — Role
        sb.append("ROLE: Analyze the given neural architecture and suggest a single ")
          .append("improvement mutation to optimize ").append(targetMetric).append(".\n\n");

        // I — Insight
        sb.append("CURRENT ARCHITECTURE:\n");
        sb.append(spec.toPromptString()).append("\n");
        sb.append("Complexity: ").append(spec.complexity()).append("\n");
        sb.append("Total neurons: ").append(spec.totalNeurons()).append("\n");
        sb.append("Generation: ").append(spec.generation()).append("\n");

        if (!fitnessHistory.isEmpty()) {
            sb.append("Fitness history: ");
            int start = Math.max(0, fitnessHistory.size() - 5);
            for (int i = start; i < fitnessHistory.size(); i++) {
                if (i > start) sb.append(" → ");
                sb.append(fitnessHistory.get(i));
            }
            sb.append("\n");
        }
        sb.append("\n");

        // S — Statement
        sb.append("TASK: Suggest exactly ONE mutation to improve ").append(targetMetric)
          .append(".\n");
        sb.append("Available mutation types: ADD_LAYER, REMOVE_LAYER, CHANGE_SIZE, ")
          .append("CHANGE_ACTIVATION, CHANGE_LAYER_TYPE\n\n");

        // P — Personality
        sb.append("APPROACH: Be precise and analytical. Consider the architecture's ")
          .append("current bottlenecks and suggest targeted improvements.\n\n");

        // E — Experiment
        sb.append("RESPONSE FORMAT (strict JSON):\n");
        sb.append("{\"mutation\": \"<TYPE>\", \"layer_index\": <N>, ")
          .append("\"parameter\": \"<value>\", \"reason\": \"<explanation>\"}\n");
        sb.append("Where <TYPE> is one of: ADD_LAYER, REMOVE_LAYER, CHANGE_SIZE, ")
          .append("CHANGE_ACTIVATION, CHANGE_LAYER_TYPE\n");
        sb.append("For ADD_LAYER: parameter is the layer spec (e.g., \"DENSE:16:RELU\")\n");
        sb.append("For CHANGE_SIZE: parameter is the new size (e.g., \"32\")\n");
        sb.append("For CHANGE_ACTIVATION: parameter is the activation (e.g., \"GELU\")\n");
        sb.append("For CHANGE_LAYER_TYPE: parameter is the type (e.g., \"ATTENTION\")\n");

        return sb.toString();
    }

    /**
     * Generates a simple mutation prompt (without full CRISPE framework).
     */
    public String generateSimplePrompt(ArchitectureSpec spec) {
        return "Given this neural architecture:\n"
                + spec.toPromptString()
                + "\nSuggest one improvement as JSON: "
                + "{\"mutation\": \"<TYPE>\", \"layer_index\": <N>, "
                + "\"parameter\": \"<value>\"}";
    }

    // ─── Response Parsing ───

    /**
     * Parses an LLM response into a MutationResult.
     *
     * <p>Attempts to extract a JSON mutation specification from the response.
     * Returns {@link MutationResult.NoOp} if parsing fails.
     *
     * @param response raw LLM response text
     * @return parsed mutation or NoOp
     */
    public MutationResult parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return new MutationResult.NoOp();
        }

        try {
            return parseJsonMutation(response);
        } catch (Exception e) {
            log.debug("Failed to parse LLM response as mutation: {}", e.getMessage());
            return new MutationResult.NoOp();
        }
    }

    private MutationResult parseJsonMutation(String response) {
        // Extract JSON from response (may be embedded in markdown code blocks)
        String json = extractJson(response);
        if (json == null) {
            return new MutationResult.NoOp();
        }

        String mutationType = extractField(json, "mutation");
        String layerIndexStr = extractField(json, "layer_index");
        String parameter = extractField(json, "parameter");

        if (mutationType == null) {
            return new MutationResult.NoOp();
        }

        int layerIndex = layerIndexStr != null ? Integer.parseInt(layerIndexStr.trim()) : 0;

        return switch (mutationType.toUpperCase().replace("\"", "").trim()) {
            case "ADD_LAYER" -> parseAddLayer(parameter, layerIndex);
            case "REMOVE_LAYER" -> new MutationResult.RemoveLayer(layerIndex);
            case "CHANGE_SIZE" -> parseChangeSize(parameter, layerIndex);
            case "CHANGE_ACTIVATION" -> parseChangeActivation(parameter, layerIndex);
            case "CHANGE_LAYER_TYPE" -> parseChangeLayerType(parameter, layerIndex);
            default -> new MutationResult.NoOp();
        };
    }

    private MutationResult parseAddLayer(String parameter, int index) {
        if (parameter == null) {
            return new MutationResult.AddLayer(
                    new LayerSpec(LayerType.DENSE, 8, Activation.RELU, index), index);
        }
        String clean = parameter.replace("\"", "").trim();
        String[] parts = clean.split(":");
        LayerType type = parts.length > 0 ? parseEnum(LayerType.class, parts[0], LayerType.DENSE) : LayerType.DENSE;
        int size = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 8;
        Activation act = parts.length > 2 ? parseEnum(Activation.class, parts[2], Activation.RELU) : Activation.RELU;
        return new MutationResult.AddLayer(new LayerSpec(type, size, act, index), index);
    }

    private MutationResult parseChangeSize(String parameter, int index) {
        if (parameter == null) return new MutationResult.NoOp();
        int newSize = Integer.parseInt(parameter.replace("\"", "").trim());
        return new MutationResult.ChangeSize(index, newSize);
    }

    private MutationResult parseChangeActivation(String parameter, int index) {
        if (parameter == null) return new MutationResult.NoOp();
        Activation act = parseEnum(Activation.class, parameter.replace("\"", "").trim(), Activation.RELU);
        return new MutationResult.ChangeActivation(index, act);
    }

    private MutationResult parseChangeLayerType(String parameter, int index) {
        if (parameter == null) return new MutationResult.NoOp();
        LayerType type = parseEnum(LayerType.class, parameter.replace("\"", "").trim(), LayerType.DENSE);
        return new MutationResult.ChangeLayerType(index, type);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> cls, String value, E fallback) {
        try {
            return Enum.valueOf(cls, value.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static String extractJson(String response) {
        // Try to find JSON in code blocks first
        int codeStart = response.indexOf("```json");
        if (codeStart >= 0) {
            int jsonStart = response.indexOf('{', codeStart);
            int jsonEnd = response.indexOf("```", jsonStart);
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                return response.substring(jsonStart, jsonEnd);
            }
        }
        // Try to find raw JSON object
        int braceStart = response.indexOf('{');
        int braceEnd = response.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return response.substring(braceStart, braceEnd + 1);
        }
        return null;
    }

    private static String extractField(String json, String fieldName) {
        String search = "\"" + fieldName + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colonIdx = json.indexOf(':', idx + search.length());
        if (colonIdx < 0) return null;
        int valueStart = colonIdx + 1;
        // Skip whitespace
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= json.length()) return null;

        char first = json.charAt(valueStart);
        if (first == '"') {
            // String value
            int valueEnd = json.indexOf('"', valueStart + 1);
            if (valueEnd < 0) return null;
            return json.substring(valueStart + 1, valueEnd);
        } else {
            // Numeric value
            int valueEnd = valueStart;
            while (valueEnd < json.length() && (Character.isDigit(json.charAt(valueEnd)) || json.charAt(valueEnd) == '-')) {
                valueEnd++;
            }
            return json.substring(valueStart, valueEnd);
        }
    }

    // ─── Async Optimization ───

    /**
     * Asks the LLM for an architecture improvement suggestion.
     *
     * @param spec           current architecture
     * @param fitnessHistory recent fitness values
     * @param targetMetric   metric to optimize
     * @return future with the parsed mutation result
     */
    public CompletableFuture<MutationResult> suggestMutation(ArchitectureSpec spec,
                                                              List<Long> fitnessHistory,
                                                              String targetMetric) {
        String prompt = generateCrispePrompt(spec, fitnessHistory, targetMetric);
        return llmBackend.apply(prompt)
                .thenApply(this::parseResponse)
                .exceptionally(ex -> {
                    log.warn("LLM suggestion failed, using random mutation: {}", ex.getMessage());
                    return mutationOperator.randomMutate(spec);
                });
    }

    /**
     * Synchronous version of {@link #suggestMutation}.
     */
    public MutationResult suggestMutationSync(ArchitectureSpec spec,
                                               List<Long> fitnessHistory,
                                               String targetMetric) {
        try {
            return suggestMutation(spec, fitnessHistory, targetMetric).join();
        } catch (Exception e) {
            log.warn("LLM sync suggestion failed: {}", e.getMessage());
            return mutationOperator.randomMutate(spec);
        }
    }

    // ─── Default Backend ───

    private static Function<String, CompletableFuture<String>> createDefaultBackend() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        String endpoint = "https://api-inference.huggingface.co/models/microsoft/Phi-3-mini-4k-instruct";
        String apiKey = System.getenv().getOrDefault("HF_API_KEY", "");

        return prompt -> {
            try {
                String body = "{\"inputs\":\"" + escapeJson(prompt)
                        + "\",\"parameters\":{\"max_new_tokens\":300,\"temperature\":0.7}}";
                var builder = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .timeout(Duration.ofSeconds(30));
                if (!apiKey.isEmpty()) {
                    builder.header("Authorization", "Bearer " + apiKey);
                }
                return client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                        .thenApply(resp -> {
                            if (resp.statusCode() == 200) {
                                return parseHuggingFaceResponse(resp.body());
                            }
                            return "";
                        });
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        };
    }

    private static String parseHuggingFaceResponse(String body) {
        // Simple extraction: find "generated_text" field
        int idx = body.indexOf("\"generated_text\"");
        if (idx < 0) return body;
        int colonIdx = body.indexOf(':', idx);
        if (colonIdx < 0) return body;
        int start = body.indexOf('"', colonIdx + 1);
        if (start < 0) return body;
        int end = body.indexOf('"', start + 1);
        if (end < 0) return body;
        return body.substring(start + 1, end);
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ─── Accessors ───

    public MutationOperator mutationOperator() {
        return mutationOperator;
    }
}
