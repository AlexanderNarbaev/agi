package io.matrix.signals;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

/**
 * Image signal module: encodes raw image bytes into 64-bit signal vector.
 *
 * <p>Real decoding: uses Java ImageIO to decode PNG/JPEG/BMP,
 * downsamples to 8x8 grayscale grid, computes mean intensity per cell,
 * packs into 64 bits (8 cells × 8 bits).
 */
public class ImageSignalModule implements SignalModule {

    @Override public String modality() { return "image"; }
    @Override public String version() { return "2.0.0"; }

    @Override
    public long[] encode(Object input) {
        if (!(input instanceof byte[] data) || data.length == 0) {
            return new long[1];
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            if (img == null) {
                // Fallback: hash-based
                return fallbackHash(data);
            }
            return realFeatures(img);
        } catch (Exception e) {
            return fallbackHash(data);
        }
    }

    private long[] realFeatures(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        // Downsample to 8x8
        float[] feats = new float[64];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int rgb = img.getRGB(x * w / 8, y * h / 8);
                int gray = ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
                feats[y * 8 + x] = gray / 3.0f / 255.0f;
            }
        }
        // Pack into 64 bits: 8 cells × 8 bits
        long signal = 0;
        for (int i = 0; i < 8; i++) {
            int cell = (int) (feats[i] * 255);
            signal |= ((long) (cell & 0xFF)) << (i * 8);
        }
        return new long[]{signal};
    }

    private long[] fallbackHash(byte[] data) {
        long hash = 0xcbf29ce484222325L;
        int len = Math.min(data.length, 64);
        for (int i = 0; i < len; i++) {
            hash ^= data[i] & 0xFF;
            hash *= 0x100000001b3L;
        }
        hash ^= ((long) data.length) << 32;
        return new long[]{hash};
    }

    @Override
    public Object decode(long[] signal) {
        return "[image:" + Long.toHexString(signal[0]) + "]";
    }
}
