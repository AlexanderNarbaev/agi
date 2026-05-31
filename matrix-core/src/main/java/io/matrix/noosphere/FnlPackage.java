package io.matrix.noosphere;

import java.util.UUID;

/**
 * Metadata package for a Functional Neural Lobe (FNL) published to the Noosphere.
 *
 * <p>Contains type, version, accuracy, author, and certification status.
 *
 * <p>Ref: L6_Memory.md §5.2, §6.6
 */
public record FnlPackage(
        UUID id,
        String name,
        String type,
        String version,
        String authorInstanceId,
        double accuracy,
        int generation,
        String description,
        String[] tags,
        boolean certified,
        long publishedAt,
        String snapshotHash
) {
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private UUID id = UUID.randomUUID();
        private String name;
        private String type = "GENERIC";
        private String version = "1.0.0";
        private String authorInstanceId;
        private double accuracy;
        private int generation;
        private String description = "";
        private String[] tags = new String[0];
        private boolean certified;
        private long publishedAt = System.currentTimeMillis();
        private String snapshotHash;

        public Builder name(String v) { name = v; return this; }
        public Builder type(String v) { type = v; return this; }
        public Builder version(String v) { version = v; return this; }
        public Builder authorInstanceId(String v) { authorInstanceId = v; return this; }
        public Builder accuracy(double v) { accuracy = v; return this; }
        public Builder generation(int v) { generation = v; return this; }
        public Builder description(String v) { description = v; return this; }
        public Builder tags(String... v) { tags = v; return this; }
        public Builder certified(boolean v) { certified = v; return this; }
        public Builder snapshotHash(String v) { snapshotHash = v; return this; }

        public FnlPackage build() {
            return new FnlPackage(id, name, type, version, authorInstanceId,
                    accuracy, generation, description, tags, certified,
                    publishedAt, snapshotHash);
        }
    }
}
