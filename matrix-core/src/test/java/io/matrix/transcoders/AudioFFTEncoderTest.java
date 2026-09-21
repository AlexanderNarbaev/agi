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
}
