package matrix.io;

import java.util.List;

/**
 * Результат кодирования (PERCEPTION): биты + provenance + witness-диагностика.
 * Иммутабелен; передаётся в DELIBERATION как часть M0 (DESIGN-05 §2).
 */
public final class EncodedInput {
    public final long[] bits;           // little-endian слова
    public final String moduleId;
    public final String moduleVersion;
    public final List<String> activeBits; // смыслы выставленных битов (INV-P3)

    public EncodedInput(long[] bits, String moduleId, String moduleVersion, List<String> activeBits) {
        this.bits = bits.clone();
        this.moduleId = moduleId;
        this.moduleVersion = moduleVersion;
        this.activeBits = List.copyOf(activeBits);
    }

    public int hamming(EncodedInput other) {
        int d = 0;
        for (int w = 0; w < bits.length; w++)
            d += Long.bitCount(bits[w] ^ other.bits[w]);
        return d;
    }
}
