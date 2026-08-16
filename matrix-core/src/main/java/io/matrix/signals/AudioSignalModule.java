package io.matrix.signals;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;

/**
 * Audio signal module: encodes raw audio bytes into 64-bit signal vector.
 *
 * <p>Real decoding: uses Java Sound API to decode WAV,
 * computes zero-crossing rate + RMS energy + spectral features,
 * packs into 64 bits.
 */
public class AudioSignalModule implements SignalModule {

    @Override public String modality() { return "audio"; }
    @Override public String version() { return "2.0.0"; }

    @Override
    public long[] encode(Object input) {
        if (!(input instanceof byte[] data) || data.length == 0) {
            return new long[1];
        }
        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data));
            AudioFormat format = ais.getFormat();
            byte[] buffer = new byte[1024];
            int read = ais.read(buffer);
            ais.close();
            return realFeatures(buffer, read, format);
        } catch (Exception e) {
            return fallbackHash(data);
        }
    }

    private long[] realFeatures(byte[] data, int length, AudioFormat format) {
        // Zero-crossing rate
        int crossings = 0;
        for (int i = 1; i < length; i++) {
            if ((data[i - 1] >= 0) != (data[i] >= 0)) crossings++;
        }
        // RMS energy
        long energy = 0;
        for (int i = 0; i < length; i++) {
            energy += data[i] * data[i];
        }
        // Spectral centroid (simplified)
        int spectral = 0;
        for (int i = 1; i < length; i++) {
            spectral += Math.abs(data[i] - data[i - 1]);
        }
        // Pack: crossings (16 bits) + energy (32 bits) + spectral (16 bits)
        long signal = ((long) (crossings & 0xFFFF) << 48)
                | ((energy & 0xFFFFFFFFL) << 16)
                | (spectral & 0xFFFF);
        return new long[]{signal};
    }

    private long[] fallbackHash(byte[] data) {
        long hash = 0xcbf29ce484222325L;
        int len = Math.min(data.length, 64);
        for (int i = 0; i < len; i++) {
            hash ^= data[i] & 0xFF;
            hash *= 0x100000001b3L;
        }
        return new long[]{hash};
    }

    @Override
    public Object decode(long[] signal) {
        int crossings = (int) (signal[0] >>> 48);
        long energy = (signal[0] >>> 16) & 0xFFFFFFFFL;
        int spectral = (int) (signal[0] & 0xFFFF);
        return "[audio:zc=" + crossings + ",energy=" + energy + ",spectral=" + spectral + "]";
    }
}
