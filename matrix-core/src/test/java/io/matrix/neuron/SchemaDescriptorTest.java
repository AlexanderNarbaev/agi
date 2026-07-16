package io.matrix.neuron;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.BitSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SchemaDescriptor} and its integration with {@link TruthTable}.
 *
 * <p>Coverage: schema creation, test vectors, enum patterns, range constraints,
 * strict/non-strict modes, edge cases, SchemaViolationException.
 */
class SchemaDescriptorTest {

    // ── SCALAR schema ────────────────────────────────────────────────────

    @Test
    void scalarSchemaShouldAcceptAnyOutput() {
        SchemaDescriptor schema = SchemaDescriptor.scalar(4);
        assertThat(schema.type()).isEqualTo(SchemaDescriptor.SchemaType.SCALAR);
        assertThat(schema.k()).isEqualTo(4);
        assertThat(schema.testVectors()).isEmpty();
        assertThat(schema.validateOutput(true, 0)).isTrue();
        assertThat(schema.validateOutput(false, 15)).isTrue();
    }

    @Test
    void scalarSchemaShouldAcceptTable() {
        TruthTable tt = TruthTable.random(2);
        SchemaDescriptor schema = SchemaDescriptor.scalar(2);
        assertThat(schema.validateTable(tt)).isTrue();
    }

    // ── Test Vectors ──────────────────────────────────────────────────────

    @Test
    void testVectorShouldValidateCorrectOutput() {
        SchemaDescriptor schema = SchemaDescriptor.builder(
                        SchemaDescriptor.SchemaType.SCALAR, 3)
                .testVector(0, false)
                .testVector(7, true)
                .build();

        assertThat(schema.validateOutput(false, 0)).isTrue();
        assertThat(schema.validateOutput(true, 7)).isTrue();
    }

    @Test
    void testVectorShouldDetectViolation() {
        SchemaDescriptor schema = SchemaDescriptor.builder(
                        SchemaDescriptor.SchemaType.SCALAR, 3)
                .testVector(5, true)
                .strict(false)
                .build();

        // Non-strict: returns false but doesn't throw
        assertThat(schema.validateOutput(false, 5)).isFalse();
    }

    @Test
    void strictTestVectorShouldThrowOnViolation() {
        SchemaDescriptor schema = SchemaDescriptor.builder(
                        SchemaDescriptor.SchemaType.SCALAR, 3)
                .testVector(5, true)
                .strict(true)
                .build();

        assertThatThrownBy(() -> schema.validateOutput(false, 5))
                .isInstanceOf(SchemaDescriptor.SchemaViolationException.class)
                .hasMessageContaining("Test vector mismatch")
                .hasMessageContaining("input=5");
    }

    @Test
    void nonStrictTestVectorShouldNotThrow() {
        SchemaDescriptor schema = SchemaDescriptor.builder(
                        SchemaDescriptor.SchemaType.SCALAR, 3)
                .testVector(5, true)
                .strict(false)
                .build();

        assertThat(schema.validateOutput(false, 5)).isFalse();
        assertThat(schema.validateOutput(true, 5)).isTrue();
    }

    // ── ENUM schema ───────────────────────────────────────────────────────

    @Test
    void enumSchemaShouldAcceptAllowedPattern() {
        // XOR function: outputs for inputs 00,01,10,11 → 0,1,1,0 = pattern 0b0110
        long xorPattern = 0b0110L;
        SchemaDescriptor schema = SchemaDescriptor.enumPattern(2, Set.of(xorPattern));

        // Create XOR truth table: f(0)=0, f(1)=1, f(2)=1, f(3)=0
        BitSet bits = new BitSet(4);
        bits.set(1); // input 01 → true
        bits.set(2); // input 10 → true
        TruthTable tt = TruthTable.of(2, bits);

        assertThat(schema.validateTable(tt)).isTrue();
    }

    @Test
    void enumSchemaShouldRejectDisallowedPattern() {
        long xorPattern = 0b0110L;
        SchemaDescriptor schema = SchemaDescriptor.builder(
                        SchemaDescriptor.SchemaType.ENUM, 2)
                .allowedPatterns(Set.of(xorPattern))
                .strict(true)
                .build();

        // AND function: outputs 0,0,0,1 = pattern 0b1000
        BitSet bits = new BitSet(4);
        bits.set(3); // input 11 → true
        TruthTable tt = TruthTable.of(2, bits);

        assertThatThrownBy(() -> schema.validateTable(tt))
                .isInstanceOf(SchemaDescriptor.SchemaViolationException.class)
                .hasMessageContaining("not in allowed set");
    }

