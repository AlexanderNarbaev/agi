package io.matrix.sdk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Subscription plan tier. */
public enum Plan {
    @JsonProperty("FREE") FREE,
    @JsonProperty("PRO") PRO,
    @JsonProperty("ENTERPRISE") ENTERPRISE;

    public int maxRequestsPerHour() {
        return switch (this) {
            case FREE -> 100;
            case PRO -> 1_000;
            case ENTERPRISE -> Integer.MAX_VALUE;
        };
    }
}
