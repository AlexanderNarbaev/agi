package io.matrix.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * WAVE T-02 — Analyze response DTO.
 *
 * <p>Always includes an {@code explain_id} so the caller can retrieve the
 * XAI breakdown via {@code GET /v1/explain/{id}}.</p>
 */
public final class AnalyzeResponse {

    @JsonProperty("reply")
    public String reply;

    @JsonProperty("confidence")
    public double confidence;

    @JsonProperty("duration_ms")
    public long durationMs;

    @JsonProperty("accepted")
    public boolean accepted;

    @JsonProperty("explain_id")
    public String explainId;

    @JsonProperty("modulators_fired")
    public List<String> modulatorsFired;

    public AnalyzeResponse() {}

    public AnalyzeResponse(String reply, double confidence, long durationMs,
                           boolean accepted, String explainId) {
        this.reply = reply;
        this.confidence = confidence;
        this.durationMs = durationMs;
        this.accepted = accepted;
        this.explainId = explainId;
    }
}
