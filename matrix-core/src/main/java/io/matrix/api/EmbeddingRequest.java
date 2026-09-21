package io.matrix.api;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * OpenAI-compatible embeddings request DTO.
 *
 * <p>Supports single string input. The model field is parsed for
 * compatibility but the MATRIX embeddings endpoint always maps
 * through Text2Vec: input → 20-bit vector → float embedding.
 */
@RegisterForReflection
public class EmbeddingRequest {

    /** Model identifier (e.g. "mpdt-smollm2"). */
    public String model;

    /** Input text to embed. */
    public String input;

    /** Ignored — MATRIX embeddings are deterministic. */
    public String encoding_format;

    public EmbeddingRequest() {}
}
