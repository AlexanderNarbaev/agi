package io.matrix.sdk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ModulatorState(
    @JsonProperty("ethical_filter") double ethicalFilter,
    @JsonProperty("safety_monitor") double safetyMonitor,
    @JsonProperty("consistency_checker") double consistencyChecker,
    @JsonProperty("lie_detector") double lieDetector
) {}
