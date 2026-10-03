package io.matrix.transcoders;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AudioFFTEncoderTest {

    @Test
    void testCreateEncoder() {
        AudioFFTEncoder encoder = new AudioFFTEncoder(1024, 8, 42L);
        assertNotNull(encoder);
        assertEquals(1024, encoder.getDimension());
        assertEquals(8, encoder.getBandCount());
    }

    @Test
    void testComputeDFT() {
        AudioFFTEncoder encoder = new AudioFFTEncoder(1024, 8, 42L);
        float[] samples = new float[512];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (float) Math.sin(2 * Math.PI * 440 * i / 16000);
        }
        AudioFFTEncoder.SpectralFrame frame = encoder.computeDFT(samples, 16000);
        assertEquals(256, frame.magnitudes().length);
        assertEquals(256, frame.phases().length);
        assertEquals(16000, frame.sampleRate());
    }

    @Test
    void testExtractBands() {
        AudioFFTEncoder encoder = new AudioFFTEncoder(1024, 8, 42L);
        float[] samples = new float[256];
        Arrays.fill(samples, (float) Math.sin(2 * Math.PI * 440 * 0 / 16000));
        AudioFFTEncoder.SpectralFrame frame = encoder.computeDFT(samples, 16000);
        List<AudioFFTEncoder.FrequencyBand> bands = encoder.extractBands(frame);
        assertEquals(8, bands.size());
        for (AudioFFTEncoder.FrequencyBand band : bands) {
            assertTrue(band.highHz() > band.lowHz());
            assertTrue(band.energy() >= 0);
        }
    }

    @Test
    void testEncodeToHDC() {
        AudioFFTEncoder encoder = new AudioFFTEncoder(1024, 8, 42L);
        float[] samples = new float[256];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (float) (Math.random() * 2 - 1);
        }
        AudioFFTEncoder.SpectralFrame frame = encoder.computeDFT(samples, 16000);
        List<AudioFFTEncoder.FrequencyBand> bands = encoder.extractBands(frame);
        boolean[] hdc = encoder.encodeToHDC(bands);
        assertEquals(1024, hdc.length);
    }

    @Test
    void testOneShotEncode() {
        AudioFFTEncoder encoder = new AudioFFTEncoder(1024, 8, 42L);
        float[] samples = new float[512];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (float) Math.sin(2 * Math.PI * 440 * i / 16000);
        }
        boolean[] hdc = encoder.encode(samples, 16000);
        assertEquals(1024, hdc.length);
        // Should have some true bits
        int trueCount = 0;
        for (boolean b : hdc) if (b) trueCount++;
        assertTrue(trueCount > 0);
    }

    /**
     * RECON-W32.5 — Article III: a frame must be a deterministic function of its input.
     *
     * <p>The encoder stamped frames with System.currentTimeMillis(), so two runs over the
     * same audio produced different SpectralFrames. Anything that compared or hashed a
     * frame — content dedup in the inbox watcher, a test asserting a frame's contents —
     * was comparing noise.</p>
     */
    @org.junit.jupiter.api.Test
    void aFixedClockMakesTheFrameByteIdenticalAcrossRuns() {
        AudioFFTEncoder a = new AudioFFTEncoder(256, 8, 42L,
            AudioFFTEncoder.fixedClock(1234L));
        AudioFFTEncoder b = new AudioFFTEncoder(256, 8, 42L,
            AudioFFTEncoder.fixedClock(1234L));
        float[] samples = new float[64];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (float) Math.sin(2 * Math.PI * 5 * i / samples.length);
        }
        var fa = a.computeDFT(samples, 8000);
        var fb = b.computeDFT(samples, 8000);
        org.junit.jupiter.api.Assertions.assertArrayEquals(fa.magnitudes(), fb.magnitudes());
        org.junit.jupiter.api.Assertions.assertArrayEquals(fa.phases(), fb.phases());
        org.junit.jupiter.api.Assertions.assertEquals(fa.timestamp(), fb.timestamp());
        org.junit.jupiter.api.Assertions.assertEquals(1234L, fa.timestamp());
    }

    @org.junit.jupiter.api.Test
    void aNullClockIsRefused() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> new AudioFFTEncoder(256, 8, 42L, null));
    }
}
