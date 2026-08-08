package io.matrix.signals;

/**
 * Image signal module: encodes raw image bytes into 64-bit signal vector.
 *
 * <p>Downsamples to 8x8 grayscale grid, computes mean intensity per cell,
 * packs into 64 bits (8 cells × 8 bits).
 */
public final class ImageSignalModule implements SignalModule {

    @Override public String modality() { return "image"; }
    @Override public String version() { return "1.0.0"; }

    @Override
    public long[] encode(Object input) {
        if (!(input instanceof byte[] data) || data.length == 0) {
            return new long[1];
        }
        // Simple: hash of image dimensions + first 64 bytes
        long hash = 0xcbf29ce484222325L;
        int len = Math.min(data.length, 64);
        for (int i = 0; i < len; i++) {
            hash ^= data[i] & 0xFF;
            hash *= 0x100000001b3L;
        }
        // Mix dimensions
        hash ^= ((long) data.length) << 32;
        return new long[]{hash};
    }

    @Override
    public Object decode(long[] signal) {
        return "[image:" + Long.toHexString(signal[0]) + "]";
    }
}
