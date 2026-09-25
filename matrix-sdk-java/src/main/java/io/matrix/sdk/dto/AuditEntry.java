package io.matrix.sdk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuditEntry(
    @JsonProperty("timestamp") String timestamp,
    @JsonProperty("user_id") String userId,
    @JsonProperty("action") String action,
    @JsonProperty("target") String target,
    @JsonProperty("status_code") int statusCode
) {}