    @Test
    void enumSchemaWithMultiplePatterns() {
        // Allow XOR (0b0110) and AND (0b1000)
        Set<Long> patterns = Set.of(0b0110L, 0b1000L);
        SchemaDescriptor schema = SchemaDescriptor.enumPattern(2, patterns);

        // AND table
        BitSet andBits = new BitSet(4);
        andBits.set(3);
        TruthTable andTt = TruthTable.of(2, andBits);
        assertThat(schema.validateTable(andTt)).isTrue();

        // XOR table
        BitSet xorBits = new BitSet(4);
        xorBits.set(1);
        xorBits.set(2);
        TruthTable xorTt = TruthTable.of(2, xorBits);
        assertThat(schema.validateTable(xorTt)).isTrue();
    }

    // ── RANGE schema ──────────────────────────────────────────────────────

    @Test
    void rangeSchemaShouldAcceptWithinBounds() {
        // k=3 → 8 entries, require 2-6 true outputs
        SchemaDescriptor schema = SchemaDescriptor.range(3, 2, 6);

        // Table with exactly 4 true outputs
        BitSet bits = new BitSet(8);
        bits.set(0, 4, true); // set bits 0,1,2,3
        TruthTable tt = TruthTable.of(3, bits);

        assertThat(schema.validateTable(tt)).isTrue();
    }

    @Test
    void rangeSchemaShouldRejectTooFewTrue() {
        SchemaDescriptor schema = SchemaDescriptor.builder(
                        SchemaDescriptor.SchemaType.RANGE, 3)
                .minTrueCount(3)
                .strict(true)
                .build();

        // Only 1 true output
        BitSet bits = new BitSet(8);
        bits.set(0);
        TruthTable tt = TruthTable.of(3, bits);

        assertThatThrownBy(() -> schema.validateTable(tt))
                .isInstanceOf(SchemaDescriptor.SchemaViolationException.class)
                .hasMessageContaining("True count");
    }

    @Test
    void rangeSchemaShouldRejectTooManyTrue() {
        SchemaDescriptor schema = SchemaDescriptor.builder(
                        SchemaDescriptor.SchemaType.RANGE, 3)
                .maxTrueCount(5)
                .strict(true)
                .build();

        // All 8 entries true
        BitSet bits = new BitSet(8);
        bits.set(0, 8, true);
        TruthTable tt = TruthTable.of(3, bits);

        assertThatThrownBy(() -> schema.validateTable(tt))
                .isInstanceOf(SchemaDescriptor.SchemaViolationException.class)
                .hasMessageContaining("True count");
    }

    // ── TruthTable integration ────────────────────────────────────────────

    @Test
    void truthTableWithSchemaShouldCheckTestVectors() {
        // XOR table: outputs 0,1,1,0
        BitSet bits = new BitSet(4);
        bits.set(1);
        bits.set(2);

        SchemaDescriptor schema = SchemaDescriptor.builder(
                        SchemaDescriptor.SchemaType.SCALAR, 2)
                .testVector(1, true)   // input 01 → expected true
                .testVector(0, false)  // input 00 → expected false
                .build();

        TruthTable tt = TruthTable.of(2, bits, null, schema);

        // These should pass (no violation for matching test vectors)
        assertThat(tt.evaluate(0)).isFalse();
        assertThat(tt.evaluate(1)).isTrue();
    }

    @Test
    void truthTableWithSchemaShouldThrowOnTestVectorViolation() {
        BitSet bits = new BitSet(4);
        bits.set(1);
        bits.set(2); // XOR: 0,1,1,0

        SchemaDescriptor schema = SchemaDescriptor.builder(
                        SchemaDescriptor.SchemaType.SCALAR, 2)
                .testVector(0, true)   // input 00 → expected true, but actual is false
                .strict(true)
                .build();

        TruthTable tt = TruthTable.of(2, bits, null, schema);

        assertThatThrownBy(() -> tt.evaluate(0))
                .isInstanceOf(SchemaDescriptor.SchemaViolationException.class)
                .hasMessageContaining("Test vector mismatch");
    }

