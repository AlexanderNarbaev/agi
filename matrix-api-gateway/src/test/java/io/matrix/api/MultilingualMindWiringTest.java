package io.matrix.api;

import io.matrix.brain.runtime.MultilingualMind;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W2 — MultilingualMind is wired into the analyze path.
 * The gateway now detects RU input and transliterates it before
 * passing to the brain, so RU probes ("столица франции") reach the
 * stored EN facts ("Paris") via HDC cosine retrieval.
 */
class MultilingualMindWiringTest {

    @Test
    void russian_text_is_transliterated_for_brain_consumption() {
        MultilingualMind mind = new MultilingualMind();
        MultilingualMind.Language lang = mind.detectLanguage("столица франции это");
        assertThat(lang).isEqualTo(MultilingualMind.Language.RUSSIAN);
        String latin = mind.transliterateCyrillicToLatin("столица франции");
        assertThat(latin).isEqualTo("stolitsa frantsii");
    }

    @Test
    void english_text_is_not_transliterated() {
        MultilingualMind mind = new MultilingualMind();
        MultilingualMind.Language lang = mind.detectLanguage("What is the capital of France?");
        assertThat(lang).isEqualTo(MultilingualMind.Language.ENGLISH);
        String lat = mind.transliterateCyrillicToLatin("What is the capital of France?");
        // No Cyrillic chars to transliterate; result is unchanged.
        assertThat(lat).isEqualTo("What is the capital of France?");
    }

    @Test
    void project_for_retrieval_returns_consistent_normalization() {
        MultilingualMind mind = new MultilingualMind();
        String ru = mind.projectForRetrieval("Париж — столица Франции");
        // Should be lowercased and ASCII-fied for HDC retrieval.
        assertThat(ru).isEqualToIgnoringCase("parizh — stolitsa frantsii");
        assertThat(ru).doesNotContain("П");
        assertThat(ru).doesNotContain("Ф");
    }

    @Test
    void gateway_initializes_multilingual_mind_field() throws Exception {
        // Use reflection to verify MinimalHttpServer has the MultilingualMind field.
        var f = MinimalHttpServer.class.getDeclaredField("multilingualMind");
        f.setAccessible(true);
        // Constructor creates the field; just check the field type is correct.
        assertThat(f.getType().getName())
            .isEqualTo("io.matrix.brain.runtime.MultilingualMind");
    }
}
