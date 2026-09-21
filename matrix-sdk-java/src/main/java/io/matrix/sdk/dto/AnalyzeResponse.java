package io.matrix.sdk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class AnalyzeResponse {
    @JsonProperty("reply") public String reply;
    @JsonProperty("confidence") public double confidence;
    @JsonProperty("duration_ms") public long durationMs;
    @JsonProperty("accepted") public boolean accepted;
    @JsonProperty("explain_id") public String explainId;
    @JsonProperty("modulators_fired") public List<String> modulatorsFired;
}
