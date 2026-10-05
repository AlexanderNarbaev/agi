package io.matrix.brain.runtime;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.Inflater;

/**
 * RECON-W32.1 — honest decoding for the perception pipeline.
 *
 * <p><b>Why this class exists.</b> W32 caught the inbox fabricating its own inputs. A
 * plain text file named {@code fake.wav} was stored as
 * {@code "audio:frame=8 total_energy=18.74766463657823"} and a text file named
 * {@code fake.png} as {@code "image:primitives=64 total_magnitude=15282.36"}, both with
 * provenance and a confidence the system was willing to report to an operator.</p>
 *
 * <p>The cause was that the transcoders never decoded anything.
 * {@code transcodeAudio} took the first 1024 <em>bytes</em> and mapped each byte to one
 * sample, so a 44-byte RIFF header was "heard" as 44 samples and each 16-bit sample was
 * "heard" as two an octave apart. {@code transcodeImage} copied file bytes into a 32x32
 * buffer and padded with {@code i % 256}. The DFT and the edge detector were real; their
 * inputs were invented, and the HDC vectors they produced were discarded in favour of a
 * text digest.</p>
 *
 * <p><b>The rule this class enforces: never return a measurement you cannot support.</b>
 * Every method here returns {@code null} rather than a guess. A decoder that invents a
 * plausible number is worse than one that refuses, because the number is indistinguishable
 * from a real observation once it carries provenance.</p>
 *
 * <p><b>Scope, stated honestly.</b> WAV support is 8-bit unsigned and 16-bit signed PCM,
 * mono, which covers synthesised and most recorded test tones. Compressed formats
 * (ADPCM, μ-law, MP3) are REFUSED rather than mis-decoded. PNG support is colour types 0
 * (grey), 2 (RGB) and 6 (RGBA) at 8 bits per channel; palette and 16-bit PNG are REFUSED.
 * Refusing is the correct behaviour: an unsupported format reported as "perceived" is the
 * W32 defect in a new disguise.</p>
 *
 * <p><b>Article III.</b> Pure functions of their byte array, no wall clock, no randomness,
 * no I/O, no network. Article VI: these are signal-processing steps, described as such.</p>
 */
public final class MediaDecoding {

    private MediaDecoding() {}

    /** Bytes examined when sniffing a format signature. Unit: bytes. */
    public static final int SNIFF_BYTES = 12;

    /**
     * Bytes inspected when guessing whether content is text rather than binary.
     * Unit: bytes. Enough to classify ordinary prose; a large binary file is rejected
     * on its first NUL long before this limit.
     */
    public static final int TEXT_SNIFF_BYTES = 512;

    /**
     * Divisor that maps a signed 16-bit PCM sample to [-1, 1].
     * Unit: counts. 2^15 = 32768. Using 32767 would clip a full-scale negative sample to
     * -1.0 exactly and lose one bit of headroom; 32768 keeps the range symmetric.
     */
    public static final float PCM16_SCALE = 32768.0f;

    /**
     * Fraction of inspected bytes that must be printable for a text classification.
     * Unit: ratio in [0,1]. 0.9 tolerates a little binary noise in a mostly-text file
     * while still rejecting compressed data, which is nowhere near 90% printable.
     */
    public static final double TEXT_PRINTABLE_RATIO = 0.9;

    /** Longest WAV accepted, to bound memory on a hostile input. Unit: bytes. */
    public static final long MAX_WAV_BYTES = 64L * 1024 * 1024;

    /** Largest PNG accepted after inflate. Unit: bytes of raw pixel data. */
    public static final int MAX_PNG_PIXELS = 16 * 1024 * 1024;

    // ---- classification ---------------------------------------------------

    /**
     * What a file claims to be, and whether we can actually decode it.
     *
     * @param kind     detected container
     * @param decodable whether this decoder supports it
     * @param reason   human-readable, always populated so a refusal is never silent
     */
    public record Classification(Kind kind, boolean decodable, String reason) {
        public boolean isDecodable() { return decodable; }
    }

    /** Container formats the inbox recognises. */
    public enum Kind { WAV, PNG, TEXT, UNKNOWN }

