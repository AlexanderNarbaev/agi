package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RECON-W32.1 — the perception pipeline was fabricating its inputs.
 *
 * <p><b>The defect this exists to prevent.</b> W32 dropped a plain TEXT file named
 * {@code fake.wav} into the inbox and the mind stored
 * {@code "audio:frame=8 hdc_dim=256 total_energy=18.74766463657823"} — a confident,
 * provenance-stamped audio perception of a file containing no audio. A text file named
 * {@code fake.png} likewise produced {@code "image:primitives=64"}.</p>
 *
 * <p>The cause: {@code transcodeAudio} never decoded WAV. It took the first 1024 <em>bytes</em>
 * of the file and mapped each byte to one sample, so the 44-byte RIFF header was heard as
 * 44 samples and each 16-bit sample was heard as two an octave apart.
 * {@code transcodeImage} never decoded PNG: it copied file bytes into a 32x32 buffer and
 * padded with {@code i % 256}. The DFT and the edge detector are real; their inputs were
 * fabricated, and the HDC vectors they produced were discarded in favour of a text
 * digest.</p>
 *
 * <p>Why 494 existing tests missed it: {@code RealInboxWatcherWiringTest} asserts wiring
 * only — the watcher is constructed, the transcoder fields are named correctly, and
 * {@code scan()} returns at least one. No test fed a real media file and asserted a value,
 * and no test asserted that a malformed file is rejected. These tests close that gap from
 * both sides: a malformed file must be refused, and a well-formed one must yield the
 * signal it actually contains.</p>
 *
 * <p>Article III: no randomness, no wall clock, no network. Fixtures are synthesised
 * deterministically so the expected values are exact rather than approximate.</p>
 */
class MediaDecodingTest {

    // ---- file-format detection -------------------------------------------

    @Test
    void recognisesARealWavByItsMagicBytes() {
        assertTrue(MediaDecoding.looksLikeWav(MediaDecodingTestFixtures.riffWaveHeader()),
            "a RIFF/WAVE header must be recognised");
    }

    @Test
    void rejectsTextThatMerelyClaimsToBeWav() {
        byte[] text = "I am not audio, I am text pretending to be a wav file"
            .getBytes(StandardCharsets.UTF_8);
        assertFalse(MediaDecoding.looksLikeWav(text),
            "the exact W32 fabrication input must be refused");
    }

    @Test
    void recognisesARealPngByItsMagicBytes() {
        assertTrue(MediaDecoding.looksLikePng(MediaDecodingTestFixtures.pngSignature()));
        byte[] real = MediaDecodingTestFixtures.pngSignature();
        real[1] = 'X';                       // break the second magic byte
        assertFalse(MediaDecoding.looksLikePng(real),
            "a corrupted signature must not pass");
    }

    @Test
    void rejectsTextThatMerelyClaimsToBePng() {
        byte[] text = "I am not a PNG, I am text pretending to be one"
            .getBytes(StandardCharsets.UTF_8);
        assertFalse(MediaDecoding.looksLikePng(text),
            "the exact W32 fabrication input must be refused");
    }

    @Test
    void refusesToDecodeWhatItCannotRecognise() {
        byte[] text = "not media at all".getBytes(StandardCharsets.UTF_8);
        assertNull(MediaDecoding.decodeWav(text),
            "decodeWav must return null rather than invent samples");
        assertNull(MediaDecoding.decodePng(text),
            "decodePng must return null rather than invent pixels");
    }

    // ---- real WAV decoding ------------------------------------------------

    @Test
    void decodesARealWavIntoTheSampleRateTheHeaderDeclares() {
        // A 440 Hz tone at 8 kHz. If the decoder honours the header, the samples have
        // period 8000/440 ~= 18.18. If it falls back to bytes, this cannot hold.
        byte[] wav = MediaDecodingTestFixtures.synthWav(440.0, 8000, 0.25);
        MediaDecoding.Audio a = MediaDecoding.decodeWav(wav);
        assertNotNull(a, "a valid WAV must decode");
        assertEquals(8000, a.sampleRate(), "sample rate must come from the header, not a guess");
        assertTrue(a.samples().length > 1000,
            "expected ~2000 samples at 8 kHz for 0.25 s, got " + a.samples().length);
    }

