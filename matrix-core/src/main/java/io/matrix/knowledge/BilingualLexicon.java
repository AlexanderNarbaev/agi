package io.matrix.knowledge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The bilingual term lexicon (RECON-W42.3).
 *
 * <p>The mission's §6 asks for a dialogue in Russian and English. The
 * knowledge is in English, and a Russian question shares no token with it —
 * which the W42 benchmark measured exactly: **the five Russian items scored
 * 0/5**. Something has to bridge the two, and the smallest honest bridge
 * is a table of terms that mean the same thing in the two languages.
 *
 * <p><b>What the table does and does not claim.</b> It says these two
 * WORDS name the same thing. It does not say the two PROPOSITIONS are
 * equivalent — the mission's §6 is explicit that "translation is not
 * evidence that two propositions are equivalent", and a claim's truth is
 * untouched by its terms appearing here. The table is a search aid: it
 * lets the retriever reach the claim that the Russian question is about,
 * and the claim is then judged on its own evidence.
 *
 * <p>The terms are stored as STEMS, because the text layer folds the case
 * endings off a Russian word before the lookup: "фотосинтезе" folds to
 * "фотосинтез", and so do "фотосинтеза" and "фотосинтезу". The English side
 * is folded the same way, so "photosynthesis" and "photosyntheses" meet.
 *
 * <p>The lexicon is loaded once and is immutable afterwards.
 */
public final class BilingualLexicon {

    /** The resource the terms are read from. */
    public static final String RESOURCE = "knowledge/ru-en-lexicon.txt";

    private static volatile BilingualLexicon instance;

    private final Map<String, Set<String>> byTerm = new LinkedHashMap<>();
    private final Map<String, String> languageOf = new LinkedHashMap<>();

    private BilingualLexicon() {
        load();
    }

    /** The lexicon, loaded once. */
    public static BilingualLexicon instance() {
        BilingualLexicon local = instance;
        if (local == null) {
            synchronized (BilingualLexicon.class) {
                local = instance;
                if (local == null) {
                    local = new BilingualLexicon();
                    instance = local;
                }
            }
        }
        return local;
    }

    private void load() {
        try (InputStream stream = BilingualLexicon.class.getClassLoader()
                .getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.strip();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int tab = trimmed.indexOf('\t');
                    if (tab <= 0 || tab >= trimmed.length() - 1) {
                        continue;
                    }
                    // Each side is folded in ITS OWN language, because the
                    // text layer folds a Russian word by its case endings
                    // and an English word by its plural. A key that is not
                    // folded the way the lookup folds it is a key that is
                    // never found.
                    String russian = fold(trimmed.substring(0, tab), TextProfiles.RUSSIAN);
                    String english = fold(trimmed.substring(tab + 1), TextProfiles.ENGLISH);
                    if (russian.isEmpty() || english.isEmpty()) {
                        continue;
                    }
                    register(russian, english);
                    register(english, russian);
                    languageOf.put(russian, "ru");
                    languageOf.put(english, "en");
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to read the bilingual lexicon", e);
        }
    }

    /**
     * The fold of a term, word by word.
     *
     * <p>A multi-word term ("цикл кальвина") is folded word by word, so
     * that the lookup finds it however the text layer folded the question.
     */
    private static String fold(String term, TextProfile profile) {
        StringBuilder out = new StringBuilder();
        for (String word : term.strip().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(profile.fold(word));
        }
        return out.toString();
    }

    private void register(String term, String translation) {
        byTerm.computeIfAbsent(term, key -> new LinkedHashSet<>()).add(translation);
    }

    /**
     * The terms that mean the same thing as the given one.
     *
     * <p>The lookup is on the stem: a caller passes the folded token. The
     * result is empty for a term the lexicon does not hold — and that is
     * the common case, because a lexicon of a few hundred terms cannot
     * cover a language.
     *
     * @param stem the folded term
     * @return the counterparts, or an empty set
     */
    public Set<String> translations(String stem) {
        if (stem == null) {
            return Set.of();
        }
        Set<String> found = byTerm.get(stem.toLowerCase(Locale.ROOT));
        return found == null ? Set.of() : Set.copyOf(found);
    }

    /** Whether the lexicon holds the term. */
    public boolean holds(String stem) {
        return stem != null && byTerm.containsKey(stem.toLowerCase(Locale.ROOT));
    }

    /**
     * The language a term is written in, as far as the lexicon knows.
     *
     * @param stem the folded term
     * @return "ru", "en", or null
     */
    public String languageOf(String stem) {
        return stem == null ? null : languageOf.get(stem.toLowerCase(Locale.ROOT));
    }

    /** How many terms the lexicon holds. */
    public int size() {
        return byTerm.size();
    }

    /** The terms the lexicon holds, for the interface. */
    public Set<String> terms() {
        return Set.copyOf(byTerm.keySet());
    }

    /**
     * The stems of the terms a text holds, with their counterparts.
     *
     * <p>A caller that wants to search across the two languages takes the
     * text's stems and asks for the counterparts of each.
     *
     * @param text the text to read
     * @return the counterparts of the terms the lexicon knows
     */
    public List<String> counterparts(String text) {
        List<String> counterparts = new ArrayList<>();
        String language = TextProfiles.detect(text);
        for (String token : TextFeatures.originalTokens(text)) {
            String stem = TextProfiles.ofLanguage(language).fold(token);
            for (String translation : translations(stem)) {
                if (!counterparts.contains(translation)) {
                    counterparts.add(translation);
                }
            }
        }
        return counterparts;
    }
}
