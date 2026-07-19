package io.matrix.imports;

import java.nio.file.Path;

/**
 * Abstraction over a source that can provide pre-trained model weights.
 *
 * <p>Implementations download ONLY the {@code *.safetensors} / {@code *.gguf}
 * weight files (not the full model with optimizer state / tokenizer artifacts).
 * The downloaded tensors are then projected into MPDT-neuron TruthTables
 * and merged into the Matrix knowledge pool via {@link WeightImporter}.
 *
 * <p>Ref: L24_WeightImport.md (proposed); enables Universal Knowledge Distillation —
 * one Matrix instance absorbing weights from many HF models as a single FNL pool.
 */
public interface WeightSource {

    /**
     * Resolves a remote model identifier (e.g. {@code "Qwen/Qwen2.5-1.5B-Instruct"})
     * into a local cached directory ready for tensor parsing.
     *
     * @param modelId HuggingFace-style {@code owner/name} identifier
     * @param target  directory under which the cache subdirectory is created (created if absent)
     * @return metadata describing what was downloaded and how much disk it occupies
     * @throws WeightSourceException if the source is unreachable or the model is unavailable
     */
    DownloadResult download(String modelId, Path target);

    /**
     * Probes the source for a model's existence and (best-effort) on-disk size,
     * without actually downloading anything.
     *
     * @param modelId remote identifier
     * @return probe result with estimated total weight bytes; {@code null} if unknown
     */
    ProbeResult probe(String modelId);

    /** Returns a stable identifier for this source (e.g. {@code "huggingface"}). */
    String sourceId();

    /**
     * Result of a download operation.
     *
     * @param modelId         remote identifier
     * @param cachedDirectory local directory containing the downloaded weight shards
     * @param files           individual files that were fetched
     * @param totalBytes      sum of file sizes actually written
     * @param sha256          aggregate hash of all downloaded files (hex)
     */
    record DownloadResult(
            String modelId,
            Path cachedDirectory,
            java.util.List<Path> files,
            long totalBytes,
            String sha256) {

        public int fileCount() { return files.size(); }
    }

    /**
     * Best-effort probe without downloading.
     *
     * @param modelId         remote identifier
     * @param availableOnSource {@code true} if the model page exists
     * @param estimatedTotalBytes  sum of weight-file sizes reported by the source
     */
    record ProbeResult(String modelId, boolean availableOnSource, long estimatedTotalBytes) {}

    /** Thrown when a {@link WeightSource} cannot satisfy a request. */
    final class WeightSourceException extends RuntimeException {
        public WeightSourceException(String message) { super(message); }
        public WeightSourceException(String message, Throwable cause) { super(message, cause); }
    }
}
