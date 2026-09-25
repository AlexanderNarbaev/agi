package io.matrix.sdk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExplainStep(
    @JsonProperty("stage") String stage,
    @JsonProperty("action") String action,
    @JsonProperty("duration_ms") long durationMs
) {}
