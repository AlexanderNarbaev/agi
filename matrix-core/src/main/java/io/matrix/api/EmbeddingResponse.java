package io.matrix.api;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible embeddings response DTO.
 *
 * <p>Mirrors the exact structure that OpenAI clients expect:
 * {@code {"object": "list", "data": [{"object": "embedding", "embedding": [...], "index": 0}],
 * "model": "...", "usage": {...}}}
 */
@RegisterForReflection
public class EmbeddingResponse {

    /** Always "list" per OpenAI spec. */
    public String object = "list";

    /** List of embedding data objects. */
    public List<Data> data;

    /** Model used for this embedding. */
    public String model;

    /** Token usage statistics. */
    public Usage usage;

    public EmbeddingResponse() {}

    /** A single embedding vector. */
    @RegisterForReflection
    public static class Data {
        /** Always "embedding" per OpenAI spec. */
        public String object = "embedding";

        /** The embedding vector (20 floats). */
        public List<Float> embedding;

        /** Index of this embedding in the batch. */
        public int index;

        public Data() {}
    }

    /** Token usage tracking. */
    @RegisterForReflection
    public static class Usage {
        /** Number of tokens in the input. */
        public int prompt_tokens;

        /** Total tokens used. */
        public int total_tokens;

        public Usage() {}
    }

    // ─── Factory ───

    /** Creates an embedding response from a float vector. */
    public static EmbeddingResponse of(List<Float> embedding, String model) {
        EmbeddingResponse resp = new EmbeddingResponse();
        resp.model = model;

        Data data = new Data();
        data.embedding = embedding;
        data.index = 0;
        resp.data = List.of(data);

        Usage usage = new Usage();
        usage.prompt_tokens = embedding.size();
        usage.total_tokens = embedding.size();
        resp.usage = usage;

        return resp;
    }
}
