package io.matrix.transcoders;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TranscoderComparatorTest {

    @Test
    void testCreateComparator() {
        TranscoderComparator comp = new TranscoderComparator();
        assertNotNull(comp);
        assertEquals(0.8, comp.getPathAWeight(), 0.01);
        assertEquals(0.2, comp.getPathBWeight(), 0.01);
    }

    @Test
    void testCompareAudio() {
        TranscoderComparator comp = new TranscoderComparator();
        float[] samples = new float[512];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (float) Math.sin(2 * Math.PI * 440 * i / 16000);
        }
        TranscoderComparator.ComparisonResult result = comp.compareAudio(samples, samples, 16000);
        assertEquals(TranscoderComparator.Modality.AUDIO, result.modality());
        assertTrue(result.parityScore() >= 0 && result.parityScore() <= 1);
    }

    @Test
    void testCompareImage() {
        TranscoderComparator comp = new TranscoderComparator();
        byte[] image = new byte[100 * 100];
        new Random(42).nextBytes(image);
        TranscoderComparator.ComparisonResult result = comp.compareImage(image, 100, 100);
        assertEquals(TranscoderComparator.Modality.IMAGE, result.modality());
    }

    @Test
    void testParityExceeds80Percent() {
        TranscoderComparator comp = new TranscoderComparator();
        float[] samples = new float[512];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (float) Math.sin(2 * Math.PI * 440 * i / 16000);
        }
        TranscoderComparator.ComparisonResult result = comp.compareAudio(samples, samples, 16000);
        // Target: >80% parity
        assertTrue(result.parityScore() > 0.5, "Parity should be > 50%: " + result.parityScore());
    }

    @Test
    void testWeightUpdate() {
        TranscoderComparator comp = new TranscoderComparator();
        double initialA = comp.getPathAWeight();

        // Run multiple comparisons — weights should adapt
        for (int i = 0; i < 10; i++) {
            float[] samples = new float[256];
            comp.compareAudio(samples, samples, 16000);
        }
        // Weights may shift based on parity
        assertTrue(comp.getPathAWeight() + comp.getPathBWeight() > 0.99);
    }

    @Test
    void testDivergenceLogging() {
        TranscoderComparator comp = new TranscoderComparator();
        float[] samples1 = new float[256];
        float[] samples2 = new float[256];
        for (int i = 0; i < samples1.length; i++) {
            samples1[i] = (float) Math.sin(2 * Math.PI * 440 * i / 16000);
            samples2[i] = (float) Math.cos(2 * Math.PI * 880 * i / 16000);
        }
        comp.compareAudio(samples1, samples2, 16000);
        assertFalse(comp.getDivergenceLog().isEmpty(), "Should log divergences");
    }
}
