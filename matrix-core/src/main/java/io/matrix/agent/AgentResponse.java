package io.matrix.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Observable Agent Response — every agent response carries a request_id,
 * timing breakdown, and retrievable sources for audit/explainability.
 *
 * <p>Implements the "Observable Agent Response" pattern from research.
 */
public record AgentResponse(
        UUID requestId,
        String answer,
        List<SourceInfo> sources,
        TimingInfo timings,
        long durationMs
) {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public AgentResponse {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(answer, "answer");
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        Objects.requireNonNull(timings, "timings");
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must be >= 0");
        }
    }

    /**
     * A source document chunk with its retrieval score.
     */
    public record SourceInfo(
            String document,
            String path,
            int chunkIndex,
            double score,
            String chunk
    ) {
        public SourceInfo {
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(path, "path");
            if (chunkIndex < 0) throw new IllegalArgumentException("chunkIndex must be >= 0");
            Objects.requireNonNull(chunk, "chunk");
        }
    }

    /**
     * Phase-level timing breakdown (all in milliseconds).
     */
    public record TimingInfo(
            long retrievalMs,
            long filteringMs,
            long reasoningMs,
            long generationMs,
            long totalMs
    ) {
        public TimingInfo {
            if (retrievalMs < 0 || filteringMs < 0 || reasoningMs < 0
                    || generationMs < 0 || totalMs < 0) {
                throw new IllegalArgumentException("timing values must be >= 0");
            }
        }

        /** Sum of the four phase timings. */
        public long sumOfPhases() {
            return retrievalMs + filteringMs + reasoningMs + generationMs;
        }
    }

    // ── JSON ──

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize AgentResponse", e);
        }
    }

    public static AgentResponse fromJson(String json) {
        try {
            return MAPPER.readValue(json, AgentResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize AgentResponse", e);
        }
    }

    // ── Builder ──

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID requestId = UUID.randomUUID();
        private String answer = "";
        private final List<SourceInfo> sources = new ArrayList<>();
        private long retrievalMs;
        private long filteringMs;
        private long reasoningMs;
        private long generationMs;

        private long startNanos;
        private long totalMs;

        /** Override auto-generated UUID. */
        public Builder requestId(UUID id) {
            this.requestId = Objects.requireNonNull(id, "id");
            return this;
        }

        public Builder answer(String answer) {
            this.answer = Objects.requireNonNull(answer, "answer");
            return this;
        }

        public Builder addSource(SourceInfo source) {
            sources.add(Objects.requireNonNull(source, "source"));
            return this;
        }

        public Builder addSource(String document, String path, int chunkIndex,
                                 double score, String chunk) {
            return addSource(new SourceInfo(document, path, chunkIndex, score, chunk));
        }

        /** Start the wall-clock timer (nanoTime-based). */
        public Builder startTiming() {
            this.startNanos = System.nanoTime();
            return this;
        }

        /** Record a named phase with elapsed time in milliseconds. */
        public Builder recordPhase(String name, long elapsedMs) {
            Objects.requireNonNull(name, "name");
            if (elapsedMs < 0) throw new IllegalArgumentException("elapsedMs must be >= 0");
            switch (name.toLowerCase()) {
                case "retrieval"  -> retrievalMs   = elapsedMs;
                case "filtering"  -> filteringMs    = elapsedMs;
                case "reasoning"  -> reasoningMs    = elapsedMs;
                case "generation" -> generationMs   = elapsedMs;
                default -> throw new IllegalArgumentException("Unknown phase: " + name
                        + ". Expected: retrieval, filtering, reasoning, generation");
            }
            return this;
        }

        /** Finish timing and build the response. */
        public AgentResponse finish() {
            if (startNanos == 0) {
                throw new IllegalStateException("startTiming() must be called before finish()");
            }
            totalMs = (System.nanoTime() - startNanos) / 1_000_000;
            AgentResponse response = new AgentResponse(
                    requestId,
                    answer.isEmpty() ? "" : answer,
                    sources,
                    new TimingInfo(retrievalMs, filteringMs, reasoningMs, generationMs, totalMs),
                    totalMs
            );
            return response;
        }

        /**
         * Build without wall-clock timing — uses the provided totalMs directly.
         * Useful for replay or deserialized data.
         */
        public AgentResponse build(long totalMs) {
            if (totalMs < 0) throw new IllegalArgumentException("totalMs must be >= 0");
            this.totalMs = totalMs;
            return new AgentResponse(
                    requestId,
                    answer.isEmpty() ? "" : answer,
                    sources,
                    new TimingInfo(retrievalMs, filteringMs, reasoningMs, generationMs, totalMs),
                    totalMs
            );
        }
    }
}