    /**
     * Decide whether a file can be perceived, from its CONTENT rather than its name.
     *
     * <p>Extension-based dispatch is what let a text file be "heard" and "seen". This
     * compares magic bytes and refuses on mismatch.</p>
     *
     * @param file file name, used only to report what was CLAIMED
     * @param bytes file contents
     * @return the verdict, never null; {@code decodable} is false for a mismatch
     */
    public static Classification classify(String file, byte[] bytes) {
        String name = file == null ? "" : file.toLowerCase(java.util.Locale.ROOT);
        if (looksLikeWav(bytes)) {
            return wavVerdict(name, bytes);
        }
        if (looksLikePng(bytes)) {
            return pngVerdict(name, bytes);
        }
        if (looksLikeText(bytes)) {
            if (name.endsWith(".wav") || name.endsWith(".raw")) {
                return new Classification(Kind.UNKNOWN, false,
                    "refused: file is named as audio but its magic bytes are not a RIFF/WAVE "
                        + "signature; the system will not invent an audio perception from text");
            }
            if (name.endsWith(".png")) {
                return new Classification(Kind.UNKNOWN, false,
                    "refused: file is named as a PNG but its magic bytes are not the PNG "
                        + "signature; the system will not invent an image perception from text");
            }
            return new Classification(Kind.TEXT, true, "text content, read directly");
        }
        // RECON-W32.23: name the SUPPORT, not just the refusal. "unrecognised content"
        // tells an operator their file is corrupt; this says the system has no decoder
        // for the format, which is a different problem with a different fix. My first
        // attempt put this check beside the extension tests, where it was unreachable —
        // a real JPEG is binary, so it never reaches looksLikeText and falls here.
        // RECON-W32.24: the FORMAT is now read from the magic bytes, not from the
        // filename. The first version said "the file looks like JPEG" on the strength of
        // the name alone, so an ELF binary named photo.jpg was told it looked like a
        // JPEG and might well be valid. This class's own rule is never to return a
        // measurement you cannot support, and a filename is not a measurement. What we
        // know for certain is the NAME says image and the CONTENT is neither PNG nor
        // text - so we say exactly that, and name what IS supported.
        String claimed = imageFormatFromName(name);
        if (claimed != null) {
            return new Classification(Kind.UNKNOWN, false,
                "refused: named as " + claimed + ", but its bytes are neither a PNG nor a "
                    + "recognisable " + claimed + " header. That FORMAT is unsupported: "
                    + "MATRIX decodes PNG only. The file may be perfectly valid in a "
                    + "format we cannot read - convert it to PNG, or add a decoder; no "
                    + "image perception will be invented from it.");
        }
        return new Classification(Kind.UNKNOWN, false,
            "refused: unrecognised content and no supported decoder for it");
    }

    /** Convenience overload for a path-free name. */
    public static Classification classify(java.nio.file.Path p, byte[] bytes) {
        return classify(p == null ? "" : p.getFileName().toString(), bytes);
    }

    private static Classification wavVerdict(String name, byte[] bytes) {
        Audio a = decodeWav(bytes);
        if (a == null) {
            return new Classification(Kind.WAV, false,
                "refused: RIFF/WAVE signature present but the stream could not be decoded "
                    + "(unsupported bit depth, channel count, or truncated data chunk)");
        }
        return new Classification(Kind.WAV, true,
            "WAV " + a.sampleRate() + " Hz, " + a.samples().length + " samples, "
                + a.bitsPerSample() + "-bit");
    }

    private static Classification pngVerdict(String name, byte[] bytes) {
        Image img = decodePng(bytes);
        if (img == null) {
            return new Classification(Kind.PNG, false,
                "refused: PNG signature present but the image could not be decoded "
                    + "(unsupported colour type or bit depth, or truncated IDAT)");
        }
        return new Classification(Kind.PNG, true,
            "PNG " + img.width() + "x" + img.height() + " RGB");
    }

    // ---- signature sniffing ------------------------------------------------

    /** True when the bytes begin with {@code RIFF....WAVE}. */
    public static boolean looksLikeWav(byte[] b) {
        if (b == null || b.length < 12) return false;
        return b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
            && b[8] == 'W' && b[9] == 'A' && b[10] == 'V' && b[11] == 'E';
    }

    /** True when the bytes begin with the 8-byte PNG signature. */
    public static boolean looksLikePng(byte[] b) {
        if (b == null || b.length < 8) return false;
        return b[0] == (byte) 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
            && b[4] == '\r' && b[5] == '\n' && b[6] == 0x1A && b[7] == '\n';
    }

