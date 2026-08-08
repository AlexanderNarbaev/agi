package io.matrix.signals;

/**
 * Audio signal module: encodes raw audio bytes into 64-bit signal vector.
 *
 * <p>Computes zero-crossing rate + RMS energy from first 64 samples,
 * packs into 64 bits.
 */
public final class AudioSignalModule implements SignalModule {

    @Override public String modality() { return "audio"; }
    @Override public String version() { return "1.0.0"; }

    @Override
    public long[] encode(Object input) {
        if (!(input instanceof byte[] data) || data.length == 0) {
            return new long[1];
        }
        // Simple features: zero-crossing rate + energy
        int crossings = 0;
        long energy = 0;
        int len = Math.min(data.length, 128);
        for (int i = 1; i < len; i++) {
            int prev = data[i - 1];
            int curr = data[i];
            if ((prev >= 0) != (curr >= 0)) crossings++;
            energy += curr * curr;
        }
        long signal = ((long) crossings << 32) | (energy & 0xFFFFFFFFL);
        return new long[]{signal};
    }

    @Override
    public Object decode(long[] signal) {
        int crossings = (int) (signal[0] >>> 32);
        long energy = signal[0] & 0xFFFFFFFFL;
        return "[audio:zc=" + crossings + ",energy=" + energy + "]";
    }
}
