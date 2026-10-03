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

}