    @Test
    void theDecodedWaveformActuallyContainsTheToneFrequency() {
        // THE test that was missing. A wiring test cannot tell a real decode from a byte
        // hash; this one can. Count zero crossings of a 440 Hz tone at 8 kHz: expected
        // 2*440*duration crossings, i.e. 220 for 0.25 s.
        byte[] wav = MediaDecodingTestFixtures.synthWav(440.0, 8000, 0.25);
        MediaDecoding.Audio a = MediaDecoding.decodeWav(wav);
        assertNotNull(a);
        int crossings = 0;
        float[] s = a.samples();
        for (int i = 1; i < s.length; i++) {
            if ((s[i - 1] < 0.0f) != (s[i] < 0.0f)) crossings++;
        }
        int expected = (int) (2 * 440 * 0.25);
        // Allow 20% for the non-integer number of periods in a finite window.
        assertTrue(Math.abs(crossings - expected) <= expected * 0.20,
            "zero crossings " + crossings + " should be near " + expected
                + " for a 440 Hz tone; a much lower count means the bytes were not decoded");
    }

    @Test
    void aDifferentFrequencyDecodesToADifferentWaveform() {
        // A 100 Hz tone and a 440 Hz tone have the same file structure. Under the old
        // byte-hash the digest was an accident of the bytes; under a real decode the
        // waveforms are genuinely different signals.
        MediaDecoding.Audio low = MediaDecoding.decodeWav(MediaDecodingTestFixtures.synthWav(100.0, 8000, 0.25));
        MediaDecoding.Audio high = MediaDecoding.decodeWav(MediaDecodingTestFixtures.synthWav(440.0, 8000, 0.25));
        assertNotNull(low);
        assertNotNull(high);
        assertFalse(java.util.Arrays.equals(low.samples(), high.samples()),
            "100 Hz and 440 Hz must decode to different waveforms");
    }

    @Test
    void refusesATruncatedWavRatherThanGuessing() {
        byte[] good = MediaDecodingTestFixtures.synthWav(440.0, 8000, 0.1);
        byte[] truncated = java.util.Arrays.copyOf(good, 20);
        assertNull(MediaDecoding.decodeWav(truncated),
            "a WAV missing its data chunk must be refused, not half-decoded");
    }

    // ---- real PNG decoding ------------------------------------------------

    @Test
    void decodesARealPngToItsDeclaredDimensions() {
        MediaDecoding.Image img = MediaDecoding.decodePng(MediaDecodingTestFixtures.synthPng(8, 8, 200, 30, 30));
        assertNotNull(img, "a valid PNG must decode");
        assertEquals(8, img.width());
        assertEquals(8, img.height());
    }

    @Test
    void theDecodedImageActuallyContainsTheEncodedColour() {
        // The W32 defect in one assertion: a solid red image must decode to red pixels.
        // Under the old byte-copy the "pixels" were PNG file bytes, so the colour was a
        // property of zlib compression, not of the image.
        MediaDecoding.Image img = MediaDecoding.decodePng(MediaDecodingTestFixtures.synthPng(8, 8, 200, 30, 30));
        assertNotNull(img);
        int r = img.r(0, 0), g = img.g(0, 0), b = img.b(0, 0);
        assertTrue(r > 150 && g < 80 && b < 80,
            "expected a red pixel, got r=" + r + " g=" + g + " b=" + b);
    }

    @Test
    void twoImagesOfDifferentColourDecodeDifferently() {
        MediaDecoding.Image red = MediaDecoding.decodePng(MediaDecodingTestFixtures.synthPng(8, 8, 220, 20, 20));
        MediaDecoding.Image blue = MediaDecoding.decodePng(MediaDecodingTestFixtures.synthPng(8, 8, 20, 20, 220));
        assertNotNull(red);
        assertNotNull(blue);
        assertFalse(red.r(0, 0) == blue.r(0, 0) && red.g(0, 0) == blue.g(0, 0),
            "a red image and a blue image must not decode identically");
    }

