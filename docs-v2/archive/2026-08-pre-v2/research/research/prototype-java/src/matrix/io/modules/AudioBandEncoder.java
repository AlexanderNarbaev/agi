package matrix.io.modules;

import java.util.ArrayList;
import java.util.List;
import matrix.io.EncodedInput;
import matrix.io.SignalModule;

/**
 * Аудио-кодировщик (DESIGN-06 §4.2): PCM-окно → признаки полос (RMS энергия,
 * zero-crossing rate) → термометрические биты. Чистая Java, без JNI-библиотек —
 * GraalVM-совместимо. Прототипный вариант: полосы делят спектр naïve-DFT;
 * продакшн (этап 2) — FFT через Vector API (JAVA_NATIVE §3).
 */
public final class AudioBandEncoder implements SignalModule {
    private final String version;
    private final int bands;      // число энергетических полос
    private final double[] edges; // пороги энергии полосы (термометр, 3 на полосу)

    public AudioBandEncoder(String version, int bands, double[] edges) {
        this.version = version;
        this.bands = bands;
        this.edges = edges.clone();
    }

    @Override public String id() { return "audio-bands"; }
    @Override public String version() { return version; }
    @Override public Direction direction() { return Direction.IN; }
    @Override public MediaType mediaType() { return MediaType.AUDIO; }
    @Override public int bitWidth() { return bands * edges.length + 4; } // +4 бита ZCR-термометра
    @Override public String bitMeaning(int bit) {
        if (bit < bands * edges.length)
            return "полоса " + (bit / edges.length) + " энергия > e" + (bit % edges.length);
        return "zcr > t" + (bit - bands * edges.length);
    }

    /** samples: нормированные [-1,1] PCM одного окна. */
    public EncodedInput encode(double[] samples) {
        long[] bits = new long[(bitWidth() + 63) / 64];
        List<String> active = new ArrayList<>();

        // naïve DFT-энергия полос (прототип; O(N·bands))
        int n = samples.length;
        for (int b = 0; b < bands; b++) {
            double re = 0, im = 0;
            int freq = b + 1;
            for (int i = 0; i < n; i++) {
                double ph = -2.0 * Math.PI * freq * i / n;
                re += samples[i] * Math.cos(ph);
                im += samples[i] * Math.sin(ph);
            }
            double energy = (re * re + im * im) / n;
            for (int e = 0; e < edges.length; e++) {
                if (energy > edges[e]) {
                    int bit = b * edges.length + e;
                    bits[bit >>> 6] |= 1L << (bit & 63);
                    active.add(bitMeaning(bit));
                }
            }
        }
        // zero-crossing rate → 4-битный термометр
        int zc = 0;
        for (int i = 1; i < n; i++)
            if ((samples[i] >= 0) != (samples[i - 1] >= 0)) zc++;
        double zcr = (double) zc / n;
        for (int t = 0; t < 4; t++) {
            if (zcr > 0.05 * (t + 1)) {
                int bit = bands * edges.length + t;
                bits[bit >>> 6] |= 1L << (bit & 63);
                active.add(bitMeaning(bit));
            }
        }
        return new EncodedInput(bits, id(), version, active);
    }
}
