package io.matrix.civilization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CivilizationCouncilTest {

    private CivilizationCouncil council;

    @BeforeEach
    void setup() {
        council = new CivilizationCouncil();

        council.register(new CivilizationCouncil.Civilization(
                "civ-west", "Western Scientific",
                "en",
                List.of("freedom", "individualism", "innovation"),
                List.of("privacy", "autonomy")));

        council.register(new CivilizationCouncil.Civilization(
                "civ-east", "Eastern Harmonic",
                "zh",
                List.of("harmony", "collective good", "tradition"),
                List.of("stability", "community")));
    }

    @Test
    void shouldRegisterCivilizations() {
        assertThat(council.civilizations()).hasSize(2);
    }

    @Test
    void shouldMediateConsensusConflict() {
        var resolution = council.mediate(
                "Data Sharing",
                "How should personal data be shared?",
                "civ-west", "Individual consent\nrequired for sharing",
                "civ-east", "Individual consent\nrequired for sharing");

        assertThat(resolution.topic()).isEqualTo("Data Sharing");
        assertThat(resolution.adopted()).isTrue();
    }

    @Test
    void shouldMediateDivergentConflict() {
        var resolution = council.mediate(
                "Autonomy",
                "How much autonomy for AI systems?",
                "civ-west", "Full autonomy\ntotal freedom\nno restrictions\nhuman oversight",
                "civ-east", "Limited autonomy\ncommunity governance\nmoral constraints\ntraditional values");

        assertThat(resolution.adopted()).isFalse();
        assertThat(resolution.resolution()).contains("Deadlocked");
    }

    @Test
    void shouldIssueAdvisoryOpinion() {
        var opinion = council.advisoryOpinion("civ-west", "Ethical AI",
                Map.of("urgency", "high"));

        assertThat(opinion).contains("Western Scientific");
        assertThat(opinion).contains("Expedited");
    }

    @Test
    void shouldHandleUnknownCivilization() {
        var opinion = council.advisoryOpinion("unknown", "Topic", Map.of());

        assertThat(opinion).contains("not registered");
    }

    @Test
    void shouldTrackResolutions() {
        council.mediate("T1", "C1", "civ-west", "posA", "civ-east", "posB");

        assertThat(council.resolutions()).hasSize(1);
    }

    @Test
    void shouldExposeWeaver() {
        assertThat(council.weaver()).isNotNull();
    }
}
