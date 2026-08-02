package matrix.io.modules;

import java.util.List;
import matrix.io.SignalModule;

/**
 * Декодер «биты → текст» (DESIGN-03 §4, тип template): вердикт + witness →
 * шаблон с обоснованием. INV-R1: декодер не может изменить решение — биты
 * вердикта переносятся как есть; шаблон только оформляет.
 */
public final class TextTemplateRenderer implements SignalModule {
    private final String version;

    public TextTemplateRenderer(String version) { this.version = version; }

    @Override public String id() { return "text-template"; }
    @Override public String version() { return version; }
    @Override public Direction direction() { return Direction.OUT; }
    @Override public MediaType mediaType() { return MediaType.TEXT; }
    @Override public int bitWidth() { return 8; } // вердикт: 8 бит решения домена
    @Override public String bitMeaning(int bit) { return "бит вердикта " + bit; }

    /** verdict: биты решения; witness: смыслы битов, повлиявших на решение. */
    public String render(long[] verdict, List<String> witness, boolean refused) {
        StringBuilder sb = new StringBuilder();
        if (refused) {
            sb.append("ОТКАЗ. Основание: ").append(String.join(", ", witness));
        } else {
            long v = verdict[0] & 0xFF;
            sb.append("РЕШЕНИЕ [код ").append(v).append("]. Основание: ")
              .append(witness.isEmpty() ? "нет witness" : String.join(", ", witness));
        }
        return sb.toString();
    }
}
