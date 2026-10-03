package io.matrix.brain.runtime;

import io.matrix.transcoders.AudioFFTEncoder;
import io.matrix.transcoders.VisionEdgeEncoder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TRUE-W4 — Inbox watcher with REAL transcoders.
 *
 * <p>Polls {@code data/inbox/} for new files. Text/CSV files are ingested
 * via the persistent HDC store. Audio files ({@code .wav}, {@code .raw})
 * are processed by the real {@link AudioFFTEncoder} (DFT → frequency
 * bands → HDC). Image files ({@code .png}, {@code .jpg}, {@code .bmp})
 * are processed by the real {@link VisionEdgeEncoder} (Sobel edge
 * detection → shape primitives → HDC).</p>
 *
 * <p>Idempotent: FNV-1a hash of absolute path; unchanged mtime = skip.</p>
 */
public final class RealInboxWatcher {

    private static final Logger LOG = Logger.getLogger(RealInboxWatcher.class.getName());

    private final Path inboxDir;
    private final PersistentHdcStore hdcStore;
    private final AudioFFTEncoder audioEncoder;
    private final VisionEdgeEncoder imageEncoder;
    /**
     * Files refused during the most recent scans, with the reason.
     *
     * <p>RECON-W32.1. A refused file used to look identical to an absent one: the scan
     * simply did not ingest it and said nothing. An operator who drops a file into an
     * inbox and gets silence has no way to learn the system rejected it.</p>
     */
    /**
     * Fixed timestamp for audio frames, so a re-scan of the same file is byte-identical.
     * Unit: milliseconds. Any constant works; the value is not a claim about time.
     */
    private static final long FIXED_FRAME_CLOCK_MS = 0L;

    private final java.util.List<String> rejected = java.util.Collections.synchronizedList(
        new java.util.ArrayList<>());

    /** Refusals from recent scans. Unit: one entry per rejected file, newest last. */
    public java.util.List<String> rejections() {
        synchronized (rejected) {
            return java.util.List.copyOf(rejected);
        }
    }

    /** Path → last seen modified-time (for change detection). */
    private final Map<String, Long> lastSeenMTime = new ConcurrentHashMap<>();
    /** Last ingest summary for /v1/status. */
    private volatile String lastIngest = "(none yet)";

    public RealInboxWatcher(Path inboxDir, PersistentHdcStore hdcStore) {
        this.inboxDir = inboxDir;
        this.hdcStore = hdcStore;
        // RECON-W32.5: a FIXED clock, not the system clock. The watcher's perceptions
        // are persisted with a content hash, so a moving timestamp would make two scans
        // of the same file disagree and defeat deduplication.
        this.audioEncoder = new AudioFFTEncoder(256, 8, 42L,
            io.matrix.transcoders.AudioFFTEncoder.fixedClock(FIXED_FRAME_CLOCK_MS));
        this.imageEncoder = new VisionEdgeEncoder(256, 32);
    }

