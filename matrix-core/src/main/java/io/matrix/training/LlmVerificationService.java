package io.matrix.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * LLM Verification Service - verifies MATRIX responses against free LLM models.
 * 
 * Uses HuggingFace Inference API (free tier).
 */
public class LlmVerificationService {
    
    private static final Logger log = LoggerFactory.getLogger(LlmVerificationService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    
    private final HttpClient httpClient;
    private final String apiEndpoint;
    private final String apiKey;
    
    // Free models to try in order
    private static final String[] MODELS = {
        "microsoft/Phi-3-mini-4k-instruct",
        "Qwen/Qwen2-0.5B-Instruct",
        "HuggingFaceTB/SmolLM2-135M-Instruct"
    };
    
    public LlmVerificationService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.apiEndpoint = "https://api-inference.huggingface.co/models";
        this.apiKey = System.getenv().getOrDefault("HF_API_KEY", "");
    }
    
    /**
     * Calls a free LLM for verification.
     */
    public CompletableFuture<String> verify(String prompt) {
        for (String model : MODELS) {
            try {
                String response = callModel(model, prompt);
                if (response != null && !response.isBlank()) {
                    return CompletableFuture.completedFuture(response);
                }
            } catch (Exception e) {
                log.debug("Model {} failed: {}", model, e.getMessage());
            }
        }
        return CompletableFuture.completedFuture(null);
    }
    
    private String callModel(String model, String prompt) throws Exception {
        String url = apiEndpoint + "/" + model;
        
        // Use Jackson for safe JSON serialization
        ObjectNode body = JSON.createObjectNode();
        body.put("inputs", prompt);
        ObjectNode params = body.putObject("parameters");
        params.put("max_new_tokens", 100);
        params.put("temperature", 0.7);
        params.put("do_sample", true);
        
        String bodyStr = JSON.writeValueAsString(body);
        
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(bodyStr))
            .timeout(Duration.ofSeconds(30));
        
        // Add auth header if API key is set
        if (!apiKey.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }
        
        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return parseResponse(response.body());
        } else if (response.statusCode() == 503) {
            log.debug("Model {} is loading, retrying...", model);
            // Model is loading, try next
            return null;
        } else {
            log.warn("HuggingFace API returned {} for model {}: {}", response.statusCode(), model, response.body());
            return null;
        }
    }
    
    private String parseResponse(String responseBody) {
        try {
            JsonNode root = JSON.readTree(responseBody);
            
            // Handle array format: [{"generated_text": "..."}]
            if (root.isArray() && root.size() > 0) {
                JsonNode first = root.get(0);
                if (first.has("generated_text")) {
                    return first.get("generated_text").asText();
                }
            }
            
            // Handle object format: {"generated_text": "..."}
            if (root.has("generated_text")) {
                return root.get("generated_text").asText();
            }
            
            log.warn("Unexpected response format: {}", responseBody.substring(0, Math.min(200, responseBody.length())));
            return null;
        } catch (Exception e) {
            log.error("Failed to parse HuggingFace response: {}", e.getMessage());
            return null;
        }
    }
}
