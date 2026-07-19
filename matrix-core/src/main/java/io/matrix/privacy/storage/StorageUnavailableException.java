package io.matrix.privacy.storage;

/**
 * Thrown when a {@link TombstoneStorage} backend is unreachable, misconfigured,
 * or rejects a write. Mirrors the same shape across all backend implementations
 * (in-memory, Postgres, Kafka, S3, composite) so callers handle a single
 * exception type.
 */
public class StorageUnavailableException extends RuntimeException {
    public StorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageUnavailableException(String message) {
        super(message);
    }
}