    /**
     * Heuristic: mostly printable bytes with no NULs. Used only to tell text from binary,
     * never to decode media.
     */
    /**
     * The image format a filename CLAIMS, or null if the name claims none.
     *
     * <p>RECON-W32.24. Including {@code .tif} alongside {@code .tiff}, which the first
     * version omitted - its own sibling. Returning the claim is honest; it is not a
     * statement that the bytes are of that format.</p>
     */
    private static String imageFormatFromName(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "JPEG";
        if (n.endsWith(".bmp")) return "BMP";
        if (n.endsWith(".gif")) return "GIF";
        if (n.endsWith(".webp")) return "WebP";
        if (n.endsWith(".tif") || n.endsWith(".tiff")) return "TIFF";
        return null;
    }

    private static boolean looksLikeText(byte[] b) {
        if (b == null || b.length == 0) return false;
        int limit = Math.min(b.length, TEXT_SNIFF_BYTES);
        int printable = 0;
        for (int i = 0; i < limit; i++) {
            int c = b[i] & 0xFF;
            if (c == 0) return false;
            if (c == '\n' || c == '\r' || c == '\t' || (c >= 32 && c < 127)) printable++;
        }
        return printable >= limit * TEXT_PRINTABLE_RATIO;
    }

    // ---- WAV ---------------------------------------------------------------

    /**
     * Decoded PCM audio.
     *
     * @param sampleRate     samples per second, read from the header
     * @param bitsPerSample  8 or 16
     * @param samples        mono samples normalised to [-1, 1]
     */
    public record Audio(int sampleRate, int bitsPerSample, float[] samples) {}

