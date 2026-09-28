package io.matrix.distill;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * RECON-W21 — {@link ActivationRecord} parsing contract.
 *
 * <p>This class exists because two real parser bugs shipped past a green test:</p>
 * <ol>
 *   <li>Python's {@code json.dumps} writes {@code "key": value} with a space after
 *       the colon; the field lookups required adjacency, so every record the real
 *       sidecar produced was rejected.</li>
 *   <li>The layer tensor is nested — {@code [[...]]} — and the extractor stripped
 *       only the outer brackets, so the first element became {@code "[0.9"} and
 *       was silently dropped. The original test asserted only "non-empty", so the
 *       truncation was invisible. <b>Every test here pins the element COUNT.</b></li>
 * </ol>
 */
class ActivationRecordTest {

    private static final String REAL_SHAPE =
        "{\"schema\": \"matrix.activation.v1\", \"batch_id\": \"capacities-8\", "
      + "\"sample_id\": 3, "
      + "\"input_text\": \"Capacity label 3 describes a sealed vault of exactly 3 units.\", "
      + "\"input_tokens\": [\"Capacity\", \"label\", \"3\"], "
      + "\"input_features\": [0.3, 0.7, 1.0, 0.1], "
      + "\"logits\": [0.680034], "
      + "\"layer_activations\": [[0.680034, 0.5, 0.25, 0.75, 0.1, 0.2, 0.3, 0.4]], "
      + "\"seed\": 42}";

    // ---- The two bugs -------------------------------------------------------

    @Test
    void parses_the_exact_shape_the_python_sidecar_emits() {
        ActivationRecord r = ActivationRecord.parse(REAL_SHAPE);
        assertThat(r).as("real sidecar output must parse").isNotNull();
        assertThat(r.schema()).isEqualTo("matrix.activation.v1");
        assertThat(r.batchId()).isEqualTo("capacities-8");
        assertThat(r.sampleId()).isEqualTo(3);
        assertThat(r.inputTokens()).containsExactly("Capacity", "label", "3");
        assertThat(r.logits()).containsExactly(0.680034f);
    }

    @Test
    void activation_length_is_exact_not_truncated() {
        ActivationRecord r = ActivationRecord.parse(REAL_SHAPE);
        assertThat(r).isNotNull();
        // 8 elements in, 8 elements out. The nested-bracket bug dropped the first.
        assertThat(r.activation()).hasSize(8);
        assertThat(r.activation()[0]).isEqualTo(0.680034f);
        assertThat(r.activation()[7]).isEqualTo(0.4f);
    }

    @Test
    void tolerates_whitespace_after_the_colon() {
        String compact = REAL_SHAPE.replace("\": ", "\":");
        ActivationRecord r = ActivationRecord.parse(compact);
        assertThat(r).isNotNull();
        assertThat(r.activation()).hasSize(8);
    }

    @Test
    void single_element_activation_is_preserved() {
        ActivationRecord r = ActivationRecord.parse(
            "{\"schema\":\"matrix.activation.v1\",\"layer_activations\":[[0.68]]}");
        assertThat(r).isNotNull();
        assertThat(r.activation()).containsExactly(0.68f);
    }

    // ---- Honest rejection ---------------------------------------------------

    @Test
    void rejects_foreign_schema() {
        assertThat(ActivationRecord.parse(
            "{\"schema\":\"other.v1\",\"layer_activations\":[[0.1]]}")).isNull();
    }

    @Test
    void rejects_missing_or_empty_activation() {
        assertThat(ActivationRecord.parse(
            "{\"schema\":\"matrix.activation.v1\"}")).isNull();
        assertThat(ActivationRecord.parse(
            "{\"schema\":\"matrix.activation.v1\",\"layer_activations\":[]}")).isNull();
    }

    @Test
    void rejects_garbage_without_throwing() {
        assertThat(ActivationRecord.parse(null)).isNull();
        assertThat(ActivationRecord.parse("")).isNull();
        assertThat(ActivationRecord.parse("   ")).isNull();
        assertThat(ActivationRecord.parse("not json at all")).isNull();
    }

    // ---- Article III determinism --------------------------------------------

    @Test
    void bit_vector_is_deterministic_and_correctly_sized() {
        ActivationRecord r = ActivationRecord.parse(REAL_SHAPE);
        assertThat(r).isNotNull();
        for (int bits : new int[]{4, 8, 20, 64, 128}) {
            long[] v1 = r.toBitVector(bits);
            long[] v2 = r.toBitVector(bits);
            assertThat(v1).isEqualTo(v2);
            assertThat(v1.length).isEqualTo(Math.max(1, (bits + 63) / 64));
        }
    }

    @Test
    void different_text_yields_different_vectors() {
        ActivationRecord a = ActivationRecord.parse(
            "{\"schema\":\"matrix.activation.v1\",\"input_text\":\"alpha one\","
            + "\"layer_activations\":[[0.5,0.5,0.5,0.5]]}");
        ActivationRecord b = ActivationRecord.parse(
            "{\"schema\":\"matrix.activation.v1\",\"input_text\":\"bravo two\","
            + "\"layer_activations\":[[0.5,0.5,0.5,0.5]]}");
        assertThat(a).isNotNull();
        assertThat(b).isNotNull();
        assertThat(a.toBitVector(64)).isNotEqualTo(b.toBitVector(64));
    }
}
