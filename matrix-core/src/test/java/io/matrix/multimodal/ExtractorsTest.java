package io.matrix.multimodal;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ImageFeatureExtractorTest {

    @Test
    void extractReturns512Features() {
        var extractor = new ImageFeatureExtractor();
        byte[] data = new byte[900]; // 300 pixels RGB
        for (int i = 0; i < data.length; i++) data[i] = (byte) i;
        var features = extractor.extract(data);
        assertEquals(512, features.length);
    }

    @Test
    void modalityIsImage() {
        assertEquals("image", new ImageFeatureExtractor().modality());
    }

    @Test
    void emptyImageHandlesGracefully() {
        var extractor = new ImageFeatureExtractor();
        var features = extractor.extract(new byte[0]);
        assertEquals(512, features.length);
        for (float f : features) assertEquals(0.0f, f, 0.001f);
    }
}

class AudioFeatureExtractorTest {

    @Test
    void extractReturns128Features() {
        var extractor = new AudioFeatureExtractor();
        byte[] data = new byte[200]; // 100 samples 16-bit
        for (int i = 0; i < data.length; i++) data[i] = (byte) ((i % 256) - 128);
        var features = extractor.extract(data);
        assertEquals(128, features.length);
    }

    @Test
    void modalityIsAudio() {
        assertEquals("audio", new AudioFeatureExtractor().modality());
    }

    @Test
    void emptyAudioHandlesGracefully() {
        var extractor = new AudioFeatureExtractor();
        var features = extractor.extract(new byte[0]);
        assertEquals(128, features.length);
    }

    @Test
    void singleSampleHandlesGracefully() {
        var extractor = new AudioFeatureExtractor();
        var features = extractor.extract(new byte[]{0, 50});
        assertEquals(128, features.length);
    }
}