    // ---- the watcher must not persist a perception it cannot support -------

    @Test
    void theWatcherRefusesATextFileNamedWav(@TempDir Path dir) throws IOException {
        Path inbox = dir.resolve("inbox");
        Files.createDirectories(inbox);
        Files.writeString(inbox.resolve("fake.wav"),
            "I am not audio, I am text pretending to be a wav file");
        Files.writeString(inbox.resolve("notes.txt"), "a real text file");

        // A .wav that is not one must not become a knowledge fact. A .txt is fine.
        MediaDecoding.Classification c = MediaDecoding.classify(
            inbox.resolve("fake.wav"), Files.readAllBytes(inbox.resolve("fake.wav")));
        assertNotNull(c);
        assertFalse(c.decodable(),
            "a text file named .wav must not be treated as decodable audio");
        assertTrue(c.reason().toLowerCase().contains("magic")
                || c.reason().toLowerCase().contains("signature"),
            "the refusal must say why, got: " + c.reason());
    }

    // ---- RECON-W32.1: frequency discrimination ---------------------------

    /**
     * The 8-band decomposition cannot separate these tones: at 8 kHz each band spans
     * 500 Hz, so both land in "0-500 Hz". Peak-picking on the raw spectrum must.
     */
    @Test
    void peakEstimateSeparatesTonesTheBandDecompositionCannot() {
        var enc = new io.matrix.transcoders.AudioFFTEncoder(2048, 8, 42L);
        double hz100 = estimate(enc, 100.0);
        double hz440 = estimate(enc, 440.0);
        assertTrue(Math.abs(hz100 - 100.0) < 15.0,
            "100 Hz must be estimated near 100, got " + hz100);
        assertTrue(Math.abs(hz440 - 440.0) < 15.0,
            "440 Hz must be estimated near 440, got " + hz440);
        assertTrue(Math.abs(hz440 - hz100) > 100.0,
            "the two estimates must differ by far more than the tolerance, got "
                + hz100 + " and " + hz440);
    }

    private static double estimate(io.matrix.transcoders.AudioFFTEncoder enc, double hz) {
        MediaDecoding.Audio a =
            MediaDecoding.decodeWav(MediaDecodingTestFixtures.synthWav(hz, 8000, 0.25));
        var frame = enc.computeDFT(a.samples(), a.sampleRate());
        return MediaDecoding.dominantFrequency(frame.magnitudes(), a.sampleRate()).hz();
    }

    @Test
    void aSilentSpectrumIsReportedAsSilentRatherThanZeroHertz() {
        // Reporting 0 Hz for silence would be a fabricated perception: a claim of a
        // frequency where none exists.
        MediaDecoding.PeakEstimate p = MediaDecoding.dominantFrequency(
            new float[2048], 8000);
        assertTrue(p.isSilent(), "an all-zero spectrum must not yield a frequency");
        assertTrue(Double.isNaN(p.hz()));
        assertTrue(p.note().contains("silent"), p.note());
    }

    @Test
    void degenerateInputsAreRefusedRatherThanEstimated() {
        assertTrue(MediaDecoding.dominantFrequency(null, 8000).isSilent());
        assertTrue(MediaDecoding.dominantFrequency(new float[0], 8000).isSilent());
        assertTrue(MediaDecoding.dominantFrequency(new float[2], 8000).isSilent());
        assertTrue(MediaDecoding.dominantFrequency(new float[512], 0).isSilent());
    }

    @Test
    void theEstimateIsDeterministic() {
        var enc = new io.matrix.transcoders.AudioFFTEncoder(2048, 8, 42L);
        double first = estimate(enc, 440.0);
        for (int i = 0; i < 10; i++) {
            assertEquals(first, estimate(enc, 440.0), 1e-9,
                "Article III: same input, same estimate");
        }
    }

    // ---- RECON-W32.23: a refusal must name what IS supported ----------------

