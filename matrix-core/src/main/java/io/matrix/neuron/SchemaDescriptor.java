package io.matrix.neuron;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Structural schema constraints for {@link TruthTable} output validation.
 *
 * <p>Implements the "SchemaDescriptor" pattern from Research Synthesis 2026 Q3 v2
 * (Phase A5 — BooleanSchemaValidator): attaches type-level and instance-level
 * constraints to truth tables, enabling structural output validation without
 * affecting the hot evaluate() path when no schema is present.
 *
 * <h3>Schema Types</h3>
 * <ul>
 *   <li>{@link SchemaType#SCALAR} — boolean output (default, no constraints)</li>
 *   <li>{@link SchemaType#ENUM} — table must match one of a known set of patterns</li>
 *   <li>{@link SchemaType#RANGE} — output cardinality bounded by min/max true count</li>
 *   <li>{@link SchemaType#VECTOR} — positional constraint (for multi-output layers)</li>
 * </ul>
 *
 * <p>Thread-safe: all fields are immutable after construction.
 */
public final class SchemaDescriptor {

    public enum SchemaType {
        /** Single boolean output, no schema-level constraints. */
        SCALAR,
        /** Output must match one of a known set of truth-table patterns. */
        ENUM,
        /** Output cardinality bounded by min/max true outputs. */
        RANGE,
        /** Positional constraint within a multi-output vector. */
        VECTOR
    }

    private final SchemaType type;
    private final Map<Integer, Boolean> testVectors;
    private final int minTrueCount;
    private final int maxTrueCount;
    private final Set<Long> allowedPatterns;
    private final boolean strict;
    private final int k;

    private SchemaDescriptor(Builder builder) {
        this.type = builder.type;
        this.k = builder.k;
        this.testVectors = Collections.unmodifiableMap(
                new HashMap<>(builder.testVectors));
        this.minTrueCount = builder.minTrueCount;
        this.maxTrueCount = builder.maxTrueCount == Integer.MAX_VALUE
                ? (1 << k) : builder.maxTrueCount;
        this.allowedPatterns = builder.allowedPatterns.isEmpty()
                ? Collections.emptySet()
                : Set.copyOf(builder.allowedPatterns);
        this.strict = builder.strict;
        validateInvariants();
    }

    private void validateInvariants() {
        if (k < 1 || k > TruthTable.K_MAX) {
            throw new IllegalArgumentException(
                    "k must be in [1, " + TruthTable.K_MAX + "], got: " + k);
        }
        int tableSize = 1 << k;
        if (minTrueCount < 0 || minTrueCount > tableSize) {
            throw new IllegalArgumentException(
                    "minTrueCount must be in [0, " + tableSize + "], got: " + minTrueCount);
        }
        if (maxTrueCount < 0 || maxTrueCount > tableSize) {
            throw new IllegalArgumentException(
                    "maxTrueCount must be in [0, " + tableSize + "], got: " + maxTrueCount);
        }
        if (minTrueCount > maxTrueCount) {
            throw new IllegalArgumentException(
                    "minTrueCount (" + minTrueCount + ") > maxTrueCount (" + maxTrueCount + ")");
        }
        for (var entry : testVectors.entrySet()) {
            int input = entry.getKey();
            if (input < 0 || input >= tableSize) {
                throw new IllegalArgumentException(
                        "test vector input " + input + " out of range [0, " + (tableSize - 1) + "]");
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────

    public SchemaType type() { return type; }
    public int k() { return k; }
    public Map<Integer, Boolean> testVectors() { return testVectors; }
    public int minTrueCount() { return minTrueCount; }
    public int maxTrueCount() { return maxTrueCount; }
    public Set<Long> allowedPatterns() { return allowedPatterns; }
    public boolean strict() { return strict; }

    /**
     * Validates a single evaluate output against the schema.
     *
     * @param output the boolean result of {@link TruthTable#evaluate}
     * @param input  the input index used for evaluation
     * @throws SchemaViolationException if strict and output violates a test vector
     * @return true if valid, false if non-strict mismatch
     */
    public boolean validateOutput(boolean output, int input) {
        Boolean expected = testVectors.get(input);
        if (expected != null && output != expected) {
            if (strict) {
                throw new SchemaViolationException(
                        "Test vector mismatch: input=" + input
                        + " expected=" + expected + " got=" + output);
            }
            return false;
        }
        return true;
    }

    /**
     * Validates an entire truth table against the schema.
     *
     * @param table the truth table to validate
     * @throws SchemaViolationException if strict and table violates constraints
     * @return true if valid, false if non-strict violation
     */
    public boolean validateTable(TruthTable table) {
        if (table.k() != k) {
            if (strict) {
                throw new SchemaViolationException(
                        "Table k=" + table.k() + " != schema k=" + k);
            }
            return false;
        }

        return switch (type) {
            case SCALAR -> validateScalar(table);
            case ENUM -> validateEnum(table);
            case RANGE -> validateRange(table);
            case VECTOR -> validateScalar(table); // vector validated externally
        };
    }

    private boolean validateScalar(TruthTable table) {
        // Check test vectors
        for (var entry : testVectors.entrySet()) {
            boolean actual = table.evaluate(entry.getKey());
            if (actual != entry.getValue()) {
                if (strict) {
                    throw new SchemaViolationException(
                            "Test vector mismatch at input " + entry.getKey()
                            + ": expected=" + entry.getValue() + " actual=" + actual);
                }
                return false;
            }
        }
        return true;
    }

    private boolean validateEnum(TruthTable table) {
        if (!allowedPatterns.isEmpty()) {
            long pattern = tableAsLong(table);
            if (!allowedPatterns.contains(pattern)) {
                if (strict) {
                    throw new SchemaViolationException(
                            "Table pattern 0x" + Long.toHexString(pattern)
                            + " not in allowed set");
                }
                return false;
            }
        }
        return validateScalar(table); // also check test vectors
    }

    private boolean validateRange(TruthTable table) {
        int trueCount = countTrue(table);
        if (trueCount < minTrueCount || trueCount > maxTrueCount) {
            if (strict) {
                throw new SchemaViolationException(
                        "True count " + trueCount + " not in ["
                        + minTrueCount + ", " + maxTrueCount + "]");
            }
            return false;
        }
        return validateScalar(table);
    }

    /**
     * Validates output at a specific vector position.
     * Used when this schema describes one position in a multi-output layer.
     */
    public boolean validateVectorPosition(boolean output, int position) {
        if (type != SchemaType.VECTOR) {
            return true;
        }
        return validateOutput(output, position);
    }

    // ── Utility ───────────────────────────────────────────────────────────

    private static long tableAsLong(TruthTable table) {
        long bits = 0L;
        int size = Math.min(table.size(), 64);
        for (int i = 0; i < size; i++) {
            if (table.evaluate(i)) {
                bits |= (1L << i);
            }
        }
        return bits;
    }

    private static int countTrue(TruthTable table) {
        int count = 0;
        int size = table.size();
        for (int i = 0; i < size; i++) {
            if (table.evaluate(i)) {
                count++;
            }
        }
        return count;
    }

    // ── equals / hashCode / toString ─────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SchemaDescriptor that)) return false;
        return type == that.type
                && k == that.k
                && minTrueCount == that.minTrueCount
                && maxTrueCount == that.maxTrueCount
                && strict == that.strict
                && testVectors.equals(that.testVectors)
                && Objects.equals(allowedPatterns, that.allowedPatterns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, k, testVectors, minTrueCount,
                maxTrueCount, allowedPatterns, strict);
    }

    @Override
    public String toString() {
        return "SchemaDescriptor{type=" + type + ", k=" + k
                + ", testVectors=" + testVectors.size()
                + ", range=[" + minTrueCount + "," + maxTrueCount + "]"
                + ", strict=" + strict + "}";
    }

    // ── Builder ───────────────────────────────────────────────────────────

    public static Builder builder(SchemaType type, int k) {
        return new Builder(type, k);
    }

    /**
     * Creates a simple SCALAR schema with no constraints.
     */
    public static SchemaDescriptor scalar(int k) {
        return builder(SchemaType.SCALAR, k).build();
    }

    /**
     * Creates an ENUM schema that validates against known patterns.
     */
    public static SchemaDescriptor enumPattern(int k, Set<Long> allowedPatterns) {
        return builder(SchemaType.ENUM, k)
                .allowedPatterns(allowedPatterns)
                .build();
    }

    /**
     * Creates a RANGE schema with cardinality constraints.
     */
    public static SchemaDescriptor range(int k, int minTrue, int maxTrue) {
        return builder(SchemaType.RANGE, k)
                .minTrueCount(minTrue)
                .maxTrueCount(maxTrue)
                .build();
    }

    public static final class Builder {
        private final SchemaType type;
        private final int k;
        private final Map<Integer, Boolean> testVectors = new HashMap<>();
        private int minTrueCount = 0;
        private int maxTrueCount = Integer.MAX_VALUE;
        private Set<Long> allowedPatterns = Set.of();
        private boolean strict = true;

        private Builder(SchemaType type, int k) {
            this.type = type;
            this.k = k;
        }

        /** Adds a test vector — input index → expected output. */
        public Builder testVector(int input, boolean expected) {
            testVectors.put(input, expected);
            return this;
        }

        /** Minimum number of {@code true} outputs in the entire table. */
        public Builder minTrueCount(int min) {
            this.minTrueCount = min;
            return this;
        }

        /** Maximum number of {@code true} outputs in the entire table. */
        public Builder maxTrueCount(int max) {
            this.maxTrueCount = max;
            return this;
        }

        /** Set of allowed truth table patterns (as long bitmasks for k ≤ 6). */
        public Builder allowedPatterns(Set<Long> patterns) {
            this.allowedPatterns = patterns;
            return this;
        }

        /** If true (default), violations throw {@link SchemaViolationException}. */
        public Builder strict(boolean s) {
            this.strict = s;
            return this;
        }

        public SchemaDescriptor build() {
            return new SchemaDescriptor(this);
        }
    }

    /**
     * Thrown when a strict schema constraint is violated.
     */
    public static final class SchemaViolationException extends RuntimeException {
        public SchemaViolationException(String message) {
            super(message);
        }
    }
}
