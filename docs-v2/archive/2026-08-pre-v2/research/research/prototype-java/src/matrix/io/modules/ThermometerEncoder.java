package matrix.io.modules;

import java.util.ArrayList;
import java.util.List;
import matrix.io.EncodedInput;
import matrix.io.SignalModule;

/**
 * Числовой кодировщик «quantile + термометр» (DESIGN-03 §2.1): бит i = 1 ⟺ x > q_i.
 * Границы вычисляются офлайн и входят в артефакт версии (INV-P1).
 */
public final class ThermometerEncoder implements SignalModule {
    private final String version;
    private final double[] quantiles; // возрастающие границы

    public ThermometerEncoder(String version, double[] quantiles) {
        this.version = version;
        this.quantiles = quantiles.clone();
    }

    @Override public String id() { return "numeric-thermometer"; }
    @Override public String version() { return version; }
    @Override public Direction direction() { return Direction.IN; }
    @Override public MediaType mediaType() { return MediaType.NUMERIC; }
    @Override public int bitWidth() { return quantiles.length; }
    @Override public String bitMeaning(int bit) { return "x > q" + bit + "=" + quantiles[bit]; }

    public EncodedInput encode(double x) {
        long[] bits = new long[(bitWidth() + 63) / 64];
        List<String> active = new ArrayList<>();
        for (int i = 0; i < quantiles.length; i++) {
            if (x > quantiles[i]) {
                bits[i >>> 6] |= 1L << (i & 63);
                active.add(bitMeaning(i));
            }
        }
        return new EncodedInput(bits, id(), version, active);
    }
}
