package io.matrix.sdk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class ExplainResponse {
    @JsonProperty("explain_id") public String explainId;
    @JsonProperty("steps") public List<ExplainStep> steps;
    @JsonProperty("modulator_snapshot") public ModulatorState modulatorSnapshot;
    @JsonProperty("hdc_memory_hits") public List<String> hdcMemoryHits;
    @JsonProperty("confidence_breakdown") public ConfidenceBreakdown confidenceBreakdown;
}
