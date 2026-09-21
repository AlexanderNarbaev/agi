package io.matrix.sdk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ConfidenceBreakdown(
    @JsonProperty("bir_confidence") double birConfidence,
    @JsonProperty("hdc_confidence") double hdcConfidence,
    @JsonProperty("mcts_confidence") double mctsConfidence,
    @JsonProperty("aggregate") double aggregate
) {}
