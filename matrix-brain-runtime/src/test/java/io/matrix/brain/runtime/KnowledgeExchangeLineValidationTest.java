package io.matrix.brain.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W33.2 — shape validation for federated knowledge lines.
 *
 * <p>{@link KnowledgeExchangeProtocol.Fact#fromJsonLine} is a lenient field extractor over
 * untrusted input. Lenient field extraction is correct; lenient field extraction with NO shape
 * check is not, because the failure mode is silent:</p>
 *
 * <p>A torn line — a federated message truncated mid-write — used to yield
 * {@code Fact("", "", "", 0.0, 0)}. That is a well-formed Java object carrying no content, and
 * nothing downstream could tell it apart from a genuine fact. {@code mergeIntoWithReport} then
 * merged it, and the store ended up holding a fact that renders as
 * {@code fed-<node>- => }: an empty subject mapped to an empty answer, learned from a
 * message that never arrived.</p>
 *
 * <p>That is the fabrication shape this project exists to prevent, arriving through the
 * federation door instead of the reasoning door. It is not a crash, so nothing alerts; it is
 * not an assertion failure, so nothing goes red. It is a knowledge store quietly
 * accumulating invented facts.</p>
 *
 * <p>These tests pin the rule: a line that does not carry all three of {@code id},
 * {@code input} and {@code answer} is not a fact. It is rejected, loudly, with the reason
 * naming which field was missing.
 */
class KnowledgeExchangeLineValidationTest {

    private static final String VALID =
            "{\"id\":\"a1\",\"input\":\"Tokyo is capital of Japan\","
            + "\"answer\":\"Tokyo\",\"confidence\":0.9,\"ts\":42}";

    // ------------------------------------------------------------------
    // The well-formed case must keep working. Validation that breaks the
    // happy path is not validation, it is a new outage.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a complete line still parses into its fields")
    void complete_line_is_accepted() {
        KnowledgeExchangeProtocol.Fact f =
                KnowledgeExchangeProtocol.Fact.fromJsonLine(VALID);
        assertThat(f.id()).isEqualTo("a1");
        assertThat(f.input()).isEqualTo("Tokyo is capital of Japan");
        assertThat(f.answer()).isEqualTo("Tokyo");
        assertThat(f.confidence()).isEqualTo(0.9);
    }

    // ------------------------------------------------------------------
    // The defect: torn / partial lines must not become empty facts.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a line truncated before answer is rejected, naming the missing field")
    void torn_line_missing_answer_is_rejected() {
        String torn = "{\"id\":\"a1\",\"input\":\"Tokyo is capital of Japan\",\"conf";
        assertThatThrownBy(() -> KnowledgeExchangeProtocol.Fact.fromJsonLine(torn))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("answer");
    }

    @Test
    @DisplayName("a line with empty field values is rejected, not taught as an empty fact")
    void empty_valued_line_is_rejected() {
        assertThatThrownBy(() -> KnowledgeExchangeProtocol.Fact.fromJsonLine(
                "{\"id\":\"\",\"input\":\"\",\"answer\":\"\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the exact shape that produced fed-<node>- => is now refused")
    void degenerate_fed_node_lesson_shape_is_refused() {
        // The literal symptom recorded before the fix: an empty Fact merged into the store
        // and rendered as a subject and answer that are both blank.
        String degenerate = "{\"id\":\"\",\"input\":\"\",\"answer\":\"\"}";
        assertThatThrownBy(() -> KnowledgeExchangeProtocol.Fact.fromJsonLine(degenerate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    @DisplayName("an empty or whitespace-only line is rejected")
    void blank_line_is_rejected() {
        assertThatThrownBy(() -> KnowledgeExchangeProtocol.Fact.fromJsonLine("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null is rejected rather than throwing NullPointerException")
    void null_line_is_rejected_with_a_meaningful_type() {
        assertThatThrownBy(() -> KnowledgeExchangeProtocol.Fact.fromJsonLine(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("each required field is reported by name when absent")
    void rejection_message_names_the_missing_field() {
        assertThatThrownBy(() -> KnowledgeExchangeProtocol.Fact.fromJsonLine(
                "{\"answer\":\"x\"}"))
                .hasMessageContaining("id");
        assertThatThrownBy(() -> KnowledgeExchangeProtocol.Fact.fromJsonLine(
                "{\"id\":\"x\"}"))
                .hasMessageContaining("input");
        assertThatThrownBy(() -> KnowledgeExchangeProtocol.Fact.fromJsonLine(
                "{\"id\":\"x\",\"input\":\"y\"}"))
                .hasMessageContaining("answer");
    }

    // ------------------------------------------------------------------
    // The lenient numeric fields stay lenient, on purpose.
    // A missing confidence is "not stated" (0.0), not a reason to discard a
    // fact that carries a real subject and a real answer.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a missing confidence is still tolerated as not-stated")
    void missing_confidence_remains_lenient() {
        KnowledgeExchangeProtocol.Fact f = KnowledgeExchangeProtocol.Fact.fromJsonLine(
                "{\"id\":\"a1\",\"input\":\"q\",\"answer\":\"a\"}");
        assertThat(f.confidence()).isZero();
        assertThat(f.input()).isEqualTo("q");
    }

    @Test
    @DisplayName("an unparseable confidence is still tolerated as not-stated")
    void malformed_confidence_remains_lenient() {
        KnowledgeExchangeProtocol.Fact f = KnowledgeExchangeProtocol.Fact.fromJsonLine(
                "{\"id\":\"a1\",\"input\":\"q\",\"answer\":\"a\",\"confidence\":\"n/a\"}");
        assertThat(f.confidence()).isZero();
        assertThat(f.answer()).isEqualTo("a");
    }
}