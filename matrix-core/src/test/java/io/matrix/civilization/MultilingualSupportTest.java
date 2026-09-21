package io.matrix.civilization;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MultilingualSupportTest {

    @Test
    void shouldPublishInMultipleLanguages() {
        MultilingualSupport mls = new MultilingualSupport();

        var entry = mls.publish("author-1",
                "This FNL detects edges in images",
                "en",
                "Western scientific context",
                List.of(
                        new MultilingualSupport.LocalizedContent("ru",
                                "Эта FNL обнаруживает границы на изображениях",
                                "Русский научный контекст"),
                        new MultilingualSupport.LocalizedContent("zh",
                                "此FNL检测图像中的边缘",
                                "中文科学语境")
                ));

        assertThat(entry.hasLanguage("en")).isTrue();
        assertThat(entry.hasLanguage("ru")).isTrue();
        assertThat(entry.hasLanguage("zh")).isTrue();
    }

    @Test
    void shouldFindByLanguage() {
        MultilingualSupport mls = new MultilingualSupport();

        mls.publish("a1", "English content", "en", "ctx", List.of());
        mls.publish("a2", "Russian content", "ru", "ctx", List.of());

        assertThat(mls.findByLanguage("en")).hasSize(1);
        assertThat(mls.findByLanguage("ru")).hasSize(1);
    }

    @Test
    void shouldGetTranslation() {
        MultilingualSupport mls = new MultilingualSupport();

        var entry = mls.publish("a1", "Hello", "en", "ctx",
                List.of(new MultilingualSupport.LocalizedContent("ru",
                        "Привет", "русский контекст")));

        var ru = entry.getTranslation("ru");
        assertThat(ru).isNotNull();
        assertThat(ru.content()).isEqualTo("Привет");
    }

    @Test
    void shouldReturnNullForMissingLanguage() {
        MultilingualSupport mls = new MultilingualSupport();
        var entry = mls.publish("a1", "Hello", "en", "ctx", List.of());

        assertThat(entry.getTranslation("ja")).isNull();
    }

    @Test
    void shouldSupportMultipleLanguages() {
        MultilingualSupport mls = new MultilingualSupport();

        assertThat(mls.supportedLanguages()).contains("en", "ru", "zh", "ar");
    }

    @Test
    void shouldAddNewLanguage() {
        MultilingualSupport mls = new MultilingualSupport();

        mls.addLanguage("ko");
        assertThat(mls.supportedLanguages()).contains("ko");
    }

    @Test
    void shouldNotDuplicateLanguage() {
        MultilingualSupport mls = new MultilingualSupport();

        mls.addLanguage("en");
        long enCount = mls.supportedLanguages().stream()
                .filter(l -> l.equals("en")).count();
        assertThat(enCount).isEqualTo(1);
    }
}
