package io.matrix.shadow;

import io.matrix.ethics.EthicalFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DigitalShadowTest {

    private DigitalShadow shadow;

    @BeforeEach
    void setup() {
        shadow = new DigitalShadow(new EthicalFilter());
    }

    @Test
    void shouldAllowCleanIncoming() {
        var verdict = shadow.screenIncoming(
                "The weather is nice today", "wikipedia.org");

        assertThat(verdict.allowed()).isTrue();
        assertThat(verdict.blocked()).isFalse();
    }

    @Test
    void shouldBlockUnethicalIncoming() {
        var verdict = shadow.screenIncoming("kill all humans", "unknown");

        assertThat(verdict.blocked()).isTrue();
        assertThat(verdict.reason()).contains("Ethical");
    }

    @Test
    void shouldBlockClickbaitAtHighLevel() {
        shadow.setProtectionLevel(DigitalShadow.ProtectionLevel.HIGH);

        var verdict = shadow.screenIncoming(
                "Shocking! You won't believe this!", "social-media.example");

        assertThat(verdict.blocked()).isTrue();
    }

    @Test
    void shouldWarnClickbaitAtMediumLevel() {
        shadow.setProtectionLevel(DigitalShadow.ProtectionLevel.MEDIUM);

        var verdict = shadow.screenIncoming(
                "Shocking! You won't believe this!", "social-media.example");

        assertThat(verdict.allowed()).isTrue();
        assertThat(verdict.warnings()).isNotEmpty();
    }

    @Test
    void shouldAllowCleanOutgoing() {
        var verdict = shadow.screenOutgoing("help user with research");

        assertThat(verdict.allowed()).isTrue();
    }

    @Test
    void shouldBlockUnethicalOutgoing() {
        var verdict = shadow.screenOutgoing("deploy autonomous weapon");

        assertThat(verdict.blocked()).isTrue();
    }

    @Test
    void shouldLogAllActivity() {
        shadow.screenIncoming("Hello", "wikipedia.org");
        shadow.screenOutgoing("optimize neurons");

        assertThat(shadow.auditLog()).hasSize(2);
    }

    @Test
    void shouldAdjustProtectionLevel() {
        assertThat(shadow.protectionLevel())
                .isEqualTo(DigitalShadow.ProtectionLevel.MEDIUM);

        shadow.setProtectionLevel(DigitalShadow.ProtectionLevel.MAXIMUM);
        assertThat(shadow.protectionLevel())
                .isEqualTo(DigitalShadow.ProtectionLevel.MAXIMUM);
    }

    @Test
    void shouldExposeSubFilters() {
        assertThat(shadow.antiDopamine()).isNotNull();
        assertThat(shadow.ecoAudit()).isNotNull();
        assertThat(shadow.explainer()).isNotNull();
    }
}
