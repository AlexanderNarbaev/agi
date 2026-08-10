package matrix.io.modules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import matrix.io.EncodedInput;
import matrix.io.SignalModule;

/**
 * Кодировщик «текст → биты» по фиксированному словарю домена (DESIGN-03 §2.1,
 * тип lexicon): bag-of-words + биграммы. Словарь — часть версии артефакта;
 * смена словаря = смена major-версии (INV-P1).
 */
public final class TextLexiconEncoder implements SignalModule {
    private final String version;
    private final Map<String, Integer> lexicon; // лексема/биграмма → бит

    public TextLexiconEncoder(String version, List<String> vocabulary) {
        this.version = version;
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < vocabulary.size(); i++) map.put(vocabulary.get(i), i);
        this.lexicon = Map.copyOf(map);
    }

    @Override public String id() { return "text-lexicon"; }
    @Override public String version() { return version; }
    @Override public Direction direction() { return Direction.IN; }
    @Override public MediaType mediaType() { return MediaType.TEXT; }
    @Override public int bitWidth() { return lexicon.size(); }

    @Override public String bitMeaning(int bit) {
        for (Map.Entry<String, Integer> e : lexicon.entrySet())
            if (e.getValue() == bit) return "лексема:" + e.getKey();
        return "неизвестный бит";
    }

    public EncodedInput encode(String text) {
        String[] tokens = text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"); // Unicode-буквы (кириллица вкл.)
        List<String> features = new ArrayList<>();
        for (String t : tokens) if (!t.isBlank()) features.add(t);
        for (int i = 0; i + 1 < tokens.length; i++)
            if (!tokens[i].isBlank() && !tokens[i + 1].isBlank())
                features.add(tokens[i] + "_" + tokens[i + 1]); // биграмма

        long[] bits = new long[(bitWidth() + 63) / 64];
        List<String> active = new ArrayList<>();
        for (String f : features) {
            Integer b = lexicon.get(f);
            if (b != null) {
                bits[b >>> 6] |= 1L << (b & 63);
                active.add("лексема:" + f);
            }
        }
        return new EncodedInput(bits, id(), version, active);
    }
}