    /** Scan for new files; return count ingested. */
    public int scan() {
        if (!Files.exists(inboxDir)) return 0;
        int ingested = 0;
        try (var stream = Files.list(inboxDir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(p)) continue;
                long mtime = Files.getLastModifiedTime(p).toMillis();
                Long prev = lastSeenMTime.get(p.toString());
                if (prev != null && prev == mtime) continue;
                if (ingest(p)) {
                    lastSeenMTime.put(p.toString(), mtime);
                    ingested++;
                }
            }
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Inbox scan failed: {0}", ex.getMessage());
            return ingested;
        }
        if (ingested > 0) {
            lastIngest = ingested + " file(s) ingested at " + System.currentTimeMillis();
        }
        return ingested;
    }

    /** Ingest a single file with type-aware real-engine transcoding. */
    public boolean ingest(Path path) {
        try {
            String name = path.getFileName().toString().toLowerCase();
            // Nullable by design: a transcoder returns null to REFUSE, which the
            // null check below turns into a recorded rejection rather than a stored claim.
            String content;
            String transcoder;

            if (name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".csv")) {
                content = readText(path);
                transcoder = "text";
            } else if (name.endsWith(".wav") || name.endsWith(".raw")) {
                content = transcodeAudio(path);
                transcoder = "MediaDecoding.decodeWav+AudioFFTEncoder";
            } else if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".bmp")) {
                content = transcodeImage(path);
                transcoder = "MediaDecoding.decodePng+VisionEdgeEncoder";
            } else if (name.endsWith(".jsonl") || name.endsWith(".ndjson")) {
                // RECON-W32.3: a sensor stream is TREND data, not an opaque blob. Stored
                // raw, "What is the temperature?" had nothing to match; before W32.1 it
                // fell through to a SHA-256 fingerprint, so the temperature existed only
                // as a hash of a file containing it.
                SensorStreamDecoder.Stream stream =
                    SensorStreamDecoder.decode(readText(path));
                // ingestFile returns boolean, so a refusal is `return false`, not null.
                if (stream == null) return false;
                // The store gets the SHORT claim so retrieval can find it; the log gets
                // the full reading so nothing is lost to the scorer's length penalty.
                // ONE FACT PER FIELD. Each claim is short enough for the current scorer
                // to retrieve, so every field in the stream is answerable rather than
                // only the first. The full reading still goes to the log.
                java.util.List<String> claims = stream.facts();
                String detail = stream.detail(path.getFileName().toString());
                if (detail != null) {
                    LOG.log(Level.INFO, "Inbox: sensor detail {0}", new Object[]{detail});
                }
                for (String claim : claims) {
                    hdcStore.teach(inboxId(path) + "-" + claimToken(claim), claim);
                }
                transcoder = "SensorStreamDecoder.decode+trends";
                content = claims.isEmpty() ? null : claims.get(0);
                if (content == null) {
                    // Well-formed but with nothing honest to state (a single reading, or
                    // no numeric field). Not an error; there is simply no perception.
                    rejected.add(name + " -> no trend: the stream is well formed but has "
                        + "fewer than two readings of any numeric field, so no direction "
                        + "can be established");
                    return false;
                }
            } else {
                // Default: SHA-256 fingerprint fallback. Honest about what it is: a
                // fingerprint, NOT a perception, and named as such so it cannot be
                // confused with something the mind decoded and understood.
                content = "unreadable content, SHA-256 fingerprint " + sha256Hex(path)
                    + " (not a perception: no decoder for this format)";
                transcoder = "SHA-256-fallback";
            }

            // RECON-W32.1: refuse rather than persist a measurement that does not exist.
            // A null content means the decoder declined — a text file named .wav, a
            // truncated stream, an unsupported colour type. Persisting anything here is
            // exactly the W32 fabrication.
            if (content == null || content.isBlank()) {
                MediaDecoding.Classification c = MediaDecoding.classify(path, Files.readAllBytes(path));
                rejected.add(name + " -> " + (c == null ? "undecodable" : c.reason()));
                LOG.log(Level.WARNING,
                    "Inbox: REFUSED {0} ({1}); nothing is perceived from a file that "
                        + "cannot be decoded", new Object[]{path, c == null ? "undecodable" : c.reason()});
                return false;
            }

            hdcStore.teach(inboxId(path), content);
            // The same perception, addressable by file name. See addressablePerception().
            String addressable = addressablePerception(path.getFileName().toString(), content);
            if (addressable != null && !addressable.equals(content)) {
                hdcStore.teach(inboxId(path) + "-addr", addressable);
            }
            LOG.log(Level.INFO, "Inbox: ingested {0} via {1} ({2} chars)",
                new Object[]{path.getFileName(), transcoder, content.length()});
            return true;
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Inbox ingest failed for {0}: {1}",
                new Object[]{path, ex.getMessage()});
            return false;
        } catch (PersistentHdcStore.PromotionRejectedException ex) {
            // RECON-W32.1: the promotion gate refusing a fact is a NORMAL outcome, not a
            // crash. Uncaught, one refused file aborted the entire scan and the good
            // files in the same batch were silently lost — which is the same failure
            // shape as a refusal that is invisible: the operator sees "ingested: 0" and
            // cannot tell a policy decision from a broken pipeline.
            rejected.add(path.getFileName() + " -> " + ex.reason()
                + " (promotion gate refused; other files in this scan continue)");
            LOG.log(Level.WARNING,
                "Inbox: REFUSED {0} by the promotion gate ({1}); scan continues",
                new Object[]{path, ex.reason()});
            return false;
        }
    }

    /**
     * Stable, human-readable id for an ingested file: name plus a content-path hash.
     *
     * <p>Article III: derived from the path, not the clock, so the same file always
     * produces the same id.</p>
     */
    /**
     * Short deterministic suffix distinguishing two claims about the same file.
     * Unit: hex digest prefix. Derived from the claim text, not the clock.
     */
    private static String claimToken(String claim) {
        return Long.toHexString(fnv1a64(claim));
    }

    private static String inboxId(Path path) {
        return "inbox-" + path.getFileName() + "-"
            + Long.toHexString(fnv1a64(path.toString()));
    }

    private String readText(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length > 4096) {
            return new String(bytes, 0, 4096, java.nio.charset.StandardCharsets.UTF_8);
        }
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Audio transcoding: DECODE the file, then run the real AudioFFTEncoder.
     *
     * <p>RECON-W32.1. This used to read the first 1024 BYTES and map each byte to one
     * sample, so a 44-byte RIFF header was "heard" as 44 samples and a plain text file
     * produced a confident audio perception with provenance. Now the file is decoded
     * through {@link MediaDecoding} and anything undecodable is REFUSED — see
     * {@link #transcode(Path, byte[])}.</p>
     */
    private String transcodeAudio(Path path) throws IOException {
        byte[] raw = Files.readAllBytes(path);
        MediaDecoding.Audio audio = MediaDecoding.decodeWav(raw);
        if (audio == null) return null;          // refuse rather than invent
        // Feed the decoder's REAL waveform and the header's REAL sample rate.
        var frame = audioEncoder.computeDFT(audio.samples(), audio.sampleRate());
        var bands = audioEncoder.extractBands(frame);
        boolean[] hdc = audioEncoder.encodeToHDC(bands);
        double totalEnergy = bands.stream().mapToDouble(b -> b.energy()).sum();
        // RECON-W32.1: report BOTH resolutions. The 8-band sum is a coarse
        // "mimics human hearing" grouping and cannot separate a 100 Hz tone from a
        // 440 Hz one at 8 kHz, because each band spans 500 Hz. The peak estimate works
        // on the raw spectrum at one-bin resolution. Publishing only the band would
        // make two different tones indistinguishable in the store.
        io.matrix.transcoders.AudioFFTEncoder.FrequencyBand dominant = dominantBand(bands);
        MediaDecoding.PeakEstimate peak =
            MediaDecoding.dominantFrequency(frame.magnitudes(), audio.sampleRate());
        StringBuilder sb = new StringBuilder();
        sb.append("audio: ").append(audio.sampleRate()).append(" Hz ")
          .append(audio.samples().length).append(" samples, ")
          .append(bands.size()).append(" bands, dominant band ")
          .append((int) Math.round(dominant.lowHz())).append("-")
          .append((int) Math.round(dominant.highHz())).append(" Hz");
        if (peak.isSilent()) {
            sb.append(", no dominant frequency (silent spectrum)");
        } else {
            sb.append(String.format(java.util.Locale.ROOT,
                ", dominant frequency %.1f Hz (peak bin %d, sharpness %.3f)",
                peak.hz(), peak.bin(), peak.sharpness()));
        }
        sb.append(String.format(java.util.Locale.ROOT,
            ", total energy %.4f", totalEnergy));
        return sb.toString();
    }

    /**
     * The highest-energy band, or a silent band when the signal carries no energy.
     *
     * <p>Uses the encoder's own Hz bounds rather than re-deriving them from a band index,
     * because the band edges are the encoder's business and assuming a uniform layout is
     * how a frequency ends up mis-reported by an octave.</p>
     */
    private static io.matrix.transcoders.AudioFFTEncoder.FrequencyBand dominantBand(
            java.util.List<io.matrix.transcoders.AudioFFTEncoder.FrequencyBand> bands) {
        io.matrix.transcoders.AudioFFTEncoder.FrequencyBand best =
            new io.matrix.transcoders.AudioFFTEncoder.FrequencyBand(0, 0, 0);
        for (var b : bands) {
            if (b.energy() > best.energy()) best = b;
        }
        return best;
    }

    /**
     * Image transcoding: DECODE the PNG, then run the real VisionEdgeEncoder.
     *
     * <p>RECON-W32.1. This used to copy file bytes into a 32x32 buffer and pad with
     * {@code i % 256}, so a solid-red PNG and a text file produced the same kind of
     * "image primitives" and the colour was a property of zlib compression rather than
     * of the image. Undecodable input is now REFUSED.</p>
     */
    private String transcodeImage(Path path) throws IOException {
        byte[] raw = Files.readAllBytes(path);
        MediaDecoding.Image img = MediaDecoding.decodePng(raw);
        if (img == null) return null;          // refuse rather than invent
        // VisionEdgeEncoder wants 8-bit grayscale; derive it from the DECODED colour.
        int w = img.width(), h = img.height();
        byte[] gray = new byte[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                gray[y * w + x] = (byte) ((img.r(x, y) * 77 + img.g(x, y) * 151
                                        + img.b(x, y) * 28) >> 8);
            }
        }
        var prims = imageEncoder.detectPrimitives(gray, w, h);
        boolean[] hdc = imageEncoder.encodeToHDC(prims);
        String colour = dominantColour(img);
        return "image: " + w + "x" + h + " px, dominant colour " + colour
            + ", " + prims.size() + " edge primitives, total magnitude "
            + String.format(java.util.Locale.ROOT, "%.4f",
                prims.stream().mapToDouble(p -> p.magnitude()).sum());
    }

    /**
     * An ADDRESSABLE restatement of a perception: which file, what was seen in it.
     *
     * <p>RECON-W32.7. The descriptive fact ("image: 32x32 px, dominant colour red") and
     * the source of that perception live in different places: the description in the fact
     * text, the source in the record id. That is deliberate — putting the filename in the
     * text costs three content tokens, and {@code ContentSimilarity} punishes length
     * enough that "What colour is red32.png?" scored 0.07 against a 0.20 floor and the
     * mind refused a question it could answer.</p>
     *
     * <p>So the perception is indexed TWICE: once descriptively for a question about what
     * was seen, and once addressably for a question about a named file. Two facts per
     * image, and that duplication is the price of a scorer whose precision term makes
     * long facts unfindable. Recorded rather than hidden.</p>
     *
     * @param fileName source file name
     * @param summary  the descriptive fact
     * @return a short claim naming the file and its dominant attribute
     */
    static String addressablePerception(String fileName, String summary) {
        String stem = fileName == null ? "" : fileName;
        int dot = stem.lastIndexOf('.');
        if (dot > 0) stem = stem.substring(0, dot);
        String colour = between(summary, "dominant colour ", ",");
        if (colour != null) return stem + " colour " + colour;
        String band = between(summary, "dominant frequency ", " Hz");
        if (band != null) return stem + " frequency " + band + " Hz";
        return null;
    }

    /** Text between two markers, or null when either marker is absent. */
    private static String between(String text, String open, String close) {
        int a = text.indexOf(open);
        if (a < 0) return null;
        int b = text.indexOf(close, a + open.length());
        if (b < 0) return null;
        String v = text.substring(a + open.length(), b).trim();
        return v.isEmpty() ? null : v;
    }

    /**
     * The most frequent colour name, computed from DECODED pixels.
     *
     * <p>Classified by hue, saturation and brightness rather than by comparing channels
     * pairwise. The first version was a chain of nested ternaries that tested red before
     * blue, so a dark blue (20, 20, 220) was reported as "dark": the shape of the code
     * was the bug. Hue is the quantity that actually names a colour, and a
     * bright-saturated-blue versus a dark-desaturated-blue distinction falls out of the
     * saturation and brightness tests instead of needing its own branch.</p>
     *
     * <p>Names are coarse on purpose. An encoder that cannot segment regions must not
     * imply it: "red" and "dark red" are claims the pixels support, "a red circle" is
     * not.</p>
     *
     * @param img decoded image
     * @return a colour name, one of white, black, grey, red, green, blue, yellow, cyan,
     *         magenta, orange, or a lightness-qualified variant
     */
    static String dominantColour(MediaDecoding.Image img) {
        // 4x4x4 colour cube: cheap, and coarse enough not to invent precision.
        long[] buckets = new long[COLOUR_CUBE_EDGE * COLOUR_CUBE_EDGE * COLOUR_CUBE_EDGE];
        for (int y = 0; y < img.height(); y++) {
            for (int x = 0; x < img.width(); x++) {
                int r = img.r(x, y) >> 6, g = img.g(x, y) >> 6, b = img.b(x, y) >> 6;
                buckets[(r << 4) | (g << 2) | b]++;
            }
        }
        int best = 0;
        for (int i = 1; i < buckets.length; i++) {
            if (buckets[i] > buckets[best]) best = i;
        }
        int r = (((best >> 4) & 3) * 64) + 32;
        int g = (((best >> 2) & 3) * 64) + 32;
        int b = ((best & 3) * 64) + 32;
        return colourName(r, g, b);
    }

    /** Edge length of the colour cube along one axis. Unit: buckets. */
    static final int COLOUR_CUBE_EDGE = 4;

    /** Brightness at or above which a colour is called white. Unit: 0-255. */
    static final int WHITE_THRESHOLD = 200;

    /** Brightness at or below which a colour is called black. Unit: 0-255. */
    static final int BLACK_THRESHOLD = 55;

    /** Saturation below which a colour is called grey. Unit: 0-255 spread. */
    static final int GREY_SPREAD = 40;

    /**
     * Name a colour from its RGB value, by hue.
     *
     * @param r red, 0-255
     * @param g green, 0-255
     * @param b blue, 0-255
     * @return the colour name
     */
    static String colourName(int r, int g, int b) {
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        int spread = max - min;

        if (max >= WHITE_THRESHOLD && spread <= GREY_SPREAD) return "white";
        if (max <= BLACK_THRESHOLD) return "black";
        if (spread <= GREY_SPREAD) return "grey";

        double hue;
        if (max == r) {
            hue = 60.0 * (((g - b) / (double) spread) % 6);
        } else if (max == g) {
            hue = 60.0 * (((b - r) / (double) spread) + 2);
        } else {
            hue = 60.0 * (((r - g) / (double) spread) + 4);
        }
        if (hue < 0) hue += 360.0;

        String base = switch (Math.round((float) (hue / HUE_SECTOR_DEGREES))) {
            case 0 -> "red";
            case 1 -> "yellow";
            case 2 -> "green";
            case 3 -> "cyan";
            case 4 -> "blue";
            case 5 -> "magenta";
            default -> "red";
        };
        // Orange is a HUE BAND, not a sector, and it straddles the 30-degree boundary
        // between the red and yellow sectors: 33 degrees is in the yellow sector but is
        // unambiguously orange to any reader. Testing the sector name instead of the hue
        // meant (235,140,20) came out "yellow". Range-checked on hue instead.
        if (hue >= ORANGE_HUE_DEGREES && hue < YELLOW_HUE_DEGREES) base = "orange";
        // Lightness is the HSL mid-point, NOT max/255. Using max made a vivid orange
        // (235,140,20) come out as "light yellow" simply because one channel was high,
        // which is a statement about the channel, not about how pale the colour looks.
        // (max+min)/2 is the conventional measure and gives the right answers.
        double lightness = (max + min) / 2.0 / 255.0;
        if (lightness <= DARK_LIGHTNESS) return "dark " + base;
        if (lightness >= LIGHT_LIGHTNESS) return "light " + base;
        return base;
    }

    /** Degrees of hue per named sector. Unit: degrees. 360/6. */
    static final double HUE_SECTOR_DEGREES = 60.0;

    /** Lower bound of the orange hue band. Unit: degrees, 0-360. */
    static final int ORANGE_HUE_DEGREES = 15;

    /** Upper bound of the orange hue band, where yellow begins. Unit: degrees, 0-360. */
    static final int YELLOW_HUE_DEGREES = 45;

    /**
     * HSL lightness at or below which a hue is qualified as dark. Unit: ratio in [0,1].
     * A saturated navy (40,40,110) lands at 0.29; a mid blue at 0.47.
     */
    static final double DARK_LIGHTNESS = 0.35;

    /**
     * HSL lightness at or above which a hue is qualified as light. Unit: ratio in [0,1].
     * A pale blue (180,180,250) lands at 0.84; a vivid orange at 0.50.
     */
    static final double LIGHT_LIGHTNESS = 0.80;

    private String sha256Hex(Path path) throws IOException {
        try {
            byte[] bytes = Files.readAllBytes(path);
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return "sha256:" + hex;
        } catch (java.security.NoSuchAlgorithmException ex) {
            return "no-sha256";
        }
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inbox_dir", inboxDir.toString());
        m.put("last_ingest", lastIngest);
        m.put("tracked_files", lastSeenMTime.size());
        m.put("audio_transcoder", "AudioFFTEncoder");
        m.put("image_transcoder", "VisionEdgeEncoder");
        return m;
    }

    private static long fnv1a64(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }
}
