package io.matrix.shadow;

import io.matrix.ethics.EthicalFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AntiDopamineTest {

    private AntiDopamine detector;

    @BeforeEach
    void setup() { detector = new AntiDopamine(); }

    @Test
    void shouldDetectClickbait() {
        var pattern = detector.scan("You won't believe this shocking discovery!");

        assertThat(pattern).isNotNull();
        assertThat(pattern.type()).isEqualTo(AntiDopamine.PatternType.CLICKBAIT);
    }

    @Test
    void shouldDetectRageBait() {
        var pattern = detector.scan("They are destroying everything! The truth about their secret agenda revealed.");

        assertThat(pattern).isNotNull();
        assertThat(pattern.type()).isEqualTo(AntiDopamine.PatternType.RAGE_BAIT);
    }

    @Test
    void shouldDetectAddictiveLoop() {
        var pattern = detector.scan("Just one more try! Keep playing for the next level!");

        assertThat(pattern).isNotNull();
        assertThat(pattern.type()).isEqualTo(AntiDopamine.PatternType.ADDICTIVE_LOOP);
    }

    @Test
    void shouldDetectInfiniteScroll() {
        var pattern = detector.scan("Infinite scroll: endless content for your consumption!");

        assertThat(pattern).isNotNull();
        assertThat(pattern.type()).isEqualTo(AntiDopamine.PatternType.INFINITE_SCROLL);
    }

    @Test
    void shouldDetectFearMongering() {
        var pattern = detector.scan("Danger! Immediate threat! Horrible consequences await — catastrophe imminent!");

        assertThat(pattern).isNotNull();
        assertThat(pattern.type()).isEqualTo(AntiDopamine.PatternType.FEAR_MONGERING);
    }

    @Test
    void shouldPassCleanContent() {
        var pattern = detector.scan("The weather today is sunny with a high of 22 degrees.");

        assertThat(pattern).isNull();
    }

    @Test
    void shouldReturnSafetyScore() {
        double score = detector.safetyScore("Normal discussion about scientific research");

        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void shouldLowerScoreForManipulativeContent() {
        double score = detector.safetyScore("You won't believe this shocking secret!");

        assertThat(score).isLessThan(1.0);
    }
}