    @Test
    void anUnsupportedImageFormatSaysSoRatherThanBlamingTheFile() {
        // "unrecognised content" tells an operator their JPEG is corrupt. It is not; we
        // simply have no decoder for it, and that is a different problem with a
        // different fix. My first attempt put this check beside the extension tests,
        // where it was UNREACHABLE - a real JPEG is binary, so it never reaches
        // looksLikeText and falls through to the generic message.
        byte[] realJpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1, 0, 1, 0, 0};
        var c = MediaDecoding.classify("photo.jpg", realJpeg);
        assertFalse(c.decodable());
        String r = c.reason().toLowerCase(java.util.Locale.ROOT);
        assertTrue(r.contains("png") && r.contains("unsupported"),
            "the refusal must name the supported format and say the format is the "
                + "problem, got: " + c.reason());
        assertTrue(r.contains("may be perfectly valid"),
            "and must not imply the operator's file is broken: " + c.reason());
    }

    @Test
    void everyRefusalReasonIsActionable() {
        // A refusal that does not say what to do next is a dead end for an operator.
        //
        // RECON-W32.24: this test was VACUOUS and a mutation check proved it - deleting
        // the entire W32.23 branch left it PASSING. The cause was the `continue` guard:
        // for text input, x.jpg/x.bmp/x.bin are all decodable, so 3 of 5 inputs skipped
        // the body, and the 2 that did not both hit the PRE-EXISTING text branch rather
        // than the new code. With no counter, a future change that made every input
        // decodable would run the body ZERO times and JUnit would still report PASSED.
        //
        // The fix is the counter below. A test that can execute its assertions zero times
        // is not a test, and the mutation check is the only reason that was caught.
        int checked = 0;
        byte[] text = "I am not audio".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (String name : new String[]{"x.wav", "x.png", "x.jpg", "x.bmp", "x.bin"}) {
            var c = MediaDecoding.classify(name, text);
            if (c.decodable()) continue;                       // not a refusal
            checked++;
            assertTrue(c.reason().length() > 40,
                "reason for " + name + " is too terse to act on: " + c.reason());
            assertTrue(c.reason().toLowerCase(java.util.Locale.ROOT).contains("refused"),
                "every refusal must say it is one: " + c.reason());
        }
        assertTrue(checked > 0, "the loop checked nothing, so nothing was verified");
    }

    // ---- RECON-W32.24: the D7 defect - the message must not assert a format it
    // never checked. An ELF binary named photo.jpg is not a JPEG, and saying
    // "the file looks like JPEG... may be perfectly valid" is a small violation of
    // this class's own doctrine: never return a measurement you cannot support.

    @Test
    void aBinaryWithAnImageNameDoesNotGetToldItLooksLikeAnImage() {
        byte[] elf = new byte[]{0x7F, 'E', 'L', 'F', 0x02, 0x01, 0x01, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        var c = MediaDecoding.classify("photo.jpg", elf);
        assertFalse(c.decodable(), "an ELF is not an image and must not decode");
        String r = c.reason().toLowerCase(java.util.Locale.ROOT);
        assertFalse(r.contains("looks like jpeg"),
            "the refusal must not claim a format it never verified: " + c.reason());
        assertTrue(r.contains("refused"), "and it must still be a refusal: " + c.reason());
    }

    @Test
    void aTextFileWithAnImageExtensionIsReadAsTextNotAsAnImage() {
        // The case the previous version got wrong by expecting a refusal. The file really
        // is text, so reading it as text is correct - but it must not be reported as a
        // decoded image, or the operator would believe a perception had been extracted.
        byte[] text = "I am not audio".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var c = MediaDecoding.classify("x.jpg", text);
        assertTrue(c.decodable(), "text is readable");
        assertTrue(c.reason().toLowerCase(java.util.Locale.ROOT).contains("text"),
            "and it must be reported as text, not as an image: " + c.reason());
        assertEquals(MediaDecoding.Kind.TEXT, c.kind(),
            "a text file must be classified as text whatever its extension says");
    }
}
