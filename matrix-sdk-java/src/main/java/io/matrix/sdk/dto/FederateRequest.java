package io.matrix.sdk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FederateRequest(
    @JsonProperty("region") String region,
    @JsonProperty("shard_capacity") Integer shardCapacity
) {}
