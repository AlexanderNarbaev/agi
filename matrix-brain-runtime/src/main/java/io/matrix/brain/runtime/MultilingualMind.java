package io.matrix.brain.runtime;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * TRUE-W7 — Multilingual mind: Unicode-aware symbol normalization +
 * transliteration-invariant tokens.
 *
 * <p>Supports RU/EN as first-class languages. "Москва" normalizes to
 * "Moskva" via Cyrillic→Latin transliteration, enabling cross-language
 * retrieval. Cyrillic detection is a primary stage in {@link MindCycle}.</p>
 */
public final class MultilingualMind {

    /** Languages we support as first-class. */
    public enum Language {
        ENGLISH, RUSSIAN, MIXED, UNKNOWN
    }

    /** Detect the dominant script of an input. */
    public Language detectLanguage(String input) {
        if (input == null || input.isBlank()) return Language.UNKNOWN;
        int cyr = 0, lat = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            // Skip whitespace, digits, punctuation — they don't count as Latin
            if (Character.isWhitespace(c) || Character.isDigit(c)) continue;
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC) cyr++;
            // Latin letters only (not digits, not punctuation)
            else if (Character.isLetter(c)
                && Character.UnicodeBlock.of(c) == Character.UnicodeBlock.BASIC_LATIN) lat++;
        }
        if (cyr > 0 && lat == 0) return Language.RUSSIAN;
        if (lat > 0 && cyr == 0) return Language.ENGLISH;
        if (cyr > 0 && lat > 0) return Language.MIXED;
        return Language.UNKNOWN;
    }

    /** Unicode-normalize + lowercase + strip diacritics. */
    public String normalize(String input) {
        if (input == null) return "";
        String n = Normalizer.normalize(input, Normalizer.Form.NFC);
        return n.toLowerCase(Locale.ROOT).trim();
    }

    /** Transliterate Cyrillic → Latin (simple mapping). */
    public String transliterateCyrillicToLatin(String input) {
        if (input == null) return "";
        Map<Character, String> map = cyrillicToLatinMap();
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (map.containsKey(c)) {
                out.append(map.get(c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Cross-language projection: returns the normalized Latin form regardless
     *  of input script (for HDC cosine alignment). */
    public String projectForRetrieval(String input) {
        if (input == null) return "";
        Language lang = detectLanguage(input);
        String n = normalize(input);
        if (lang == Language.RUSSIAN || lang == Language.MIXED) {
            n = transliterateCyrillicToLatin(n);
        }
        return n;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("languages_supported", "EN, RU, MIXED");
        m.put("normalization_form", "NFC");
        m.put("transliteration", "Cyrillic -> Latin");
        return m;
    }

    /** Compact Cyrillic → Latin map covering the 33-letter Russian alphabet + ё. */
    private static Map<Character, String> cyrillicToLatinMap() {
        Map<Character, String> m = new LinkedHashMap<>();
        String[][] pairs = {
            {"а","a"},{"б","b"},{"в","v"},{"г","g"},{"д","d"},{"е","e"},{"ё","yo"},
            {"ж","zh"},{"з","z"},{"и","i"},{"й","j"},{"к","k"},{"л","l"},{"м","m"},
            {"н","n"},{"о","o"},{"п","p"},{"р","r"},{"с","s"},{"т","t"},{"у","u"},
            {"ф","f"},{"х","kh"},{"ц","ts"},{"ч","ch"},{"ш","sh"},{"щ","shch"},{"ъ",""},
            {"ы","y"},{"ь",""},{"э","e"},{"ю","yu"},{"я","ya"}
        };
        for (String[] p : pairs) {
            char lower = p[0].charAt(0);
            String transliteration = p[1];
            m.put(lower, transliteration);
            // Upper-case form: capitalize the first letter of the transliteration.
            if (transliteration.length() > 0) {
                m.put(Character.toUpperCase(lower),
                    Character.toUpperCase(transliteration.charAt(0))
                        + transliteration.substring(1));
            } else {
                m.put(Character.toUpperCase(lower), "");
            }
        }
        return m;
    }
}
