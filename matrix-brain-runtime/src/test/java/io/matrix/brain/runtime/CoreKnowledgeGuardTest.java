package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoreKnowledgeGuardTest {

    @Test
    void ordinary_reply_passes() {
        CoreKnowledgeGuard g = new CoreKnowledgeGuard();
        assertThat(g.check("Paris is the capital of France"))
            .isEqualTo(CoreKnowledgeGuard.Violation.NONE);
    }

    @Test
    void null_or_blank_passes() {
        CoreKnowledgeGuard g = new CoreKnowledgeGuard();
        assertThat(g.check(null)).isEqualTo(CoreKnowledgeGuard.Violation.NONE);
        assertThat(g.check("")).isEqualTo(CoreKnowledgeGuard.Violation.NONE);
    }

    @Test
    void teleport_word_violates_object_permanence() {
        CoreKnowledgeGuard g = new CoreKnowledgeGuard();
        assertThat(g.check("The ball teleported across the room"))
            .isEqualTo(CoreKnowledgeGuard.Violation.TELEPORTATION);
    }

    @Test
    void vanish_word_violates_object_permanence() {
        CoreKnowledgeGuard g = new CoreKnowledgeGuard();
        assertThat(g.check("The cup vanished into thin air"))
            .isEqualTo(CoreKnowledgeGuard.Violation.VANISH_OBJECT);
    }

    @Test
    void distant_force_violates_contact() {
        CoreKnowledgeGuard g = new CoreKnowledgeGuard();
        assertThat(g.check("The book moved without touching it"))
            .isEqualTo(CoreKnowledgeGuard.Violation.ACTION_AT_DISTANCE);
    }

    @Test
    void counts_accumulate() {
        CoreKnowledgeGuard g = new CoreKnowledgeGuard();
        g.check("The ball vanished.");
        g.check("It teleported.");
        g.check("It moved without touching anything.");
        g.check("Paris is fine.");
        assertThat(g.counts().get(CoreKnowledgeGuard.Violation.VANISH_OBJECT)).isEqualTo(1);
        assertThat(g.counts().get(CoreKnowledgeGuard.Violation.TELEPORTATION)).isEqualTo(1);
        assertThat(g.counts().get(CoreKnowledgeGuard.Violation.ACTION_AT_DISTANCE)).isEqualTo(1);
        assertThat(g.counts().get(CoreKnowledgeGuard.Violation.NONE)).isEqualTo(1);
    }

    @Test
    void case_insensitive() {
        CoreKnowledgeGuard g = new CoreKnowledgeGuard();
        assertThat(g.check("THE BALL TELEPORTED"))
            .isEqualTo(CoreKnowledgeGuard.Violation.TELEPORTATION);
    }
}
