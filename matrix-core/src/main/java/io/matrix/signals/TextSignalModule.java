package io.matrix.signals;

/**
 * Text signal module: encodes text into 64-bit signal vector.
 *
 * <p>Uses character-level hashing with positional mixing to produce
 * a deterministic 64-bit representation. Not a cryptographic hash —
 * designed for neural signature matching, not security.
 */
public class TextSignalModule implements SignalModule {

    @Override public String modality() { return "text"; }
    @Override public String version() { return "1.0.0"; }

    @Override
    public long[] encode(Object input) {
        String text = input == null ? "" : input.toString();
        long[] signal = new long[1];
        long hash = 0xcbf29ce484222325L; // FNV offset basis
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            hash ^= c;
            hash *= 0x100000001b3L; // FNV prime
            // Mix position
            hash ^= (long) i << 32;
        }
        signal[0] = hash;
        return signal;
    }

    @Override
    public Object decode(long[] signal) {
        // Text is not decodable from hash — return signal as hex string
        return Long.toHexString(signal[0]);
    }

    @Override
    public boolean validate() {
        long[] test = encode("test");
        return test.length == 1 && test[0] != 0;
    }
}
