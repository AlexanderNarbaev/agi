package io.matrix.training;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * LLM Verification Service - verifies MATRIX responses against free LLM models.
 * 
 * Uses HuggingFace Inference API (free tier) or local Ollama.
 */
public class LlmVerificationService {
    
    private final HttpClient httpClient;
    private final String apiEndpoint;
    private final String apiKey;
    
    public LlmVerificationService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        // Use HuggingFace free inference API
        this.apiEndpoint = "https://api-inference.huggingface.co/models";
        this.apiKey = System.getenv().getOrDefault("HF_API_KEY", "");
    }
    
    /**
     * Calls a free LLM for verification.
     */
    public CompletableFuture<String> verify(String prompt) {
        // Try multiple free models
        String[] models = {
            "microsoft/Phi-3-mini-4k-instruct",
            "Qwen/Qwen2-0.5B-Instruct",
            "HuggingFaceTB/SmolLM2-135M-Instruct"
        };
        
        for (String model : models) {
            try {
                String response = callModel(model, prompt);
                if (response != null && !response.isBlank()) {
                    return CompletableFuture.completedFuture(response);
                }
            } catch (Exception e) {
                // Try next model
            }
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    private String callModel(String model, String prompt) throws Exception {
        String url = apiEndpoint + "/" + model;
        
        String body = """
            {
                "inputs": "%s",
                "parameters": {
                    "max_new_tokens": 100,
                    "temperature": 0.7,
                    "do_sample": true
                }
            }
            """.formatted(prompt.replace("\"", "\\\""));
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(30))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            // Parse response
            String responseBody = response.body();
            // Simple extraction - get generated text
            if (responseBody.contains("\"generated_text\":")) {
                int start = responseBody.indexOf("\"generated_text\":") + 18;
                int end = responseBody.indexOf("\"", start);
                return responseBody.substring(start, end);
            }
        }
        
        return null;
    }
}
