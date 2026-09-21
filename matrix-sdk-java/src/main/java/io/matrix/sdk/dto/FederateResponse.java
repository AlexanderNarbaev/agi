package io.matrix.sdk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FederateResponse(
    @JsonProperty("node_id") String nodeId,
    @JsonProperty("region") String region,
    @JsonProperty("shard_capacity") int shardCapacity,
    @JsonProperty("joined_at") String joinedAt,
    @JsonProperty("total_nodes") int totalNodes
) {}