    @Test
    void truthTableWithNonStrictSchemaShouldReturnFalseWithoutThrowing() {
        BitSet bits = new BitSet(4);
        bits.set(1);
        bits.set(2);

        SchemaDescriptor schema = SchemaDescriptor.builder(
                        SchemaDescriptor.SchemaType.SCALAR, 2)
                .testVector(0, true)   // mismatch
                .strict(false)
                .build();

        TruthTable tt = TruthTable.of(2, bits, null, schema);

        // In non-strict mode, evaluate still returns the actual result
        // but validateOutput returns false — we check via validateSchema
        assertThat(tt.evaluate(0)).isFalse();
        assertThat(tt.validateSchema()).isFalse();
    }

    @Test
    void truthTableWithoutSchemaShouldWorkAsBefore() {
        // Backward compatibility: no schema should not affect behavior
        BitSet bits = new BitSet(4);
        bits.set(0, 4);
        TruthTable tt = TruthTable.of(2, bits);

        assertThat(tt.schema()).isNull();
        assertThat(tt.evaluate(0)).isTrue();
        assertThat(tt.evaluate(3)).isTrue();
        assertThat(tt.validateSchema()).isTrue();
    }

    @Test
    void withSchemaShouldCreateCopy() {
        BitSet bits = new BitSet(4);
        bits.set(1);
        TruthTable original = TruthTable.of(2, bits);

        SchemaDescriptor schema = SchemaDescriptor.scalar(2);
        TruthTable withSchema = original.withSchema(schema);

        assertThat(original.schema()).isNull();
        assertThat(withSchema.schema()).isSameAs(schema);
        assertThat(withSchema.k()).isEqualTo(original.k());

        // Original still works without schema
        assertThat(original.evaluate(0)).isFalse();
        assertThat(original.evaluate(1)).isTrue();
    }

    @Test
    void withSchemaShouldAllowRemovingSchema() {
        BitSet bits = new BitSet(4);
        SchemaDescriptor schema = SchemaDescriptor.scalar(2);
        TruthTable tt = TruthTable.of(2, bits, null, schema);

        TruthTable noSchema = tt.withSchema(null);
        assertThat(noSchema.schema()).isNull();
        assertThat(noSchema.evaluate(1)).isEqualTo(tt.evaluate(1));
    }

    // ── Edge cases ────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 21, 100})
    void shouldRejectInvalidK(int invalidK) {
        assertThatThrownBy(() -> SchemaDescriptor.scalar(invalidK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectInvalidRangeBounds() {
        assertThatThrownBy(() -> SchemaDescriptor.range(3, 5, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minTrueCount");
    }

    @Test
    void shouldRejectTestVectorOutOfRange() {
        assertThatThrownBy(() -> {
            SchemaDescriptor.builder(SchemaDescriptor.SchemaType.SCALAR, 3)
                    .testVector(8, true) // 2^3 = 8, valid indices are 0-7
                    .build();
        })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    void shouldRejectSchemaWithWrongKForTruthTable() {
        SchemaDescriptor schema = SchemaDescriptor.scalar(3);

        assertThatThrownBy(() -> TruthTable.of(2, new BitSet(4), null, schema))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must equal k=");
    }

    @Test
    void schemaWithWrongKShouldFailValidationStrict() {
        SchemaDescriptor schema = SchemaDescriptor.scalar(3);
        TruthTable tt = TruthTable.random(2);

        assertThatThrownBy(() -> schema.validateTable(tt))
                .isInstanceOf(SchemaDescriptor.SchemaViolationException.class)
                .hasMessageContaining("Table k=");
    }

    @Test
    void equalsAndHashCode() {
        SchemaDescriptor a = SchemaDescriptor.scalar(4);
        SchemaDescriptor b = SchemaDescriptor.scalar(4);
        SchemaDescriptor c = SchemaDescriptor.range(4, 1, 5);

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void toStringShouldIncludeTypeAndK() {
        SchemaDescriptor schema = SchemaDescriptor.scalar(4);
        String str = schema.toString();
        assertThat(str).contains("SCALAR").contains("k=4");
    }
}