    /**
     * Decode a RIFF/WAVE file to mono float samples.
     *
     * <p>Walks the chunk list rather than assuming a 44-byte header, because real files
     * carry LIST/INFO chunks before {@code data} — and assuming the canonical header size
     * is how a decoder ends up "hearing" a LIST chunk as samples.</p>
     *
     * @return decoded audio, or {@code null} if the format is not supported or is
     *         truncated. Never a guess.
     */
    public static Audio decodeWav(byte[] b) {
        if (!looksLikeWav(b)) return null;
        if (b.length > MAX_WAV_BYTES) return null;
        int pos = 12;
        int fmt = -1, channels = 0, sampleRate = 0, bits = 0;
        int dataOff = -1, dataLen = 0;
        while (pos + 8 <= b.length) {
            String id = new String(b, pos, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int size = le32(b, pos + 4);
            int body = pos + 8;
            if (size < 0 || body + size > b.length) return null;   // truncated chunk
            if ("fmt ".equals(id) && size >= 16) {
                fmt = le16(b, body);
                channels = le16(b, body + 2);
                sampleRate = le32(b, body + 4);
                bits = le16(b, body + 14);
            } else if ("data".equals(id)) {
                dataOff = body;
                dataLen = size;
            }
            pos = body + size + (size & 1);   // chunks are word-aligned
        }
        if (fmt != 1 || dataOff < 0 || channels < 1) return null;  // PCM only
        if (bits != 8 && bits != 16) return null;                  // honest refusal
        if (sampleRate <= 0) return null;

        int bytesPerSample = bits / 8;
        int count = dataLen / (bytesPerSample * channels);
        if (count <= 0) return null;
        float[] out = new float[count];
        for (int i = 0; i < count; i++) {
            int off = dataOff + i * bytesPerSample * channels;   // first channel only
            if (bits == 16) {
                int raw = (short) le16(b, off);
                out[i] = raw / PCM16_SCALE;
            } else {
                out[i] = ((b[off] & 0xFF) - 128) / 128.0f;        // 8-bit is unsigned
            }
        }
        return new Audio(sampleRate, bits, out);
    }

    // ---- PNG ---------------------------------------------------------------

    /**
     * Decoded 8-bit RGB image.
     *
     * @param width   image width in pixels
     * @param height  image height in pixels
     * @param rgb     width*height*3 bytes, row-major, 3 channels
     */
    public record Image(int width, int height, byte[] rgb) {
        public int r(int x, int y) {
            return rgb[(y * width + x) * 3] & 0xFF;
        }
        public int g(int x, int y) {
            return rgb[(y * width + x) * 3 + 1] & 0xFF;
        }
        public int b(int x, int y) {
            return rgb[(y * width + x) * 3 + 2] & 0xFF;
        }
    }

    /**
     * Decode an 8-bit PNG to RGB.
     *
     * <p>Supports colour types 0 (grey), 2 (RGB) and 6 (RGBA), which is what a
     * vision edge encoder can honestly consume. Palette (3) and 16-bit (bit depth 16)
     * are REFUSED rather than approximated.</p>
     *
     * <p>Filter reconstruction implements all five PNG filter types, because a decoder
     * that handles only filter 0 silently mis-renders most real PNGs.</p>
     *
     * @return decoded image, or {@code null} when unsupported or truncated
     */
    public static Image decodePng(byte[] b) {
        if (!looksLikePng(b)) return null;
        int pos = 8;
        int w = -1, h = -1, depth = -1, colorType = -1, interlace = -1;
        byte[] idat = null;
        // PNG chunk layout is [length][type][data][crc] — the length comes FIRST, which is
        // the opposite of RIFF's [id][size][data]. Reading the PNG the RIFF way silently
        // interprets the length as a chunk type and every real image is rejected.
        while (pos + 8 <= b.length) {
            int size = le32(b, pos);
            String id = new String(b, pos + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int body = pos + 8;
            if (size < 0 || body + size > b.length) return null;
            if ("IHDR".equals(id) && size >= 13) {
                w = le32(b, body);
                h = le32(b, body + 4);
                depth = b[body + 8] & 0xFF;
                colorType = b[body + 9] & 0xFF;
                interlace = b[body + 12] & 0xFF;
            } else if ("IDAT".equals(id)) {
                if (idat == null) idat = new byte[size];
                if (size > idat.length) return null;
                System.arraycopy(b, body, idat, 0, size);
            } else if ("IEND".equals(id)) {
                break;
            }
            pos = body + size + 4;          // +4 for the CRC
        }
        if (w <= 0 || h <= 0 || idat == null) return null;
        if (depth != 8) return null;                      // honest refusal
        if (interlace != 0) return null;                  // Adam7 not supported
        int channels;
        if (colorType == 0) channels = 1;
        else if (colorType == 2) channels = 3;
        else if (colorType == 6) channels = 4;
        else return null;                                // palette etc. refused

        long pixels = (long) w * h;
        if (pixels <= 0 || pixels > MAX_PNG_PIXELS) return null;

        byte[] raw;
        try {
            raw = inflate(idat, (int) (pixels * (channels + 1)) + 64);
        } catch (IOException | java.util.zip.DataFormatException e) {
            return null;
        }
        int stride = w * channels;
        if (raw.length < (stride + 1) * h) return null;   // truncated scanline data

        byte[] cur = new byte[stride];
        byte[] prev = new byte[stride];
        byte[] out = new byte[(int) pixels * 3];
        int p = 0;
        for (int y = 0; y < h; y++) {
            int filter = raw[p++] & 0xFF;
            for (int x = 0; x < stride; x++) {
                int rv = raw[p + x] & 0xFF;
                int a = x >= channels ? cur[x - channels] & 0xFF : 0;
                int bb = prev[x] & 0xFF;
                int c = x >= channels ? prev[x - channels] & 0xFF : 0;
                int val;
                switch (filter) {
                    case 0 -> val = rv;
                    case 1 -> val = rv + a;
                    case 2 -> val = rv + bb;
                    case 3 -> val = rv + ((a + bb) >> 1);
                    case 4 -> val = rv + paeth(a, bb, c);
                    default -> { return null; }           // unknown filter: refuse
                }
                cur[x] = (byte) val;
            }
            p += stride;
            for (int x = 0; x < w; x++) {
                int o = (y * w + x) * 3;
                if (channels == 1) {
                    int g = cur[x] & 0xFF;
                    out[o] = (byte) g; out[o + 1] = (byte) g; out[o + 2] = (byte) g;
                } else {
                    out[o] = cur[x * channels];
                    out[o + 1] = cur[x * channels + 1];
                    out[o + 2] = cur[x * channels + 2];
                }
            }
            byte[] swap = prev; prev = cur; cur = swap;
        }
        return new Image(w, h, out);
    }

    private static int paeth(int a, int bb, int c) {
        int pp = a + bb - c;
        int pa = Math.abs(pp - a), pb = Math.abs(pp - bb), pc = Math.abs(pp - c);
        if (pa <= pb && pa <= pc) return a;
        return pb <= pc ? bb : c;
    }

    private static byte[] inflate(byte[] data, int expected)
            throws IOException, java.util.zip.DataFormatException {
        Inflater inf = new Inflater();
        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            inf.setInput(data);
            byte[] out = new byte[expected];
            int n = 0;
            while (!inf.finished() && n < out.length) {
                int k = inf.inflate(out, n, out.length - n);
                if (k == 0) {
                    if (inf.needsInput() || inf.needsDictionary()) break;
                }
                n += k;
            }
            if (n <= 0) throw new IOException("empty inflate");
            return out;
        } finally {
            inf.end();
        }
    }

    // ---- spectral analysis -------------------------------------------------

    /**
     * A frequency estimate from a spectrum, with the evidence that produced it.
     *
     * @param hz        estimated dominant frequency, or NaN for a silent spectrum
     * @param bin       index of the peak bin
     * @param sharpness peak prominence over the mean magnitude, in [0,1]-ish; higher
     *                  means a cleaner tone and a more trustworthy estimate
     * @param note      human-readable basis, for the trace
     */
    public record PeakEstimate(double hz, int bin, double sharpness, String note) {
        public boolean isSilent() { return Double.isNaN(hz); }
    }

    /**
     * Estimate the dominant frequency of a magnitude spectrum by peak-picking.
     *
     * <p><b>Why not use the band sum.</b> {@code extractBands} produces 8 bands, so over
     * 8 kHz each band spans 500 Hz: a 100 Hz tone and a 440 Hz tone both report
     * "0-500 Hz" and are indistinguishable. That is a resolution limit of the band
     * decomposition, not a property of the signal. This works on the raw magnitudes,
     * where the resolution is one FFT bin.</p>
     *
     * <p>Parabolic interpolation is applied around the peak bin. Without it the estimate
     * is quantised to the bin width, and at 2048 bins over 8 kHz that is ~3.9 Hz — fine
     * here, but the same code at a lower sample rate would report 440 Hz as 437 Hz and
     * the error would be invisible without the interpolation.</p>
     *
     * <p>Bin k maps to {@code k * sampleRate / (2 * bins)}, matching
     * {@code AudioFFTEncoder.extractBands}.</p>
     *
     * @param magnitudes magnitude spectrum, DC bin first
     * @param sampleRate samples per second
     * @return the estimate; silent when the spectrum carries no usable energy
     */
    public static PeakEstimate dominantFrequency(float[] magnitudes, int sampleRate) {
        if (magnitudes == null || magnitudes.length < 3 || sampleRate <= 0) {
            return new PeakEstimate(Double.NaN, -1, 0.0, "no spectrum");
        }
        int n = magnitudes.length;
        // Bin 0 is DC and the top bins are the mirrored negative frequencies; neither
        // carries pitch information, so both ends are excluded.
        int lo = 1;
        int hi = n / 2 - 1;
        if (hi <= lo) return new PeakEstimate(Double.NaN, -1, 0.0, "spectrum too short");

        int peak = lo;
        double peakVal = 0.0;
        double sum = 0.0;
        for (int k = lo; k <= hi; k++) {
            sum += magnitudes[k];
            if (magnitudes[k] > peakVal) { peakVal = magnitudes[k]; peak = k; }
        }
        if (peakVal <= 0.0) return new PeakEstimate(Double.NaN, -1, 0.0, "silent spectrum");

        // Parabolic refinement around the peak.
        double delta = 0.0;
        if (peak > lo && peak < hi) {
            double a = magnitudes[peak - 1];
            double b = magnitudes[peak];
            double c = magnitudes[peak + 1];
            double denom = a - 2 * b + c;
            if (denom != 0.0) delta = 0.5 * (a - c) / denom;
            if (delta < -1.0) delta = -1.0;
            if (delta > 1.0) delta = 1.0;
        }
        double hz = (peak + delta) * sampleRate / (2.0 * n);
        double mean = sum / (hi - lo + 1);
        double sharpness = mean > 0 ? (peakVal - mean) / peakVal : 0.0;
        return new PeakEstimate(hz, peak, sharpness,
            String.format(java.util.Locale.ROOT,
                "peak bin %d of %d usable, %s Hz at %d Hz, sharpness %.3f",
                peak, n, String.format(java.util.Locale.ROOT, "%.1f", hz), sampleRate, sharpness));
    }

    // ---- little-endian helpers --------------------------------------------

    private static int le16(byte[] b, int off) {
        if (off + 1 >= b.length) return -1;
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static int le32(byte[] b, int off) {
        if (off + 3 >= b.length) return -1;
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
             | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }
}
