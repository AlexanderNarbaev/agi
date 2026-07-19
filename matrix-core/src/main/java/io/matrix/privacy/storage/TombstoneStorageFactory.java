package io.matrix.privacy.storage;

import io.matrix.privacy.TombstoneService;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory that wires up a {@link TombstoneStorage} from environment
 * variables or system properties.
 *
 * <p>Configuration:
 * <ul>
 *   <li>{@code matrix.tombstone.backend=memory|postgres|kafka|s3|composite}
 *       — single backend or composite (comma-separated for fan-out).</li>
 *   <li>For postgres: {@code matrix.tombstone.postgres.url} etc. (Quarkus auto-configures a {@code DataSource}).</li>
 *   <li>For kafka: {@code matrix.tombstone.kafka.bootstrap} (default {@code localhost:9092}),
 *       {@code matrix.tombstone.kafka.topic} (default {@code matrix.tombstones}).</li>
 *   <li>For s3: {@code matrix.tombstone.s3.endpoint},
 *       {@code matrix.tombstone.s3.region}, {@code matrix.tombstone.s3.bucket},
 *       {@code matrix.tombstone.s3.access-key}, {@code matrix.tombstone.s3.secret-key}.</li>
 * </ul>
 *
 * <p>Examples:
 * <pre>
 *   # dev / test
 *   MATRIX_TOMBSTONE_BACKEND=memory
 *
 *   # staging (single backend)
 *   MATRIX_TOMBSTONE_BACKEND=postgres
 *
 *   # production (fan-out to Kafka audit + PG query + S3 archive)
 *   MATRIX_TOMBSTONE_BACKEND=composite
 *   MATRIX_TOMBSTONE_COMPOSITE_BACKENDS=kafka,postgres,s3
 *   MATRIX_TOMBSTONE_KAFKA_BOOTSTRAP=kafka:9092
 *   MATRIX_TOMBSTONE_S3_BUCKET=matrix-audit-eu-west-1
 * </pre>
 */
public final class TombstoneStorageFactory {

    private TombstoneStorageFactory() {}

    /** Build a single-backend storage by name. */
    public static TombstoneStorage build(String backendName, StorageContext ctx) {
        if (backendName == null || backendName.isBlank()) {
            return new InMemoryTombstoneStorage();
        }
        String name = backendName.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (name) {
            case "memory", "in-memory", "inmemory" -> new InMemoryTombstoneStorage();
            case "postgres", "pg" -> {
                if (ctx.dataSource() == null) {
                    throw new IllegalStateException("Postgres backend requires a DataSource");
                }
                yield new PostgresTombstoneStorage(ctx.dataSource());
            }
            case "kafka" -> new KafkaTombstoneStorage(
                    ctx.kafkaBootstrap(), ctx.kafkaTopic());
            case "s3" -> {
                if (ctx.s3Endpoint() == null) {
                    throw new IllegalStateException("S3 backend requires an endpoint");
                }
                yield new S3TombstoneStorage(ctx.s3Endpoint(), ctx.s3Region(),
                        ctx.s3Bucket(), ctx.s3AccessKey(), ctx.s3SecretKey());
            }
            default -> throw new IllegalArgumentException("Unknown backend: " + backendName);
        };
    }

    /** Build a composite backend from a comma-separated list. */
    public static TombstoneStorage buildComposite(String backends, StorageContext ctx) {
        List<TombstoneStorage> list = new ArrayList<>();
        for (String b : backends.split(",")) {
            list.add(build(b.trim(), ctx));
        }
        return new CompositeTombstoneStorage(list);
    }

    /** Auto-detect backend from system properties (or default to memory). */
    public static TombstoneStorage autoBuild(StorageContext ctx) {
        String prop = System.getProperty("matrix.tombstone.backend",
                System.getenv().getOrDefault("MATRIX_TOMBSTONE_BACKEND", "memory"));
        if ("composite".equalsIgnoreCase(prop)) {
            String list = System.getProperty("matrix.tombstone.composite.backends",
                    System.getenv().getOrDefault("MATRIX_TOMBSTONE_COMPOSITE_BACKENDS", "memory"));
            return buildComposite(list, ctx);
        }
        return build(prop, ctx);
    }

    /**
     * Carries all the per-backend configuration in a single value object.
     * Callers typically fill this from Quarkus {@code @ConfigMapping}.
     */
    public record StorageContext(
            javax.sql.DataSource dataSource,
            String kafkaBootstrap,
            String kafkaTopic,
            String s3Endpoint,
            String s3Region,
            String s3Bucket,
            String s3AccessKey,
            String s3SecretKey) {

        public static StorageContext empty() {
            return new StorageContext(null, "localhost:9092", "matrix.tombstones",
                    null, "us-east-1", "tombstones", null, null);
        }
    }
}
