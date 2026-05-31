package io.matrix.civilization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Multilingual support for Noosphere publications.
 *
 * <p>FNL descriptions and knowledge entries can be published
 * in multiple languages simultaneously. Each language version
 * preserves the original cultural context without translation
 * into a "universal" language.
 *
 * <p>Ref: L8_Roadmap.md §3.8-2
 */
public class MultilingualSupport {

    public record LocalizedContent(
            String language,
            String content,
            String culturalNote
    ) {}

    public record MultilingualEntry(
            String entryId,
            String authorId,
            List<LocalizedContent> translations,
            Map<String, String> culturalContext
    ) {
        public boolean hasLanguage(String lang) {
            return translations.stream()
                    .anyMatch(t -> t.language().equalsIgnoreCase(lang));
        }

        public LocalizedContent getTranslation(String lang) {
            return translations.stream()
                    .filter(t -> t.language().equalsIgnoreCase(lang))
                    .findFirst()
                    .orElse(null);
        }
    }

    private final List<MultilingualEntry> entries = new ArrayList<>();
    private final List<String> supportedLanguages = new ArrayList<>(
            List.of("en", "ru", "zh", "es", "ar", "fr", "de", "ja", "pt", "hi"));

    /**
     * Publishes a multilingual entry to the Noosphere.
     */
    public MultilingualEntry publish(String authorId, String baseContent,
                                       String baseLanguage, String culturalContext,
                                       List<LocalizedContent> additionalTranslations) {
        List<LocalizedContent> allTranslations = new ArrayList<>();
        allTranslations.add(new LocalizedContent(baseLanguage, baseContent,
                culturalContext));
        allTranslations.addAll(additionalTranslations);

        Map<String, String> context = new HashMap<>();
        context.put("cultural_context", culturalContext);
        context.put("primary_language", baseLanguage);

        var entry = new MultilingualEntry(
                java.util.UUID.randomUUID().toString(),
                authorId,
                allTranslations,
                context);

        entries.add(entry);
        return entry;
    }

    /**
     * Finds entries available in a specific language.
     */
    public List<MultilingualEntry> findByLanguage(String language) {
        return entries.stream()
                .filter(e -> e.hasLanguage(language))
                .toList();
    }

    /**
     * Lists all supported languages.
     */
    public List<String> supportedLanguages() {
        return List.copyOf(supportedLanguages);
    }

    /**
     * Adds a new supported language.
     */
    public void addLanguage(String languageCode) {
        if (!supportedLanguages.contains(languageCode)) {
            supportedLanguages.add(languageCode);
        }
    }

    public List<MultilingualEntry> entries() { return List.copyOf(entries); }
}
