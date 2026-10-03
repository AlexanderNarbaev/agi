package io.matrix.brain.runtime;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * RECON-W32.1 — shared deterministic media fixtures.
 *
 * <p>Extracted so the transcoder integration tests and the decoder unit tests construct
 * the SAME real files. Duplicating the fixture per test is how a test ends up asserting
 * against a subtly different input from the one the decoder was reasoned about.</p>
 *
 * <p>Pure and deterministic: no randomness, no clock, no I/O. A sine tone's zero
 * crossings are therefore an exact expectation rather than a tolerance.</p>
 */
public final class MediaDecodingTestFixtures {

    private MediaDecodingTestFixtures() {}

    /** Minimal valid RIFF/WAVE header, PCM 16-bit mono. */
    public static byte[] riffWaveHeader() {
        return new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};
    }

    public static byte[] pngSignature() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    }

    /** A real 16-bit mono PCM WAV containing a pure sine tone. */
    public static byte[] synthWav(double hz, int sampleRate, double seconds) {
        int n = (int) (sampleRate * seconds);
        byte[] data = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            int v = (int) (12000 * Math.sin(2 * Math.PI * hz * i / sampleRate));
            data[i * 2] = (byte) (v & 0xFF);
            data[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
        }
        byte[] out = new byte[44 + data.length];
        int p = 0;
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, out, p, 4); p += 4;
        putI32(out, p, 36 + data.length); p += 4;
        System.arraycopy("WAVE".getBytes(StandardCharsets.US_ASCII), 0, out, p, 4); p += 4;
        System.arraycopy("fmt ".getBytes(StandardCharsets.US_ASCII), 0, out, p, 4); p += 4;
        putI32(out, p, 16); p += 4;
        putI16(out, p, 1); p += 2;              // PCM
        putI16(out, p, 1); p += 2;              // mono
        putI32(out, p, sampleRate); p += 4;
        putI32(out, p, sampleRate * 2); p += 4;
        putI16(out, p, 2); p += 2;              // block align
        putI16(out, p, 16); p += 2;             // bits per sample
        System.arraycopy("data".getBytes(StandardCharsets.US_ASCII), 0, out, p, 4); p += 4;
        putI32(out, p, data.length); p += 4;
        System.arraycopy(data, 0, out, p, data.length);
        return out;
    }

    private static void putI32(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >> 8) & 0xFF);
        b[off + 2] = (byte) ((v >> 16) & 0xFF);
        b[off + 3] = (byte) ((v >> 24) & 0xFF);
    }

    private static void putI16(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >> 8) & 0xFF);
    }

    /** A real 8-bit RGB PNG of one solid colour, with correct chunk CRCs. */
    public static byte[] synthPng(int w, int h, int r, int g, int b) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (int y = 0; y < h; y++) {
            body.write(0);                       // filter type 0
            for (int x = 0; x < w; x++) {
                body.write(r); body.write(g); body.write(b);
            }
        }
        byte[] raw = body.toByteArray();
        byte[] ihdr = new byte[13];
        putI32(ihdr, 0, w);
        putI32(ihdr, 4, h);
        ihdr[8] = 8;                             // bit depth
        ihdr[9] = 2;                             // colour type 2 = truecolour RGB
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(pngSignature());
        writeChunk(out, "IHDR", ihdr);
        writeChunk(out, "IDAT", deflate(raw));
        writeChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) {
        putI32toStream(out, data.length);
        byte[] t = type.getBytes(StandardCharsets.US_ASCII);
        out.writeBytes(t);
        out.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        putI32toStream(out, (int) crc.getValue());
    }

    private static byte[] deflate(byte[] raw) {
        Deflater d = new Deflater();
        try {
            d.setInput(raw);
            d.finish();
            ByteArrayOutputStream o = new ByteArrayOutputStream(raw.length);
            byte[] buf = new byte[8192];
            while (!d.finished()) {
                int k = d.deflate(buf);
                if (k <= 0) break;
                o.write(buf, 0, k);
            }
            return o.toByteArray();
        } finally {
            d.end();
        }
    }

    private static void putI32toStream(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
        out.write((v >> 16) & 0xFF);
        out.write((v >> 24) & 0xFF);
    }
}
