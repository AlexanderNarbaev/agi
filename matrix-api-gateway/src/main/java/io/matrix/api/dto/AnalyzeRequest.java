package io.matrix.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * WAVE T-02 — Analyze request DTO.
 *
 * <p>Accepts text, base64 audio, or base64 image input for hybrid inference.</p>
 *
 * <p><b>OWASP protection:</b> {@code @Size} limits prevent payload exhaustion.
 * Inputs are validated and sanitized in {@code InputValidator}.</p>
 */
public final class AnalyzeRequest {

    /** Optional content type hint. Default: text. */
    @JsonProperty("content_type")
    public String contentType = "text";

    /** Required textual or base64-encoded payload. */
    @NotBlank
    @Size(max = 1_048_576)  // 1 MiB hard limit
    @JsonProperty("input")
    public String input;

    /** Optional context to refine the analysis (max 64 KiB). */
    @Size(max = 65_536)
    @JsonProperty("context")
    public String context;

    /** Optional model selector. Default: default. */
    @JsonProperty("model")
    public String model = "default";

    public AnalyzeRequest() {}

    public AnalyzeRequest(String input) {
        this.input = input;
    }
}
