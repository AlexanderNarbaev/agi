package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRUE-W7 — Multilingual mind tests (RU/EN).
 */
class MultilingualMindIntegrationTest {

    @Test
    void detects_english_text() {
        MultilingualMind m = new MultilingualMind();
        assertThat(m.detectLanguage("Hello world")).isEqualTo(MultilingualMind.Language.ENGLISH);
    }

    @Test
    void detects_russian_text() {
        MultilingualMind m = new MultilingualMind();
        assertThat(m.detectLanguage("Привет мир")).isEqualTo(MultilingualMind.Language.RUSSIAN);
    }

    @Test
    void detects_mixed_text() {
        MultilingualMind m = new MultilingualMind();
        assertThat(m.detectLanguage("Hello Привет мир")).isEqualTo(MultilingualMind.Language.MIXED);
    }

    @Test
    void detects_empty_as_unknown() {
        MultilingualMind m = new MultilingualMind();
        assertThat(m.detectLanguage("")).isEqualTo(MultilingualMind.Language.UNKNOWN);
        assertThat(m.detectLanguage(null)).isEqualTo(MultilingualMind.Language.UNKNOWN);
    }

    @Test
    void normalizes_to_lowercase_nfc() {
        MultilingualMind m = new MultilingualMind();
        assertThat(m.normalize("Hello World")).isEqualTo("hello world");
        assertThat(m.normalize("  HELLO  ")).isEqualTo("hello");
    }

    @Test
    void transliterates_moscow_to_moskva() {
        MultilingualMind m = new MultilingualMind();
        assertThat(m.transliterateCyrillicToLatin("Москва")).isEqualTo("Moskva");
        assertThat(m.transliterateCyrillicToLatin("Россия")).isEqualTo("Rossiya");
    }

    @Test
    void project_for_retrieval_handles_cyrillic_input() {
        MultilingualMind m = new MultilingualMind();
        String proj = m.projectForRetrieval("Москва");
        assertThat(proj).isEqualTo("moskva");
    }

    @Test
    void project_for_retrieval_passes_through_english() {
        MultilingualMind m = new MultilingualMind();
        String proj = m.projectForRetrieval("Moscow");
        assertThat(proj).isEqualTo("moscow");
    }

    @Test
    void snapshot_describes_capabilities() {
        MultilingualMind m = new MultilingualMind();
        var snap = m.snapshot();
        assertThat(snap.get("languages_supported")).isEqualTo("EN, RU, MIXED");
    }

    @Test
    void russian_capital_letters_transliterate() {
        MultilingualMind m = new MultilingualMind();
        // "П" → "P", "р" → "r"
        String r = m.transliterateCyrillicToLatin("Пр");
        assertThat(r).isEqualTo("Pr");
    }

    @Test
    void mixed_script_detected_via_project() {
        MultilingualMind m = new MultilingualMind();
        // "Привет world" — Cyrillic + Latin
        String proj = m.projectForRetrieval("Привет world");
        // Project handles MIXED → transliterate Cyrillic + keep Latin (lowercased)
        String lower = proj.toLowerCase();
        assertThat(lower).contains("privet");
        assertThat(lower).contains("world");
    }
}
